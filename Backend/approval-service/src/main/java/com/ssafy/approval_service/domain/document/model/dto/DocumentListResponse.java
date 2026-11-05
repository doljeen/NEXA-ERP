package com.ssafy.approval_service.domain.document.model.dto;

import com.ssafy.approval_service.domain.approval.model.ApprovalProgress;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.document.model.entity.Document;

import java.util.List;
import java.util.Map;

public record DocumentListResponse(
        List<DocumentSummaryResponse> documents,
        int totalCount
) {
    /**
     * @param linesByDocId 문서ID별 결재선 (목록 문서 전체를 한 번에 조회해 묶은 것). 없는 문서는 결재선이 없는 것으로 본다
     * @param requesterId  myTurn 계산 기준
     */
    public static DocumentListResponse of(List<Document> documents, Map<Long, List<ApprovalLine>> linesByDocId,
                                          Long requesterId) {
        List<DocumentSummaryResponse> items = documents.stream()
                .map(document -> DocumentSummaryResponse.of(
                        document,
                        ApprovalProgress.of(document.isInProgress(),
                                linesByDocId.getOrDefault(document.getDocId(), List.of())),
                        requesterId))
                .toList();
        return new DocumentListResponse(items, items.size());
    }
}
