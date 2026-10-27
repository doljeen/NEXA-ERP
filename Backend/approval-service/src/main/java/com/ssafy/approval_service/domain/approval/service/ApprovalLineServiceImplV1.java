package com.ssafy.approval_service.domain.approval.service;

import com.ssafy.approval_service.domain.approval.model.dto.ApprovalLineResponse;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalHistory;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.approval.model.entity.enums.HistoryAction;
import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;
import com.ssafy.approval_service.domain.approval.repository.ApprovalHistoryRepository;
import com.ssafy.approval_service.domain.approval.repository.ApprovalLineRepository;
import com.ssafy.approval_service.domain.document.model.dto.ApprovalLineRequest;
import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.service.DocumentService;
import com.ssafy.approval_service.global.exception.BusinessException;
import com.ssafy.approval_service.global.exception.ErrorCode;
import com.ssafy.approval_service.global.security.Requester;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 문서 참여자(결재자/참조자/열람자) 관리.
 * 변경은 기안자 본인 + 진행 중 문서에서만 가능하며, 이미 결재한 결재자는 제거하거나 순서를 바꿀 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalLineServiceImplV1 implements ApprovalLineService {

    private static final List<LineRole> APPROVER_ROLES = List.of(LineRole.APPROVER, LineRole.AGREEMENT);
    private static final int FIRST_ROUND = 1;

    private final ApprovalLineRepository approvalLineRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final DocumentService documentService;

    // ===== 결재자 =====

    @Override
    public List<ApprovalLineResponse> getApprovers(Long documentId, Requester requester) {
        return findLines(documentId, APPROVER_ROLES, requester);
    }

    @Override
    @Transactional
    public void addApprover(Long employeeId, Long documentId, ApprovalLineRequest request) {
        Document document = getEditableDocument(employeeId, documentId);
        if (!APPROVER_ROLES.contains(request.role())) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER_ROLE);
        }
        validateNewParticipant(document, request.empId());

        List<ApprovalLine> approvers = approvers(documentId);
        if (approvers.stream().anyMatch(line -> line.getStep().equals(request.step()))) {
            throw new BusinessException(ErrorCode.DUPLICATE_APPROVAL_STEP);
        }
        // 이미 결재가 끝난 순서 앞에는 끼워 넣을 수 없다
        short lastActedStep = approvers.stream()
                .filter(line -> !line.isPending())
                .map(ApprovalLine::getStep)
                .max(Short::compare)
                .orElse((short) 0);
        if (request.step() <= lastActedStep) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER_ORDER, "이미 결재가 끝난 순서 앞에는 추가할 수 없습니다.");
        }

        approvalLineRepository.save(ApprovalLine.builder()
                .document(document)
                .empId(request.empId())
                .step(request.step())
                .role(request.role())
                .build());
        recordHistory(document, employeeId, HistoryAction.APPROVER_ADD, request.empId());
    }

    @Override
    @Transactional
    public void removeApprover(Long employeeId, Long documentId, Long approverId) {
        Document document = getEditableDocument(employeeId, documentId);
        ApprovalLine line = getLine(documentId, approverId, APPROVER_ROLES);
        if (!line.isPending()) {
            throw new BusinessException(ErrorCode.ALREADY_ACTED);
        }
        if (approvers(documentId).size() <= 1) {
            throw new BusinessException(ErrorCode.LAST_APPROVER);
        }

        approvalLineRepository.delete(line);
        recordHistory(document, employeeId, HistoryAction.APPROVER_REMOVE, approverId);
    }

    @Override
    @Transactional
    public void reorderApprovers(Long employeeId, Long documentId, List<Long> approverIds) {
        getEditableDocument(employeeId, documentId);
        List<ApprovalLine> approvers = approvers(documentId);

        // 보낸 목록 = 현재 결재자 전체 (누락/추가/중복 없이)
        Map<Long, ApprovalLine> byEmpId = approvers.stream()
                .collect(Collectors.toMap(ApprovalLine::getEmpId, Function.identity()));
        if (approverIds.size() != byEmpId.size()
                || new HashSet<>(approverIds).size() != approverIds.size()
                || !byEmpId.keySet().containsAll(approverIds)) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER_ORDER, "현재 결재자 전체를 중복 없이 보내야 합니다.");
        }

        // 앞에서부터 step 1, 2, ... 다시 매김. 이미 결재한 사람은 자리가 바뀌면 안 된다
        for (int i = 0; i < approverIds.size(); i++) {
            ApprovalLine line = byEmpId.get(approverIds.get(i));
            short newStep = (short) (i + 1);
            if (!line.isPending() && line.getStep() != newStep) {
                throw new BusinessException(ErrorCode.ALREADY_ACTED);
            }
            line.changeStep(newStep);   // 변경 감지로 UPDATE
        }
    }

    // ===== 참조자 =====

    @Override
    public List<ApprovalLineResponse> getReferrers(Long documentId, Requester requester) {
        return findLines(documentId, List.of(LineRole.REFERENCE), requester);
    }

    @Override
    @Transactional
    public void addReferrer(Long employeeId, Long documentId, Long referrerId) {
        addParticipant(employeeId, documentId, referrerId, LineRole.REFERENCE);
    }

    @Override
    @Transactional
    public void removeReferrer(Long employeeId, Long documentId, Long referrerId) {
        removeParticipant(employeeId, documentId, referrerId, LineRole.REFERENCE);
    }

    // ===== 열람자 =====

    @Override
    public List<ApprovalLineResponse> getViewers(Long documentId, Requester requester) {
        return findLines(documentId, List.of(LineRole.VIEWER), requester);
    }

    @Override
    @Transactional
    public void addViewer(Long employeeId, Long documentId, Long viewerId) {
        addParticipant(employeeId, documentId, viewerId, LineRole.VIEWER);
    }

    @Override
    @Transactional
    public void removeViewer(Long employeeId, Long documentId, Long viewerId) {
        removeParticipant(employeeId, documentId, viewerId, LineRole.VIEWER);
    }

    // ===== 공통 =====

    /** 순서가 없는 참여자(참조자/열람자) 추가. step은 비워 둔다 */
    private void addParticipant(Long employeeId, Long documentId, Long empId, LineRole role) {
        Document document = getEditableDocument(employeeId, documentId);
        validateNewParticipant(document, empId);
        approvalLineRepository.save(ApprovalLine.builder()
                .document(document)
                .empId(empId)
                .role(role)
                .build());
    }

    private void removeParticipant(Long employeeId, Long documentId, Long empId, LineRole role) {
        getEditableDocument(employeeId, documentId);
        approvalLineRepository.delete(getLine(documentId, empId, List.of(role)));
    }

    /** 변경 가능한 문서: 기안자 본인 + 진행 중 */
    private Document getEditableDocument(Long employeeId, Long documentId) {
        Document document = documentService.getDocument(documentId);
        if (!document.isDraftedBy(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_DRAFTER);
        }
        if (!document.isInProgress()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_IN_PROGRESS);
        }
        return document;
    }

    /** 기안자 본인 제외, 문서 내 사원 중복 금지 (결재자/참조자/열람자 통틀어 한 번만) */
    private void validateNewParticipant(Document document, Long empId) {
        if (document.isDraftedBy(empId)) {
            throw new BusinessException(ErrorCode.DRAFTER_IN_APPROVAL_LINE);
        }
        if (approvalLineRepository.existsByDocumentDocIdAndEmpId(document.getDocId(), empId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_APPROVER, "이미 결재선에 있는 사원: " + empId);
        }
    }

    /** 문서에서 해당 역할의 사원 결재선. 역할이 다르면 없는 것으로 본다 (참조자 API로 결재자 삭제 방지) */
    private ApprovalLine getLine(Long documentId, Long empId, Collection<LineRole> roles) {
        return approvalLineRepository.findByDocumentDocIdAndEmpId(documentId, empId)
                .filter(line -> roles.contains(line.getRole()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND));
    }

    private List<ApprovalLine> approvers(Long documentId) {
        return approvalLineRepository.findByDocumentDocIdAndRoleInOrderByStepAscLineIdAsc(documentId, APPROVER_ROLES);
    }

    /** 현재 차수로 이력 기록. 차수는 가장 최근 이력의 round를 따른다 */
    private void recordHistory(Document document, Long actorId, HistoryAction action, Long targetEmpId) {
        int round = approvalHistoryRepository.findTopByDocumentDocIdOrderByOccurredAtDesc(document.getDocId())
                .map(ApprovalHistory::getRound)
                .orElse(FIRST_ROUND);
        approvalHistoryRepository.save(ApprovalHistory.builder()
                .document(document)
                .round(round)
                .actorId(actorId)
                .action(action)
                .targetEmpId(targetEmpId)
                .build());
    }

    /** 문서가 없거나 조회 권한이 없으면 404, 있으면 해당 역할의 결재선을 순서대로 조회 */
    private List<ApprovalLineResponse> findLines(Long documentId, Collection<LineRole> roles, Requester requester) {
        documentService.getReadableDocument(documentId, requester);
        return approvalLineRepository.findByDocumentDocIdAndRoleInOrderByStepAscLineIdAsc(documentId, roles).stream()
                .map(ApprovalLineResponse::from)
                .toList();
    }
}
