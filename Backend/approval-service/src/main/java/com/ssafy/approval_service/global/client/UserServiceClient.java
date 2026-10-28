package com.ssafy.approval_service.global.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * user-service 사원 API. 요청한 사용자의 access 토큰을 Authorization: Bearer 로 넘겨 호출한다
 * (UserServiceClientConfig). 응답은 결재선 검증에 필요한 값(사원 ID, 재적 상태)만 받는다.
 */
@HttpExchange("/api/v1/employees")
public interface UserServiceClient {

    /** 여러 사원을 한 번에 조회. 없는 ID는 결과에서 빠진다 (user-service 최대 100개) */
    @GetExchange
    EmployeeListResult getEmployees(@RequestParam("ids") List<Long> ids);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmployeeListResult(EmployeeList data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmployeeList(List<EmployeeStatus> employees) {
    }

    /** status: EMPLOYED(재직) / ON_LEAVE(휴직) / TERMINATED(퇴직). 값이 늘어도 깨지지 않게 문자열로 받는다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmployeeStatus(Long empId, String status) {
    }
}
