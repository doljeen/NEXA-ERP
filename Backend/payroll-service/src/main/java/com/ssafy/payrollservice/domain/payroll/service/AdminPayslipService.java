package com.ssafy.payrollservice.domain.payroll.service;

import com.ssafy.payrollservice.domain.payroll.dto.PayslipGenerateRequest;
import com.ssafy.payrollservice.domain.payroll.dto.PayslipGenerateResponse;

public interface AdminPayslipService {

    /** 사원 1명의 월 급여명세서 생성. 근태 월 집계로 수당을 계산하고 항목 13개를 0원 포함 저장한다 */
    PayslipGenerateResponse generate(PayslipGenerateRequest request);
}