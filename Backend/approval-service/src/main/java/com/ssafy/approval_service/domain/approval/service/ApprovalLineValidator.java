package com.ssafy.approval_service.domain.approval.service;

import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;
import com.ssafy.approval_service.domain.document.model.dto.ApprovalLineRequest;
import com.ssafy.approval_service.global.exception.BusinessException;
import com.ssafy.approval_service.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 결재선 일괄 지정(상신/재기안) 검증. 문서 생성과 재기안이 같은 규칙을 쓴다 */
@Component
@RequiredArgsConstructor
public class ApprovalLineValidator {

    private final EmployeeVerifier employeeVerifier;

    /**
     * 결재자(APPROVER) 최소 1명, 기안자 본인 제외, 사원 중복 금지, step 중복 금지, 모든 사원이 재직 중.
     * 요청 안에서 끝나는 검사를 먼저 하고, user-service 확인은 마지막에 한 번만 호출한다
     */
    public void validate(List<ApprovalLineRequest> lines, Long drafterId) {
        // 결재자 없이 합의/참조/열람만 있으면 결재를 끝낼 사람이 없다
        if (lines.stream().noneMatch(line -> line.role() == LineRole.APPROVER)) {
            throw new BusinessException(ErrorCode.LAST_APPROVER);
        }

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
        employeeVerifier.verifyActive(empIds);
    }
}
