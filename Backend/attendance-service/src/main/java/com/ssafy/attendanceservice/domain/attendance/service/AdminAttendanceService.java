package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.dto.AdminWorkStatusLogRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.DailyAttendanceResponse;

public interface AdminAttendanceService {

    /** 근무 상태 로그 직접 등록. 등록 후 그날 근태를 재동기화하고 수정된 일별 타임라인을 반환 */
    DailyAttendanceResponse registerLog(Long adminId, AdminWorkStatusLogRequest request);
}