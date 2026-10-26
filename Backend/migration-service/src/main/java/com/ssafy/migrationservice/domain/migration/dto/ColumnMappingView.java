package com.ssafy.migrationservice.domain.migration.dto;

/** targetField가 null이면 연결 안 된 컬럼. confirmed=false면 시스템 추천 */
public record ColumnMappingView(String sourceColumn, String targetField, String targetLabel, boolean confirmed) {
}
