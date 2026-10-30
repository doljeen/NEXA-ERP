package com.ssafy.leaveservice.domain.request;

import com.ssafy.leaveservice.domain.request.controller.MyLeaveRequestController;
import com.ssafy.leaveservice.domain.request.entity.LeaveRequest;
import com.ssafy.leaveservice.domain.request.repository.LeaveRequestRepository;
import com.ssafy.leaveservice.domain.request.service.MyLeaveRequestService;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MyLeaveRequestAuthenticationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class, UnavailableEmployeeAccess.class);

    @ParameterizedTest
    @MethodSource("profilesAndEndpoints")
    void nonDevProfilesRemainBlockedForBothEndpoints(String profile, String endpoint) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            LeaveRequestRepository repository = mock(LeaveRequestRepository.class);
            endpoint(repository, context.getBean(EmployeeAccess.class))
                    .perform(get("/api/v1/leaves/me/requests" + endpoint).param("employeeId", "84").header("X-Employee-Id", "84"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("LEAVE_AUTH_001"));
            verifyNoInteractions(repository);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/50"})
    void devStillUsesEmployeeOneDespiteSpoofedIdentity(String endpoint) {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            LeaveRequestRepository repository = mock(LeaveRequestRepository.class);
            if (!endpoint.isEmpty()) {
                LeaveRequest request = mock(LeaveRequest.class, RETURNS_DEEP_STUBS);
                when(request.getRequestId()).thenReturn(50L);
                when(request.getEmployeeId()).thenReturn(1L);
                when(request.getDates()).thenReturn(List.of());
                when(repository.findDetailByRequestIdAndEmployeeId(50L, 1L)).thenReturn(Optional.of(request));
            }
            MockMvc mvc = endpoint(repository, context.getBean(EmployeeAccess.class));
            mvc.perform(get("/api/v1/leaves/me/requests" + endpoint).param("employeeId", "84").header("X-Employee-Id", "84"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
            if (endpoint.isEmpty()) {
                verify(repository).findAllWithLeaveTypeByEmployeeId(1L);
            } else {
                verify(repository).findDetailByRequestIdAndEmployeeId(50L, 1L);
            }
            verify(repository, never()).findAllWithLeaveTypeByEmployeeId(84L);
            verify(repository, never()).findDetailByRequestIdAndEmployeeId(anyLong(), eq(84L));
        });
    }

    @ParameterizedTest
    @MethodSource("invalidIdsAndEndpoints")
    void invalidAuthenticatedIdCannotReadEitherEndpoint(Long employeeId, String endpoint) throws Exception {
        EmployeeAccess employeeAccess = mock(EmployeeAccess.class);
        when(employeeAccess.currentEmployeeId()).thenReturn(employeeId);
        LeaveRequestRepository repository = mock(LeaveRequestRepository.class);
        endpoint(repository, employeeAccess).perform(get("/api/v1/leaves/me/requests" + endpoint))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_001"));
        verifyNoInteractions(repository);
    }

    private MockMvc endpoint(LeaveRequestRepository repository, EmployeeAccess employeeAccess) {
        return MockMvcBuilders.standaloneSetup(new MyLeaveRequestController(new MyLeaveRequestService(repository, employeeAccess)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    static Stream<Arguments> profilesAndEndpoints() {
        return Stream.of("", "test", "prod", "production", "dev,prod", "dev,production")
                .flatMap(profile -> Stream.of("", "/50").map(endpoint -> Arguments.of(profile, endpoint)));
    }

    static Stream<Arguments> invalidIdsAndEndpoints() {
        return Stream.<Long>of(null, 0L, -1L)
                .flatMap(employeeId -> Stream.of("", "/50").map(endpoint -> Arguments.of(employeeId, endpoint)));
    }
}
