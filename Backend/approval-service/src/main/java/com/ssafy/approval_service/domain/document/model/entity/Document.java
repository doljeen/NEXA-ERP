package com.ssafy.approval_service.domain.document.model.entity;

import com.ssafy.approval_service.domain.document.model.entity.enums.DocumentStatus;
import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import com.ssafy.approval_service.domain.document.model.entity.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 결재 문서 (approval_document) */
@Entity
@Table(name = "approval_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doc_id")
    private Long docId;                     // 문서ID

    @Column(name = "doc_no")
    private String docNo;                     // 문서번호

    @Enumerated(EnumType.STRING)
    @Column(name = "form_type")
    private FormType formType;              // 문서양식

    @Column(name = "title", length = 255)
    private String title;                   // 제목

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DocumentStatus status;          // 결재상태

    @Column(name = "drafted_at")
    private OffsetDateTime draftedAt;       // 상신일시

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;     // 완료일시

    @Column(name = "drafter_id")
    private Long drafterId;                 // 기안자 사원ID (user-service employee.emp_id)

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;          // 출처 서비스

    @Column(name = "source_id")
    private Long sourceId;                  // 출처 서비스의 원본 신청 ID (DIRECT 면 null)

    @Builder
    private Document(String docNo, FormType formType, String title, Long drafterId,
                     SourceType sourceType, Long sourceId) {
        this.docNo = docNo;
        this.formType = formType;
        this.title = title;
        this.drafterId = drafterId;
        this.sourceType = sourceType == null ? SourceType.DIRECT : sourceType;
        this.sourceId = sourceId;
        this.status = DocumentStatus.INPROGRESS;
        this.draftedAt = OffsetDateTime.now();
    }

    public void approve() {
        this.status = DocumentStatus.APPROVED;
        this.completedAt = OffsetDateTime.now();
    }

    public void reject() {
        this.status = DocumentStatus.REJECTED;
        this.completedAt = OffsetDateTime.now();
    }

    public void cancel() {
        this.status = DocumentStatus.CANCEL;
        this.completedAt = OffsetDateTime.now();
    }

    /** 반려된 문서를 다시 상신. 제목을 바꾸고 진행 중으로 되돌린다 */
    public void resubmit(String title) {
        this.title = title;
        this.status = DocumentStatus.INPROGRESS;
        this.draftedAt = OffsetDateTime.now();
        this.completedAt = null;
    }

    /** 반려된 문서인지 (재기안 가능 여부) */
    public boolean isRejected() {
        return this.status == DocumentStatus.REJECTED;
    }

    /** 해당 사원이 기안자인지 */
    public boolean isDraftedBy(Long employeeId) {
        return this.drafterId.equals(employeeId);
    }

    /** 결재 진행 중인지 (결재선 변경/결재 처리 가능 여부) */
    public boolean isInProgress() {
        return this.status == DocumentStatus.INPROGRESS;
    }
}
