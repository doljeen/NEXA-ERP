package com.ssafy.attendanceservice.domain.attendance.controller;

import com.ssafy.attendanceservice.domain.attendance.dto.*;
import com.ssafy.attendanceservice.domain.attendance.service.AttendanceService;
import com.ssafy.attendanceservice.global.dto.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

    // 퇴근 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @PostMapping("/clock-out")
    public ResponseEntity<ApiResult<AttendanceStatusResponse>> clockOut(@RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(ApiResult.success(attendanceService.clockOut(empId)));
    }

    // 근무상태 변경 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @PostMapping("/status")
    public ResponseEntity<ApiResult<AttendanceStatusResponse>> changeStatus(@RequestHeader("X-User-Id") Long empId,
                                                                            @Valid @RequestBody WorkStatusChangeRequest request) {
        return ResponseEntity.ok(ApiResult.success(attendanceService.changeStatus(empId, request.statusCode())));
    }

    // 현재 근무 상태 조회 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping("/today")
    public ResponseEntity<ApiResult<AttendanceStatusResponse>> getToday(@RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(ApiResult.success(attendanceService.getToday(empId)));
    }

    // 일별 근태 타임라인 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping("/me/daily/{date}")
    public ResponseEntity<ApiResult<DailyAttendanceResponse>> getDaily(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResult.success(attendanceService.getDaily(empId, date)));
    }

    // 주간 근태 조회 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping("/me/weekly")
    public ResponseEntity<ApiResult<WeeklyAttendanceResponse>> getWeekly(
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResult.success(attendanceService.getWeekly(empId, date)));
    }

    // 근무 상태 코드 목록 API
    @GetMapping("/status-codes")
    public ResponseEntity<ApiResult<List<WorkStatusCodeResponse>>> getStatusCodes() {
        return ResponseEntity.ok(ApiResult.success(attendanceService.getStatusCodes()));
    }
}