package com.ssafy.payrollservice.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증/인가 (user-service와 코드 동일하게 유지)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다."),
    TOKEN_BLACKLISTED(HttpStatus.UNAUTHORIZED, "AUTH_004", "로그아웃된 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_009", "접근 권한이 없습니다."),

    // 급여 (단계 진행하면서 추가)
    ATTENDANCE_CALL_FAILED(HttpStatus.BAD_GATEWAY, "PAY_001", "근태 서비스 호출에 실패했습니다."),
    PAY_GRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY_002", "직급별 기본급이 등록되지 않았습니다."),
    PAYSLIP_ALREADY_EXISTS(HttpStatus.CONFLICT, "PAY_003", "해당 월의 급여명세서가 이미 있습니다."),
    PAY_ITEM_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "PAY_004", "급여 항목이 설정되지 않았습니다."),

    // 공통 (user-service와 동일)
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "COMMON_002", "데이터 제약 조건에 위배됩니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_003", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
