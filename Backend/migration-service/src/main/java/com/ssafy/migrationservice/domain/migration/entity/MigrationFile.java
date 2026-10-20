package com.ssafy.migrationservice.domain.migration.entity;

import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 업로드 파일 메타데이터. 원본 파일은 읽은 뒤 저장하지 않고, 행 데이터만 staging_row에 남긴다. */
@Entity
@Table(name = "migration_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MigrationFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "migration_id", nullable = false)
    private Long migrationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity", nullable = false, length = 40)
    private MigrationEntity entity;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "headers", nullable = false, columnDefinition = "text[]")
    private String[] headers;               // 원본 헤더 (staging_row.data의 키 순서)

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Builder
    private MigrationFile(Long migrationId, MigrationEntity entity, String fileName, String[] headers, int rowCount) {
        this.migrationId = migrationId;
        this.entity = entity;
        this.fileName = fileName;
        this.headers = headers;
        this.rowCount = rowCount;
        this.uploadedAt = LocalDateTime.now();
    }
}
