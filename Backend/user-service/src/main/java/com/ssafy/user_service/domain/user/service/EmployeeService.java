package com.ssafy.user_service.domain.user.service;

import com.ssafy.user_service.domain.user.dto.EmployeeSummary;
import com.ssafy.user_service.domain.user.entity.Employee;

import java.util.Optional;

public interface EmployeeService {

    Optional<Employee> findByLoginId(String loginId);

    Optional<Employee> findById(Long employeeId);

    /** 부서명/직급명을 포함한 요약. 참조가 깨져 있으면 BusinessException */
    EmployeeSummary summarize(Employee employee);
}
