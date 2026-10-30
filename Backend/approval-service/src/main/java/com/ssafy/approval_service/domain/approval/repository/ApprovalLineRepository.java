package com.ssafy.approval_service.domain.approval.repository;

import com.ssafy.approval_service.domain.approval.model.entity.ApprovalLine;
import com.ssafy.approval_service.domain.approval.model.entity.enums.LineAction;
import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {

    /** 문서의 결재선을 순서대로 조회 */
    List<ApprovalLine> findByDocumentDocIdOrderByStepAsc(Long docId);

    /** 문서의 특정 역할 결재선 조회 (결재자: APPROVER/AGREEMENT, 참조자: REFERENCE, 열람자: VIEWER) */
    List<ApprovalLine> findByDocumentDocIdAndRoleInOrderByStepAscLineIdAsc(Long docId, Collection<LineRole> roles);

    /** 문서에서 특정 사원의 결재선 조회 */
    Optional<ApprovalLine> findByDocumentDocIdAndEmpId(Long docId, Long empId);

    /** 문서에서 특정 상태(예: PENDING)인 결재선을 순서대로 조회 */
    List<ApprovalLine> findByDocumentDocIdAndActionOrderByStepAsc(Long docId, LineAction action);

    /** 사원이 포함된 결재선 조회 (받은 결재함) */
    List<ApprovalLine> findByEmpIdAndAction(Long empId, LineAction action);

    boolean existsByDocumentDocIdAndEmpId(Long docId, Long empId);

    void deleteByDocumentDocId(Long docId);
}
