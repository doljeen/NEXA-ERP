package com.ssafy.approval_service.domain.document.service;

import com.ssafy.approval_service.domain.approval.model.entity.ApprovalHistory;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.approval.model.entity.enums.HistoryAction;
import com.ssafy.approval_service.domain.approval.repository.ApprovalHistoryRepository;
import com.ssafy.approval_service.domain.approval.repository.ApprovalLineRepository;
import com.ssafy.approval_service.domain.approval.service.ApprovalLineValidator;
import com.ssafy.approval_service.domain.document.model.dto.DocumentBox;
import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateRequest;
import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentInfoResponse;
import com.ssafy.approval_service.domain.document.model.dto.DocumentListResponse;
import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.model.entity.enums.DocumentStatus;
import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import com.ssafy.approval_service.domain.document.model.entity.enums.SourceType;
import com.ssafy.approval_service.domain.document.repository.DocumentRepository;
import com.ssafy.approval_service.global.exception.BusinessException;
import com.ssafy.approval_service.global.exception.ErrorCode;
import com.ssafy.nexa.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImplV1 implements DocumentService {

    private static final int FIRST_ROUND = 1;

    private final DocumentRepository documentRepository;
    private final ApprovalLineRepository approvalLineRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final ApprovalLineValidator approvalLineValidator;
    private final DocumentNumberGenerator documentNumberGenerator;
    private final RequestKeyLock requestKeyLock;

    @Override
    public DocumentListResponse findByStatusAndType(String approvalStatus, String formType, String box,
                                                    AuthUser requester) {
        DocumentStatus status = parseEnum(DocumentStatus.class, approvalStatus, "approvalStatus");
        FormType type = parseEnum(FormType.class, formType, "formType");
        Long drafterId = parseEnum(DocumentBox.class, box, "box") == DocumentBox.DRAFTED
                ? requester.employeeId()
                : null;

        List<Document> documents = documentRepository.findReadableByStatusAndFormType(
                status, type, drafterId, requester.employeeId(), requester.isAdmin());
        return DocumentListResponse.from(documents);
    }

    /*
     * 결재 이력 생성
     */
    @Override
    @Transactional
    public DocumentCreateResult create(DocumentCreateRequest request, Long drafterId) {
        // 출처 검증: 원본 신청 ID·요청 키 유무, 양식과 출처의 조합
        validateSource(request.formType(), request.sourceType(), request.sourceId(), request.requestKey());

        // 같은 요청 키로 이미 만든 문서가 있으면 새로 만들지 않고 돌려준다 (재시도 대비)
        if (request.requestKey() != null) {
            requestKeyLock.lock(request.requestKey());
            Optional<Document> existing = documentRepository.findByRequestKey(request.requestKey());
            if (existing.isPresent()) {
                return new DocumentCreateResult(toCreateResponse(existing.get(), request, drafterId), false);
            }
        }

        // 결재선 검증
        approvalLineValidator.validate(request.approvalLines(), drafterId);

        // 문서 생성
        Document draft = Document.builder()
                .formType(request.formType())
                .title(request.title())
                .drafterId(drafterId)
                .sourceType(request.sourceType())
                .sourceId(request.sourceId())
                .requestKey(request.requestKey())
                .build();
        // 양식·연도별 일련번호를 받아 문서번호를 채운 뒤 저장한다
        draft.assignDocNo(documentNumberGenerator.next(draft.getFormType(), draft.numberingYear()));
        Document document = documentRepository.save(draft);

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

        return new DocumentCreateResult(new DocumentCreateResponse(document.getDocId(), document.getDocNo()), true);
    }

    /** 이미 있는 문서를 재요청에 돌려준다. 같은 키인데 다른 신청이면 409 */
    private DocumentCreateResponse toCreateResponse(Document existing, DocumentCreateRequest request, Long drafterId) {
        if (!existing.isSameRequest(drafterId, request.formType(), request.sourceType(), request.sourceId())) {
            throw new BusinessException(ErrorCode.REQUEST_KEY_CONFLICT);
        }
        return new DocumentCreateResponse(existing.getDocId(), existing.getDocNo());
    }

    /**
     * 요청 키로 문서 조회. 원본 서비스가 생성 결과를 모를 때(타임아웃 등)와 결과 이벤트를 놓쳤을 때 쓴다.
     * 없거나 조회 권한이 없으면 404 (404 = 이 키로 만든 문서가 없음)
     */
    @Override
    public DocumentInfoResponse findByRequestKey(UUID requestKey, AuthUser requester) {
        Document document = documentRepository.findByRequestKey(requestKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        return findById(document.getDocId(), requester);
    }

    @Override
    public DocumentInfoResponse findById(Long documentId, AuthUser requester) {
        Document document = getReadableDocument(documentId, requester);

        List<ApprovalLine> lines = approvalLineRepository.findByDocumentDocIdOrderByStepAsc(documentId);
        List<ApprovalHistory> histories = approvalHistoryRepository.findByDocumentDocIdOrderByOccurredAtAsc(documentId);

        return DocumentInfoResponse.of(document, lines, histories);
    }

    /** 잠금 없는 내부 조회 (조회 권한 검사용). 다른 도메인은 getReadableDocument / getDocumentForUpdate 를 쓴다. 없으면 404 */
    private Document getDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /**
     * 결재 처리와 결재선 변경용. 잠금은 호출한 쪽 트랜잭션이 끝날 때 풀리므로,
     * 같은 문서의 다음 요청은 기다렸다가 바뀐 상태로 다시 검증된다. 없으면 404
     */
    @Override
    @Transactional
    public Document getDocumentForUpdate(Long documentId) {
        return documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /**
     * 조회 가능: 관리자, 기안자, 현재 결재선 참여자(결재자/합의자/참조자/열람자).
     * 권한이 없으면 문서 존재 여부도 드러나지 않게 없는 문서와 같은 404로 응답한다
     */
    @Override
    public Document getReadableDocument(Long documentId, AuthUser requester) {
        Document document = getDocument(documentId);
        if (requester.isAdmin() || document.isDraftedBy(requester.employeeId())) {
            return document;
        }
        if (!approvalLineRepository.existsByDocumentDocIdAndEmpId(documentId, requester.employeeId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    /**
     * 다른 서비스에서 온 문서는 원본 신청 ID가 있어야 하고, 직접 기안은 없어야 한다.
     * 양식마다 만들 수 있는 출처가 정해져 있다 (FormType.requiredSource)
     */
    private void validateSource(FormType formType, SourceType sourceType, Long sourceId, UUID requestKey) {
        boolean direct = sourceType == SourceType.DIRECT;
        if (direct != (sourceId == null)) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_SOURCE);
        }
        if (formType.requiredSource() != sourceType) {
            throw new BusinessException(ErrorCode.FORM_SOURCE_MISMATCH);
        }
        // 다른 서비스는 생성 요청을 재시도하므로 중복 생성을 막을 요청 키가 있어야 한다 (직접 기안은 선택)
        if (!direct && requestKey == null) {
            throw new BusinessException(ErrorCode.REQUEST_KEY_REQUIRED);
        }
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
