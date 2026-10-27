package com.ssafy.attendanceservice.domain.attendance.controller;

import com.ssafy.attendanceservice.domain.attendance.dto.DailyAttendanceResponse;
import com.ssafy.attendanceservice.domain.attendance.service.AttendanceService;
import com.ssafy.attendanceservice.global.dto.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 관리자 근태 API (현재 전사 관리자 기준) */
// TODO: 인증 적용 시 관리자 권한 확인 (SecurityConfig에서 /api/v1/attn/admin/** ROLE_ADMIN)
// TODO: 부서장 권한 도입 시 자기 부서 직원만 조회/수정하도록 범위 제한
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attn/admin")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    // 직원 일별 타임라인 조회 API
    @GetMapping("/employees/{empId}/daily/{date}")
    public ResponseEntity<ApiResult<DailyAttendanceResponse>> getEmployeeDaily(
            @PathVariable Long empId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResult.success(attendanceService.getDaily(empId, date)));
    }
}