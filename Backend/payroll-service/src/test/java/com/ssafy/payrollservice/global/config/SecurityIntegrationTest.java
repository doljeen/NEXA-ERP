package com.ssafy.payrollservice.global.config;

import com.ssafy.payrollservice.domain.payroll.service.AdminPayslipService;
import com.ssafy.payrollservice.domain.payroll.service.PayslipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * nexa-security 적용 확인: 토큰 없으면 401, 관리자 경로 권한, 토큰의 sub 가 사원 ID 로 들어가는지.
 * 인증은 jwt() 로 흉내 내므로 JWKS 서버가 필요 없다. 서비스는 mock 으로 두고 보안 설정만 본다.
 */
@SpringBootTest(properties = {"spring.config.location=classpath:/payroll-ci.yml"})
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {

    private static final String CONTRACT = "/api/v1/pay/me/contract";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PayslipService payslipService;

    @MockitoBean
    AdminPayslipService adminPayslipService;

    @Test
    void 토큰이_없으면_401_AUTH_001() throws Exception {
        mvc.perform(get(CONTRACT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void X_User_Id_헤더만_보내면_401() throws Exception {
        mvc.perform(get(CONTRACT).header("X-User-Id", "7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void 토큰의_sub가_사원_ID로_들어감() throws Exception {
        mvc.perform(get(CONTRACT).with(user(7)))
                .andExpect(status().isOk());

        verify(payslipService).getMyContract(7L);
    }

    @Test
    void 일반_사원은_급여_생성_403_AUTH_009() throws Exception {
        mvc.perform(generate().with(user(7)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_009"));
    }

    @Test
    void 관리자는_급여_생성_가능() throws Exception {
        mvc.perform(generate().with(admin(1)))
                .andExpect(status().isCreated());
    }

    @Test
    void 헬스체크는_토큰_없이_열림() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder generate() {
        return post("/api/v1/pay/admin/payslips/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"empId":7,"month":"2026-10","empName":"홍길동","deptName":"개발팀",
                         "positionName":"사원","hireDate":"2024-01-02","gradeId":1}""");
    }

    private static RequestPostProcessor user(long empId) {
        return jwt().jwt(j -> j.subject(String.valueOf(empId)).claim("type", "access").claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor admin(long empId) {
        return jwt().jwt(j -> j.subject(String.valueOf(empId)).claim("type", "access").claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
