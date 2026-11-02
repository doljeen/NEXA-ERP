package com.ssafy.approval_service.domain.document.model.dto;

import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import com.ssafy.approval_service.domain.document.model.entity.enums.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 문서 생성 + 상신 요청. 생성 즉시 INPROGRESS 상태로 결재가 시작된다.
 * sourceType 을 생략하면 DIRECT(직접 기안). DIRECT 가 아니면 sourceId(원본 신청 ID)와 requestKey 가 필요하다.
 * requestKey 가 같은 요청이 다시 오면 문서를 새로 만들지 않고 처음 만든 문서를 돌려준다.
 * 문서번호(docNo)는 서버가 만든다. 요청에 docNo 가 있어도 무시한다.
 */
public record DocumentCreateRequest(
        @NotBlank @Size(max = 255) String title,            // 제목
        @NotNull FormType formType,                         // 문서양식
        @NotEmpty @Valid List<ApprovalLineRequest> approvalLines, // 결재선 (최소 1명)
        SourceType sourceType,                              // 출처 서비스 (생략 시 DIRECT)
        Long sourceId,                                      // 출처 서비스의 원본 신청 ID
        UUID requestKey                                     // 생성 요청 키 (출처가 있으면 필수)
) {
    public DocumentCreateRequest {
        if (sourceType == null) {
            sourceType = SourceType.DIRECT;
        }
    }
}
