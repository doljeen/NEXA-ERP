package com.ssafy.approval_service.domain.approval.service;

import com.ssafy.approval_service.domain.document.model.dto.ApprovalLineRequest;
import com.ssafy.approval_service.global.exception.BusinessException;
import com.ssafy.approval_service.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 결재선 일괄 지정(상신/재기안) 검증. 문서 생성과 재기안이 같은 규칙을 쓴다 */
@Component
public class ApprovalLineValidator {

    /** 기안자 본인 제외, 사원 중복 금지, step 중복 금지 */
    public void validate(List<ApprovalLineRequest> lines, Long drafterId) {
        Set<Long> empIds = new HashSet<>();
        Set<Short> steps = new HashSet<>();

        for (ApprovalLineRequest line : lines) {
            if (line.empId().equals(drafterId)) {
                throw new BusinessException(ErrorCode.DRAFTER_IN_APPROVAL_LINE);
            }
            if (!empIds.add(line.empId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_APPROVER, "중복된 결재자: " + line.empId());
            }
            if (!steps.add(line.step())) {
                throw new BusinessException(ErrorCode.DUPLICATE_APPROVAL_STEP, "중복된 결재 순서: " + line.step());
            }
        }
    }
}
