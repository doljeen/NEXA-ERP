package com.ssafy.user_service.domain.user.dto;

import com.ssafy.user_service.domain.user.entity.Employee;

import java.util.List;

public record EmployeeListResponse(
        List<EmployeeInfoResponse> employees
) {
    public static EmployeeListResponse from(List<Employee> employees) {
        return new EmployeeListResponse(employees.stream().map(EmployeeInfoResponse::from).toList());
    }
}
