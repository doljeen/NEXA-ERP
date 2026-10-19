package com.ssafy.user_service.domain.user;

import com.jayway.jsonpath.JsonPath;
import com.ssafy.user_service.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 사원 등록/퇴직 API. 각 테스트는 고유한 이메일로 자기 사원을 만든다. */
class EmployeeAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_LOGIN = """
            {"email":"admin@local.dev","pass":"admin1234!"}""";

    @Autowired
    MockMvc mvc;

    private Cookie[] login(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private static String loginJson(String email, String pass) {
        return """
                {"email":"%s","pass":"%s"}""".formatted(email, pass);
    }

    private static String employeeJson(String email, long positionId) {
        return """
                {"email":"%s","password":"user12345","name":"Hong","deptId":1,"positionId":%d,
                 "gradeId":1,"dutyId":1,"jobFamilyId":1,"jobKindId":2,"jobRoleId":3,
                 "gender":"MALE","hireType":"RECOMMENDATION","hireDate":"2026-01-02"}""".formatted(email, positionId);
    }

    private long createEmployee(Cookie[] admin, String email) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/admin/employees").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(employeeJson(email, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeId").isNumber())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.employeeId");
        return id.longValue();
    }

    @Test
    void 필수값_누락은_400_필드별_메시지() throws Exception {
        Cookie[] admin = login(ADMIN_LOGIN);
        mvc.perform(post("/api/v1/admin/employees").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"x","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 없는_직위는_400_EMP_005() throws Exception {
        Cookie[] admin = login(ADMIN_LOGIN);
        mvc.perform(post("/api/v1/admin/employees").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("pos@local.dev", 99)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMP_005"));
    }

    @Test
    void 등록_성공_후_같은_이메일은_409() throws Exception {
        Cookie[] admin = login(ADMIN_LOGIN);
        createEmployee(admin, "dup@local.dev");
        mvc.perform(post("/api/v1/admin/employees").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(employeeJson("dup@local.dev", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMP_002"));
    }

    @Test
    void 일반_사원은_admin_API_403() throws Exception {
        Cookie[] admin = login(ADMIN_LOGIN);
        createEmployee(admin, "plain@local.dev");
        Cookie[] user = login(loginJson("plain@local.dev", "user12345"));
        mvc.perform(post("/api/v1/admin/employees").cookie(user).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_009"));
    }

    @Test
    void 퇴직처리_후_재발급과_로그인_차단_중복퇴직은_409() throws Exception {
        Cookie[] admin = login(ADMIN_LOGIN);
        long employeeId = createEmployee(admin, "quit@local.dev");
        Cookie[] user = login(loginJson("quit@local.dev", "user12345"));

        mvc.perform(patch("/api/v1/admin/employees/" + employeeId + "/terminate").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resignDate":"2026-10-19","resignReason":"이직"}"""))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/reissue").cookie(user))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_006"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("quit@local.dev", "user12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_006"));

        mvc.perform(patch("/api/v1/admin/employees/" + employeeId + "/terminate").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resignDate":"2026-10-19"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMP_008"));
    }

    @Test
    void 없는_사원_퇴직처리는_404() throws Exception {
        Cookie[] admin = login(ADMIN_LOGIN);
        mvc.perform(patch("/api/v1/admin/employees/999999/terminate").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resignDate":"2026-10-19"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EMP_001"));
    }
}
