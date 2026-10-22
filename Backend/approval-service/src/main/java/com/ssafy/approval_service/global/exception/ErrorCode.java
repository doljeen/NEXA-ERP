package com.ssafy.approval_service.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증/인가
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다."),
    TOKEN_BLACKLISTED(HttpStatus.UNAUTHORIZED, "AUTH_004", "로그아웃된 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_005", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_TERMINATED(HttpStatus.UNAUTHORIZED, "AUTH_006", "퇴직 처리된 계정입니다."),
    REFRESH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "AUTH_007", "refreshToken이 없습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_008", "유효하지 않은 refreshToken입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_009", "접근 권한이 없습니다."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "AUTH_010", "로그인 실패가 반복되어 계정이 잠시 잠겼습니다. 잠시 후 다시 시도하세요."),

    // 결재
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "APPROVAL_001", "문서를 찾을 수 없습니다."),
    DRAFTER_IN_APPROVAL_LINE(HttpStatus.BAD_REQUEST, "APPROVAL_002", "기안자는 결재선에 포함될 수 없습니다."),
    DUPLICATE_APPROVER(HttpStatus.BAD_REQUEST, "APPROVAL_003", "결재선에 같은 사원이 중복되었습니다."),
    DUPLICATE_APPROVAL_STEP(HttpStatus.BAD_REQUEST, "APPROVAL_004", "결재 순서가 중복되었습니다."),

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "COMMON_002", "데이터 제약 조건에 위배됩니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_003", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
