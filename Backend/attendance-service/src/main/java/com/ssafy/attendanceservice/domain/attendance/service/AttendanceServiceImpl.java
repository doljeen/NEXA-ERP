package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.calculator.AnomalyJudge;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkLogEntry;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkTimeCalculator;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkTimeResult;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceStatusResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.WorkStatusCodeResponse;
import com.ssafy.attendanceservice.domain.attendance.entity.*;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AnomalyType;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.WorkStatusSource;
import com.ssafy.attendanceservice.domain.attendance.repository.*;
import com.ssafy.attendanceservice.global.exception.BusinessException;
import com.ssafy.attendanceservice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    // 퇴근 후 퇴근취소(상태변경)를 허용하는 시간
    private static final Duration CLOCK_OUT_CANCEL_LIMIT = Duration.ofHours(2);

    private final AttendanceAnomalyRepository attendanceAnomalyRepository;

    // 버튼(출근/퇴근)으로만 처리하는 상태. 상태변경 목록에서 제외한다.
    private static final List<String> CLOCK_STATUS_CODES = List.of(
            WorkStatusCode.CLOCK_IN,
            WorkStatusCode.CLOCK_OUT,
            WorkStatusCode.CLOCK_OUT_CANCEL
    );

    private final AttendanceDailyRepository attendanceDailyRepository;
    private final WorkStatusLogRepository workStatusLogRepository;
    private final WorkStatusCodeRepository workStatusCodeRepository;
    private final EmployeeWorkGroupRepository employeeWorkGroupRepository;
    private final WorkGroupRepository workGroupRepository;
    private final HolidayRepository holidayRepository;

    @Override
    @Transactional
    public AttendanceStatusResponse clockIn(Long empId) {
        OffsetDateTime now = OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS);
        LocalDate today = now.toLocalDate();

        // 1. 출근은 하루 1번
        if (attendanceDailyRepository.existsByEmpIdAndWorkDate(empId, today)) {
            throw new BusinessException(ErrorCode.ALREADY_CLOCKED_IN);
        }

        // 2. 직전 근무에 퇴근이 없으면 출퇴근 누락 (오늘 기록을 만들기 전에 판정)
        attendanceDailyRepository.findTopByEmpIdOrderByWorkDateDesc(empId)
                .filter(previous -> !previous.isClockedOut())
                .ifPresent(previous -> saveAnomalies(previous, List.of(AnomalyType.MISSING_CLOCK)));

        // 3. 적용 근무그룹, 휴일 여부
        WorkGroup workGroup = resolveWorkGroup(empId, today);
        boolean isHoliday = holidayRepository.existsById(today)
                || !workGroup.isWorkDay(today.getDayOfWeek());

        // 4. 일일 근태 생성
        AttendanceDaily daily = attendanceDailyRepository.save(AttendanceDaily.builder()
                .workGroup(workGroup)
                .empId(empId)
                .workDate(today)
                .clockIn(now)
                .isHoliday(isHoliday)
                .build());

        // 5. 지각 판정
        saveAnomalies(daily, AnomalyJudge.onClockIn(today, now, workGroup.getStartTime(), isHoliday));

        // 6. 출근 로그
        WorkStatusCode clockInCode = getStatusCode(WorkStatusCode.CLOCK_IN);
        workStatusLogRepository.save(WorkStatusLog.builder()
                .workStatusCode(clockInCode)
                .empId(empId)
                .workDate(today)
                .startAt(now)
                .source(WorkStatusSource.SELF)
                .createdBy(empId)
                .build());

        return AttendanceStatusResponse.of(daily, clockInCode, getAbleStatuses(clockInCode));
    }

    @Override
    @Transactional
    public AttendanceStatusResponse clockOut(Long empId) {
        OffsetDateTime now = OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS);

        // 1. 가장 최근 근태 (자정을 넘긴 퇴근도 출근일 기록에 처리)
        AttendanceDaily daily = attendanceDailyRepository.findTopByEmpIdOrderByWorkDateDesc(empId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CLOCKED_IN));

        // 2. 퇴근 취소 없이 다시 퇴근할 수 없음
        if (daily.isClockedOut()) {
            throw new BusinessException(ErrorCode.ALREADY_CLOCKED_OUT);
        }

        // 3. 퇴근 시각 기록 (변경 감지로 UPDATE)
        daily.clockOut(now);

        // 4. 퇴근 로그
        WorkStatusCode clockOutCode = getStatusCode(WorkStatusCode.CLOCK_OUT);
        workStatusLogRepository.save(WorkStatusLog.builder()
                .workStatusCode(clockOutCode)
                .empId(empId)
                .workDate(daily.getWorkDate())
                .startAt(now)
                .source(WorkStatusSource.SELF)
                .createdBy(empId)
                .build());

        // 5. 근로시간 집계 + 근태이상 판정
        WorkTimeResult workTime = recalculate(daily);
        refreshClockOutAnomalies(daily, workTime);

        return AttendanceStatusResponse.of(daily, clockOutCode, getAbleStatuses(clockOutCode));
    }

    @Override
    @Transactional
    public AttendanceStatusResponse changeStatus(Long empId, String statusCode) {
        OffsetDateTime now = OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS);

        // 1. 가장 최근 근태와 현재 상태
        AttendanceDaily daily = attendanceDailyRepository.findTopByEmpIdOrderByWorkDateDesc(empId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CLOCKED_IN));
        WorkStatusLog lastLog = workStatusLogRepository
                .findTopByEmpIdAndWorkDateAndIsDeletedFalseOrderByStartAtDescLogIdDesc(empId, daily.getWorkDate())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CLOCKED_IN));
        WorkStatusCode current = lastLog.getWorkStatusCode();

        // 2. 퇴근 후 2시간이 지나면 변경 불가 (이후는 근무시간 수정 신청으로 처리)
        if (daily.isClockedOut() && now.isAfter(daily.getClockOut().plus(CLOCK_OUT_CANCEL_LIMIT))) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_CHANGE);
        }

        // 3. 요청한 상태가 지금 바꿀 수 있는 상태인지
        WorkStatusCode target = getAbleStatuses(current).stream()
                .filter(code -> code.getStatusCode().equals(statusCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_CHANGE));

        // 4. 퇴근 상태였다면 퇴근취소 (집계 초기화, 퇴근 판정 근태이상 삭제)
        if (daily.isClockedOut()) {
            lastLog.changeWorkStatusCode(getStatusCode(WorkStatusCode.CLOCK_OUT_CANCEL));
            daily.cancelClockOut();
            WorkTimeResult workTime = recalculate(daily);
            refreshClockOutAnomalies(daily, workTime);
        }

        // 5. 상태 로그
        workStatusLogRepository.save(WorkStatusLog.builder()
                .workStatusCode(target)
                .empId(empId)
                .workDate(daily.getWorkDate())
                .startAt(now)
                .source(WorkStatusSource.SELF)
                .createdBy(empId)
                .build());

        return AttendanceStatusResponse.of(daily, target, getAbleStatuses(target));
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceStatusResponse getToday(Long empId) {
        OffsetDateTime now = OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS);
        LocalDate today = now.toLocalDate();

        // 1. 근태 기록이 없으면 출근 전
        Optional<AttendanceDaily> latest = attendanceDailyRepository.findTopByEmpIdOrderByWorkDateDesc(empId);
        if (latest.isEmpty()) {
            return AttendanceStatusResponse.beforeClockIn(today);
        }

        AttendanceDaily daily = latest.get();
        WorkStatusCode current = workStatusLogRepository
                .findTopByEmpIdAndWorkDateAndIsDeletedFalseOrderByStartAtDescLogIdDesc(empId, daily.getWorkDate())
                .map(WorkStatusLog::getWorkStatusCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CLOCKED_IN));

        // 2. 퇴근 후 2시간이 지난 경우
        if (daily.isClockedOut() && now.isAfter(daily.getClockOut().plus(CLOCK_OUT_CANCEL_LIMIT))) {
            // 오늘 근무: 다시 출근할 수 없으니 퇴근 상태로 보여주고 변경 불가
            if (daily.getWorkDate().equals(today)) {
                return AttendanceStatusResponse.of(daily, current, List.of());
            }
            // 이전 근무: 새 근무일 시작 전
            return AttendanceStatusResponse.beforeClockIn(today);
        }

        // 3. 근무 중이거나 퇴근 후 2시간 이내
        return AttendanceStatusResponse.of(daily, current, getAbleStatuses(current));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkStatusCodeResponse> getStatusCodes() {
        return workStatusCodeRepository.findAll(Sort.by("statusCode")).stream()
                .map(WorkStatusCodeResponse::from)
                .toList();
    }

    /** 해당 날짜에 배정된 근무그룹. 배정이 없으면 기본 그룹 */
    private WorkGroup resolveWorkGroup(Long empId, LocalDate date) {
        return employeeWorkGroupRepository.findEffectiveWorkGroup(empId, date)
                .or(workGroupRepository::findByIsDefaultTrue)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEFAULT_WORK_GROUP_NOT_FOUND));
    }

    private WorkStatusCode getStatusCode(String statusCode) {
        return workStatusCodeRepository.findById(statusCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_STATUS_CODE_NOT_FOUND));
    }

    /** 현재 상태에서 바꿀 수 있는 상태 목록 */
    private List<WorkStatusCode> getAbleStatuses(WorkStatusCode current) {
        // 퇴근 후에는 업무로만 바꿀 수 있음 (업무로 바꾸면 퇴근취소)
        if (WorkStatusCode.CLOCK_OUT.equals(current.getStatusCode())) {
            return List.of(getStatusCode(WorkStatusCode.WORKING));
        }

        // 그 외: 출퇴근 버튼 상태와 현재 상태 제외
        return workStatusCodeRepository.findByStatusCodeNotIn(CLOCK_STATUS_CODES).stream()
                .filter(code -> !code.getStatusCode().equals(current.getStatusCode()))
                .toList();
    }

    /** 그 근무일의 로그로 근로시간을 다시 계산해서 반영 (퇴근 전이면 0) */
    private WorkTimeResult recalculate(AttendanceDaily daily) {
        List<WorkLogEntry> entries = workStatusLogRepository
                .findByEmpIdAndWorkDateAndIsDeletedFalseOrderByStartAtAscLogIdAsc(daily.getEmpId(), daily.getWorkDate())
                .stream()
                .map(log -> new WorkLogEntry(log.getStartAt(), log.getWorkStatusCode().getCountAsWork()))
                .toList();

        WorkGroup workGroup = daily.getWorkGroup();
        WorkTimeResult result = WorkTimeCalculator.calculate(
                entries,
                daily.getClockOut(),
                workGroup.getDailyStdMinutes(),
                workGroup.getBreakMinutes(),
                daily.getIsHoliday());
        daily.applyWorkTime(result);
        return result;
    }

    /** 퇴근 판정 근태이상 갱신: 기존 판정분을 지우고, 퇴근 상태면 다시 판정 */
    private void refreshClockOutAnomalies(AttendanceDaily daily, WorkTimeResult workTime) {
        attendanceAnomalyRepository.deleteByAttendanceDailyAndAnomalyTypeIn(daily, AnomalyJudge.clockOutTypes());
        if (!daily.isClockedOut()) {
            return;
        }
        WorkGroup workGroup = daily.getWorkGroup();
        saveAnomalies(daily, AnomalyJudge.onClockOut(
                daily.getWorkDate(),
                daily.getClockOut(),
                workGroup.getEndTime(),
                workGroup.getDailyStdMinutes(),
                daily.getIsHoliday(),
                workTime));
    }

    private void saveAnomalies(AttendanceDaily daily, List<AnomalyType> types) {
        types.forEach(type -> attendanceAnomalyRepository.save(AttendanceAnomaly.builder()
                .attendanceDaily(daily)
                .anomalyType(type)
                .build()));
    }
}