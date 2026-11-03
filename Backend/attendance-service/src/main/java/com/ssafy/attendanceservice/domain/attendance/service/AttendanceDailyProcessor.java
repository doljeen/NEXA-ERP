package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.calculator.AnomalyJudge;
import com.ssafy.attendanceservice.domain.attendance.calculator.ApprovedOvertime;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkLogEntry;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkTimeCalculator;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkTimeResult;
import com.ssafy.attendanceservice.domain.attendance.entity.*;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AnomalyType;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestStatus;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;
import com.ssafy.attendanceservice.domain.attendance.repository.AttendanceAnomalyRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.AttendanceDailyRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.AttendanceRequestRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.EmployeeWorkGroupRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.HolidayRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkGroupRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusCodeRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusLogRepository;
import com.ssafy.attendanceservice.global.exception.BusinessException;
import com.ssafy.attendanceservice.global.exception.ErrorCode;
import com.ssafy.attendanceservice.global.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 일일 근태 공통 처리. 본인 출퇴근, 관리자 로그 수정, 결재 반영이 같은 규칙으로 동작하도록 한 곳에 모은다.
 * 트랜잭션은 호출하는 서비스 메서드의 트랜잭션에 참여한다.
 */
@Component
@RequiredArgsConstructor
public class AttendanceDailyProcessor {

    private final WorkStatusLogRepository workStatusLogRepository;
    private final WorkStatusCodeRepository workStatusCodeRepository;
    private final AttendanceAnomalyRepository attendanceAnomalyRepository;
    private final EmployeeWorkGroupRepository employeeWorkGroupRepository;
    private final WorkGroupRepository workGroupRepository;
    private final HolidayRepository holidayRepository;
    private final AttendanceRequestRepository attendanceRequestRepository;
    private final AttendanceDailyRepository attendanceDailyRepository;

    // 휴일 판정에 반영하는 휴일대체 상태 (승인된 것만)
    private static final List<AttendanceRequestStatus> APPROVED_ONLY = List.of(AttendanceRequestStatus.APPROVED);

