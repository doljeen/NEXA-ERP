package com.ssafy.leaveservice.global.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod & !production")
public class DevEmployeeAccess implements EmployeeAccess {
    @Override
    public Long currentEmployeeId() {
        // TODO: 인증된 사용자 식별자로 HR Service 직원 조회 API를 호출해 실제 emp_id를 반환한다.
        return 1L;
    }

    @Override
    public boolean isAdmin(Long employeeId) {
        // TODO: HR Service에서 emp_id로 직원 직책을 조회하고 관리자 권한을 판단한다.
        return true;
    }
}
