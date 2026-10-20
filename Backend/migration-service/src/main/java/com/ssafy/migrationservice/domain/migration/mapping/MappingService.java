package com.ssafy.migrationservice.domain.migration.mapping;

import com.ssafy.migrationservice.domain.migration.dto.ColumnMappingView;
import com.ssafy.migrationservice.domain.migration.dto.MappingUpdateRequest;
import com.ssafy.migrationservice.domain.migration.entity.MigrationFile;
import com.ssafy.migrationservice.domain.migration.validation.schema.EntitySchema;
import com.ssafy.migrationservice.domain.migration.validation.schema.EntitySchemas;
import com.ssafy.migrationservice.domain.migration.validation.schema.FieldSpec;
import com.ssafy.migrationservice.global.exception.BusinessException;
import com.ssafy.migrationservice.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MappingService {

    private final ColumnMappingRepository mappingRepository;

    /** 업로드 직후: 헤더마다 추천 매핑을 만든다 (confirmed=false) */
    @Transactional
    public void suggest(MigrationFile file) {
        mappingRepository.deleteByFileId(file.getFileId());
        Map<String, String> suggested = EntitySchemas.of(file.getEntity())
                .map(schema -> ColumnMappingSuggester.suggest(List.of(file.getHeaders()), schema))
                .orElseGet(() -> {
                    Map<String, String> none = new HashMap<>();
                    for (String h : file.getHeaders()) {
                        none.put(h, null);
                    }
                    return none;
                });
        for (String header : file.getHeaders()) {
            mappingRepository.save(ColumnMapping.builder()
                    .fileId(file.getFileId())
                    .sourceColumn(header)
                    .targetField(suggested.get(header))
                    .confirmed(false)
                    .build());
        }
    }

    /**
     * 사람이 매핑을 확정한다. 요청에 없는 컬럼은 기존 값을 유지한다.
     * 없는 필드, 한 필드에 두 컬럼 연결은 거절한다.
     */
    @Transactional
    public void update(MigrationFile file, MappingUpdateRequest request) {
        EntitySchema schema = EntitySchemas.of(file.getEntity())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, file.getEntity() + "은(는) 아직 매핑을 지원하지 않습니다"));
        Set<String> headers = Set.of(file.getHeaders());

        Map<String, String> next = new HashMap<>();
        for (ColumnMapping cm : mappingRepository.findByFileIdOrderByMappingIdAsc(file.getFileId())) {
            next.put(cm.getSourceColumn(), cm.getTargetField());
        }
        for (MappingUpdateRequest.Item item : request.columnMappings()) {
            if (!headers.contains(item.sourceColumn())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "파일에 없는 컬럼입니다: " + item.sourceColumn());
            }
            if (item.targetField() != null && schema.field(item.targetField()).isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "없는 필드입니다: " + item.targetField());
            }
            next.put(item.sourceColumn(), item.targetField());
        }
        Set<String> seen = new HashSet<>();
        for (String target : next.values()) {
            if (target != null && !seen.add(target)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "한 필드에 여러 컬럼을 연결할 수 없습니다: " + target);
            }
        }

        mappingRepository.deleteByFileId(file.getFileId());
        for (String header : file.getHeaders()) {
            mappingRepository.save(ColumnMapping.builder()
                    .fileId(file.getFileId())
                    .sourceColumn(header)
                    .targetField(next.get(header))
                    .confirmed(true)
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<ColumnMappingView> view(MigrationFile file) {
        Map<String, String> labels = new HashMap<>();
        EntitySchemas.of(file.getEntity()).ifPresent(s -> s.fields().forEach(f -> labels.put(f.name(), f.label())));
        return mappingRepository.findByFileIdOrderByMappingIdAsc(file.getFileId()).stream()
                .map(cm -> new ColumnMappingView(cm.getSourceColumn(), cm.getTargetField(),
                        labels.get(cm.getTargetField()), cm.isConfirmed()))
                .toList();
    }

    /** 필수 필드 중 아직 어느 컬럼에도 연결되지 않은 것 (라벨) */
    @Transactional(readOnly = true)
    public List<String> missingRequired(MigrationFile file) {
        Set<String> mapped = new HashSet<>();
        mappingRepository.findByFileIdOrderByMappingIdAsc(file.getFileId())
                .forEach(cm -> {
                    if (cm.getTargetField() != null) {
                        mapped.add(cm.getTargetField());
                    }
                });
        return EntitySchemas.of(file.getEntity()).map(s -> s.fields().stream()
                        .filter(FieldSpec::required)
                        .filter(f -> !mapped.contains(f.name()))
                        .map(FieldSpec::label)
                        .toList())
                .orElse(List.of());
    }
}
