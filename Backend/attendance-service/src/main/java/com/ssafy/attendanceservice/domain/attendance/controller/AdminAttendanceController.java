package com.ssafy.attendanceservice.domain.attendance.controller;

import com.ssafy.attendanceservice.domain.attendance.dto.AdminWorkStatusLogRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.AdminWorkStatusLogUpdateRequest;
import com.ssafy.attendanceservice.domain.attendance.dto.DailyAttendanceResponse;
import com.ssafy.attendanceservice.domain.attendance.service.AdminAttendanceService;
import com.ssafy.attendanceservice.domain.attendance.service.AttendanceService;
import com.ssafy.attendanceservice.global.dto.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** 관리자 근태 API (현재 전사 관리자 기준) */
// TODO: 인증 적용 시 관리자 권한 확인 (SecurityConfig에서 /api/v1/attn/admin/** ROLE_ADMIN)
// TODO: 부서장 권한 도입 시 자기 부서 직원만 조회/수정하도록 범위 제한
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attn/admin")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;
    private final AdminAttendanceService adminAttendanceService;

    // 직원 일별 타임라인 조회 API
    @GetMapping("/employees/{empId}/daily/{date}")
    public ResponseEntity<ApiResult<DailyAttendanceResponse>> getEmployeeDaily(
            @PathVariable Long empId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResult.success(attendanceService.getDaily(empId, date)));
    }

    // 근무 상태 직접 등록 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @PostMapping("/logs")
    public ResponseEntity<ApiResult<DailyAttendanceResponse>> registerLog(
            @RequestHeader("X-User-Id") Long adminId,
            @Valid @RequestBody AdminWorkStatusLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(adminAttendanceService.registerLog(adminId, request)));
    }

    // 근무 상태 로그 수정 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @PutMapping("/logs/{logId}")
    public ResponseEntity<ApiResult<DailyAttendanceResponse>> updateLog(
            @RequestHeader("X-User-Id") Long adminId,
            @PathVariable Long logId,
            @Valid @RequestBody AdminWorkStatusLogUpdateRequest request) {
        return ResponseEntity.ok(ApiResult.success(adminAttendanceService.updateLog(adminId, logId, request)));
    }

    // 근무 상태 로그 삭제 API
    @DeleteMapping("/logs/{logId}")
    public ResponseEntity<ApiResult<DailyAttendanceResponse>> deleteLog(@PathVariable Long logId) {
        return ResponseEntity.ok(ApiResult.success(adminAttendanceService.deleteLog(logId)));
    }
}