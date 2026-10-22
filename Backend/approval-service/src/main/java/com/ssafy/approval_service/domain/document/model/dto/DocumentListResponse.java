package com.ssafy.approval_service.domain.document.model.dto;

import com.ssafy.approval_service.domain.document.model.entity.Document;

import java.util.List;

public record DocumentListResponse(
        List<DocumentSummaryResponse> documents,
        int totalCount
) {
    public static DocumentListResponse from(List<Document> documents) {
        List<DocumentSummaryResponse> items = documents.stream()
                .map(DocumentSummaryResponse::from)
                .toList();
        return new DocumentListResponse(items, items.size());
    }
}
