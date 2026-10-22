package com.ssafy.approval_service.domain.approval.model.dto;

import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.approval.model.entity.enums.LineAction;
import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;

import java.time.OffsetDateTime;

public record ApprovalLineResponse(
        Long lineId,                // 결재선ID
        Short step,                 // 순서
        LineRole role,              // 역할
        LineAction action,          // 동작수행
        Long empId,                 // 결재자 사원ID
        OffsetDateTime actedAt      // 처리일시
) {
    public static ApprovalLineResponse from(ApprovalLine line) {
        return new ApprovalLineResponse(
                line.getLineId(),
                line.getStep(),
                line.getRole(),
                line.getAction(),
                line.getEmpId(),
                line.getActedAt()
        );
    }
}
