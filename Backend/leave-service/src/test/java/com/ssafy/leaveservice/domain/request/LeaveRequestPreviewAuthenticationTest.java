package com.ssafy.leaveservice.domain.request;

import com.ssafy.leaveservice.client.attendance.HolidayClient;
import com.ssafy.leaveservice.domain.request.dto.LeaveRequestPreviewRequest;
import com.ssafy.leaveservice.domain.request.entity.LeaveRequestPortion;
import com.ssafy.leaveservice.domain.request.service.*;
import com.ssafy.leaveservice.global.auth.*;
import com.ssafy.leaveservice.global.exception.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveRequestPreviewAuthenticationTest {
    private static final ValidatorFactory FACTORY=Validation.buildDefaultValidatorFactory();
    private final HolidayClient holidays=mock(HolidayClient.class);
    private final LeaveRequestPreviewSnapshotService snapshot=mock(LeaveRequestPreviewSnapshotService.class);
    private final Clock clock=Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC);
    private final LeaveRequestPreviewRequest request=new LeaveRequestPreviewRequest(1L,null,List.of(
            new LeaveRequestPreviewRequest.DateRequest(LocalDate.of(2030,1,7),LeaveRequestPortion.FULL)));
    private final ApplicationContextRunner runner=new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class,UnavailableEmployeeAccess.class);

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @ParameterizedTest
    @ValueSource(strings={"","test","prod","production","dev,prod","dev,production"})
    void existingProfilesBlockBeforeAnyExternalOrDatabaseWork(String profile) {
        runner.withPropertyValues("spring.profiles.active="+profile).run(context -> {
            LeaveRequestPreviewService service=service(context.getBean(EmployeeAccess.class));
            assertThatThrownBy(()->service.preview(request)).isInstanceOfSatisfying(BusinessException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_UNAVAILABLE));
            verifyNoInteractions(holidays,snapshot);
        });
    }

    @Test
    void devUsesExistingEmployeeOneWithoutRequiringAdminAndPassesSingleClockInstant() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            EmployeeAccess access=spy(context.getBean(EmployeeAccess.class));
            HolidayClient.HolidayResult result=new HolidayClient.HolidayResult(true,List.of());
            when(holidays.findHolidays(any(),any())).thenReturn(result);
            service(access).preview(request);
            verify(access).currentEmployeeId();
            verify(access,never()).isAdmin(any());
            verify(snapshot).preview(eq(request),eq(OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC)),same(result));
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs={0,-1})
    void invalidAuthenticatedIdIsRejectedBeforeCalls(Long employeeId) {
        EmployeeAccess access=mock(EmployeeAccess.class);
        when(access.currentEmployeeId()).thenReturn(employeeId);
        assertThatThrownBy(()->service(access).preview(request)).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(holidays,snapshot);
    }

    @Test
    void directServiceCallsStillValidateRequest() {
        EmployeeAccess access=new DevEmployeeAccess();
        for(LeaveRequestPreviewRequest invalid:Arrays.asList(null,new LeaveRequestPreviewRequest(1L,null,List.of()))) {
            assertThatThrownBy(()->service(access).preview(invalid)).isInstanceOfSatisfying(BusinessException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        verifyNoInteractions(holidays,snapshot);
    }

    private LeaveRequestPreviewService service(EmployeeAccess access) {
        return new LeaveRequestPreviewService(access,FACTORY.getValidator(),clock,holidays,snapshot);
    }
}
