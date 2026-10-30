package com.ssafy.payrollservice.domain.payroll.controller;

import com.ssafy.payrollservice.domain.payroll.dto.ContractResponse;
import com.ssafy.payrollservice.domain.payroll.dto.PayslipDetailResponse;
import com.ssafy.payrollservice.domain.payroll.dto.PayslipListResponse;
import com.ssafy.payrollservice.domain.payroll.dto.RetirementEstimateResponse;
import com.ssafy.payrollservice.domain.payroll.service.PayslipService;
import com.ssafy.payrollservice.global.dto.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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

    // 명세서 상세 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping("/payslips/{payslipId}")
    public ResponseEntity<ApiResult<PayslipDetailResponse>> getMyPayslip(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long payslipId) {
        return ResponseEntity.ok(ApiResult.success(payslipService.getMyPayslip(empId, payslipId)));
    }

    // 내 급여 정보 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping("/contract")
    public ResponseEntity<ApiResult<ContractResponse>> getMyContract(@RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(ApiResult.success(payslipService.getMyContract(empId)));
    }

    // 예상 퇴직금 조회 API
    // TODO: 인증 적용 시 @RequestHeader("X-User-Id") → @AuthenticationPrincipal AuthUser 로 교체
    @GetMapping("/retirement-estimate")
    public ResponseEntity<ApiResult<RetirementEstimateResponse>> getRetirementEstimate(
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate resignDate) {
        return ResponseEntity.ok(ApiResult.success(payslipService.getRetirementEstimate(empId, resignDate)));
    }
}