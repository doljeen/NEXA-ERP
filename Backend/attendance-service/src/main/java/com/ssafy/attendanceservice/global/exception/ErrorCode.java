package com.ssafy.attendanceservice.global.exception;

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

    // 근태 (단계 진행하면서 추가)
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "ATTN_001", "존재하지 않는 사원입니다."),
    ALREADY_CLOCKED_IN(HttpStatus.CONFLICT, "ATTN_002", "이미 출근 처리되었습니다."),
    DEFAULT_WORK_GROUP_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "ATTN_003", "기본 근무그룹이 설정되지 않았습니다."),
    WORK_STATUS_CODE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "ATTN_004", "근무 상태 코드가 설정되지 않았습니다."),
    NOT_CLOCKED_IN(HttpStatus.CONFLICT, "ATTN_005", "출근 기록이 없습니다."),
    ALREADY_CLOCKED_OUT(HttpStatus.CONFLICT, "ATTN_006", "이미 퇴근 처리되었습니다."),
    INVALID_STATUS_CHANGE(HttpStatus.CONFLICT, "ATTN_007", "변경할 수 없는 근무 상태입니다."),
    DUPLICATE_CLOCK_LOG(HttpStatus.CONFLICT, "ATTN_008", "해당 근무일에 이미 출근 또는 퇴근 기록이 있습니다."),

    // 공통 (user-service와 동일)
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "COMMON_002", "데이터 제약 조건에 위배됩니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_003", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}