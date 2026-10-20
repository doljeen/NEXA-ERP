package com.ssafy.migrationservice.domain.migration.dto;

import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;

import java.time.LocalDateTime;
import java.util.List;

public record FileDetailResponse(
        Long fileId,
        String fileName,
        MigrationEntity entity,
        int rows,
        LocalDateTime uploadedAt,
        List<ColumnProfile> columns
) {
}
