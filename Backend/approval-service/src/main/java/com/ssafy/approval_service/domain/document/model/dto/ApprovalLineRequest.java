package com.ssafy.approval_service.domain.document.model.dto;

import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;
import com.ssafy.approval_service.domain.document.model.entity.Document;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 결재선 한 줄 (문서 생성·재기안 요청, 자동 결정 결과) */
public record ApprovalLineRequest(
        @NotNull Long empId,        // 결재자 사원ID
        @NotNull @Min(1) Short step, // 결재 순서 (1부터)
        @NotNull LineRole role      // APPROVER / AGREEMENT / REFERENCE
) {
    /** 문서에 붙일 결재선 엔티티 (PENDING 상태) */
    public ApprovalLine toEntity(Document document) {
        return ApprovalLine.builder()
                .document(document)
                .empId(empId)
                .step(step)
                .role(role)
                .build();
    }
}
