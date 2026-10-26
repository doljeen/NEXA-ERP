package com.ssafy.approval_service.domain.approval.service;

import com.ssafy.approval_service.domain.approval.model.dto.ApprovalHistoryResponse;
import com.ssafy.approval_service.domain.approval.model.dto.ResubmitRequest;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalHistory;
import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.approval.model.entity.enums.HistoryAction;
import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;
import com.ssafy.approval_service.domain.approval.repository.ApprovalHistoryRepository;
import com.ssafy.approval_service.domain.approval.repository.ApprovalLineRepository;
import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.service.DocumentService;
import com.ssafy.approval_service.global.exception.BusinessException;
import com.ssafy.approval_service.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결재 처리. 순차 결재: PENDING 결재자(APPROVER/AGREEMENT) 중 step이 가장 작은 사람이 현재 차례다.
 * 합의자의 비동의는 반려로 처리하고, 전결은 차례인 APPROVER만 할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalServiceImplV1 implements ApprovalService {

    private static final List<LineRole> APPROVER_ROLES = List.of(LineRole.APPROVER, LineRole.AGREEMENT);
    private static final int FIRST_ROUND = 1;

    private final ApprovalLineRepository approvalLineRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final DocumentService documentService;
    private final ApprovalLineValidator approvalLineValidator;

    @Override
    public List<ApprovalHistoryResponse> getHistories(Long documentId) {
        return approvalHistoryRepository.findByDocumentDocIdOrderByOccurredAtAsc(documentId).stream()
                .map(ApprovalHistoryResponse::from)
                .toList();
    }

    // ===== 결재자 =====

    @Override
    @Transactional
    public void approve(Long employeeId, Long documentId) {
        Document document = getInProgressDocument(documentId);
        List<ApprovalLine> approvers = approvers(documentId);
        ApprovalLine myLine = getMyTurn(approvers, employeeId);

        myLine.agree();
        // 남은 결재자가 없으면 최종 승인
        if (approvers.stream().noneMatch(ApprovalLine::isPending)) {
            document.approve();
        }
        recordHistory(document, currentRound(documentId), employeeId, HistoryAction.APPROVE, null);
    }

    @Override
    @Transactional
    public void reject(Long employeeId, Long documentId, String comment) {
        Document document = getInProgressDocument(documentId);
        ApprovalLine myLine = getMyTurn(approvers(documentId), employeeId);

        myLine.disagree();
        document.reject();
        recordHistory(document, currentRound(documentId), employeeId, HistoryAction.REJECT, comment);
    }

    @Override
    @Transactional
    public void finalApprove(Long employeeId, Long documentId) {
        Document document = getInProgressDocument(documentId);
        List<ApprovalLine> approvers = approvers(documentId);
        ApprovalLine myLine = getMyTurn(approvers, employeeId);
        if (myLine.getRole() != LineRole.APPROVER) {
            throw new BusinessException(ErrorCode.FINAL_APPROVE_NOT_ALLOWED);
        }

        myLine.agree();
        // 뒤에 남은 결재자는 지우지 않고 생략 처리 (누가 건너뛰어졌는지 남김)
        approvers.stream()
                .filter(ApprovalLine::isPending)
                .forEach(ApprovalLine::skip);
        document.approve();
        recordHistory(document, currentRound(documentId), employeeId, HistoryAction.FINAL_APPROVE, null);
    }

    // ===== 기안자 =====

    @Override
    @Transactional
    public void cancel(Long employeeId, Long documentId) {
        Document document = getInProgressDocument(documentId);
        validateDrafter(document, employeeId);
        // 결재자가 한 명이라도 처리했으면 회수 불가
        if (approvers(documentId).stream().anyMatch(line -> !line.isPending())) {
            throw new BusinessException(ErrorCode.ALREADY_ACTED, "결재가 진행된 문서는 회수할 수 없습니다.");
        }

        document.cancel();
        recordHistory(document, currentRound(documentId), employeeId, HistoryAction.CANCEL, null);
    }

    @Override
    @Transactional
    public void resubmit(Long employeeId, Long documentId, ResubmitRequest request) {
        Document document = documentService.getDocument(documentId);
        validateDrafter(document, employeeId);
        if (!document.isRejected()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_REJECTED);
        }
        approvalLineValidator.validate(request.approvalLines(), employeeId);

        // 이전 차수의 결재 기록은 이력에 남아 있으므로 결재선은 새로 지정
        approvalLineRepository.deleteByDocumentDocId(documentId);
        approvalLineRepository.saveAll(request.approvalLines().stream()
                .map(line -> ApprovalLine.builder()
                        .document(document)
                        .empId(line.empId())
                        .step(line.step())
                        .role(line.role())
                        .build())
                .toList());

        document.resubmit(request.title());
        recordHistory(document, currentRound(documentId) + 1, employeeId, HistoryAction.RESUBMIT, null);
    }

    // ===== 공통 =====

    private Document getInProgressDocument(Long documentId) {
        Document document = documentService.getDocument(documentId);
        if (!document.isInProgress()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_IN_PROGRESS);
        }
        return document;
    }

    private void validateDrafter(Document document, Long employeeId) {
        if (!document.isDraftedBy(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_DRAFTER);
        }
    }

    /** 현재 차례(PENDING 중 가장 앞 step)가 요청자인지 확인하고 그 결재선을 반환 */
    private ApprovalLine getMyTurn(List<ApprovalLine> approvers, Long employeeId) {
        ApprovalLine current = approvers.stream()
                .filter(ApprovalLine::isPending)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_IN_PROGRESS));
        if (!current.getEmpId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_YOUR_TURN);
        }
        return current;
    }

    /** 결재자/합의자를 step 순으로 조회 (참조자/열람자 제외) */
    private List<ApprovalLine> approvers(Long documentId) {
        return approvalLineRepository.findByDocumentDocIdAndRoleInOrderByStepAscLineIdAsc(documentId, APPROVER_ROLES);
    }

    /** 가장 최근 이력의 차수 */
    private int currentRound(Long documentId) {
        return approvalHistoryRepository.findTopByDocumentDocIdOrderByOccurredAtDesc(documentId)
                .map(ApprovalHistory::getRound)
                .orElse(FIRST_ROUND);
    }

    private void recordHistory(Document document, int round, Long actorId, HistoryAction action, String comment) {
        approvalHistoryRepository.save(ApprovalHistory.builder()
                .document(document)
                .round(round)
                .actorId(actorId)
                .action(action)
                .comment(comment)
                .build());
    }
}
