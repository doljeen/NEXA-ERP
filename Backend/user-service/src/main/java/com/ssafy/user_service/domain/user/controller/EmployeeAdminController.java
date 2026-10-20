package com.ssafy.user_service.domain.user.controller;

import com.ssafy.user_service.domain.user.dto.*;
import com.ssafy.user_service.domain.user.entity.Employee;
import com.ssafy.user_service.domain.user.service.EmployeeService;
import com.ssafy.user_service.global.dto.ApiResult;
import com.ssafy.user_service.global.exception.BusinessException;
import com.ssafy.user_service.global.exception.ErrorCode;
import com.ssafy.user_service.global.security.SecurityConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 사원 관리 API. /api/v1/admin/** 는 SecurityConfig에서 ROLE_ADMIN 전용으로 묶인다. */
@RestController
@RequiredArgsConstructor
@RequestMapping(SecurityConstants.EMPLOYEE_BASE_PATH)
public class EmployeeAdminController {

    private final EmployeeService employeeService;

    // 회원 가입 API
    @PostMapping
    public ResponseEntity<ApiResult<EmployeeCreateResponse>> create(@Valid @RequestBody EmployeeCreateRequest request) {
        Long employeeId = employeeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(new EmployeeCreateResponse(employeeId)));
    }

    // 회원 목록 조회 API
    /** 사원 목록. deptId가 있으면 해당 부서원만, 없으면 전체 */
    @GetMapping
    public ResponseEntity<ApiResult<EmployeeListResponse>> getList(@RequestParam(required = false) Long deptId) {
        List<Employee> employees = deptId == null
                ? employeeService.findAll()
                : employeeService.findByDeptId(deptId);
        return ResponseEntity.ok(ApiResult.success(EmployeeListResponse.from(employees)));
    }

    // 회원 상세 조회 API
    @GetMapping("/{employeeId}")
    public ResponseEntity<ApiResult<EmployeeInfoResponse>> getEmployeeInfo(@PathVariable Long employeeId) {
        Employee employee = employeeService.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
        return ResponseEntity.ok(ApiResult.success(EmployeeInfoResponse.from(employee)));
    }

    // 회원 탈퇴 API
    @PatchMapping("/{employeeId}/terminate")
    public ResponseEntity<ApiResult<Void>> terminate(@PathVariable Long employeeId,
                                                     @Valid @RequestBody EmployeeTerminateRequest request) {
        employeeService.terminate(employeeId, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
