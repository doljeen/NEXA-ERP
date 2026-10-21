package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceStatusResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.WorkStatusCodeResponse;
import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceDaily;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkGroup;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusCode;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusLog;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.WorkStatusSource;
import com.ssafy.attendanceservice.domain.attendance.repository.AttendanceDailyRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.EmployeeWorkGroupRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.HolidayRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkGroupRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusCodeRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusLogRepository;
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

        // 2. 적용 근무그룹, 휴일 여부
        WorkGroup workGroup = resolveWorkGroup(empId, today);
        boolean isHoliday = holidayRepository.existsById(today)
                || !workGroup.isWorkDay(today.getDayOfWeek());

        // 3. 일일 근태 생성
        AttendanceDaily daily = attendanceDailyRepository.save(AttendanceDaily.builder()
                .workGroup(workGroup)
                .empId(empId)
                .workDate(today)
                .clockIn(now)
                .isHoliday(isHoliday)
                .build());

        // 4. 출근 로그
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

        // 4. 퇴근 상태였다면 퇴근취소
        if (daily.isClockedOut()) {
            lastLog.changeWorkStatusCode(getStatusCode(WorkStatusCode.CLOCK_OUT_CANCEL));
            daily.cancelClockOut();
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
}