package com.ssafy.payrollservice.domain.payroll.service;

import com.ssafy.payrollservice.domain.payroll.dto.PayslipListResponse;
import com.ssafy.payrollservice.domain.payroll.repository.PayslipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayslipServiceImpl implements PayslipService {

    private final PayslipRepository payslipRepository;

    @Override
    @Transactional(readOnly = true)
    public PayslipListResponse getMyPayslips(Long empId, int year) {
        return PayslipListResponse.of(year,
                payslipRepository.findByEmpIdAndPayYearOrderByPayMonthDesc(empId, (short) year));
    }
}