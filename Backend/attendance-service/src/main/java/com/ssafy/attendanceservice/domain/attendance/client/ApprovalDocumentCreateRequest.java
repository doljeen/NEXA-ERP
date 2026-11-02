package com.ssafy.attendanceservice.domain.attendance.client;

import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 결재 문서 생성 요청 (approval-service POST /api/v1/documents).
 * DOCS/approval-document-source.md, requestKey 는 DOCS/approval-integration-decisions.md D2
 */
public record ApprovalDocumentCreateRequest(
        String title,                       // 제목
        String formType,                    // 문서 양식 (근태 신청 종류와 같은 이름)
        String sourceType,                  // 문서 출처
        Long sourceId,                      // 원본 근태 신청 ID
        UUID requestKey,                    // 생성 요청 키. 같은 키로 다시 요청하면 처음 만든 문서를 돌려준다
        List<ApprovalLine> approvalLines    // 결재선
) {

    private static final String SOURCE_ATTENDANCE = "ATTENDANCE";

    public record ApprovalLine(
            Long empId,         // 결재선 사원 ID
            int step,           // 결재 순서 (1부터)
            String role         // APPROVER / AGREEMENT / REFERENCE / VIEWER
    ) {
    }

    /** 근태 신청서의 결재 문서 */
    public static ApprovalDocumentCreateRequest ofAttendance(String title, AttendanceRequestType reqType,
                                                             Long attReqId, List<ApprovalLine> approvalLines) {
        return new ApprovalDocumentCreateRequest(title, reqType.name(), SOURCE_ATTENDANCE, attReqId,
                requestKeyOf(attReqId), approvalLines);
    }

    /** 근태 신청의 결재 요청 키. 신청 ID로 계산해 항상 같은 값이 나온다 (저장하지 않음) */
    public static UUID requestKeyOf(Long attReqId) {
        return UUID.nameUUIDFromBytes((SOURCE_ATTENDANCE + "-" + attReqId).getBytes(StandardCharsets.UTF_8));
    }
}
