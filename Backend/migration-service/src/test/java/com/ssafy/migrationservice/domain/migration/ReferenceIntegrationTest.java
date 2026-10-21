package com.ssafy.migrationservice.domain.migration;

import com.jayway.jsonpath.JsonPath;
import com.ssafy.migrationservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 직원명부가 아닌 파일의 사번·이름을 같은 작업의 직원명부와 대조 */
class ReferenceIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/migrations";

    private static final String ROSTER = """
            사번,이름,이메일,부서,직위,직급,직책,직군,직종,직무,입사일
            00123,홍길동,a@x.com,개발팀,과장,G3,팀원,개발,SW,백엔드,2024-01-02
            00124,김민수,b@x.com,개발팀,대리,G2,팀원,개발,SW,백엔드,2023-03-15
            00125,이영희,c@x.com,인사팀,사원,G1,팀원,경영지원,인사,채용,2025-07-01
            """;

    // 근태기록 같은 참고 자료: 앞자리 0 빠진 사번, 명부에 없는 사번, 사번 빈 칸(이름만), 이름 다름
    private static final String ATTENDANCE = """
            사번,성명,근무일,출근,퇴근
            00123,홍길동,2026-12-01,09:00,18:00
            124,김민수,2026-12-01,09:00,18:00
            00999,박철수,2026-12-01,09:00,18:00
            ,이영희,2026-12-01,09:00,18:00
            00125,이영이,2026-12-02,09:00,18:00
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void 사번이_직원명부에_있는지_이름이_같은지_본다() throws Exception {
        String created = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                        {"companyId":1,"name":"참조 테스트"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long migrationId = ((Number) JsonPath.read(created, "$.data.migrationId")).longValue();
        upload(migrationId, "직원.csv", ROSTER, "HR_EMPLOYEE");
        long fileId = upload(migrationId, "근태기록.csv", ATTENDANCE, "REFERENCE");

        String body = mvc.perform(get(BASE + "/{id}/issues", migrationId).param("fileId", String.valueOf(fileId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> issues = JsonPath.read(body, "$.data.content");

        assertThat(issues).extracting(i -> i.get("rowNo") + ":" + i.get("code")).containsExactlyInAnyOrder(
                "4:EMP_NOT_IN_ROSTER", "5:EMP_KEY_MISSING", "6:EMP_NAME_MISMATCH", "null:EMPNO_ZERO_PADDED");
        assertThat(issues).filteredOn(i -> "EMP_KEY_MISSING".equals(i.get("code")))
                .extracting(i -> i.get("suggestion")).containsExactly("00125");
    }

    private long upload(long migrationId, String name, String csv, String entity) throws Exception {
        String body = mvc.perform(multipart(BASE + "/{id}/files", migrationId)
                        .file(new MockMultipartFile("file", name, "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("entity", entity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.fileId")).longValue();
    }
}
