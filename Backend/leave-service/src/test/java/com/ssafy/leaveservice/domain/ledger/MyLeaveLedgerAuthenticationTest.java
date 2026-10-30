package com.ssafy.leaveservice.domain.ledger;

import com.ssafy.leaveservice.domain.ledger.controller.MyLeaveLedgerController;
import com.ssafy.leaveservice.domain.ledger.repository.LeaveLedgerRepository;
import com.ssafy.leaveservice.domain.ledger.service.MyLeaveLedgerService;
import com.ssafy.leaveservice.global.auth.DevEmployeeAccess;
import com.ssafy.leaveservice.global.auth.EmployeeAccess;
import com.ssafy.leaveservice.global.auth.UnavailableEmployeeAccess;
import com.ssafy.leaveservice.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MyLeaveLedgerAuthenticationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class, UnavailableEmployeeAccess.class);

    @ParameterizedTest
    @ValueSource(strings = {"", "test", "prod", "production", "dev,prod", "dev,production"})
    void nonDevAuthenticationRemainsBlockedDespiteSpoofedIdentity(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            LeaveLedgerRepository repository = mock(LeaveLedgerRepository.class);
            endpoint(repository, context.getBean(EmployeeAccess.class))
                    .perform(get("/api/v1/leaves/me/ledger").param("employeeId", "84").header("X-Employee-Id", "84"))
                    .andExpect(status().isServiceUnavailable()).andExpect(content().json("""
                            {"success":false,"error":{"code":"LEAVE_AUTH_001","message":"직원 인증 연동이 준비되지 않았습니다."},"data":null}
                            """, JsonCompareMode.STRICT));
            verifyNoInteractions(repository);
        });
    }

    @Test
    void devUsesExistingEmployeeOneAndReturnsEmptyApiResult() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            LeaveLedgerRepository repository = mock(LeaveLedgerRepository.class);
            endpoint(repository, context.getBean(EmployeeAccess.class))
                    .perform(get("/api/v1/leaves/me/ledger").param("employeeId", "84").header("X-Employee-Id", "84"))
                    .andExpect(status().isOk()).andExpect(content().json(
                            "{\"success\":true,\"error\":null,\"data\":[]}", JsonCompareMode.STRICT));
            verify(repository).findAllWithAccountAndBalanceTypeByEmployeeId(1L);
            verify(repository, never()).findAllWithAccountAndBalanceTypeByEmployeeId(84L);
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidAuthenticatedIdReturnsExistingUnauthorizedResponse(Long employeeId) throws Exception {
        EmployeeAccess employeeAccess = mock(EmployeeAccess.class);
        LeaveLedgerRepository repository = mock(LeaveLedgerRepository.class);
        when(employeeAccess.currentEmployeeId()).thenReturn(employeeId);
        endpoint(repository, employeeAccess).perform(get("/api/v1/leaves/me/ledger"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_001"));
        verifyNoInteractions(repository);
    }

    private MockMvc endpoint(LeaveLedgerRepository repository, EmployeeAccess employeeAccess) {
        return MockMvcBuilders.standaloneSetup(new MyLeaveLedgerController(new MyLeaveLedgerService(repository, employeeAccess)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
