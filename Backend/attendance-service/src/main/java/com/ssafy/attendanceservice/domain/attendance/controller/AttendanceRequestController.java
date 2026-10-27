package com.ssafy.attendanceservice.domain.attendance.controller;

import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestDetailResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestPreviewResponse;
import com.ssafy.attendanceservice.domain.attendance.dto.AttendanceRequestSummaryResponse;
import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;
import com.ssafy.attendanceservice.domain.attendance.service.AttendanceRequestService;
import com.ssafy.attendanceservice.global.dto.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    // 내 신청 이력 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping
    public ResponseEntity<ApiResult<List<AttendanceRequestSummaryResponse>>> getMyRequests(
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam int year,
            @RequestParam(required = false) AttendanceRequestType requestType) {
        return ResponseEntity.ok(ApiResult.success(attendanceRequestService.getMyRequests(empId, year, requestType)));
    }

    // 신청서 상세 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResult<AttendanceRequestDetailResponse>> getMyRequest(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResult.success(attendanceRequestService.getMyRequest(empId, requestId)));
    }
}
