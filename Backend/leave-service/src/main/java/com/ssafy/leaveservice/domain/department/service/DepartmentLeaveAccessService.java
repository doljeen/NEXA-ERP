package com.ssafy.leaveservice.domain.department.service;

import com.ssafy.leaveservice.client.hr.HrClient;
import com.ssafy.leaveservice.client.hr.HrClient.*;
import com.ssafy.leaveservice.client.hr.HrIntegrationProperties;
import com.ssafy.leaveservice.global.auth.EmployeeAccess;
import com.ssafy.leaveservice.global.exception.BusinessException;
import com.ssafy.leaveservice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DepartmentLeaveAccessService {
    private final EmployeeAccess employeeAccess;
    private final HrIntegrationProperties properties;
    private final HrClient hrClient;

    @Transactional(propagation = Propagation.NEVER)
    public List<Long> getAuthorizedEmployeeIds(Long departmentId) {
        // 검증된 JWT와 기존 EmployeeAccess의 본인 식별 정보를 대조
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)
                || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Jwt jwt = authentication.getToken();
        Long subjectId = positiveClaim(jwt.getSubject());
        Long employeeId = employeeAccess.currentEmployeeId();
        if (!subjectId.equals(employeeId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (departmentId == null || departmentId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 미확정 조직 Claim·URI·Client는 Leave 조회 전에 차단
        if (!properties.enabled() || !properties.hasConfiguredEndpoints() || !properties.hasConfiguredClaims()
                || !callHr(hrClient::isAvailable)) {
            throw new BusinessException(ErrorCode.HR_VERIFICATION_UNAVAILABLE);
        }
        Long tokenDepartmentId = positiveClaim(jwt.getClaim(properties.jwtClaims().departmentId()));
        Long tokenDutyId = positiveClaim(jwt.getClaim(properties.jwtClaims().dutyId()));
        Long tokenPositionId = positiveClaim(jwt.getClaim(properties.jwtClaims().positionId()));

        // 최신 직원의 소속·직책과 오래된 토큰이 다르면 재인증을 요구
        EmployeeInfo employee = callHr(() -> hrClient.findEmployee(employeeId));
        if (employee == null || !employeeId.equals(employee.employeeId())
                || !positive(employee.departmentId()) || !positive(employee.dutyId())
                || (employee.positionId() != null && !positive(employee.positionId()))) {
            throw new BusinessException(ErrorCode.HR_VERIFICATION_UNAVAILABLE);
        }
        if (!tokenDepartmentId.equals(employee.departmentId()) || !tokenDutyId.equals(employee.dutyId())
                || (employee.positionId() != null && !tokenPositionId.equals(employee.positionId()))) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "인사 정보가 변경되었습니다. 다시 인증해주세요.");
        }

        // 직급·시스템 ADMIN과 무관하게 확인된 직책명만 사용
        DutyInfo duty = callHr(() -> hrClient.findDuty(employee.dutyId()));
        if (duty == null || !employee.dutyId().equals(duty.dutyId()) || duty.name() == null || duty.name().isBlank()) {
            throw new BusinessException(ErrorCode.HR_VERIFICATION_UNAVAILABLE);
        }
        if (!"휴가 관리자".equals(duty.name()) || !departmentId.equals(employee.departmentId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 부서 ID 대신 인증된 직원 ID로 직접 소속 부서원 전체를 조회
        MemberBatch batch = callHr(() -> hrClient.findDepartmentMembers(employeeId));
        if (batch == null || !batch.complete() || batch.members() == null) {
            throw new BusinessException(ErrorCode.HR_VERIFICATION_UNAVAILABLE);
        }
        Set<Long> employeeIds = new HashSet<>();
        for (Member member : batch.members()) {
            if (member == null || !positive(member.employeeId()) || !departmentId.equals(member.departmentId())
                    || !employeeIds.add(member.employeeId())) {
                throw new BusinessException(ErrorCode.HR_VERIFICATION_UNAVAILABLE);
            }
        }
        return employeeIds.stream().sorted().toList();
    }

    private Long positiveClaim(Object claim) {
        try {
            long value;
            if (claim instanceof String text && text.matches("[1-9][0-9]*")) {
                value = Long.parseLong(text);
            } else if (claim instanceof Byte || claim instanceof Short || claim instanceof Integer || claim instanceof Long) {
                value = ((Number) claim).longValue();
            } else if (claim instanceof BigInteger integer) {
                value = integer.longValueExact();
            } else {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            if (value <= 0) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return value;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private <T> T callHr(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            // 원격 인증·Timeout·5xx와 응답 오류를 권한 없음이나 빈 목록으로 바꾸지 않는다.
            throw new BusinessException(ErrorCode.HR_VERIFICATION_UNAVAILABLE);
        }
    }
}
