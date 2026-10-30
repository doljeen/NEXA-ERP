package com.ssafy.approval_service.domain.document.service;

import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateRequest;
import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentInfoResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentListResponse;
import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.nexa.security.AuthUser;

public interface DocumentService {

    /** 결재상태/문서양식/문서함으로 목록 조회. 모두 null 허용. 요청자가 조회할 수 있는 문서만 */
    DocumentListResponse findByStatusAndType(String approvalStatus, String formType, String box, AuthUser requester);

    /** 문서 생성 + 상신. 결재선 저장과 SUBMIT 이력 기록까지 한 트랜잭션 */
    DocumentCreateResponse create(DocumentCreateRequest request, Long drafterId);

    /** 문서 상세 (문서 + 결재선 + 이력) */
    DocumentInfoResponse findById(Long documentId, AuthUser requester);

    /** 변경용 엔티티 조회. 문서 행을 잠가 동시 변경을 막는다. 없으면 404 */
    Document getDocumentForUpdate(Long documentId);

    /** 조회 권한까지 확인한 문서. 없거나 권한이 없으면 404 */
    Document getReadableDocument(Long documentId, AuthUser requester);
}
