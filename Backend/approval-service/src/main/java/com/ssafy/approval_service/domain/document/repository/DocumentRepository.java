package com.ssafy.approval_service.domain.document.repository;

import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.model.entity.enums.DocumentStatus;
import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** 결재상태/문서양식 필터 조회. null인 조건은 무시 */
    @Query("""
            SELECT d FROM Document d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:formType IS NULL OR d.formType = :formType)
            ORDER BY d.draftedAt DESC
            """)
    List<Document> findByStatusAndFormType(@Param("status") DocumentStatus status,
                                           @Param("formType") FormType formType);

    /** 기안자 기준 조회 (기안함) */
    List<Document> findByDrafterIdOrderByDraftedAtDesc(Long drafterId);
}
