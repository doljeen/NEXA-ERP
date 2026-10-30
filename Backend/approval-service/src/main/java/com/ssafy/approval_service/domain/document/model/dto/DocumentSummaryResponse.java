package com.ssafy.approval_service.domain.document.model.dto;

import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.model.entity.enums.DocumentStatus;
import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;

import java.time.OffsetDateTime;

/** 문서 목록의 한 건 */
public record DocumentSummaryResponse(
        Long documentId,            // 문서ID
        String docNo,                 // 문서번호
        FormType formType,          // 문서양식
        String title,               // 제목
        DocumentStatus status,      // 결재상태
        Long drafterId,             // 기안자 사원ID
        OffsetDateTime draftedAt,   // 상신일시
        OffsetDateTime completedAt  // 완료일시
) {
    public static DocumentSummaryResponse from(Document document) {
        return new DocumentSummaryResponse(
                document.getDocId(),
                document.getDocNo(),
                document.getFormType(),
                document.getTitle(),
                document.getStatus(),
                document.getDrafterId(),
                document.getDraftedAt(),
                document.getCompletedAt()
        );
    }
}
