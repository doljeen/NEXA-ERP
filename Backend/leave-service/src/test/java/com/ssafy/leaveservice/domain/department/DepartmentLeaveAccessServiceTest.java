package com.ssafy.leaveservice.domain.department;

import com.ssafy.leaveservice.client.hr.HrClient;
import com.ssafy.leaveservice.client.hr.HrClient.*;
import com.ssafy.leaveservice.client.hr.HrIntegrationProperties;
import com.ssafy.leaveservice.client.hr.HrIntegrationProperties.JwtClaims;
import com.ssafy.leaveservice.domain.department.service.DepartmentLeaveAccessService;
import com.ssafy.leaveservice.global.auth.TokenEmployeeAccess;
import com.ssafy.leaveservice.global.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DepartmentLeaveAccessServiceTest {
    private final HrClient hrClient = mock(HrClient.class);
    private DepartmentLeaveAccessService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentLeaveAccessService(new TokenEmployeeAccess(),properties(true,"employee","members","duty",
                new JwtClaims("test_dept","test_duty","test_position")),hrClient);
        token("10",new HashMap<>(Map.of("test_dept",3L,"test_duty",7L,"test_position",99L)));
        when(hrClient.isAvailable()).thenReturn(true);
        when(hrClient.findEmployee(10L)).thenReturn(new EmployeeInfo(10L,3L,7L,99L));
        when(hrClient.findDuty(7L)).thenReturn(new DutyInfo(7L,"휴가 관리자"));
        when(hrClient.findDepartmentMembers(10L)).thenReturn(new MemberBatch(List.of(new Member(11L,3L),new Member(10L,3L)),true));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void actualSubAndConfiguredClaimsUseCurrentHrDutyAndActorBasedMembers() {
        assertThat(service.getAuthorizedEmployeeIds(3L)).containsExactly(10L,11L);
        verify(hrClient).findEmployee(10L);
        verify(hrClient).findDuty(7L);
        verify(hrClient).findDepartmentMembers(10L);
        verify(hrClient,never()).findDepartmentMembers(3L);
    }

    @ParameterizedTest
    @ValueSource(strings={"DISABLED","EMPLOYEE_URI","MEMBER_URI","DUTY_URI","NO_KEYS","MISSING_KEY","DUPLICATE_KEY","RESERVED_KEY"})
    void missingConfigurationFailsClosedBeforeAnyHrOrDatabase(String issue) {
        JwtClaims keys = switch (issue) {
            case "NO_KEYS" -> null;
            case "MISSING_KEY" -> new JwtClaims("test_dept","test_duty","");
            case "DUPLICATE_KEY" -> new JwtClaims("test_dept","test_dept","test_position");
            case "RESERVED_KEY" -> new JwtClaims("sub","test_duty","test_position");
            default -> new JwtClaims("test_dept","test_duty","test_position");
        };
        HrIntegrationProperties settings = properties(!issue.equals("DISABLED"),
                issue.equals("EMPLOYEE_URI")?"":"employee",issue.equals("MEMBER_URI")?"":"members",
                issue.equals("DUTY_URI")?"":"duty",keys);
        DepartmentLeaveAccessService unavailable = new DepartmentLeaveAccessService(new TokenEmployeeAccess(),settings,hrClient);
        assertCode(()->unavailable.getAuthorizedEmployeeIds(3L),ErrorCode.HR_VERIFICATION_UNAVAILABLE);
        verifyNoInteractions(hrClient);
    }

    @Test
    void configuredUrisCannotEnableMissingClient() {
        when(hrClient.isAvailable()).thenReturn(false);
        assertCode(()->service.getAuthorizedEmployeeIds(3L),ErrorCode.HR_VERIFICATION_UNAVAILABLE);
        verify(hrClient,never()).findEmployee(any());
    }

    @ParameterizedTest
    @ValueSource(strings={"NULL","ZERO","NEGATIVE","TEXT","FRACTION","FLOAT","OVERFLOW","BOOL","ARRAY"})
    void invalidOrganizationIdClaimCannotGrantAccess(String kind) {
        Object claim = switch (kind) {
            case "NULL" -> null;
            case "ZERO" -> 0L;
            case "NEGATIVE" -> -1L;
            case "TEXT" -> "ADMIN";
            case "FRACTION" -> 3.5;
            case "FLOAT" -> 3.0;
            case "OVERFLOW" -> new BigInteger("9223372036854775808");
            case "BOOL" -> true;
            default -> List.of(3L);
        };
        Map<String,Object> claims = new HashMap<>(Map.of("test_duty",7L,"test_position",99L));
        if (claim != null) { claims.put("test_dept",claim); }
        token("10",claims);
        assertCode(()->service.getAuthorizedEmployeeIds(3L),ErrorCode.UNAUTHORIZED);
        verify(hrClient,never()).findEmployee(any());
    }

    @Test
    void decimalStringIdsAreAcceptedWithoutRoundingOrTrimming() {
        token("10",new HashMap<>(Map.of("test_dept","3","test_duty","7","test_position","99")));
        assertThat(service.getAuthorizedEmployeeIds(3L)).containsExactly(10L,11L);
    }

    @ParameterizedTest
    @ValueSource(strings={"bad","0","-1","9223372036854775808"})
    void invalidSubjectDoesNotLeakParsingException(String subject) {
        token(subject,new HashMap<>(Map.of("test_dept",3L,"test_duty",7L,"test_position",99L)));
        assertCode(()->service.getAuthorizedEmployeeIds(3L),ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(hrClient);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs={0,-1})
    void invalidDepartmentIdIsBadRequest(Long departmentId) {
        assertCode(()->service.getAuthorizedEmployeeIds(departmentId),ErrorCode.INVALID_INPUT);
        verifyNoInteractions(hrClient);
    }

    @Test
    void missingAuthenticationCannotUseHrOrClaimConfiguration() {
        SecurityContextHolder.clearContext();
        assertCode(()->service.getAuthorizedEmployeeIds(3L),ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(hrClient);
    }

    private void token(String subject, Map<String,Object> claims) {
        Jwt jwt = Jwt.withTokenValue("test-only").header("alg","none").subject(subject)
                .claim("role","ADMIN").claims(values -> values.putAll(claims)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of()));
    }

    private HrIntegrationProperties properties(boolean enabled,String employeeUri,String membersUri,String dutyUri,JwtClaims claims) {
        return new HrIntegrationProperties(enabled,"",employeeUri,membersUri,dutyUri,Duration.ofSeconds(2),Duration.ofSeconds(3),claims);
    }

    private void assertCode(Runnable action,ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
