package com.ssafy.leaveservice.domain.request;

import com.ssafy.leaveservice.domain.request.controller.AdminLeaveRequestController;
import com.ssafy.leaveservice.domain.request.entity.LeaveRequest;
import com.ssafy.leaveservice.domain.request.repository.LeaveRequestRepository;
import com.ssafy.leaveservice.domain.request.service.AdminLeaveRequestService;
import com.ssafy.leaveservice.global.auth.DevEmployeeAccess;
import com.ssafy.leaveservice.global.auth.EmployeeAccess;
import com.ssafy.leaveservice.global.auth.UnavailableEmployeeAccess;
import com.ssafy.leaveservice.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminLeaveRequestAuthenticationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class, UnavailableEmployeeAccess.class);

    @ParameterizedTest
    @MethodSource("profilesAndEndpoints")
    void nonDevAndMixedProfilesBlockEvenSpoofedAdminAndMalformedIds(String profile, String endpoint) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            LeaveRequestRepository repository = mock(LeaveRequestRepository.class);
            AdminLeaveRequestService service = new AdminLeaveRequestService(repository, context.getBean(EmployeeAccess.class));
            MockMvcBuilders.standaloneSetup(new AdminLeaveRequestController(service))
                    .setControllerAdvice(new GlobalExceptionHandler()).build()
                    .perform(get("/api/v1/leaves/admin/requests" + endpoint).header("X-Admin", "true").header("X-Employee-Id", "1"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("LEAVE_AUTH_001"));
            verifyNoInteractions(repository);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/50"})
    void devUsesExistingAdminAccessWithoutRestrictingResultsToEmployeeOne(String endpoint) {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            LeaveRequestRepository repository = mock(LeaveRequestRepository.class);
            LeaveRequest request = mock(LeaveRequest.class, RETURNS_DEEP_STUBS);
            when(request.getRequestId()).thenReturn(50L);
            when(request.getEmployeeId()).thenReturn(84L);
            when(request.getDates()).thenReturn(List.of());
            when(repository.findAllWithLeaveType()).thenReturn(List.of(request));
            when(repository.findDetailByRequestId(50L)).thenReturn(Optional.of(request));
            AdminLeaveRequestService service = new AdminLeaveRequestService(repository, context.getBean(EmployeeAccess.class));
            MockMvcBuilders.standaloneSetup(new AdminLeaveRequestController(service))
                    .setControllerAdvice(new GlobalExceptionHandler()).build()
                    .perform(get("/api/v1/leaves/admin/requests" + endpoint))
                    .andExpect(status().isOk()).andExpect(jsonPath(endpoint.isEmpty() ? "$.data[0].employeeId" : "$.data.employeeId").value(84));
            if (endpoint.isEmpty()) {
                verify(repository).findAllWithLeaveType();
            } else {
                verify(repository).findDetailByRequestId(50L);
            }
            verify(repository, never()).findAllWithLeaveTypeByEmployeeId(anyLong());
            verify(repository, never()).findDetailByRequestIdAndEmployeeId(anyLong(), anyLong());
        });
    }

    static Stream<Arguments> profilesAndEndpoints() {
        return Stream.of("", "test", "prod", "production", "dev,prod", "dev,production")
                .flatMap(profile -> Stream.of("", "/50", "/abc").map(endpoint -> Arguments.of(profile, endpoint)));
    }
}
