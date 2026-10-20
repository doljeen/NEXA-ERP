package com.ssafy.migrationservice.domain.migration.dto;

import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import com.ssafy.migrationservice.domain.migration.validation.issue.Severity;

import java.util.List;
import java.util.Map;

/** 업로드하면 매핑 추천과 검증까지 자동으로 돌고, 그 결과 건수를 함께 돌려준다 */
public record FileUploadResponse(Long fileId, MigrationEntity entity, int rows, List<String> headers,
                                 List<String> missingRequired, Map<Severity, Long> issueTotals) {
}
