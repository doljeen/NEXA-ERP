package com.ssafy.leaveservice.domain.request.entity;

import com.ssafy.leaveservice.domain.leavetype.entity.LeaveType;
import com.ssafy.leaveservice.global.exception.BusinessException;
import com.ssafy.leaveservice.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "leave_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_req_id")
    private Long requestId;

    @Column(name = "emp_id", nullable = false)
    private Long employeeId;

    @Column(name = "policy_version_id", nullable = false)
    private Long policyVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "approval_doc_id")
    private Long approvalDocId;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "total_deduct_minutes", nullable = false)
    private int totalDeductMinutes;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeaveRequestStatus status;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 2000)
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "submission_key", length = 128)
    private String submissionKey;

    @Column(name = "submission_payload_hash", length = 64)
    private String submissionPayloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_creation_status", length = 20)
    private ApprovalCreationStatus approvalCreationStatus;

    @Column(name = "approval_request_id")
    private java.util.UUID approvalRequestId;

    @Column(name = "approval_status_changed_at")
    private OffsetDateTime approvalStatusChangedAt;

    @Column(name = "approval_last_error", length = 500)
    private String approvalLastError;

    @Column(name = "approval_attempt_count")
    private Integer approvalAttemptCount;

    @OneToMany(mappedBy = "leaveRequest", fetch = FetchType.LAZY)
    @OrderBy("leaveDate ASC, startAt ASC, requestDateId ASC")
    private List<LeaveRequestDate> dates = new ArrayList<>();

    public static LeaveRequest submitting(
            Long employeeId,
            LeaveType leaveType,
            Long policyVersionId,
            int totalMinutes,
            int totalDeductMinutes,
            String reason,
            String submissionKey,
            String payloadHash,
            OffsetDateTime asOf
    ) {
        LeaveRequest request = new LeaveRequest();
        request.employeeId = employeeId;
        request.leaveType = leaveType;
        request.policyVersionId = policyVersionId;
        request.totalMinutes = totalMinutes;
        request.totalDeductMinutes = totalDeductMinutes;
        request.reason = reason;
        request.status = LeaveRequestStatus.SUBMITTING;
        request.submissionKey = submissionKey;
        request.submissionPayloadHash = payloadHash;
        request.approvalCreationStatus = ApprovalCreationStatus.NOT_SENT;
        request.approvalRequestId = java.util.UUID.randomUUID();
        request.approvalStatusChangedAt = asOf;
        request.approvalAttemptCount = 0;
        request.createdAt = asOf;
        request.updatedAt = asOf;
        return request;
    }

    public void startApprovalCall(OffsetDateTime now) {
        requirePendingApproval();
        if (approvalCreationStatus != ApprovalCreationStatus.NOT_SENT) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        int nextAttempt;
        try {
            nextAttempt = Math.addExact(approvalAttemptCount, 1);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        approvalAttemptCount = nextAttempt;
        approvalCreationStatus = ApprovalCreationStatus.IN_PROGRESS;
        approvalLastError = null;
        approvalStatusChangedAt = now;
        updatedAt = now;
    }

    public void linkCreatedApproval(Long documentId, OffsetDateTime now) {
        requirePendingApproval();
        approvalDocId = documentId;
        approvalCreationStatus = ApprovalCreationStatus.LINKED;
        status = LeaveRequestStatus.SUBMITTED;
        submittedAt = now;
        approvalLastError = null;
        approvalStatusChangedAt = now;
        updatedAt = now;
    }

    public void recordUnknownApproval(String reason, OffsetDateTime now) {
        requirePendingApproval();
        approvalCreationStatus = ApprovalCreationStatus.UNKNOWN;
        approvalLastError = reason;
        approvalStatusChangedAt = now;
        updatedAt = now;
    }

    public void cancelAfterConfirmedApprovalFailure(String reason, Long actorId, OffsetDateTime now) {
        requirePendingApproval();
        status = LeaveRequestStatus.CANCELLED;
        approvalCreationStatus = ApprovalCreationStatus.FAILED;
        approvalLastError = reason;
        approvalStatusChangedAt = now;
        cancelledAt = now;
        cancelledBy = actorId;
        cancelReason = reason;
        updatedAt = now;
    }

    private void requirePendingApproval() {
        boolean pending = approvalCreationStatus == ApprovalCreationStatus.NOT_SENT
                || approvalCreationStatus == ApprovalCreationStatus.IN_PROGRESS
                || approvalCreationStatus == ApprovalCreationStatus.UNKNOWN;
        if (status != LeaveRequestStatus.SUBMITTING || approvalDocId != null || !pending) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_VIOLATION);
        }
    }
}
