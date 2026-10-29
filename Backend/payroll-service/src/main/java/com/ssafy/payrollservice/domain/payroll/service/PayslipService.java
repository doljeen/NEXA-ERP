package com.ssafy.payrollservice.domain.payroll.service;

import com.ssafy.payrollservice.domain.payroll.dto.PayslipListResponse;

public interface PayslipService {

    /** 내 급여명세서 목록 (연도별, 최근 월부터) + 연간 합계 */
    PayslipListResponse getMyPayslips(Long empId, int year);
}