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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 업로드 → 매핑 추천 → 검증 → 원인별 묶음 → 매핑 수정 후 재검증 */
class ValidationIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/migrations";

    // 실제 회사 명부처럼: 필수 컬럼 일부 없음, 날짜 표기 섞임, 입사일 빈 칸, 사번 중복
    private static final String EMPLOYEE_CSV = """
            사번,이름,부서,직위,입사년월일,기본급,비고
            00123,홍길동,개발1팀,과장,2024-01-02,3600000,
            00124,김민수,개발1팀,대리,2023.3.15,3100000,육아휴직
            00125,이영희,인사팀,사원,20250701,2800000,
            00126,박철수,개발2팀,과정,,3300000,
            00123,김민수,영업팀,차장,2019-11-04,4200000,
            """;

    @Autowired
    MockMvc mvc;

    private long createJob() throws Exception {
        String body = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":1,"name":"검증 테스트"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.migrationId")).longValue();
    }

    private long upload(long migrationId) throws Exception {
        String body = mvc.perform(multipart(BASE + "/{id}/files", migrationId)
                        .file(new MockMultipartFile("file", "직원.csv", "text/csv", EMPLOYEE_CSV.getBytes(StandardCharsets.UTF_8)))
                        .param("entity", "HR_EMPLOYEE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.missingRequired",
                        containsInAnyOrder("이메일", "직급", "직책", "직군", "직종", "직무")))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.fileId")).longValue();
    }

    @Test
    void 업로드하면_매핑_추천과_검증이_돌고_원인별로_묶인다() throws Exception {
        long migrationId = createJob();
        long fileId = upload(migrationId);

        mvc.perform(get(BASE + "/{id}/files/{fileId}", migrationId, fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columnMappings[0].sourceColumn").value("사번"))
                .andExpect(jsonPath("$.data.columnMappings[0].targetField").value("empNo"))
                .andExpect(jsonPath("$.data.columnMappings[4].targetLabel").value("입사일"))
                .andExpect(jsonPath("$.data.columnMappings[5].targetField").doesNotExist());

        String issues = mvc.perform(get(BASE + "/{id}/issues", migrationId).param("fileId", String.valueOf(fileId)))
                .andExpect(status().isOk())
                // 필수 컬럼 없음 6 + 입사일 빈 칸 1 + 사번 중복 2 = BLOCK 9 / 날짜 표기 2 = FIX 2
                .andExpect(jsonPath("$.data.totals.BLOCK").value(9))
                .andExpect(jsonPath("$.data.totals.FIX").value(2))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> groups = JsonPath.read(issues, "$.data.groups");
        Map<String, Object> dup = groups.stream().filter(g -> "DUPLICATE_KEY".equals(g.get("code"))).findFirst().orElseThrow();
        assertThat(((Number) dup.get("count")).intValue()).isEqualTo(2);
        assertThat(dup.get("sampleRows")).isEqualTo(List.of(2, 6));
        assertThat((String) dup.get("message")).contains("00123").contains("2, 6행");

        Map<String, Object> dateFix = groups.stream().filter(g -> "DATE_FORMAT".equals(g.get("code"))).findFirst().orElseThrow();
        assertThat(((Number) dateFix.get("count")).intValue()).isEqualTo(2);
        assertThat(dateFix.get("sourceColumn")).isEqualTo("입사년월일");
        @SuppressWarnings("unchecked")
        List<String> sampleValues = (List<String>) dateFix.get("sampleValues");
        assertThat(sampleValues).containsExactlyInAnyOrder("2023.3.15", "20250701");
    }

    @Test
    void 매핑을_고치면_다시_검증한다() throws Exception {
        long migrationId = createJob();
        long fileId = upload(migrationId);

        // 입사일 연결을 끊으면: 입사일 필수 컬럼 없음 +1, 입사일 빈 칸·날짜 표기 오류는 사라짐
        mvc.perform(put(BASE + "/{id}/files/{fileId}/mappings", migrationId, fileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnMappings":[{"sourceColumn":"입사년월일","targetField":null}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingRequired", containsInAnyOrder("이메일", "직급", "직책", "직군", "직종", "직무", "입사일")))
                .andExpect(jsonPath("$.data.issueTotals.BLOCK").value(9))
                .andExpect(jsonPath("$.data.issueTotals.FIX").value(0));

        mvc.perform(get(BASE + "/{id}/files/{fileId}", migrationId, fileId))
                .andExpect(jsonPath("$.data.columnMappings[4].confirmed").value(true));
    }

    @Test
    void 잘못된_매핑은_400() throws Exception {
        long migrationId = createJob();
        long fileId = upload(migrationId);

        mvc.perform(put(BASE + "/{id}/files/{fileId}/mappings", migrationId, fileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnMappings":[{"sourceColumn":"비고","targetField":"empNo"}]}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(put(BASE + "/{id}/files/{fileId}/mappings", migrationId, fileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnMappings":[{"sourceColumn":"비고","targetField":"없는필드"}]}"""))
                .andExpect(status().isBadRequest());
    }
}
