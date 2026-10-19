package com.ssafy.user_service.domain.user.service;

import com.ssafy.user_service.domain.user.dto.EmployeeSummary;
import com.ssafy.user_service.domain.user.entity.Department;
import com.ssafy.user_service.domain.user.entity.Employee;
import com.ssafy.user_service.domain.user.entity.Grade;
import com.ssafy.user_service.domain.user.repository.DepartmentRepository;
import com.ssafy.user_service.domain.user.repository.EmployeeRepository;
import com.ssafy.user_service.domain.user.repository.GradeRepository;
import com.ssafy.user_service.global.exception.BusinessException;
import com.ssafy.user_service.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImplV1 implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final GradeRepository gradeRepository;

    @Override
    public Optional<Employee> findByLoginId(String loginId) {
        return employeeRepository.findByLoginId(loginId);
    }

    @Override
    public Optional<Employee> findById(Long employeeId) {
        return employeeRepository.findById(employeeId);
    }

    @Override
    public EmployeeSummary summarize(Employee employee) {
        String deptName = departmentRepository.findById(employee.getJob().getDeptId())
                .map(Department::getName)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        String gradeName = gradeRepository.findById(employee.getJob().getGradeId())
                .map(Grade::getName)
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADE_NOT_FOUND));
        return new EmployeeSummary(employee.getEmpId(), employee.getName(), deptName, gradeName);
    }
}
