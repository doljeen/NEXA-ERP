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
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveSubmissionAuthenticationTest {
    private static final ValidatorFactory FACTORY=Validation.buildDefaultValidatorFactory();
    private final HolidayClient holidays=mock(HolidayClient.class);
    private final LeaveSubmissionTransactionService transaction=mock(LeaveSubmissionTransactionService.class);
    private final ApplicationContextRunner runner=new ApplicationContextRunner()
            .withUserConfiguration(DevEmployeeAccess.class,UnavailableEmployeeAccess.class);
    private final LeaveRequestPreviewRequest input=new LeaveRequestPreviewRequest(1L,null,List.of(
            new LeaveRequestPreviewRequest.DateRequest(LocalDate.of(2035,1,1),LeaveRequestPortion.FULL)));

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @ParameterizedTest
    @ValueSource(strings={"","test","prod","production","dev,prod","dev,production"})
    void productionProfilesFailBeforeExternalAndStorage(String profile) {
        runner.withPropertyValues("spring.profiles.active="+profile).run(context -> {
            assertThatThrownBy(()->service(context.getBean(EmployeeAccess.class)).submit("key",input))
                    .isInstanceOfSatisfying(BusinessException.class,error->
                            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_UNAVAILABLE));
            verifyNoInteractions(holidays,transaction);
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs={0,-1})
    void invalidEmployeeCannotSubmit(Long id) {
        EmployeeAccess access=mock(EmployeeAccess.class);
        when(access.currentEmployeeId()).thenReturn(id);
        assertThatThrownBy(()->service(access).submit("key",input)).isInstanceOfSatisfying(BusinessException.class,
                error->assertThat(error.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(holidays,transaction);
    }

    @Test
    void devUsesExistingEmployeeAndDoesNotRequireAdmin() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context->{
            EmployeeAccess access=spy(context.getBean(EmployeeAccess.class));
            when(holidays.findHolidays(any(),any())).thenReturn(new HolidayClient.HolidayResult(true,List.of()));
            service(access).submit("key",input);
            verify(access).currentEmployeeId();
            verify(access,never()).isAdmin(any());
            verify(transaction).submit(eq("key"),matches("[0-9a-f]{64}"),eq(input),any());
        });
    }

    private LeaveSubmissionService service(EmployeeAccess access) {
        return new LeaveSubmissionService(access,FACTORY.getValidator(),new SubmissionPayloadHash(),holidays,transaction);
    }
}
