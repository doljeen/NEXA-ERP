package com.ssafy.user_service.domain.user.controller;

import com.ssafy.user_service.domain.user.dto.EmployeeCreateRequest;
import com.ssafy.user_service.domain.user.dto.EmployeeCreateResponse;
import com.ssafy.user_service.domain.user.dto.EmployeeTerminateRequest;
import com.ssafy.user_service.domain.user.service.EmployeeService;
import com.ssafy.user_service.global.dto.ApiResult;
import com.ssafy.user_service.global.security.SecurityConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사원 관리 API. /api/v1/admin/** 는 SecurityConfig에서 ROLE_ADMIN 전용으로 묶인다. */
@RestController
@RequiredArgsConstructor
@RequestMapping(SecurityConstants.ADMIN_BASE_PATH + "/employees")
public class EmployeeAdminController {

    private final EmployeeService employeeService;

    @PostMapping
    public ResponseEntity<ApiResult<EmployeeCreateResponse>> create(@Valid @RequestBody EmployeeCreateRequest request) {
        Long employeeId = employeeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(new EmployeeCreateResponse(employeeId)));
    }

    @PatchMapping("/{employeeId}/terminate")
    public ResponseEntity<ApiResult<Void>> terminate(@PathVariable Long employeeId,
                                                     @Valid @RequestBody EmployeeTerminateRequest request) {
        employeeService.terminate(employeeId, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
