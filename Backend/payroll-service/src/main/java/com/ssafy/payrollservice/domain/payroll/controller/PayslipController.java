package com.ssafy.payrollservice.domain.payroll.controller;

import com.ssafy.payrollservice.domain.payroll.dto.PayslipListResponse;
import com.ssafy.payrollservice.domain.payroll.service.PayslipService;
import com.ssafy.payrollservice.global.dto.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 본인 급여 API */
// TODO: 인증 적용 전까지 사용자 식별은 X-User-Id 헤더로 임시 처리
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pay/me")
public class PayslipController {

    private final PayslipService payslipService;

    // 내 명세서 목록 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping("/payslips")
    public ResponseEntity<ApiResult<PayslipListResponse>> getMyPayslips(
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam int year) {
        return ResponseEntity.ok(ApiResult.success(payslipService.getMyPayslips(empId, year)));
    }
}