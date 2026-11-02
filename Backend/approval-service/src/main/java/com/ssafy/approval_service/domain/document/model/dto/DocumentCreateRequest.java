package com.ssafy.approval_service.domain.document.model.dto;

import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import com.ssafy.approval_service.domain.document.model.entity.enums.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 문서 생성 + 상신 요청. 생성 즉시 INPROGRESS 상태로 결재가 시작된다.
 * sourceType 을 생략하면 DIRECT(직접 기안). DIRECT 가 아니면 sourceId(원본 신청 ID)가 필요하다.
 */
public record DocumentCreateRequest(
        @NotBlank String docNo,
        @NotBlank @Size(max = 255) String title,            // 제목
        @NotNull FormType formType,                         // 문서양식
        @NotEmpty @Valid List<ApprovalLineRequest> approvalLines, // 결재선 (최소 1명)
        SourceType sourceType,                              // 출처 서비스 (생략 시 DIRECT)
        Long sourceId                                       // 출처 서비스의 원본 신청 ID
) {
    public DocumentCreateRequest {
        if (sourceType == null) {
            sourceType = SourceType.DIRECT;
        }
    }
}
