package com.ssafy.approval_service.domain.approval.model.entity;

import com.ssafy.approval_service.domain.approval.model.entity.enums.LineAction;
import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;
import com.ssafy.approval_service.domain.document.model.entity.Document;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 결재선 (approval_line) */
@Entity
@Table(name = "approval_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_id")
    private Long lineId;                // 결재선ID

    @Column(name = "step")
    private Short step;                 // 순서

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private LineRole role;              // 역할

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private LineAction action;          // 동작수행

    @Column(name = "acted_at")
    private OffsetDateTime actedAt;     // 처리일시

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_id")
    private Document document;          // 결재 문서

    @Column(name = "emp_id")
    private Long empId;                 // 결재자 사원ID (user-service employee.emp_id)

    @Builder
    private ApprovalLine(Short step, LineRole role, Document document, Long empId) {
        this.step = step;
        this.role = role;
        this.document = document;
        this.empId = empId;
        this.action = LineAction.PENDING;
    }

    public void agree() {
        this.action = LineAction.AGREE;
        this.actedAt = OffsetDateTime.now();
    }

    public void disagree() {
        this.action = LineAction.DISAGREE;
        this.actedAt = OffsetDateTime.now();
    }
}
