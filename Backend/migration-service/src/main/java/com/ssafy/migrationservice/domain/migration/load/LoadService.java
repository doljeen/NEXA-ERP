package com.ssafy.migrationservice.domain.migration.load;

import com.ssafy.migrationservice.domain.migration.dto.BatchView;
import com.ssafy.migrationservice.domain.migration.dto.LoadPreviewResponse;
import com.ssafy.migrationservice.domain.migration.dto.LoadResponse;
import com.ssafy.migrationservice.domain.migration.dto.ReconciliationItem;
import com.ssafy.migrationservice.domain.migration.entity.MigrationFile;
import com.ssafy.migrationservice.domain.migration.entity.MigrationJob;
import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import com.ssafy.migrationservice.domain.migration.mapping.MappingService;
import com.ssafy.migrationservice.domain.migration.repository.MigrationFileRepository;
import com.ssafy.migrationservice.domain.migration.repository.MigrationJobRepository;
import com.ssafy.migrationservice.domain.migration.repository.StagingRowRepository;
import com.ssafy.migrationservice.domain.migration.validation.issue.IssueRepository;
import com.ssafy.migrationservice.domain.migration.validation.issue.Severity;
import com.ssafy.migrationservice.domain.migration.validation.schema.EntitySchema;
import com.ssafy.migrationservice.domain.migration.validation.schema.EntitySchemas;
import com.ssafy.migrationservice.global.exception.BusinessException;
import com.ssafy.migrationservice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 적재·되돌리기·대사.
 * 대상 서비스 호출이 섞여 있어 메서드 전체를 하나의 DB 트랜잭션으로 묶지 않는다.
 * 묶음 상태는 단계마다 저장해서, 중간에 실패해도 어디까지 됐는지 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoadService {

    private static final int PREVIEW_SAMPLE = 2;

    private final MigrationJobRepository jobRepository;
    private final MigrationFileRepository fileRepository;
    private final MigrationBatchRepository batchRepository;
    private final StagingRowRepository stagingRowRepository;
    private final MappingService mappingService;
    private final IssueRepository issueRepository;
    private final List<TargetLoader> loaders;

    public LoadPreviewResponse preview(Long migrationId, List<Long> fileIds) {
        findEditableJob(migrationId);
        List<MigrationFile> files = selectFiles(migrationId, fileIds);
        long blocks = count(migrationId, files, Severity.BLOCK);
        long fixes = count(migrationId, files, Severity.FIX);
        List<LoadPreviewResponse.Item> items = files.stream().map(f -> {
            List<LoadRow> rows = transform(f);
            List<Map<String, Object>> sample = rows.stream().limit(PREVIEW_SAMPLE).map(LoadRow::data).toList();
            return new LoadPreviewResponse.Item(f.getFileId(), f.getEntity(), rows.size(), sample);
        }).toList();
        return new LoadPreviewResponse(blocks == 0 && fixes == 0, blocks, fixes, items);
    }

    /**
     * entity 순서(조직 → 직원 …)대로 파일마다 묶음을 만들어 넣는다.
     * 한 묶음이 실패하면 그 묶음을 대상 서비스에서 되돌리고, 뒤 순서는 진행하지 않는다 (뒤가 앞에 의존하므로).
     */
    public LoadResponse load(Long migrationId, List<Long> fileIds) {
        MigrationJob job = findEditableJob(migrationId);
        List<MigrationFile> files = selectFiles(migrationId, fileIds);
        long blocks = count(migrationId, files, Severity.BLOCK);
        if (blocks > 0) {
            throw new BusinessException(ErrorCode.BLOCK_ISSUES_EXIST, "차단 오류 " + blocks + "건을 먼저 해결해야 합니다");
        }
        // 임시 테이블 값 = 보낸 값이 되도록, 자동 정리는 적재 전에 반영돼 있어야 한다 (변경 이력에도 남음)
        long fixes = count(migrationId, files, Severity.FIX);
        if (fixes > 0) {
            throw new BusinessException(ErrorCode.FIX_PENDING, "자동 정리 " + fixes + "건을 먼저 적용하세요");
        }

        for (MigrationFile file : files) {
            TargetLoader loader = loaderFor(file);
            List<LoadRow> rows = transform(file);
            MigrationBatch batch = batchRepository.save(MigrationBatch.builder()
                    .batchId(newBatchId(file))
                    .migrationId(migrationId)
                    .fileId(file.getFileId())
                    .entity(file.getEntity())
                    .sourceRows(rows.size())
                    .build());
            try {
                LoadResult result = loader.upsert(file.getEntity(), batch.getBatchId(), rows);
                batch.loaded(result.created(), result.updated());
                batchRepository.save(batch);
                job.markLoaded();
                jobRepository.save(job);
            } catch (RuntimeException e) {
                log.warn("적재 실패 batch={}: {}", batch.getBatchId(), e.getMessage());
                undoQuietly(loader, batch);
                batch.failed(e.getMessage());
                batchRepository.save(batch);
                break;
            }
        }
        return new LoadResponse(job.getStatus(), batches(migrationId));
    }

    /** 나중 순서의 묶음이 아직 적재돼 있으면 막는다 (예: 직원이 있는데 조직만 되돌리기) */
    public LoadResponse rollback(Long migrationId, String batchId) {
        MigrationJob job = findEditableJob(migrationId);
        MigrationBatch batch = batchRepository.findByBatchIdAndMigrationId(batchId, migrationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BATCH_NOT_FOUND));
        if (batch.getStatus() != BatchStatus.LOADED) {
            throw new BusinessException(ErrorCode.BATCH_NOT_LOADED);
        }
        String dependents = batchRepository.findByMigrationIdOrderByStartedAtAsc(migrationId).stream()
                .filter(b -> b.getStatus() == BatchStatus.LOADED)
                .filter(b -> b.getEntity().ordinal() > batch.getEntity().ordinal())
                .map(MigrationBatch::getBatchId)
                .collect(Collectors.joining(", "));
        if (!dependents.isEmpty()) {
            throw new BusinessException(ErrorCode.ROLLBACK_DEPENDENCY_EXISTS, dependents + "을(를) 먼저 되돌려야 합니다");
        }

        loaderFor(batch.getEntity()).rollback(batch.getEntity(), batchId);
        batch.rolledBack();
        batchRepository.save(batch);
        if (!batchRepository.existsByMigrationIdAndStatus(migrationId, BatchStatus.LOADED)) {
            job.markDraft();
            jobRepository.save(job);
        }
        return new LoadResponse(job.getStatus(), batches(migrationId));
    }

    /** 적재된 묶음마다 원본 행 수와 대상 서비스에 들어간 수를 비교 */
    public List<ReconciliationItem> reconcile(Long migrationId) {
        findJob(migrationId);
        return batchRepository.findByMigrationIdOrderByStartedAtAsc(migrationId).stream()
                .filter(b -> b.getStatus() == BatchStatus.LOADED)
                .map(b -> {
                    long loaded = loaderFor(b.getEntity()).summarize(b.getEntity(), b.getBatchId()).count();
                    return new ReconciliationItem(b.getBatchId(), b.getEntity(), "count", b.getSourceRows(), loaded,
                            b.getSourceRows() == loaded);
                })
                .toList();
    }

    public List<BatchView> batches(Long migrationId) {
        return batchRepository.findByMigrationIdOrderByStartedAtAsc(migrationId).stream().map(BatchView::from).toList();
    }

    // ---------- 내부 ----------

    /** fileIds가 비면 적재 가능한 파일 전체(이미 적재된 것·미지원 entity 제외), 있으면 그 파일만 엄격하게 확인 */
    private List<MigrationFile> selectFiles(Long migrationId, List<Long> fileIds) {
        List<MigrationFile> all = fileRepository.findByMigrationIdOrderByFileIdAsc(migrationId);
        List<MigrationFile> selected;
        if (fileIds == null || fileIds.isEmpty()) {
            selected = all.stream()
                    .filter(f -> isSupported(f) && !batchRepository.existsByFileIdAndStatus(f.getFileId(), BatchStatus.LOADED))
                    .toList();
        } else {
            selected = fileIds.stream().map(id -> all.stream().filter(f -> f.getFileId().equals(id)).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.MIGRATION_FILE_NOT_FOUND))).toList();
            for (MigrationFile f : selected) {
                if (!isSupported(f)) {
                    throw new BusinessException(ErrorCode.LOAD_NOT_SUPPORTED, f.getEntity() + "은(는) 아직 적재를 지원하지 않습니다");
                }
                if (batchRepository.existsByFileIdAndStatus(f.getFileId(), BatchStatus.LOADED)) {
                    throw new BusinessException(ErrorCode.FILE_ALREADY_LOADED, "이미 적재된 파일입니다: " + f.getFileName());
                }
            }
        }
        if (selected.isEmpty()) {
            throw new BusinessException(ErrorCode.NOTHING_TO_LOAD);
        }
        return selected.stream()
                .sorted(Comparator.comparing((MigrationFile f) -> f.getEntity().ordinal()).thenComparing(MigrationFile::getFileId))
                .toList();
    }

    private boolean isSupported(MigrationFile f) {
        return EntitySchemas.of(f.getEntity()).isPresent() && loaders.stream().anyMatch(l -> l.supports(f.getEntity()));
    }

    private long count(Long migrationId, List<MigrationFile> files, Severity severity) {
        return files.stream().mapToLong(f -> issueRepository.totals(migrationId, f.getFileId()).get(severity)).sum();
    }

    private List<LoadRow> transform(MigrationFile file) {
        EntitySchema schema = EntitySchemas.of(file.getEntity()).orElseThrow();
        return RowTransformer.transform(schema, mappingService.fieldToColumn(file.getFileId()),
                stagingRowRepository.findByFileId(file.getFileId()));
    }

    private TargetLoader loaderFor(MigrationFile file) {
        return loaderFor(file.getEntity());
    }

    private TargetLoader loaderFor(MigrationEntity entity) {
        return loaders.stream().filter(l -> l.supports(entity)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.LOAD_NOT_SUPPORTED));
    }

    private void undoQuietly(TargetLoader loader, MigrationBatch batch) {
        try {
            loader.rollback(batch.getEntity(), batch.getBatchId());
        } catch (RuntimeException e) {
            log.error("실패 묶음 자동 되돌리기도 실패 batch={}: {}", batch.getBatchId(), e.getMessage());
        }
    }

    private static String newBatchId(MigrationFile file) {
        return "b-" + file.getMigrationId() + "-" + file.getEntity().name().toLowerCase() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
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
}
