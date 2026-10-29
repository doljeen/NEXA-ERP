package com.ssafy.payrollservice.domain.payroll.repository;

import com.ssafy.payrollservice.domain.payroll.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    boolean existsByEmpIdAndPayYearAndPayMonth(Long empId, Short payYear, Short payMonth);

    List<Payslip> findByEmpIdAndPayYearOrderByPayMonthDesc(Long empId, Short payYear);
}