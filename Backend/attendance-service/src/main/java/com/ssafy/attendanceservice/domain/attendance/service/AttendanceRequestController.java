package com.ssafy.attendanceservice.domain.attendance.controller;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewResponse;
import com.ssafy.attendanceservice.domain.attendance.service.AttendanceRequestService;
import com.ssafy.attendanceservice.global.dto.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 본인 근태 신청서 API */
// TODO: 인증 적용 전까지 사용자 식별은 X-User-Id 헤더로 임시 처리
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attn/me/requests")
public class AttendanceRequestController {

    private final AttendanceRequestService attendanceRequestService;

    // 신청 검증 및 한도 계산 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @PostMapping("/preview")
    public ResponseEntity<ApiResult<AttendanceRequestPreviewResponse>> preview(
            @RequestHeader("X-User-Id") Long empId,
            @Valid @RequestBody AttendanceRequestPreviewRequest request) {
        return ResponseEntity.ok(ApiResult.success(attendanceRequestService.preview(empId, request)));
    }
}