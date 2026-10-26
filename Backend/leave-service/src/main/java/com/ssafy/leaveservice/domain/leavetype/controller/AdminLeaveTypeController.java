package com.ssafy.leaveservice.domain.leavetype.controller;

import com.ssafy.leaveservice.domain.leavetype.dto.LeaveTypeResponse;
import com.ssafy.leaveservice.domain.leavetype.dto.LeaveTypeCreateRequest;
import com.ssafy.leaveservice.domain.leavetype.service.LeaveTypeService;
import com.ssafy.leaveservice.global.dto.ApiResult;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leaves/admin/types")
@RequiredArgsConstructor
public class AdminLeaveTypeController {
    private final LeaveTypeService leaveTypeService;

    @GetMapping
    public ResponseEntity<ApiResult<List<LeaveTypeResponse>>> getAllTypes() {
        List<LeaveTypeResponse> leaveTypes = leaveTypeService.getAllTypes();
        return ResponseEntity.ok(ApiResult.success(leaveTypes));
    }

    @PostMapping
    public ResponseEntity<ApiResult<LeaveTypeResponse>> createType(
            @Valid @RequestBody LeaveTypeCreateRequest request
    ) {
        LeaveTypeResponse response = leaveTypeService.createType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(response));
    }
}
