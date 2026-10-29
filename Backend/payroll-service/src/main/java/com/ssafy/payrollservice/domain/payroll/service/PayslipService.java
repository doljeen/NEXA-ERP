package com.ssafy.payrollservice.domain.payroll.service;

import com.ssafy.payrollservice.domain.payroll.dto.PayslipDetailResponse;
import com.ssafy.payrollservice.domain.payroll.dto.PayslipListResponse;

public interface PayslipService {

    /** 내 급여명세서 목록 (연도별, 최근 월부터) + 연간 합계 */
    PayslipListResponse getMyPayslips(Long empId, int year);

    /** 내 급여명세서 상세. 없거나 다른 사람 명세서면 404 */
    PayslipDetailResponse getMyPayslip(Long empId, Long payslipId);
}