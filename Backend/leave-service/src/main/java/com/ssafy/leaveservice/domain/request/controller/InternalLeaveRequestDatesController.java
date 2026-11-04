package com.ssafy.leaveservice.domain.request.controller;

import com.ssafy.leaveservice.domain.request.dto.LeaveRequestDatesResponse;
import com.ssafy.leaveservice.domain.request.service.LeaveRequestDatesService;
import com.ssafy.leaveservice.global.dto.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/leaves/internal/requests")
public class InternalLeaveRequestDatesController {
    private final LeaveRequestDatesService datesService;

    // 근태 연동용 휴가 신청 날짜와 신청 단위 조회
    @GetMapping("/{requestId}/dates")
    public ApiResult<LeaveRequestDatesResponse> getDates(@PathVariable Long requestId) {
        return ApiResult.success(datesService.getDates(requestId));
    }
}
