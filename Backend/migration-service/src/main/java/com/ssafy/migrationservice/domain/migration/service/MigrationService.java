package com.ssafy.migrationservice.domain.migration.service;

import com.ssafy.migrationservice.domain.migration.dto.FileDetailResponse;
import com.ssafy.migrationservice.domain.migration.dto.BatchView;
import com.ssafy.migrationservice.domain.migration.dto.FileUploadResponse;
import com.ssafy.migrationservice.domain.migration.load.BatchStatus;
import com.ssafy.migrationservice.domain.migration.load.MigrationBatchRepository;
import com.ssafy.migrationservice.domain.migration.dto.FixApplyRequest;
import com.ssafy.migrationservice.domain.migration.dto.FixApplyResponse;
import com.ssafy.migrationservice.domain.migration.dto.RowUpdateRequest;
import com.ssafy.migrationservice.domain.migration.dto.RowUpdateResponse;
import com.ssafy.migrationservice.domain.migration.repository.StagingChangeRepository;
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
import java.util.Map;

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
    private final StagingChangeRepository stagingChangeRepository;
    private final MigrationBatchRepository batchRepository;

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
        return MigrationDetailResponse.of(job, fileRepository.findByMigrationIdOrderByFileIdAsc(migrationId),
                batchRepository.findByMigrationIdOrderByStartedAtAsc(migrationId).stream().map(BatchView::from).toList());
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
        MigrationFile file = findEditableFile(migrationId, fileId);
        mappingService.update(file, request);
        validationService.validate(file);
        return new MappingUpdateResponse(mappingService.missingRequired(file), issueRepository.totals(migrationId, fileId));
    }

    /** FIX 이슈의 제안값을 일괄 반영하고, 바뀐 파일을 다시 검증한다 */
    @Transactional
    public FixApplyResponse applyFixes(Long migrationId, FixApplyRequest r) {
        findEditableJob(migrationId);
        String code = r.code() == null ? null : r.code().name();
        List<Long> fileIds = stagingChangeRepository.filesWithFixes(migrationId, r.fileId(), code, r.field());
        fileIds.forEach(id -> findEditableFile(migrationId, id));
        int applied = stagingChangeRepository.applyFixes(migrationId, r.fileId(), code, r.field());
        for (Long fileId : fileIds) {
            validationService.validate(findFile(migrationId, fileId));
        }
        return new FixApplyResponse(applied, issueRepository.totals(migrationId, null));
    }

    /** 화면에서 행 하나를 고친다. 그 파일을 다시 검증하고 그 행에 남은 오류를 돌려준다 */
    @Transactional
    public RowUpdateResponse updateRow(Long migrationId, Long fileId, int rowNo, RowUpdateRequest r) {
        findEditableJob(migrationId);
        MigrationFile file = findEditableFile(migrationId, fileId);
        List<String> headers = List.of(file.getHeaders());
        for (String column : r.values().keySet()) {
            if (!headers.contains(column)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "파일에 없는 컬럼입니다: " + column);
            }
        }
        for (Map.Entry<String, String> e : r.values().entrySet()) {
            String value = e.getValue() == null || e.getValue().isBlank() ? null : e.getValue().strip();
            if (!stagingChangeRepository.updateCell(fileId, rowNo, e.getKey(), value)) {
                throw new BusinessException(ErrorCode.STAGING_ROW_NOT_FOUND);
            }
        }
        validationService.validate(file);
        return new RowUpdateResponse(rowNo, issueRepository.findByRow(fileId, rowNo),
                issueRepository.totals(migrationId, fileId));
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

    /** 적재된 파일은 되돌린 뒤에만 고칠 수 있다 (임시 테이블과 대상 서비스 데이터가 어긋나지 않게) */
    private MigrationFile findEditableFile(Long migrationId, Long fileId) {
        MigrationFile file = findFile(migrationId, fileId);
        if (batchRepository.existsByFileIdAndStatus(fileId, BatchStatus.LOADED)) {
            throw new BusinessException(ErrorCode.FILE_ALREADY_LOADED);
        }
        return file;
    }

    private MigrationFile findFile(Long migrationId, Long fileId) {
        return fileRepository.findByFileIdAndMigrationId(fileId, migrationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MIGRATION_FILE_NOT_FOUND));
    }
}
