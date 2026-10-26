package com.ssafy.leaveservice.global.auth;

import com.ssafy.leaveservice.global.exception.BusinessException;
import com.ssafy.leaveservice.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev | prod | production")
public class UnavailableEmployeeAccess implements EmployeeAccess {
    @Override
    public Long currentEmployeeId() {
        // TODO: 실제 인증 및 HR Service 직원 조회 API 연동 구현으로 교체한다.
        throw new BusinessException(ErrorCode.AUTHENTICATION_UNAVAILABLE);
    }

    @Override
    public boolean isAdmin(Long employeeId) {
        // TODO: HR Service 직책 기반 관리자 검증으로 교체한다. 연동 전에는 접근을 차단한다.
        return false;
    }
}
