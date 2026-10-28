package com.ssafy.payrollservice.domain.payroll.repository;

import com.ssafy.payrollservice.domain.payroll.entity.PayslipItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipItemRepository extends JpaRepository<PayslipItem, Long> {
}