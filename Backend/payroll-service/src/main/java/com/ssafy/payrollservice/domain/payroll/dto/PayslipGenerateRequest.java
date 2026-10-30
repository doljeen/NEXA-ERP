package com.ssafy.payrollservice.domain.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 월 급여 생성 요청.
 * TODO: user-service 연동 방식이 정해지면 사원 정보(이름~직급ID)는 empId로 조회하고 요청에서 제거
 */
public record PayslipGenerateRequest(
        @NotNull Long empId,            // 사원 ID
        @NotNull YearMonth month,       // 급여 월 (YYYY-MM)
        @NotBlank String empName,       // 사원명
        @NotBlank String deptName,      // 부서명
        String positionName,            // 직위명
        @NotNull LocalDate hireDate,    // 입사일
        @NotNull Long gradeId           // 직급 ID (pay_grade.grade_id)
) {
}