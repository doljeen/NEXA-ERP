package com.ssafy.approval_service.domain.approval.policy;

import com.ssafy.approval_service.domain.document.model.dto.ApprovalLineRequest;

import java.util.List;

/**
 * 조직도로 만든 결재선.
 *
 * @param lines        결재자(APPROVER)만, step 1부터 아래 단계 → 위 단계 순
 * @param selfApproval 기안자가 최상위 부서장(대표이사)이라 결재자가 없음 → 상신 즉시 승인 (자가결재)
 */
public record GeneratedApprovalLine(List<ApprovalLineRequest> lines, boolean selfApproval) {
}
