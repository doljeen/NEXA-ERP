package com.ssafy.leaveservice.domain.employee;

import com.ssafy.leaveservice.domain.account.repository.LeaveAccountRepository;
import com.ssafy.leaveservice.domain.account.service.MyLeaveBalanceService;
import com.ssafy.leaveservice.domain.employee.controller.AdminEmployeeLeaveQueryController;
import com.ssafy.leaveservice.domain.grant.repository.LeaveGrantRepository;
import com.ssafy.leaveservice.domain.grant.service.MyLeaveGrantService;
import com.ssafy.leaveservice.domain.ledger.repository.LeaveLedgerRepository;
import com.ssafy.leaveservice.domain.ledger.service.MyLeaveLedgerService;
import com.ssafy.leaveservice.global.auth.DevEmployeeAccess;
import com.ssafy.leaveservice.global.auth.EmployeeAccess;
import com.ssafy.leaveservice.global.auth.UnavailableEmployeeAccess;
import com.ssafy.leaveservice.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminEmployeeLeaveAuthenticationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class, UnavailableEmployeeAccess.class);
    private final LeaveAccountRepository accountRepository = mock(LeaveAccountRepository.class);
    private final LeaveGrantRepository grantRepository = mock(LeaveGrantRepository.class);
    private final LeaveLedgerRepository ledgerRepository = mock(LeaveLedgerRepository.class);

    @ParameterizedTest
    @MethodSource("nonDevProfilesAndEndpoints")
    void productionAndMixedProfilesCannotQueryOtherEmployees(String profile, String endpoint) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            endpoint(context.getBean(EmployeeAccess.class)).perform(get("/api/v1/leaves/admin/employees/42/" + endpoint)
                            .header("X-Employee-Id", "1").header("X-Role", "ADMIN"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("LEAVE_AUTH_001"));
            verifyNoInteractions(accountRepository, grantRepository, ledgerRepository);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"balances", "grants", "ledger"})
    void existingDevAdminCanQueryPathTargetInsteadOfDevEmployeeOne(String endpoint) {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            endpoint(context.getBean(EmployeeAccess.class)).perform(get("/api/v1/leaves/admin/employees/42/" + endpoint))
                    .andExpect(status().isOk()).andExpect(content().json("{\"success\":true,\"error\":null,\"data\":[]}"));
            switch (endpoint) {
                case "balances" -> verify(accountRepository).findBalancesByEmployeeId(eq(42L), any());
                case "grants" -> verify(grantRepository).findAllWithAccountAndBalanceTypeByEmployeeId(42L);
                case "ledger" -> verify(ledgerRepository).findAllWithAccountAndBalanceTypeByEmployeeId(42L);
                default -> throw new AssertionError(endpoint);
            }
            verify(accountRepository, never()).findBalancesByEmployeeId(eq(1L), any());
            verify(grantRepository, never()).findAllWithAccountAndBalanceTypeByEmployeeId(1L);
            verify(ledgerRepository, never()).findAllWithAccountAndBalanceTypeByEmployeeId(1L);
        });
    }

    private MockMvc endpoint(EmployeeAccess employeeAccess) {
        AdminEmployeeLeaveQueryController controller = new AdminEmployeeLeaveQueryController(
                new MyLeaveBalanceService(accountRepository, employeeAccess),
                new MyLeaveGrantService(grantRepository, employeeAccess),
                new MyLeaveLedgerService(ledgerRepository, employeeAccess)
        );
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    static Stream<Arguments> nonDevProfilesAndEndpoints() {
        return Stream.of("", "test", "prod", "production", "dev,prod", "dev,production")
                .flatMap(profile -> Stream.of("balances", "grants", "ledger").map(endpoint -> Arguments.of(profile, endpoint)));
    }
}
