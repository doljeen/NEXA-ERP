package com.ssafy.payrollservice.domain.payroll.dto;

import com.ssafy.payrollservice.domain.payroll.entity.Payslip;

import java.time.LocalDate;

/** 내 급여 정보 (최근 명세서 기준) */
public record ContractResponse(
        String empName,         // 사원명
        String deptName,        // 부서명
        String positionName,    // 직위명
        LocalDate hireDate,     // 입사일
        int monthlyBasePay,     // 월 기본급
        int annualSalary,       // 연봉 (월 기본급 × 12)
        int baseYear,           // 기준 명세서 연도
        int baseMonth           // 기준 명세서 월
) {

    public static ContractResponse of(Payslip payslip, int monthlyBasePay) {
        return new ContractResponse(
                payslip.getEmpName(),
                payslip.getDeptName(),
                payslip.getPositionName(),
                payslip.getHireDate(),
                monthlyBasePay,
                monthlyBasePay * 12,
                payslip.getPayYear(),
                payslip.getPayMonth());
    }
}