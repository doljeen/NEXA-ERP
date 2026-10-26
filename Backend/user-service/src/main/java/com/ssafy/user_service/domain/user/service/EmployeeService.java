package com.ssafy.user_service.domain.user.service;

import com.ssafy.user_service.domain.user.dto.EmployeeCreateRequest;
import com.ssafy.user_service.domain.user.dto.EmployeeSummary;
import com.ssafy.user_service.domain.user.dto.EmployeeTerminateRequest;
import com.ssafy.user_service.domain.user.entity.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeService {
    /** 사원 계정을 등록하고 생성된 employeeId를 반환한다. */
    Long create(EmployeeCreateRequest request);

    /** 퇴직 처리. 재적 상태를 TERMINATED로 바꾸고 이 사원의 refresh 세션을 삭제한다. */
    void terminate(Long employeeId, EmployeeTerminateRequest request);

    Optional<Employee> findByLoginId(String loginId);

    Optional<Employee> findById(Long employeeId);

    /** 부서명/직급명을 포함한 요약. 참조가 깨져 있으면 BusinessException */
    EmployeeSummary summarize(Employee employee);

    List<Employee> findAll();

    /** 부서 기준 사원 목록. 존재하지 않는 부서면 BusinessException */
    List<Employee> findByDeptId(Long deptId);
}
