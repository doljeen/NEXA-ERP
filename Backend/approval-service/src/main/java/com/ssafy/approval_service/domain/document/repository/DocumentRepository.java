package com.ssafy.approval_service.domain.document.repository;

import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.model.entity.enums.DocumentStatus;
import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * 결재상태/문서양식 필터 조회. null인 조건은 무시.
     * 관리자가 아니면 본인이 기안했거나 결재선(결재자/합의자/참조자/열람자)에 있는 문서만 조회한다
     */
    @Query("""
            SELECT d FROM Document d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:formType IS NULL OR d.formType = :formType)
              AND (:admin = true
                   OR d.drafterId = :employeeId
                   OR EXISTS (SELECT 1 FROM ApprovalLine l WHERE l.document = d AND l.empId = :employeeId))
            ORDER BY d.draftedAt DESC
            """)
    List<Document> findReadableByStatusAndFormType(@Param("status") DocumentStatus status,
                                                   @Param("formType") FormType formType,
                                                   @Param("employeeId") Long employeeId,
                                                   @Param("admin") boolean admin);

    /** 변경용 조회. 행 잠금(SELECT ... FOR UPDATE)으로 같은 문서의 변경 요청을 한 번에 하나씩 처리한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.docId = :docId")
    Optional<Document> findByIdForUpdate(@Param("docId") Long docId);

    /** 기안자 기준 조회 (기안함) */
    List<Document> findByDrafterIdOrderByDraftedAtDesc(Long drafterId);
}
