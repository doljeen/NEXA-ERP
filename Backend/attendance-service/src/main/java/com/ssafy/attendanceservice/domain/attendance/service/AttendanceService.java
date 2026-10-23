package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceStatusResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceSummaryResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.DailyAttendanceResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.MonthlyAttendanceResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.WeeklyAttendanceResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.WorkStatusCodeResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.WorkStatusHistoryResponse;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface AttendanceService {

    /** 출근. 오늘 이미 출근했으면 BusinessException(ALREADY_CLOCKED_IN) */
    AttendanceStatusResponse clockIn(Long empId);

    /** 퇴근. 출근 기록이 없거나 이미 퇴근했으면 BusinessException */
    AttendanceStatusResponse clockOut(Long empId);

    /** 근무상태 변경. 퇴근 상태에서 업무로 바꾸면 퇴근취소. 바꿀 수 없는 상태면 BusinessException */
    AttendanceStatusResponse changeStatus(Long empId, String statusCode);

    /** 현재 근무 상태 조회 (근태 위젯) */
    AttendanceStatusResponse getToday(Long empId);

    /** 근무 상태 코드 전체 목록 */
    List<WorkStatusCodeResponse> getStatusCodes();

    /** 일별 근태 타임라인. 기록이 없는 날은 빈 응답 */
    DailyAttendanceResponse getDaily(Long empId, LocalDate date);

    /** 주간 근태 (date가 포함된 월~일) */
    WeeklyAttendanceResponse getWeekly(Long empId, LocalDate date);

    /** 월간 근태 (1일 ~ 말일) */
    MonthlyAttendanceResponse getMonthly(Long empId, YearMonth month);

    /** 근무상태 변경 이력 (삭제 포함). from이 to보다 늦으면 BusinessException(INVALID_INPUT) */
    List<WorkStatusHistoryResponse> getHistory(Long empId, LocalDate from, LocalDate to);

    /** 급여용 월별 근태 집계 */
    AttendanceSummaryResponse getSummary(Long empId, YearMonth month);
}