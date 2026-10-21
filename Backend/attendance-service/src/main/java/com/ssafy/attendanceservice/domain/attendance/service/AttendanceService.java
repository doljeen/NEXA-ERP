package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceStatusResponse;

public interface AttendanceService {

    /** 출근. 오늘 이미 출근했으면 BusinessException(ALREADY_CLOCKED_IN) */
    AttendanceStatusResponse clockIn(Long empId);
}