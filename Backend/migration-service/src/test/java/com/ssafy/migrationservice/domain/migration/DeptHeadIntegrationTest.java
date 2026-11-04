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

/** 조직 파일의 부서유형 단계·부서장 사번 ↔ 직원명부 (user-service 부서 유형·부서장 규칙) */
class DeptHeadIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/migrations";

    // 4행: 명부에 없는 사번, 5행: 사번 대신 이름, 6행: 팀 아래 본부 + 퇴직자 + 다른 부서 소속, 7행: 회사 아래 회사
    private static final String ORG = """
            부서코드,부서명,상위부서,부서유형,부서장
            C0,테스트(주),,회사,E001
            D1,개발본부,C0,본부,E002
            T1,개발1팀,D1,팀,E009
            T2,영업팀,D1,팀,김영업
            T3,품질팀,T1,본부,E004
            T4,디자인팀,C0,회사,
            """;

    private static final String ROSTER = """
            사번,이름,부서,재직상태,퇴사일
            E001,대표,테스트(주),재직,
            E002,본부장,개발1팀,재직,
            E003,김영업,영업팀,재직,
            E004,퇴사자,영업팀,TERMINATED,
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void 부서유형_단계와_부서장을_직원명부와_대조한다_명부를_나중에_올려도() throws Exception {
        String created = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                        {"companyId":1,"name":"부서장 테스트"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long migrationId = ((Number) JsonPath.read(created, "$.data.migrationId")).longValue();
        long org = upload(migrationId, "조직.csv", ORG, "HR_ORG");

        assertThat(codes(migrationId, org)).contains("6:DEPT_TYPE_ORDER", "7:DEPT_TYPE_ORDER")
                .doesNotContain("4:HEAD_NOT_IN_ROSTER");

        upload(migrationId, "직원명부.csv", ROSTER, "HR_EMPLOYEE");

        assertThat(codes(migrationId, org))
                .contains("4:HEAD_NOT_IN_ROSTER", "5:HEAD_NAME_GIVEN", "6:HEAD_NOT_EMPLOYED", "6:HEAD_OUTSIDE_DEPT",
                        "6:DEPT_TYPE_ORDER", "7:DEPT_TYPE_ORDER")
                .doesNotContain("2:HEAD_OUTSIDE_DEPT", "3:HEAD_OUTSIDE_DEPT", "5:HEAD_OUTSIDE_DEPT",
                        "2:DEPT_TYPE_ORDER", "3:DEPT_TYPE_ORDER", "5:DEPT_TYPE_ORDER");
    }

    private List<String> codes(long migrationId, long fileId) throws Exception {
        String body = mvc.perform(get(BASE + "/{id}/issues", migrationId).param("fileId", String.valueOf(fileId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> issues = JsonPath.read(body, "$.data.content");
        return issues.stream().map(i -> i.get("rowNo") + ":" + i.get("code")).toList();
    }

    private long upload(long migrationId, String name, String csv, String entity) throws Exception {
        String body = mvc.perform(multipart(BASE + "/{id}/files", migrationId)
                        .file(new MockMultipartFile("file", name, "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("entity", entity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.fileId")).longValue();
    }
}
