package com.ssafy.migrationservice.domain.migration.service;

import com.ssafy.migrationservice.domain.migration.dto.FileDetailResponse;
import com.ssafy.migrationservice.domain.migration.dto.FileUploadResponse;
import com.ssafy.migrationservice.domain.migration.dto.IssueListResponse;
import com.ssafy.migrationservice.domain.migration.dto.MappingUpdateRequest;
import com.ssafy.migrationservice.domain.migration.dto.MappingUpdateResponse;
import com.ssafy.migrationservice.domain.migration.dto.MigrationCreateRequest;
import com.ssafy.migrationservice.domain.migration.dto.MigrationCreateResponse;
import com.ssafy.migrationservice.domain.migration.dto.MigrationDetailResponse;
import com.ssafy.migrationservice.domain.migration.entity.MigrationFile;
import com.ssafy.migrationservice.domain.migration.entity.MigrationJob;
import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import com.ssafy.migrationservice.domain.migration.mapping.MappingService;
import com.ssafy.migrationservice.domain.migration.reader.ParsedTable;
import com.ssafy.migrationservice.domain.migration.reader.TableReader;
import com.ssafy.migrationservice.domain.migration.repository.MigrationFileRepository;
import com.ssafy.migrationservice.domain.migration.repository.MigrationJobRepository;
import com.ssafy.migrationservice.domain.migration.repository.StagingRowRepository;
import com.ssafy.migrationservice.domain.migration.validation.ValidationService;
import com.ssafy.migrationservice.domain.migration.validation.issue.IssueRepository;
import com.ssafy.migrationservice.domain.migration.validation.issue.Severity;
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
    private final MappingService mappingService;
    private final ValidationService validationService;
    private final IssueRepository issueRepository;

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

    /** 읽기 → 임시 테이블 저장 → 매핑 추천 → 검증까지 한 번에. 원본 바이트는 저장하지 않는다. */
    @Transactional
    public FileUploadResponse upload(Long migrationId, MigrationEntity entity, String fileName, byte[] content) {
        MigrationJob job = findEditableJob(migrationId);
        ParsedTable table = tableReader.read(fileName, content);

        MigrationFile file = fileRepository.save(MigrationFile.builder()
                .migrationId(job.getMigrationId())
                .entity(entity)
                .fileName(fileName)
                .headers(table.headers().toArray(String[]::new))
                .rowCount(table.rows().size())
                .build());
        stagingRowRepository.saveAll(file.getFileId(), table.rows());
        mappingService.suggest(file);
        validationService.validate(file);

        return new FileUploadResponse(file.getFileId(), entity, table.rows().size(), table.headers(),
                mappingService.missingRequired(file), issueRepository.totals(migrationId, file.getFileId()));
    }

    @Transactional(readOnly = true)
    public FileDetailResponse getFile(Long migrationId, Long fileId) {
        MigrationFile file = findFile(migrationId, fileId);
        List<ParsedTable.Row> rows = stagingRowRepository.findByFileId(fileId);
        return new FileDetailResponse(file.getFileId(), file.getFileName(), file.getEntity(), file.getRowCount(),
                file.getUploadedAt(), ColumnProfiler.profile(List.of(file.getHeaders()), rows),
                mappingService.view(file), mappingService.missingRequired(file),
                issueRepository.totals(migrationId, fileId));
    }

    /** 매핑을 확정하면 그 파일을 다시 검증한다 */
    @Transactional
    public MappingUpdateResponse updateMappings(Long migrationId, Long fileId, MappingUpdateRequest request) {
        findEditableJob(migrationId);
        MigrationFile file = findFile(migrationId, fileId);
        mappingService.update(file, request);
        validationService.validate(file);
        return new MappingUpdateResponse(mappingService.missingRequired(file), issueRepository.totals(migrationId, fileId));
    }

    @Transactional(readOnly = true)
    public IssueListResponse issues(Long migrationId, Long fileId, Severity severity, int page, int size) {
        findJob(migrationId);
        return new IssueListResponse(
                issueRepository.totals(migrationId, fileId),
                issueRepository.groups(migrationId, fileId, severity),
                issueRepository.page(migrationId, fileId, severity, page, size),
                page, size,
                issueRepository.count(migrationId, fileId, severity));
    }

    private MigrationJob findJob(Long migrationId) {
        return jobRepository.findById(migrationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MIGRATION_NOT_FOUND));
    }

    private MigrationJob findEditableJob(Long migrationId) {
        MigrationJob job = findJob(migrationId);
        if (job.isCompleted()) {
            throw new BusinessException(ErrorCode.MIGRATION_COMPLETED);
        }
        return job;
    }

    private MigrationFile findFile(Long migrationId, Long fileId) {
        return fileRepository.findByFileIdAndMigrationId(fileId, migrationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MIGRATION_FILE_NOT_FOUND));
    }
}
