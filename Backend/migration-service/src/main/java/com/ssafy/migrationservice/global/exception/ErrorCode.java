package com.ssafy.migrationservice.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 이관 작업
    MIGRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "MIG_001", "존재하지 않는 이관 작업입니다."),
    MIGRATION_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "MIG_002", "존재하지 않는 업로드 파일입니다."),
    MIGRATION_COMPLETED(HttpStatus.CONFLICT, "MIG_003", "전환 완료된 이관 작업은 변경할 수 없습니다."),

    // 파일
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "FILE_001", "빈 파일입니다."),
    FILE_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "FILE_002", "xlsx, csv 파일만 업로드할 수 있습니다."),
    FILE_READ_FAILED(HttpStatus.BAD_REQUEST, "FILE_003", "파일을 읽을 수 없습니다."),
    FILE_NO_HEADER(HttpStatus.BAD_REQUEST, "FILE_004", "헤더 행을 찾을 수 없습니다."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "FILE_005", "파일 크기가 너무 큽니다."),

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "COMMON_002", "데이터 제약 조건에 위배됩니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_003", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
