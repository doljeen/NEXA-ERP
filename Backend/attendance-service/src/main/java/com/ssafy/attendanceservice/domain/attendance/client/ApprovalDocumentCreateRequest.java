package com.ssafy.attendanceservice.domain.attendance.client;

import com.ssafy.attendanceservice.domain.attendance.entity.enums.AttendanceRequestType;

import java.util.List;

/** 결재 문서 생성 요청 (approval-service POST /api/v1/documents, DOCS/approval-document-source.md) */
public record ApprovalDocumentCreateRequest(
        String title,                       // 제목
        String formType,                    // 문서 양식 (근태 신청 종류와 같은 이름)
        String sourceType,                  // 문서 출처
        Long sourceId,                      // 원본 근태 신청 ID
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
        return new ApprovalDocumentCreateRequest(title, reqType.name(), SOURCE_ATTENDANCE, attReqId, approvalLines);
    }
}
