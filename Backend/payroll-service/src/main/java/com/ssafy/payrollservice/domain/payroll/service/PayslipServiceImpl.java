package com.ssafy.payrollservice.domain.payroll.service;

import com.ssafy.payrollservice.domain.payroll.dto.PayslipDetailResponse;
import com.ssafy.payrollservice.domain.payroll.dto.PayslipListResponse;
import com.ssafy.payrollservice.domain.payroll.entity.Payslip;
import com.ssafy.payrollservice.domain.payroll.repository.PayslipItemRepository;
import com.ssafy.payrollservice.domain.payroll.repository.PayslipRepository;
import com.ssafy.payrollservice.global.exception.BusinessException;
import com.ssafy.payrollservice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayslipServiceImpl implements PayslipService {

    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;   // 필드 추가

    @Override
    @Transactional(readOnly = true)
    public PayslipListResponse getMyPayslips(Long empId, int year) {
        return PayslipListResponse.of(year,
                payslipRepository.findByEmpIdAndPayYearOrderByPayMonthDesc(empId, (short) year));
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipDetailResponse getMyPayslip(Long empId, Long payslipId) {
        // 없는 명세서와 다른 사람 명세서를 같은 404로 응답 (존재 여부 노출 방지)
        Payslip payslip = payslipRepository.findById(payslipId)
                .filter(p -> p.getEmpId().equals(empId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYSLIP_NOT_FOUND));

        return PayslipDetailResponse.of(payslip, payslipItemRepository.findWithPayItemByPayslipId(payslipId));
    }
}