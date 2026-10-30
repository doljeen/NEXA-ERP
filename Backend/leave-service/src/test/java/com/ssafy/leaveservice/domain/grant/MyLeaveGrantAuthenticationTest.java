package com.ssafy.leaveservice.domain.grant;

import com.ssafy.leaveservice.domain.grant.controller.MyLeaveGrantController;
import com.ssafy.leaveservice.domain.grant.repository.LeaveGrantRepository;
import com.ssafy.leaveservice.domain.grant.service.MyLeaveGrantService;
import com.ssafy.leaveservice.global.auth.DevEmployeeAccess;
import com.ssafy.leaveservice.global.auth.EmployeeAccess;
import com.ssafy.leaveservice.global.auth.UnavailableEmployeeAccess;
import com.ssafy.leaveservice.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MyLeaveGrantAuthenticationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class, UnavailableEmployeeAccess.class);

    @ParameterizedTest
    @ValueSource(strings = {"", "test", "prod", "production", "dev,prod", "dev,production"})
    void nonDevAuthenticationRemainsBlockedDespiteSpoofedHeaders(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            LeaveGrantRepository repository = mock(LeaveGrantRepository.class);
            endpoint(repository, context.getBean(EmployeeAccess.class))
                    .perform(get("/api/v1/leaves/me/grants").param("employeeId", "84").header("X-Employee-Id", "84"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("LEAVE_AUTH_001"));
            verifyNoInteractions(repository);
        });
    }

    @Test
    void devUsesExistingEmployeeOneAndReturns200ForNoRecords() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            LeaveGrantRepository repository = mock(LeaveGrantRepository.class);
            endpoint(repository, context.getBean(EmployeeAccess.class))
                    .perform(get("/api/v1/leaves/me/grants").param("employeeId", "84").header("X-Employee-Id", "84"))
                    .andExpect(status().isOk()).andExpect(content().json(
                            "{\"success\":true,\"error\":null,\"data\":[]}", JsonCompareMode.STRICT));
            verify(repository).findAllWithAccountAndBalanceTypeByEmployeeId(1L);
            verify(repository, never()).findAllWithAccountAndBalanceTypeByEmployeeId(84L);
        });
    }

    private MockMvc endpoint(LeaveGrantRepository repository, EmployeeAccess employeeAccess) {
        return MockMvcBuilders.standaloneSetup(new MyLeaveGrantController(new MyLeaveGrantService(repository, employeeAccess)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
