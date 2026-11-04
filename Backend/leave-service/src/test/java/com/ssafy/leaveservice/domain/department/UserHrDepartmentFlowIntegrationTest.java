package com.ssafy.leaveservice.domain.department;

import com.ssafy.leaveservice.support.PostgresTestConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "leave.test.database-owner=UserHrDepartmentFlowIntegrationTest",
        "leave.hr.enabled=true",
        "leave.hr.employee-info-uri=/api/v1/employees/{employeeId}",
        "leave.hr.department-members-uri=/api/v1/employees?deptId={departmentId}",
        "leave.hr.duty-info-uri=/api/v1/duties/{dutyId}",
        "leave.hr.jwt-claims.department-id=deptId",
        "leave.hr.jwt-claims.duty-id=dutyId",
        "leave.hr.read-timeout=1s",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserHrDepartmentFlowIntegrationTest {
    private static HttpServer server;
    private static final AtomicReference<UserHrDepartmentFlowIntegrationTest> CURRENT = new AtomicReference<>();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String requesterStatus = "EMPLOYED";
    private String dutyName = "휴가 관리자";
    private String errorPath;
    private int remoteStatus = 503;
    private boolean delayEmployee;
    private String memberOverride;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Throwable> handlerFailure = new AtomicReference<>();

    @DynamicPropertySource
    static void httpProperties(DynamicPropertyRegistry registry) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/", exchange -> CURRENT.get().handle(exchange));
        server.start();
        registry.add("leave.hr.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterAll
    static void stopHttp() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void seed() {
        CURRENT.set(this);
        jdbc.update("update leave_policy_group set active_policy_version_id=null");
        for (String table : List.of("leave_ledger", "leave_request_allocation", "leave_request_date", "leave_request",
                "leave_grant", "leave_policy_type_rule", "employee_leave_policy_assignment", "leave_account",
                "leave_type", "leave_balance_type", "leave_policy_version", "leave_policy_group")) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("insert into leave_policy_group(policy_id,name) values(1,'정책')");
        jdbc.update("""
                insert into leave_policy_version(policy_version_id,policy_id,version_no,base_type)
                values(1,1,1,'HIRE_DATE')
                """);
        jdbc.update("insert into leave_balance_type(balance_type_id,name) values(1,'연차')");
        jdbc.update("""
                insert into leave_type(leave_type_id,balance_type_id,name,balance_mode,request_unit)
                values(1,1,'연차','ACCOUNT','DAY')
                """);
        jdbc.update("insert into leave_account(account_id,emp_id,balance_type_id) values(1,10,1),(2,11,1),(3,12,1),(4,99,1)");
        jdbc.update("""
                insert into leave_grant(grant_id,account_id,grant_kind,original_minutes,valid_from)
                values(1,1,'MANUAL',9999,'2000-01-01Z'),(2,2,'MANUAL',9999,'2000-01-01Z'),
                      (3,3,'MANUAL',9999,'2000-01-01Z'),(4,4,'MANUAL',9999,'2000-01-01Z')
                """);
        jdbc.update("""
                insert into leave_ledger(account_id,grant_id,event_type,quantity_delta,reserved_delta,idempotency_key,created_at)
                values(1,1,'GRANT',480,0,'grant1','2000-01-01Z'),(1,1,'RESERVE',0,120,'reserve1','2000-01-01Z'),
                      (2,2,'GRANT',960,0,'grant2','2000-01-01Z'),(3,3,'GRANT',777,0,'terminated','2000-01-01Z'),
                      (4,4,'GRANT',888,0,'other-department','2000-01-01Z')
                """);
        jdbc.update("""
                insert into leave_request(leave_req_id,emp_id,policy_version_id,leave_type_id,status,total_minutes,created_at)
                values(1,10,1,1,'APPROVED',480,'2020-01-01Z'),(2,11,1,1,'REJECTED',480,'2020-01-02Z'),
                      (3,12,1,1,'APPROVED',480,'2020-01-03Z'),(4,99,1,1,'APPROVED',480,'2020-01-04Z')
                """);
    }

    @Test
    void realHttpAndJwtPermitCurrentDirectDepartmentAndPreserveLedger() throws Exception {
        List<Map<String,Object>> before = jdbc.queryForList("select *,xmin::text from leave_ledger order by entry_id");
        request("requests", 3L, 3L, 7L).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].employeeId").value(11))
                .andExpect(jsonPath("$.data[1].employeeId").value(10));
        assertThat(calls).hasValue(3);

        request("balances", 3L, 3L, 7L).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentId").value(3))
                .andExpect(jsonPath("$.data.employees.length()").value(3))
                .andExpect(jsonPath("$.data.employees[0].balances[0].quantityMinutes").value(480))
                .andExpect(jsonPath("$.data.employees[0].balances[0].reservedMinutes").value(120))
                .andExpect(jsonPath("$.data.employees[0].balances[0].remainingMinutes").value(360))
                .andExpect(jsonPath("$.data.employees[1].employeeId").value(11))
                .andExpect(jsonPath("$.data.employees[2].balances.length()").value(0));
        assertThat(calls).hasValue(6);
        assertThat(handlerFailure.get()).isNull();
        assertThat(jdbc.queryForList("select *,xmin::text from leave_ledger order by entry_id")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ON_LEAVE", "TERMINATED"})
    void nonEmployedRequesterCannotAccessEvenWithAdminRole(String employment) throws Exception {
        requesterStatus = employment;
        request("requests", 3L, 3L, 7L).andExpect(status().isForbidden());
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEPARTMENT", "DUTY", "OTHER_DEPARTMENT", "DUTY_NAME"})
    void latestOrganizationAndDutyMustMatch(String issue) throws Exception {
        if (issue.equals("DUTY_NAME")) {
            dutyName = "일반 직원";
        }
        request("balances", issue.equals("OTHER_DEPARTMENT") ? 4L : 3L,
                issue.equals("DEPARTMENT") ? 4L : 3L, issue.equals("DUTY") ? 8L : 7L)
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 500, 503})
    void failedRemoteEmployeeLookupIs503(int status) throws Exception {
        errorPath = "/api/v1/employees/10";
        remoteStatus = status;
        request("balances", 3L, 3L, 7L).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("LEAVE_017"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL_STATUS", "UNKNOWN_STATUS", "DUPLICATE", "OTHER_DEPT", "MISSING_ARRAY", "MALFORMED"})
    void incompleteOrUntrustedMembersFailClosed(String issue) throws Exception {
        memberOverride = switch (issue) {
            case "NULL_STATUS" -> "{\"employees\":[{\"empId\":11,\"deptId\":3}]}";
            case "UNKNOWN_STATUS" -> "{\"employees\":[{\"empId\":11,\"deptId\":3,\"status\":\"ACTIVE\"}]}";
            case "DUPLICATE" -> "{\"employees\":[" + employee(11,3,"EMPLOYED") + "," + employee(11,3,"EMPLOYED") + "]}";
            case "OTHER_DEPT" -> "{\"employees\":[" + employee(99,4,"TERMINATED") + "]}";
            case "MISSING_ARRAY" -> "{}";
            default -> "not-json";
        };
        request("requests", 3L, 3L, 7L).andExpect(status().isServiceUnavailable());
    }

    @Test
    void emptyDepartmentReturnsBothEmptyResponses() throws Exception {
        memberOverride = "{\"employees\":[]}";
        request("requests", 3L, 3L, 7L).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        request("balances", 3L, 3L, 7L).andExpect(status().isOk()).andExpect(jsonPath("$.data.employees.length()").value(0));
    }


    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/duties/7", "/api/v1/employees"})
    void failedDutyOrMembershipLookupCannotGrantAccess(String path) throws Exception {
        errorPath = path;
        request("requests", 3L, 3L, 7L).andExpect(status().isServiceUnavailable());
    }

    @Test
    void readTimeoutFailsClosed() throws Exception {
        delayEmployee = true;
        request("balances", 3L, 3L, 7L).andExpect(status().isServiceUnavailable());
    }

    private org.springframework.test.web.servlet.ResultActions request(String endpoint, long department, long tokenDept, long tokenDuty)
            throws Exception {
        return mvc.perform(get("/api/v1/leaves/departments/{departmentId}/" + endpoint, department)
                .with(jwt().jwt(token -> token.subject("10").claim("type","access").claim("role","ADMIN")
                        .claim("deptId",tokenDept).claim("dutyId",tokenDuty).claim("gradeId",500L))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void handle(HttpExchange exchange) {
        try (exchange) {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Bearer ");
            String path = exchange.getRequestURI().getPath();
            if (delayEmployee && path.equals("/api/v1/employees/10")) {
                Thread.sleep(1500);
            }
            if (path.equals(errorPath)) {
                respond(exchange, remoteStatus, "{\"success\":false}");
                return;
            }
            String data;
            if (path.equals("/api/v1/employees/10")) {
                data = employee(10,3,requesterStatus);
            } else if (path.equals("/api/v1/duties/7")) {
                data = "{\"dutyId\":7,\"name\":\"" + dutyName + "\"}";
            } else {
                assertThat(path).isEqualTo("/api/v1/employees");
                assertThat(exchange.getRequestURI().getQuery()).isEqualTo("deptId=3");
                data = memberOverride == null ? "{\"employees\":[" + employee(10,3,"EMPLOYED") + ","
                        + employee(11,3,"ON_LEAVE") + "," + employee(12,3,"TERMINATED") + ","
                        + employee(13,3,"EMPLOYED") + "]}" : memberOverride;
            }
            respond(exchange,200,"{\"success\":true,\"error\":null,\"data\":" + data + "}");
        } catch (Throwable failure) {
            handlerFailure.compareAndSet(null,failure);
        }
    }

    private static String employee(long employeeId, long deptId, String status) {
        return "{\"empId\":" + employeeId + ",\"deptId\":" + deptId
                + ",\"dutyId\":7,\"positionId\":99,\"gradeId\":500,\"status\":\"" + status + "\"}";
    }

    private void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(status,body.length);
        exchange.getResponseBody().write(body);
    }
}
