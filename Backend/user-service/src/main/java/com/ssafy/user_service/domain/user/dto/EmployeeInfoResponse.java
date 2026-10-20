package com.ssafy.user_service.domain.user.dto;

import com.ssafy.user_service.domain.user.entity.Employee;
import com.ssafy.user_service.domain.user.entity.JobAssignment;
import com.ssafy.user_service.domain.user.entity.enums.EmpType;
import com.ssafy.user_service.domain.user.entity.enums.EmploymentStatus;
import com.ssafy.user_service.domain.user.entity.enums.HireType;
import com.ssafy.user_service.global.security.Role;

import java.time.LocalDate;

/** 사원 조회 응답. 개인정보(PersonalInfo)는 제외한다. */
public record EmployeeInfoResponse(
        Long empId,
        String empNo,
        String name,
        String loginId,
        Role role,
        Long deptId,
        Long positionId,
        Long gradeId,
        Long dutyId,
        EmpType empType,
        HireType hireType,
        LocalDate hireDate,
        EmploymentStatus status
) {
    public static EmployeeInfoResponse from(Employee employee) {
        JobAssignment job = employee.getJob();
        return new EmployeeInfoResponse(
                employee.getEmpId(),
                employee.getEmpNo(),
                employee.getName(),
                employee.getLoginId(),
                employee.getRole(),
                job.getDeptId(),
                job.getPositionId(),
                job.getGradeId(),
                job.getDutyId(),
                employee.getEmpType(),
                employee.getHireType(),
                employee.getHireDate(),
                employee.getStatus()
        );
    }
}
