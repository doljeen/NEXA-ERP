package com.ssafy.approval_service.domain.document.model.dto;

import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 문서 생성 시 함께 전달되는 결재선 한 줄 */
public record ApprovalLineRequest(
        @NotNull Long empId,        // 결재자 사원ID
        @NotNull @Min(1) Short step, // 결재 순서 (1부터)
        @NotNull LineRole role      // APPROVER / AGREEMENT / REFERENCE
) {
}
