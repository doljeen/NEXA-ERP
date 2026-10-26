package com.ssafy.approval_service.domain.document.controller;

import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateRequest;
import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentInfoResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentListResponse;
import com.ssafy.approval_service.domain.document.service.DocumentService;
import com.ssafy.approval_service.global.dto.ApiResult;
import com.ssafy.approval_service.global.security.CurrentEmployeeId;
import com.ssafy.approval_service.global.security.SecurityConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityConstants.DOCUMENT_BASE_PATH)
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 문서 목록 조회
     * approvalStatus = 결재상태 (INPROGRESS/APPROVED/REJECTED/CANCEL)
     * formType = 문서양식
     */
    @GetMapping
    public ResponseEntity<ApiResult<DocumentListResponse>> getDocumentList(
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String formType
    ) {
        DocumentListResponse response = documentService.findByStatusAndType(approvalStatus, formType);
        return ResponseEntity.ok(ApiResult.success(response));
    }

    /** 문서 상세 (문서 + 결재선 + 이력) */
    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResult<DocumentInfoResponse>> getDocumentInfo(
            @PathVariable Long documentId
    ) {
        DocumentInfoResponse response = documentService.findById(documentId);
        return ResponseEntity.ok(ApiResult.success(response));
    }

    /** 문서 생성 + 상신. 기안자는 access 토큰의 sub(사원 ID)로 식별 */
    @PostMapping
    public ResponseEntity<ApiResult<DocumentCreateResponse>> createDocument(
            @CurrentEmployeeId Long drafterId,
            @Valid @RequestBody DocumentCreateRequest request
    ) {
        DocumentCreateResponse response = documentService.create(request, drafterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(response));
    }
}
