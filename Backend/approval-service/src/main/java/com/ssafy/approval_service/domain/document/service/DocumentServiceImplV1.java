package com.ssafy.approval_service.domain.document.service;

import com.ssafy.approval_service.domain.approval.model.entity.ApprovalHistory;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.approval.model.entity.enums.HistoryAction;
import com.ssafy.approval_service.domain.approval.repository.ApprovalHistoryRepository;
import com.ssafy.approval_service.domain.approval.repository.ApprovalLineRepository;
import com.ssafy.approval_service.domain.approval.service.ApprovalLineValidator;
import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateRequest;
import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentInfoResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentListResponse;
import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.model.entity.enums.DocumentStatus;
import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import com.ssafy.approval_service.domain.document.repository.DocumentRepository;
import com.ssafy.approval_service.global.exception.BusinessException;
import com.ssafy.approval_service.global.exception.ErrorCode;
import com.ssafy.approval_service.global.security.Requester;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImplV1 implements DocumentService {

    private static final int FIRST_ROUND = 1;

    private final DocumentRepository documentRepository;
    private final ApprovalLineRepository approvalLineRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final ApprovalLineValidator approvalLineValidator;

    @Override
    public DocumentListResponse findByStatusAndType(String approvalStatus, String formType, Requester requester) {
        DocumentStatus status = parseEnum(DocumentStatus.class, approvalStatus, "approvalStatus");
        FormType type = parseEnum(FormType.class, formType, "formType");

        List<Document> documents = documentRepository.findReadableByStatusAndFormType(
                status, type, requester.employeeId(), requester.admin());
        return DocumentListResponse.from(documents);
    }

    /*
     * 결재 이력 생성
     */
    @Override
    @Transactional
    public DocumentCreateResponse create(DocumentCreateRequest request, Long drafterId) {
        // 결재선 검증
        approvalLineValidator.validate(request.approvalLines(), drafterId);

        // 문서 생성
        Document document = documentRepository.save(Document.builder()
                .docNo(request.docNo())
                .formType(request.formType())
                .title(request.title())
                .drafterId(drafterId)
                .build());

        // req -> 결재선
        List<ApprovalLine> lines = request.approvalLines().stream()
                .map(line -> ApprovalLine.builder()
                        .document(document)
                        .empId(line.empId())
                        .step(line.step())
                        .role(line.role())
                        .build())
                .toList();

        // 결재선 우선 저장
        approvalLineRepository.saveAll(lines);

        // 결재 이력 저장
        approvalHistoryRepository.save(ApprovalHistory.builder()
                .document(document)
                .round(FIRST_ROUND)
                .actorId(drafterId)
                .action(HistoryAction.SUBMIT)
                .build());

        return new DocumentCreateResponse(document.getDocId());
    }

    @Override
    public DocumentInfoResponse findById(Long documentId, Requester requester) {
        Document document = getReadableDocument(documentId, requester);

        List<ApprovalLine> lines = approvalLineRepository.findByDocumentDocIdOrderByStepAsc(documentId);
        List<ApprovalHistory> histories = approvalHistoryRepository.findByDocumentDocIdOrderByOccurredAtAsc(documentId);

        return DocumentInfoResponse.of(document, lines, histories);
    }

    /** 다른 도메인 서비스용 엔티티 조회. 없으면 404 */
    @Override
    public Document getDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /**
     * 조회 가능: 관리자, 기안자, 현재 결재선 참여자(결재자/합의자/참조자/열람자).
     * 권한이 없으면 문서 존재 여부도 드러나지 않게 없는 문서와 같은 404로 응답한다
     */
    @Override
    public Document getReadableDocument(Long documentId, Requester requester) {
        Document document = getDocument(documentId);
        if (requester.admin() || document.isDraftedBy(requester.employeeId())) {
            return document;
        }
        if (!approvalLineRepository.existsByDocumentDocIdAndEmpId(documentId, requester.employeeId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    /** 쿼리 파라미터 문자열을 enum으로 변환. null/빈 문자열은 null, 잘못된 값은 400 */
    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, paramName + " 값이 올바르지 않습니다: " + value);
        }
    }
}
