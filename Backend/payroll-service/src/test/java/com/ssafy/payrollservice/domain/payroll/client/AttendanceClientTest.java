package com.ssafy.payrollservice.domain.payroll.client;

import com.ssafy.nexa.security.BearerTokenRelayInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 근태 서비스 호출의 토큰 전달 (Spring 컨텍스트 없이 MockRestServiceServer 로) */
class AttendanceClientTest {

    private static final String BASE_URL = "http://attendance-service";
    private static final String SUMMARY_URL = BASE_URL + "/api/v1/attn/summary?empId=7&month=2026-10";
    private static final String SUMMARY_RESPONSE = """
            {"success":true,"data":{
              "days":{"workDays":21,"attendedDays":20,"holidayWorkedDays":1,"unconfirmedDays":0},
              "workTime":{"totalMin":9600,"regularMin":9600,"nightMin":0,"otExtendedMin":0,"otNightMin":0,"otHolidayMin":0}}}""";

    private MockRestServiceServer server;
    private AttendanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AttendanceClient(builder, BASE_URL, new BearerTokenRelayInterceptor());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 요청한_관리자의_access_토큰을_Bearer로_넘김() {
        Jwt jwt = Jwt.withTokenValue("admin-access-token").header("alg", "RS256")
                .subject("1").claim("type", "access").claim("role", "ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        server.expect(requestTo(SUMMARY_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-access-token"))
                .andRespond(withSuccess(SUMMARY_RESPONSE, MediaType.APPLICATION_JSON));

        AttendanceSummary summary = client.getSummary(7L, YearMonth.of(2026, 10));

        assertThat(summary.workedDays()).isEqualTo(21);
        server.verify();
    }

    @Test
    void 인증_정보가_없으면_Authorization_헤더를_붙이지_않음() {
        server.expect(requestTo(SUMMARY_URL))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(SUMMARY_RESPONSE, MediaType.APPLICATION_JSON));

        client.getSummary(7L, YearMonth.of(2026, 10));

        server.verify();
    }
}
