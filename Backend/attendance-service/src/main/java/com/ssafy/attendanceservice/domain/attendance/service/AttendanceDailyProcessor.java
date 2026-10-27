package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.calculator.AnomalyJudge;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkLogEntry;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkTimeCalculator;
import com.ssafy.attendanceservice.domain.attendance.calculator.WorkTimeResult;
import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceAnomaly;
import com.ssafy.attendanceservice.domain.attendance.entity.AttendanceDaily;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkGroup;
import com.ssafy.attendanceservice.domain.attendance.entity.WorkStatusCode;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AnomalyType;
import com.ssafy.attendanceservice.domain.attendance.repository.AttendanceAnomalyRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.EmployeeWorkGroupRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.HolidayRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkGroupRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusCodeRepository;
import com.ssafy.attendanceservice.domain.attendance.repository.WorkStatusLogRepository;
import com.ssafy.attendanceservice.global.exception.BusinessException;
import com.ssafy.attendanceservice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

    /** 해당 날짜에 배정된 근무그룹. 배정이 없으면 기본 그룹 */
    public WorkGroup resolveWorkGroup(Long empId, LocalDate date) {
        return employeeWorkGroupRepository.findEffectiveWorkGroup(empId, date)
                .or(workGroupRepository::findByIsDefaultTrue)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEFAULT_WORK_GROUP_NOT_FOUND));
    }

    /** 휴일 여부: 공휴일이거나 근무그룹의 근무 요일이 아님 */
    public boolean isHoliday(LocalDate date, WorkGroup workGroup) {
        return holidayRepository.existsById(date) || !workGroup.isWorkDay(date.getDayOfWeek());
    }

    public WorkStatusCode getStatusCode(String statusCode) {
        return workStatusCodeRepository.findById(statusCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_STATUS_CODE_NOT_FOUND));
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
                daily.getIsHoliday());
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
}
