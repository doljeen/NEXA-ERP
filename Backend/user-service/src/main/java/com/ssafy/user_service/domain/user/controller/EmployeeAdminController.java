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

/** 사원 관리 API. 조회는 인증 사용자 모두, 등록(POST)/퇴직 처리(PATCH)는 SecurityConfig에서 ROLE_ADMIN 전용으로 묶인다. */
@RestController
@RequiredArgsConstructor
@RequestMapping(SecurityConstants.EMPLOYEE_BASE_PATH)
public class EmployeeAdminController {

    private final EmployeeService employeeService;

    @PostMapping
    public ResponseEntity<ApiResult<EmployeeCreateResponse>> create(@Valid @RequestBody EmployeeCreateRequest request) {
        Long employeeId = employeeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(new EmployeeCreateResponse(employeeId)));
    }

    /**
     * 사원 목록. ids가 있으면 그 사원들만(없는 ID는 빠짐), deptId가 있으면 해당 부서원만, 둘 다 없으면 전체.
     * ids는 결재선 검증·이름 표시처럼 여러 사원을 한 번에 확인할 때 쓴다 (approval-service, 프런트엔드)
     */
    @GetMapping
    public ResponseEntity<ApiResult<EmployeeListResponse>> getList(@RequestParam(required = false) Long deptId,
                                                                   @RequestParam(required = false) List<Long> ids) {
        if (ids != null && deptId != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "ids와 deptId는 함께 쓸 수 없습니다.");
        }
        List<Employee> employees;
        if (ids != null) {
            employees = employeeService.findAllByIds(ids);
        } else if (deptId != null) {
            employees = employeeService.findByDeptId(deptId);
        } else {
            employees = employeeService.findAll();
        }
        return ResponseEntity.ok(ApiResult.success(EmployeeListResponse.from(employees)));
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<ApiResult<EmployeeInfoResponse>> getEmployeeInfo(@PathVariable Long employeeId) {
        Employee employee = employeeService.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
        return ResponseEntity.ok(ApiResult.success(EmployeeInfoResponse.from(employee)));
    }

    @PatchMapping("/{employeeId}/terminate")
    public ResponseEntity<ApiResult<Void>> terminate(@PathVariable Long employeeId,
                                                     @Valid @RequestBody EmployeeTerminateRequest request) {
        employeeService.terminate(employeeId, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
