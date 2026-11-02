package com.ssafy.approval_service.domain.document.model.dto;

import com.ssafy.approval_service.domain.approval.model.dto.ApprovalHistoryResponse;
import com.ssafy.approval_service.domain.approval.model.dto.ApprovalLineResponse;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalHistory;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.model.entity.enums.DocumentStatus;
import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import com.ssafy.approval_service.domain.document.model.entity.enums.SourceType;

import java.time.OffsetDateTime;
import java.util.List;

/** 문서 상세: 문서 + 결재선 + 변경 이력 */
public record DocumentInfoResponse(
        Long documentId,                            // 문서ID
        String docNo,                                 // 문서번호
        FormType formType,                          // 문서양식
        String title,                               // 제목
        DocumentStatus status,                      // 결재상태
        Long drafterId,                             // 기안자 사원ID
        OffsetDateTime draftedAt,                   // 상신일시
        OffsetDateTime completedAt,                 // 완료일시
        SourceType sourceType,                      // 출처 서비스
        Long sourceId,                              // 출처 서비스의 원본 신청 ID
        List<ApprovalLineResponse> approvalLines,   // 결재선 (step 순)
        List<ApprovalHistoryResponse> histories     // 변경 이력 (발생 순)
) {
    public static DocumentInfoResponse of(Document document,
                                          List<ApprovalLine> lines,
                                          List<ApprovalHistory> histories) {
        return new DocumentInfoResponse(
                document.getDocId(),
                document.getDocNo(),
                document.getFormType(),
                document.getTitle(),
                document.getStatus(),
                document.getDrafterId(),
                document.getDraftedAt(),
                document.getCompletedAt(),
                document.getSourceType(),
                document.getSourceId(),
                lines.stream().map(ApprovalLineResponse::from).toList(),
                histories.stream().map(ApprovalHistoryResponse::from).toList()
        );
    }
}
