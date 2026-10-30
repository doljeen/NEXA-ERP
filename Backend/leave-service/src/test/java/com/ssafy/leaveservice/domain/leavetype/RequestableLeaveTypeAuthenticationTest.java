package com.ssafy.leaveservice.domain.leavetype;

import com.ssafy.leaveservice.domain.leavetype.controller.RequestableLeaveTypeController;
import com.ssafy.leaveservice.domain.leavetype.repository.LeaveTypeRepository;
import com.ssafy.leaveservice.domain.leavetype.service.RequestableLeaveTypeService;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RequestableLeaveTypeAuthenticationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class, UnavailableEmployeeAccess.class);

    @ParameterizedTest
    @ValueSource(strings = {"", "test", "prod", "production", "dev,prod", "dev,production"})
    void existingNonDevAuthenticationRejectsSpoofedEmployeeBeforeAnyQuery(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            EmployeeLeavePolicyAssignmentRepository assignments = mock(EmployeeLeavePolicyAssignmentRepository.class);
            LeaveTypeRepository types = mock(LeaveTypeRepository.class);
            endpoint(assignments, types, context.getBean(EmployeeAccess.class))
                    .perform(get("/api/v1/leaves/types").param("employeeId", "84").header("X-Employee-Id", "84"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("LEAVE_AUTH_001"));
            verifyNoInteractions(assignments, types);
        });
    }

    @Test
    void devUsesOnlyExistingTestEmployeeOne() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            EmployeeLeavePolicyAssignmentRepository assignments = mock(EmployeeLeavePolicyAssignmentRepository.class);
            LeaveTypeRepository types = mock(LeaveTypeRepository.class);
            endpoint(assignments, types, context.getBean(EmployeeAccess.class))
                    .perform(get("/api/v1/leaves/types").param("employeeId", "84").header("X-Employee-Id", "84"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("LEAVE_010"));
            verify(assignments).findApplicablePolicies(eq(1L), any());
            verify(assignments, never()).findApplicablePolicies(eq(84L), any());
            verifyNoInteractions(types);
        });
    }

    private MockMvc endpoint(
            EmployeeLeavePolicyAssignmentRepository assignments,
            LeaveTypeRepository types,
            EmployeeAccess employeeAccess
    ) {
        MyLeavePolicyService policyService = new MyLeavePolicyService(assignments, employeeAccess);
        RequestableLeaveTypeService typeService = new RequestableLeaveTypeService(policyService, types);
        return MockMvcBuilders.standaloneSetup(new RequestableLeaveTypeController(typeService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
