package com.ssafy.payrollservice.domain.payroll.repository;

import com.ssafy.payrollservice.domain.payroll.entity.PayslipItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PayslipItemRepository extends JpaRepository<PayslipItem, Long> {

    // 명세서 항목 (급여 항목 함께 조회, 정렬 순서대로)
    @Query("""
            select psi from PayslipItem psi
            join fetch psi.payItem pi
            where psi.payslip.payslipId = :payslipId
            order by pi.sortOrder
            """)
    List<PayslipItem> findWithPayItemByPayslipId(@Param("payslipId") Long payslipId);
}