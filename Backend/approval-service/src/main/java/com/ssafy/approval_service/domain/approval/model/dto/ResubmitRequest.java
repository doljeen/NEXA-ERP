package com.ssafy.approval_service.domain.approval.model.dto;

import com.ssafy.approval_service.domain.document.model.dto.ApprovalLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 반려된 문서 재기안 요청. 결재선은 새로 지정하고 차수가 1 올라간다 */
public record ResubmitRequest(
        @NotBlank @Size(max = 255) String title,                 // 제목
        @NotEmpty @Valid List<ApprovalLineRequest> approvalLines // 결재선 (최소 1명)
) {
}
