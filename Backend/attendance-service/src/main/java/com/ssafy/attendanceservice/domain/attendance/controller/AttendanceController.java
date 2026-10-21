package com.ssafy.attendanceservice.domain.attendance.controller;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceStatusResponse;
import com.ssafy.attendanceservice.domain.attendance.service.AttendanceService;
import com.ssafy.attendanceservice.global.dto.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 본인 근태 API */
// TODO: 인증 적용 전까지 사용자 식별은 X-User-Id 헤더로 임시 처리
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attn")
public class AttendanceController {

    private final AttendanceService attendanceService;

    // 출근 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @PostMapping("/clock-in")
    public ResponseEntity<ApiResult<AttendanceStatusResponse>> clockIn(@RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(ApiResult.success(attendanceService.clockIn(empId)));
    }
}