    /** 해당 날짜에 배정된 근무그룹. 배정이 없으면 기본 그룹 */
    public WorkGroup resolveWorkGroup(Long empId, LocalDate date) {
        return employeeWorkGroupRepository.findEffectiveWorkGroup(empId, date)
                .or(workGroupRepository::findByIsDefaultTrue)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEFAULT_WORK_GROUP_NOT_FOUND));
    }

    /**
     * 휴일 여부. 승인된 휴일대체가 우선이다: 근무한 휴일(targetDate)은 근무일, 대신 쉬는 날(substDate)은 휴일.
     * 그 외에는 공휴일이거나 근무그룹의 근무 요일이 아니면 휴일
     */
    public boolean isHoliday(Long empId, LocalDate date, WorkGroup workGroup) {
        if (attendanceRequestRepository.existsByEmpIdAndReqTypeAndTargetDateAndStatusIn(
                empId, AttendanceRequestType.HOLIDAY_SUBST, date, APPROVED_ONLY)) {
            return false;
        }
        if (attendanceRequestRepository.existsByEmpIdAndReqTypeAndSubstDateAndStatusIn(
                empId, AttendanceRequestType.HOLIDAY_SUBST, date, APPROVED_ONLY)) {
            return true;
        }
        return holidayRepository.existsById(date) || !workGroup.isWorkDay(date.getDayOfWeek());
    }

    /** 로그 시각은 근무일 당일 또는 다음 날(야간 근무 퇴근)만 허용 */
    public void validateStartAt(LocalDate workDate, OffsetDateTime startAt) {
        if (startAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "startAt은 필수입니다.");
        }
        LocalDate startDate = startAt.atZoneSameInstant(DateTimeUtils.ZONE).toLocalDate();
        if (!startDate.equals(workDate) && !startDate.equals(workDate.plusDays(1))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "startAt은 근무일 당일 또는 다음 날이어야 합니다.");
        }
    }

    public WorkStatusCode getStatusCode(String statusCode) {
        return workStatusCodeRepository.findById(statusCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_STATUS_CODE_NOT_FOUND));
    }

    /** 그 근무일에 승인된 초과근무 신청의 시간 구간 (재계산과 근무시간 수정 미리보기가 같이 쓴다) */
    public List<ApprovedOvertime> approvedOvertimes(Long empId, LocalDate workDate) {
        return attendanceRequestRepository.findByEmpIdAndTargetDateAndReqTypeAndStatus(
                        empId, workDate, AttendanceRequestType.OVERTIME, AttendanceRequestStatus.APPROVED).stream()
                .flatMap(request -> request.getItems().stream())
                .filter(item -> item.getEndAt() != null)
                .map(item -> new ApprovedOvertime(item.getStartAt(), item.getEndAt()))
                .toList();
    }

    /** 그 근무일의 로그로 근로시간을 다시 계산해서 반영 (퇴근 전이면 0) */
    public WorkTimeResult recalculate(AttendanceDaily daily) {
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
                daily.getIsHoliday(),
                approvedOvertimes(daily.getEmpId(), daily.getWorkDate()));
        daily.applyWorkTime(result);
        return result;
    }

    /** 퇴근 판정 근태이상 갱신: 기존 판정분을 지우고, 퇴근 상태면 다시 판정 */
    public void refreshClockOutAnomalies(AttendanceDaily daily, WorkTimeResult workTime) {
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

    public void saveAnomalies(AttendanceDaily daily, List<AnomalyType> types) {
        types.forEach(type -> attendanceAnomalyRepository.save(AttendanceAnomaly.builder()
                .attendanceDaily(daily)
                .anomalyType(type)
                .build()));
    }

    /**
     * 로그 기준으로 그날의 근태 전체를 다시 맞춘다 (관리자 수정, 결재 반영 후).
     * 출퇴근 시각 → 근로시간 → 근태이상(지각, 퇴근 판정, 출퇴근 누락) 순서로 재계산한다.
     */
    public void syncDaily(AttendanceDaily daily) {
        List<WorkStatusLog> logs = workStatusLogRepository
                .findByEmpIdAndWorkDateAndIsDeletedFalseOrderByStartAtAscLogIdAsc(daily.getEmpId(), daily.getWorkDate());

        // 1. 출퇴근 시각: 출근은 첫 출근 로그, 퇴근은 마지막 퇴근 로그
        OffsetDateTime clockIn = logs.stream()
                .filter(log -> WorkStatusCode.CLOCK_IN.equals(log.getWorkStatusCode().getStatusCode()))
                .map(WorkStatusLog::getStartAt)
                .findFirst()
                .orElse(null);
        OffsetDateTime clockOut = logs.stream()
                .filter(log -> WorkStatusCode.CLOCK_OUT.equals(log.getWorkStatusCode().getStatusCode()))
                .map(WorkStatusLog::getStartAt)
                .reduce((first, second) -> second)
                .orElse(null);
        daily.syncClock(clockIn, clockOut);

        // 2. 근로시간
        WorkTimeResult workTime = recalculate(daily);

        // 3. 지각: 기존 판정을 지우고 출근 시각으로 다시 판정
        WorkGroup workGroup = daily.getWorkGroup();
        attendanceAnomalyRepository.deleteByAttendanceDailyAndAnomalyTypeIn(daily, List.of(AnomalyType.LATE));
        if (clockIn != null) {
            saveAnomalies(daily, AnomalyJudge.onClockIn(
                    daily.getWorkDate(), clockIn, workGroup.getStartTime(), daily.getIsHoliday()));
        }

        // 4. 출퇴근 누락: 퇴근이 생겼으면 해소
        if (clockOut != null) {
            attendanceAnomalyRepository.deleteByAttendanceDailyAndAnomalyTypeIn(daily, List.of(AnomalyType.MISSING_CLOCK));
        }

        // 5. 조퇴, 소정근로 미달, 미승인 초과근로
        refreshClockOutAnomalies(daily, workTime);
    }

    /**
     * 그날 근태 기록의 휴일 여부를 다시 판정하고 근태를 재계산한다 (휴일대체 승인 후).
     * 기록이 없는 날은 할 일이 없다 (조회 시 isHoliday로 판정)
     */
    public void resyncHoliday(Long empId, LocalDate date) {
        attendanceDailyRepository.findByEmpIdAndWorkDate(empId, date).ifPresent(daily -> {
            daily.changeHoliday(isHoliday(empId, date, daily.getWorkGroup()));
            syncDaily(daily);
        });
    }
}
