package com.ssafy.migrationservice.domain.migration.dto;

import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;

import java.util.List;

public record FileUploadResponse(Long fileId, MigrationEntity entity, int rows, List<String> headers) {
}
