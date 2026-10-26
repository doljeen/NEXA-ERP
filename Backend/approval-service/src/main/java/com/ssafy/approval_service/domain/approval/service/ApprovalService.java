package com.ssafy.approval_service.domain.approval.service;

import com.ssafy.approval_service.domain.approval.model.dto.ApprovalHistoryResponse;
import com.ssafy.approval_service.domain.approval.model.dto.ResubmitRequest;

import java.util.List;

public interface ApprovalService {
    List<ApprovalHistoryResponse> getHistories(Long documentId);

    /** 결재자: 승인 */
    void approve(Long employeeId, Long documentId);

    /** 결재자: 반려 */
    void reject(Long employeeId, Long documentId, String comment);

    /** 결재자: 전결 (남은 결재선을 건너뛰고 최종 승인) */
    void finalApprove(Long employeeId, Long documentId);

    /** 기안자: 결재 취소 */
    void cancel(Long employeeId, Long documentId);

    /** 기안자: 반려된 문서 재기안 */
    void resubmit(Long employeeId, Long documentId, ResubmitRequest request);
}
