package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceStatusResponse;

public interface AttendanceService {

    /** 출근. 오늘 이미 출근했으면 BusinessException(ALREADY_CLOCKED_IN) */
    AttendanceStatusResponse clockIn(Long empId);

    /** 퇴근. 출근 기록이 없거나 이미 퇴근했으면 BusinessException */
    AttendanceStatusResponse clockOut(Long empId);

    /** 근무상태 변경. 퇴근 상태에서 업무로 바꾸면 퇴근취소. 바꿀 수 없는 상태면 BusinessException */
    AttendanceStatusResponse changeStatus(Long empId, String statusCode);

    /** 현재 근무 상태 조회 (근태 위젯) */
    AttendanceStatusResponse getToday(Long empId);
}