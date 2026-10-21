package com.ssafy.leaveservice.domain.leavetype.service;

import com.ssafy.leaveservice.domain.leavetype.dto.LeaveTypeResponse;
import com.ssafy.leaveservice.domain.leavetype.dto.LeaveTypeCreateRequest;
import com.ssafy.leaveservice.domain.leavetype.entity.BalanceMode;
import com.ssafy.leaveservice.domain.leavetype.entity.LeaveBalanceType;
import com.ssafy.leaveservice.domain.leavetype.entity.LeaveType;
import com.ssafy.leaveservice.domain.leavetype.repository.LeaveBalanceTypeRepository;
import com.ssafy.leaveservice.domain.leavetype.repository.LeaveTypeRepository;
import com.ssafy.leaveservice.global.auth.EmployeeAccess;
import com.ssafy.leaveservice.global.exception.BusinessException;
import com.ssafy.leaveservice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeAccess employeeAccess;
    private final LeaveBalanceTypeRepository leaveBalanceTypeRepository;

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getAllTypes() {
        // 직원 조회 및 관리자 권한 검증
        Long employeeId = employeeAccess.currentEmployeeId();
        if (!employeeAccess.isAdmin(employeeId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 전체 휴가 유형 조회
        List<LeaveType> leaveTypes = leaveTypeRepository.findAllWithBalanceType();

        // 응답 생성
        List<LeaveTypeResponse> response = leaveTypes.stream()
                .map(LeaveTypeResponse::from)
                .toList();
        return response;
    }

    @Transactional
    public LeaveTypeResponse createType(LeaveTypeCreateRequest request) {
        Long employeeId = employeeAccess.currentEmployeeId();
        if (!employeeAccess.isAdmin(employeeId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // ACCOUNT 유형은 차감할 잔액 유형이 반드시 필요하다.
        if (request.balanceMode() == BalanceMode.ACCOUNT && request.balanceTypeId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "ACCOUNT 유형은 잔액 유형 ID가 필수입니다.");
        }

        LeaveBalanceType balanceType = null;
        if (request.balanceTypeId() != null) {
            balanceType = leaveBalanceTypeRepository.findById(request.balanceTypeId()).orElseThrow(
                    () -> new BusinessException(ErrorCode.BALANCE_TYPE_NOT_FOUND)
            );
        }

        LeaveType leaveType = LeaveType.builder()
                .balanceType(balanceType)
                .name(request.name())
                .paid(request.isPaid())
                .balanceMode(request.balanceMode())
                .requestUnit(request.requestUnit())
                .maxMinutes(request.maxMinutes())
                .active(request.isActive())
                .build();
        LeaveType savedLeaveType = leaveTypeRepository.save(leaveType);

        LeaveTypeResponse response = LeaveTypeResponse.from(savedLeaveType);
        return response;
    }
}
