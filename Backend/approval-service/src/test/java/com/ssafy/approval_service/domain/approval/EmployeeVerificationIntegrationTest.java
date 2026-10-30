package com.ssafy.approval_service.domain.approval;

import com.jayway.jsonpath.JsonPath;
import com.ssafy.approval_service.support.AbstractIntegrationTest;
import com.ssafy.approval_service.support.FakeUserServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.ssafy.approval_service.support.ApprovalTestFixtures.BASE;
import static com.ssafy.approval_service.support.ApprovalTestFixtures.line;
import static com.ssafy.approval_service.support.ApprovalTestFixtures.path;
import static com.ssafy.approval_service.support.ApprovalTestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결재선 사원 존재·재직 확인. 역할과 관계없이 재직(EMPLOYED) 사원만 넣을 수 있고,
 * user-service 에 확인할 수 없으면 503 으로 막는다. user-service 는 FakeUserServiceClient (사원 ID 300_000번대).
 */
class EmployeeVerificationIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong EMP_SEQ = new AtomicLong(300_000);

    @Autowired
    FakeUserServiceClient userService;

    @AfterEach
    void tearDown() {
        userService.reset();
    }

    private static long newEmployee() {
        return EMP_SEQ.incrementAndGet();
    }

    private ResultActions addApprover(long documentId, long drafter, String line) throws Exception {
        return mvc.perform(post(path(documentId, "/approvers")).with(user(drafter))
                .contentType(MediaType.APPLICATION_JSON).content(line));
    }

    private ResultActions addParticipant(long documentId, String sub, long drafter, long empId) throws Exception {
        return mvc.perform(post(path(documentId, sub)).with(user(drafter))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"empId":%d}""".formatted(empId)));
    }

    private static void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code));
    }

    private List<Number> lineEmpIds(long documentId, long drafter) throws Exception {
        String body = mvc.perform(get(path(documentId, "")).with(user(drafter)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.approvalLines[*].empId");
    }

    private List<Number> listedDocuments(long drafter) throws Exception {
        String body = mvc.perform(get(BASE).with(user(drafter)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.documents[*].documentId");
    }

    // ===== 상신 =====

    @Test
    void 결재선에_없는_사원이_있으면_상신_400_APPROVAL_015() throws Exception {
        long drafter = newEmployee();
        long ghost = newEmployee();
        userService.markMissing(ghost);

        expectError(submit(drafter, line(newEmployee(), 1, "APPROVER"), line(ghost, 2, "APPROVER")),
                400, "APPROVAL_015");
        assertThat(listedDocuments(drafter)).isEmpty();
    }

    @Test
    void 퇴직자와_휴직자는_역할과_관계없이_상신_400_APPROVAL_016() throws Exception {
        long drafter = newEmployee();
        long terminated = newEmployee();
        long onLeave = newEmployee();
        userService.setStatus(terminated, "TERMINATED");
        userService.setStatus(onLeave, "ON_LEAVE");

        expectError(submit(drafter, line(terminated, 1, "APPROVER")), 400, "APPROVAL_016");
        expectError(submit(drafter, line(newEmployee(), 1, "APPROVER"), line(onLeave, 2, "AGREEMENT")),
                400, "APPROVAL_016");
        expectError(submit(drafter, line(newEmployee(), 1, "APPROVER"), line(terminated, 2, "REFERENCE")),
                400, "APPROVAL_016");
        expectError(submit(drafter, line(newEmployee(), 1, "APPROVER"), line(onLeave, 2, "VIEWER")),
                400, "APPROVAL_016");
        assertThat(listedDocuments(drafter)).isEmpty();
    }

    @Test
    void 결재선_전체를_한_번의_호출로_확인() throws Exception {
        long drafter = newEmployee();
        long approver = newEmployee();
        long agreement = newEmployee();
        long referrer = newEmployee();
        long viewer = newEmployee();

        createDocument(drafter,
                line(approver, 1, "APPROVER"),
                line(agreement, 2, "AGREEMENT"),
                line(referrer, 3, "REFERENCE"),
                line(viewer, 4, "VIEWER"));

        assertThat(userService.callsContaining(approver))
                .singleElement()
                .satisfies(ids -> assertThat(ids).containsExactlyInAnyOrder(approver, agreement, referrer, viewer));
    }

    @Test
    void 요청_안의_규칙_위반은_user_service를_부르지_않음() throws Exception {
        long drafter = newEmployee();
        long approver = newEmployee();

        expectError(submit(drafter, line(approver, 1, "APPROVER"), line(drafter, 2, "APPROVER")), 400, "APPROVAL_002");
        assertThat(userService.callsContaining(approver)).isEmpty();
    }

    // ===== 결재선 변경, 재기안 =====

    @Test
    void 결재자_참조자_열람자_추가도_재직_중인_사원만() throws Exception {
        long drafter = newEmployee();
        long approver = newEmployee();
        long documentId = createDocument(drafter, line(approver, 1, "APPROVER"));
        long ghost = newEmployee();
        long terminated = newEmployee();
        long onLeave = newEmployee();
        userService.markMissing(ghost);
        userService.setStatus(terminated, "TERMINATED");
        userService.setStatus(onLeave, "ON_LEAVE");

        expectError(addApprover(documentId, drafter, line(ghost, 2, "APPROVER")), 400, "APPROVAL_015");
        expectError(addApprover(documentId, drafter, line(onLeave, 2, "AGREEMENT")), 400, "APPROVAL_016");
        expectError(addParticipant(documentId, "/referrers", drafter, terminated), 400, "APPROVAL_016");
        expectError(addParticipant(documentId, "/viewers", drafter, onLeave), 400, "APPROVAL_016");

        assertThat(lineEmpIds(documentId, drafter)).extracting(Number::longValue).containsExactly(approver);
        addParticipant(documentId, "/viewers", drafter, newEmployee()).andExpect(status().isCreated());
    }

    @Test
    void 재기안의_새_결재선도_재직_중인_사원만() throws Exception {
        long drafter = newEmployee();
        long approver = newEmployee();
        long documentId = createDocument(drafter, line(approver, 1, "APPROVER"));
        mvc.perform(post(path(documentId, "/reject")).with(user(approver))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"comment":"보완 필요"}"""))
                .andExpect(status().isOk());
        long terminated = newEmployee();
        userService.setStatus(terminated, "TERMINATED");

        expectError(mvc.perform(post(path(documentId, "/resubmit")).with(user(drafter))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"재기안","approvalLines":[%s]}""".formatted(line(terminated, 1, "APPROVER")))),
                400, "APPROVAL_016");

        assertThat(lineEmpIds(documentId, drafter)).extracting(Number::longValue).containsExactly(approver);
    }

    // ===== user-service 장애 =====

    @Test
    void user_service에_확인할_수_없으면_503_APPROVAL_017_저장하지_않음() throws Exception {
        long drafter = newEmployee();
        long approver = newEmployee();
        long documentId = createDocument(drafter, line(approver, 1, "APPROVER"));
        userService.setDown(true);

        expectError(submit(drafter, line(newEmployee(), 1, "APPROVER")), 503, "APPROVAL_017");
        expectError(addApprover(documentId, drafter, line(newEmployee(), 2, "APPROVER")), 503, "APPROVAL_017");
        expectError(addParticipant(documentId, "/viewers", drafter, newEmployee()), 503, "APPROVAL_017");

        assertThat(listedDocuments(drafter)).extracting(Number::longValue).containsExactly(documentId);
        assertThat(lineEmpIds(documentId, drafter)).extracting(Number::longValue).containsExactly(approver);

        // user-service 를 거치지 않는 결재 처리는 그대로 동작
        mvc.perform(post(path(documentId, "/approve")).with(user(approver))).andExpect(status().isOk());
    }
}
