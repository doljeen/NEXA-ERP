package com.ssafy.leaveservice.global.auth;

import com.ssafy.leaveservice.global.exception.BusinessException;
import com.ssafy.leaveservice.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;

class EmployeeAccessTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class, UnavailableEmployeeAccess.class);

    @Test
    void devAllowsTemporaryEmployeeAndAdmin() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasSingleBean(EmployeeAccess.class);
            EmployeeAccess access = context.getBean(EmployeeAccess.class);
            assertThat(access).isInstanceOf(DevEmployeeAccess.class);
            assertThat(access.currentEmployeeId()).isEqualTo(1L);
            assertThat(access.isAdmin(access.currentEmployeeId())).isTrue();
        });
    }

    @Test
    void allNonDevAndProductionCombinationsDenyAccess() {
        for (String profiles : new String[]{"", "test", "prod", "production", "dev,prod", "dev,production"}) {
            runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
                assertThat(context).hasSingleBean(EmployeeAccess.class);
                EmployeeAccess access = context.getBean(EmployeeAccess.class);
                assertThat(access).isInstanceOf(UnavailableEmployeeAccess.class);
                assertThat(access.isAdmin(1L)).isFalse();
                assertThatThrownBy(access::currentEmployeeId)
                        .isInstanceOfSatisfying(BusinessException.class,
                                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_UNAVAILABLE));
            });
        }
    }
}
