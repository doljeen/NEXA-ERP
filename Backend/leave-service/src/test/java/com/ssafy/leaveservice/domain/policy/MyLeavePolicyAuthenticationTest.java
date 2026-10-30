package com.ssafy.leaveservice.domain.policy;

import com.ssafy.leaveservice.domain.policy.controller.MyLeavePolicyController;
import com.ssafy.leaveservice.domain.policy.repository.EmployeeLeavePolicyAssignmentRepository;
import com.ssafy.leaveservice.domain.policy.service.MyLeavePolicyService;
import com.ssafy.leaveservice.global.auth.DevEmployeeAccess;
import com.ssafy.leaveservice.global.auth.EmployeeAccess;
import com.ssafy.leaveservice.global.auth.UnavailableEmployeeAccess;
import com.ssafy.leaveservice.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MyLeavePolicyAuthenticationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class, UnavailableEmployeeAccess.class);

    @ParameterizedTest
    @ValueSource(strings = {"", "test", "prod", "production", "dev,prod", "dev,production"})
    void realNonDevAuthenticationBlocksEndpointEvenWithSpoofedEmployeeHeader(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            EmployeeAccess employeeAccess = context.getBean(EmployeeAccess.class);
            assertThat(employeeAccess).isInstanceOf(UnavailableEmployeeAccess.class);
            EmployeeLeavePolicyAssignmentRepository repository = mock(EmployeeLeavePolicyAssignmentRepository.class);
            MockMvc mvc = endpoint(repository, employeeAccess);
            mvc.perform(get("/api/v1/leaves/me/policy").header("X-Employee-Id", "42").param("employeeId", "42"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("LEAVE_AUTH_001"));
            verifyNoInteractions(repository);
        });
    }

    @Test
    void realDevEmployeeIdentityRemainsOneDespiteClientParameters() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            EmployeeAccess employeeAccess = context.getBean(EmployeeAccess.class);
            assertThat(employeeAccess).isInstanceOf(DevEmployeeAccess.class);
            EmployeeLeavePolicyAssignmentRepository repository = mock(EmployeeLeavePolicyAssignmentRepository.class);
            endpoint(repository, employeeAccess).perform(get("/api/v1/leaves/me/policy")
                            .header("X-Employee-Id", "84").param("employeeId", "84"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("LEAVE_010"));
            verify(repository).findApplicablePolicies(eq(1L), any());
            verify(repository, never()).findApplicablePolicies(eq(84L), any());
        });
    }

    private MockMvc endpoint(EmployeeLeavePolicyAssignmentRepository repository, EmployeeAccess employeeAccess) {
        MyLeavePolicyService service = new MyLeavePolicyService(repository, employeeAccess);
        return MockMvcBuilders.standaloneSetup(new MyLeavePolicyController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
