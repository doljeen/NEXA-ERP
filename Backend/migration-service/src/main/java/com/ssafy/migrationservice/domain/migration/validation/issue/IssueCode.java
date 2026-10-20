package com.ssafy.migrationservice.domain.migration.validation.issue;

public enum IssueCode {
    // 파일 구조
    MISSING_REQUIRED_COLUMN,
    SCHEMA_NOT_READY,
    TOTAL_ROW_SKIPPED,
    ROW_AFTER_TOTAL,
    // 엑셀 수식
    FORMULA_ERROR,
    HARDCODED_VALUE,
    BLANK_IN_FORMULA_COLUMN,
    FORMULA_INCONSISTENT,
    FORMULA_DIFFERS_ACROSS_SHEETS,
    // 형식·필수값
    REQUIRED,
    INVALID_DATE,
    DATE_FORMAT,
    INVALID_NUMBER,
    NUMBER_FORMAT,
    INVALID_EMAIL,
    INVALID_PHONE,
    INVALID_ENUM,
    TOO_LONG,
    // 파일 안 정합성
    DUPLICATE_KEY,
    DUPLICATE_SUSPECTED,
    CODE_TYPO_SUSPECTED,
    // 업무 규칙
    DATE_ORDER,
    SELF_PARENT,
    PARENT_NOT_IN_FILE
}
