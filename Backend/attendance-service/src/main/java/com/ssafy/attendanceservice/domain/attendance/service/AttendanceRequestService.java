package com.ssafy.attendanceservice.domain.attendance.service;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewResponse;

public interface AttendanceRequestService {

    /** 신청 검증 및 결과 미리보기 (저장하지 않음) */
    AttendanceRequestPreviewResponse preview(Long empId, AttendanceRequestPreviewRequest request);
}