package com.ssafy.user_service.domain.user.dto;

import java.util.List;

public record EmployeeListResponse(
        List<EmployeeInfoResponse> employees
) {
}
