package com.ssafy.approval_service.domain.document.service;

import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateRequest;
import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentInfoResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentListResponse;
import com.ssafy.approval_service.domain.document.model.entity.Document;

public interface DocumentService {

    /** 결재상태/문서양식으로 목록 조회. 둘 다 null 허용 */
    DocumentListResponse findByStatusAndType(String approvalStatus, String formType);

    /** 문서 생성 + 상신. 결재선 저장과 SUBMIT 이력 기록까지 한 트랜잭션 */
    DocumentCreateResponse create(DocumentCreateRequest request, Long drafterId);

    /** 문서 상세 (문서 + 결재선 + 이력) */
    DocumentInfoResponse findById(Long documentId);

    Document getDocument(Long documentId);
}
