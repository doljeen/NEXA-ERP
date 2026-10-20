package com.ssafy.migrationservice.domain.migration.service;

import com.ssafy.migrationservice.domain.migration.dto.FileDetailResponse;
import com.ssafy.migrationservice.domain.migration.dto.FileUploadResponse;
import com.ssafy.migrationservice.domain.migration.dto.MigrationCreateRequest;
import com.ssafy.migrationservice.domain.migration.dto.MigrationCreateResponse;
import com.ssafy.migrationservice.domain.migration.dto.MigrationDetailResponse;
import com.ssafy.migrationservice.domain.migration.entity.MigrationFile;
import com.ssafy.migrationservice.domain.migration.entity.MigrationJob;
import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import com.ssafy.migrationservice.domain.migration.reader.ParsedTable;
import com.ssafy.migrationservice.domain.migration.reader.TableReader;
import com.ssafy.migrationservice.domain.migration.repository.MigrationFileRepository;
import com.ssafy.migrationservice.domain.migration.repository.MigrationJobRepository;
import com.ssafy.migrationservice.domain.migration.repository.StagingRowRepository;
import com.ssafy.migrationservice.global.exception.BusinessException;
import com.ssafy.migrationservice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MigrationService {

    private final MigrationJobRepository jobRepository;
    private final MigrationFileRepository fileRepository;
    private final StagingRowRepository stagingRowRepository;
    private final TableReader tableReader;

    @Transactional
    public MigrationCreateResponse create(MigrationCreateRequest r) {
        MigrationJob job = jobRepository.save(MigrationJob.builder()
                .companyId(r.companyId())
                .name(r.name())
                .cutoverDate(r.cutoverDate())
                .build());
        return new MigrationCreateResponse(job.getMigrationId(), job.getStatus());
    }

    @Transactional(readOnly = true)
    public MigrationDetailResponse get(Long migrationId) {
        MigrationJob job = findJob(migrationId);
        return MigrationDetailResponse.of(job, fileRepository.findByMigrationIdOrderByFileIdAsc(migrationId));
    }

    /** 파일을 읽어 행을 임시 테이블에 저장한다. 원본 바이트는 저장하지 않는다. */
    @Transactional
    public FileUploadResponse upload(Long migrationId, MigrationEntity entity, String fileName, byte[] content) {
        MigrationJob job = findJob(migrationId);
        if (job.isCompleted()) {
            throw new BusinessException(ErrorCode.MIGRATION_COMPLETED);
        }
        ParsedTable table = tableReader.read(fileName, content);

        MigrationFile file = fileRepository.save(MigrationFile.builder()
                .migrationId(migrationId)
                .entity(entity)
                .fileName(fileName)
                .headers(table.headers().toArray(String[]::new))
                .rowCount(table.rows().size())
                .build());
        stagingRowRepository.saveAll(file.getFileId(), table.rows());

        return new FileUploadResponse(file.getFileId(), entity, table.rows().size(), table.headers());
    }

    @Transactional(readOnly = true)
    public FileDetailResponse getFile(Long migrationId, Long fileId) {
        MigrationFile file = fileRepository.findByFileIdAndMigrationId(fileId, migrationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MIGRATION_FILE_NOT_FOUND));
        List<ParsedTable.Row> rows = stagingRowRepository.findByFileId(fileId);
        return new FileDetailResponse(file.getFileId(), file.getFileName(), file.getEntity(), file.getRowCount(),
                file.getUploadedAt(), ColumnProfiler.profile(List.of(file.getHeaders()), rows));
    }

    private MigrationJob findJob(Long migrationId) {
        return jobRepository.findById(migrationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MIGRATION_NOT_FOUND));
    }
}
