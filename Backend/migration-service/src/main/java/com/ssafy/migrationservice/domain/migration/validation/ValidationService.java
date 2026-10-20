package com.ssafy.migrationservice.domain.migration.validation;

import com.ssafy.migrationservice.domain.migration.entity.MigrationFile;
import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import com.ssafy.migrationservice.domain.migration.mapping.ColumnMapping;
import com.ssafy.migrationservice.domain.migration.mapping.ColumnMappingRepository;
import com.ssafy.migrationservice.domain.migration.reader.ParsedTable;
import com.ssafy.migrationservice.domain.migration.repository.StagingRowRepository;
import com.ssafy.migrationservice.domain.migration.validation.issue.IssueCode;
import com.ssafy.migrationservice.domain.migration.validation.issue.IssueRepository;
import com.ssafy.migrationservice.domain.migration.validation.issue.Severity;
import com.ssafy.migrationservice.domain.migration.validation.issue.ValidationIssue;
import com.ssafy.migrationservice.domain.migration.validation.schema.EntitySchema;
import com.ssafy.migrationservice.domain.migration.validation.schema.EntitySchemas;
import com.ssafy.migrationservice.domain.migration.validation.schema.FieldSpec;
import com.ssafy.migrationservice.domain.migration.validation.schema.FieldType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 파일 하나를 단계별로 검증하고 결과를 validation_issue에 덮어쓴다.
 * ① 파일 구조 → ② 형식·③ 필수값 → ④ 파일 안 중복 → ⑥ 업무 규칙
 * 앞 단계에서 형식이 틀린 값은 업무 규칙에서 쓰지 않는다 (오류가 번지지 않게).
 * 참조(⑤)·검산(⑦)은 다음 단계에서 추가.
 */
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final ColumnMappingRepository mappingRepository;
    private final StagingRowRepository stagingRowRepository;
    private final IssueRepository issueRepository;

    @Transactional
    public void validate(MigrationFile file) {
        List<ValidationIssue> issues = new ArrayList<>();
        Optional<EntitySchema> schemaOpt = EntitySchemas.of(file.getEntity());
        if (schemaOpt.isEmpty()) {
            issues.add(fileIssue(file, Severity.WARN, IssueCode.SCHEMA_NOT_READY,
                    file.getEntity() + " 검증 규칙은 준비 중입니다"));
            issueRepository.replaceForFile(file.getMigrationId(), file.getFileId(), issues);
            return;
        }
        EntitySchema schema = schemaOpt.get();
        Map<String, String> fieldToColumn = mappedColumns(file.getFileId());

        // ① 파일 구조: 필수 필드가 어느 컬럼에도 연결되지 않음
        for (FieldSpec f : schema.fields()) {
            if (f.required() && !fieldToColumn.containsKey(f.name())) {
                issues.add(new ValidationIssue(file.getFileId(), null, f.name(), null, Severity.BLOCK,
                        IssueCode.MISSING_REQUIRED_COLUMN, "필수 컬럼 '" + f.label() + "'이(가) 없습니다", null, null));
            }
        }

        // ② 형식 · ③ 필수값. 형식이 맞는 날짜만 모아 업무 규칙에 넘긴다
        List<ParsedTable.Row> rows = stagingRowRepository.findByFileId(file.getFileId());
        Map<Integer, Map<String, LocalDate>> validDates = new HashMap<>();
        for (ParsedTable.Row row : rows) {
            for (Map.Entry<String, String> e : fieldToColumn.entrySet()) {
                FieldSpec f = schema.field(e.getKey()).orElseThrow();
                String column = e.getValue();
                String value = row.values().get(column);
                if (value == null) {
                    if (f.required()) {
                        issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), f.name(), column, Severity.BLOCK,
                                IssueCode.REQUIRED, f.label() + "이(가) 비어 있습니다", null, null));
                    }
                    continue;
                }
                Optional<FieldValueChecker.Finding> finding = FieldValueChecker.check(f, value);
                finding.ifPresent(fd -> issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), f.name(), column,
                        fd.severity(), fd.code(), fd.message(), value, fd.suggestion())));
                if (f.type() == FieldType.DATE && finding.map(fd -> fd.severity() != Severity.BLOCK).orElse(true)) {
                    FieldValueChecker.parseDate(value)
                            .ifPresent(d -> validDates.computeIfAbsent(row.rowNo(), k -> new HashMap<>()).put(f.name(), d));
                }
            }
        }

        // ④ 파일 안 중복: 기준 키는 SQL로 한 번에
        for (FieldSpec key : schema.keyFields()) {
            String column = fieldToColumn.get(key.name());
            if (column == null) {
                continue;
            }
            stagingRowRepository.findDuplicates(file.getFileId(), column).forEach((value, rowNos) -> {
                String where = rowNos.stream().map(String::valueOf).collect(Collectors.joining(", "));
                for (Integer rowNo : rowNos) {
                    issues.add(new ValidationIssue(file.getFileId(), rowNo, key.name(), column, Severity.BLOCK,
                            IssueCode.DUPLICATE_KEY, key.label() + " '" + value + "'이(가) 중복됩니다 (" + where + "행)",
                            value, null));
                }
            });
        }

        // ⑥ 업무 규칙
        if (file.getEntity() == MigrationEntity.HR_EMPLOYEE) {
            employeeRules(file, rows, fieldToColumn, validDates, issues);
        } else if (file.getEntity() == MigrationEntity.HR_ORG) {
            orgRules(file, rows, fieldToColumn, issues);
        }

        issueRepository.replaceForFile(file.getMigrationId(), file.getFileId(), issues);
    }

    private void employeeRules(MigrationFile file, List<ParsedTable.Row> rows, Map<String, String> fieldToColumn,
                               Map<Integer, Map<String, LocalDate>> validDates, List<ValidationIssue> issues) {
        for (ParsedTable.Row row : rows) {
            Map<String, LocalDate> d = validDates.getOrDefault(row.rowNo(), Map.of());
            LocalDate hire = d.get("hireDate");
            LocalDate resign = d.get("resignDate");
            LocalDate recog = d.get("recogHireDate");
            if (hire != null && resign != null && resign.isBefore(hire)) {
                issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), "resignDate", fieldToColumn.get("resignDate"),
                        Severity.BLOCK, IssueCode.DATE_ORDER, "퇴사일이 입사일보다 빠릅니다", resign.toString(), null));
            }
            if (hire != null && recog != null && recog.isAfter(hire)) {
                issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), "recogHireDate", fieldToColumn.get("recogHireDate"),
                        Severity.WARN, IssueCode.DATE_ORDER, "인정입사일이 입사일보다 늦습니다", recog.toString(), null));
            }
        }

        // 같은 사람이 두 번 들어갔는지: 이름 + 생년월일이 같으면 경고 (동명이인일 수 있어 차단하지 않음)
        String nameCol = fieldToColumn.get("name");
        String birthCol = fieldToColumn.get("birthDate");
        if (nameCol == null || birthCol == null) {
            return;
        }
        Map<String, List<Integer>> byPerson = new LinkedHashMap<>();
        for (ParsedTable.Row row : rows) {
            String name = row.values().get(nameCol);
            LocalDate birth = validDates.getOrDefault(row.rowNo(), Map.of()).get("birthDate");
            if (name != null && birth != null) {
                byPerson.computeIfAbsent(name + "|" + birth, k -> new ArrayList<>()).add(row.rowNo());
            }
        }
        byPerson.forEach((person, rowNos) -> {
            if (rowNos.size() < 2) {
                return;
            }
            String where = rowNos.stream().map(String::valueOf).collect(Collectors.joining(", "));
            String name = person.substring(0, person.indexOf('|'));
            for (Integer rowNo : rowNos) {
                issues.add(new ValidationIssue(file.getFileId(), rowNo, "name", nameCol, Severity.WARN,
                        IssueCode.DUPLICATE_SUSPECTED, "이름·생년월일이 같은 직원이 있습니다 (" + where + "행). 동명이인인지 확인해 주세요",
                        name, null));
            }
        });
    }

    private void orgRules(MigrationFile file, List<ParsedTable.Row> rows, Map<String, String> fieldToColumn,
                          List<ValidationIssue> issues) {
        String codeCol = fieldToColumn.get("deptCode");
        String parentCol = fieldToColumn.get("parentDeptCode");
        if (codeCol == null || parentCol == null) {
            return;
        }
        Set<String> codes = new HashSet<>();
        rows.forEach(r -> Optional.ofNullable(r.values().get(codeCol)).ifPresent(codes::add));
        for (ParsedTable.Row row : rows) {
            String code = row.values().get(codeCol);
            String parent = row.values().get(parentCol);
            if (parent == null) {
                continue;
            }
            if (parent.equals(code)) {
                issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), "parentDeptCode", parentCol, Severity.BLOCK,
                        IssueCode.SELF_PARENT, "자기 자신을 상위부서로 지정했습니다", parent, null));
            } else if (!codes.contains(parent)) {
                issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), "parentDeptCode", parentCol, Severity.WARN,
                        IssueCode.PARENT_NOT_IN_FILE, "상위부서 '" + parent + "'이(가) 이 파일에 없습니다. 기존 부서인지 확인해 주세요",
                        parent, null));
            }
        }
    }

    /** 필드 → 원본 컬럼 (연결된 것만) */
    private Map<String, String> mappedColumns(Long fileId) {
        Map<String, String> m = new LinkedHashMap<>();
        for (ColumnMapping cm : mappingRepository.findByFileIdOrderByMappingIdAsc(fileId)) {
            if (cm.getTargetField() != null) {
                m.put(cm.getTargetField(), cm.getSourceColumn());
            }
        }
        return m;
    }

    private static ValidationIssue fileIssue(MigrationFile file, Severity severity, IssueCode code, String message) {
        return new ValidationIssue(file.getFileId(), null, null, null, severity, code, message, null, null);
    }
}
