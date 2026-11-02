package com.ssafy.migrationservice.domain.migration.validation;

import com.ssafy.migrationservice.domain.migration.entity.MigrationFile;
import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import com.ssafy.migrationservice.domain.migration.mapping.ColumnMappingSuggester;
import com.ssafy.migrationservice.domain.migration.mapping.MappingService;
import com.ssafy.migrationservice.domain.migration.reader.ParsedTable;
import com.ssafy.migrationservice.domain.migration.repository.MigrationFileRepository;
import com.ssafy.migrationservice.domain.migration.repository.StagingRowRepository;
import com.ssafy.migrationservice.domain.migration.validation.issue.IssueCode;
import com.ssafy.migrationservice.domain.migration.validation.issue.Severity;
import com.ssafy.migrationservice.domain.migration.validation.issue.ValidationIssue;
import com.ssafy.migrationservice.domain.migration.validation.schema.EntitySchemas;
import com.ssafy.migrationservice.domain.migration.validation.schema.FieldSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ⑤ 참조: 직원명부가 아닌 파일(급여·연차·근태 …)의 사번이 같은 작업의 직원명부에 있는지, 이름이 같은지.
 * 사번·이름 컬럼은 매핑이 있으면 매핑, 없으면(참고 자료) 직원명부 필드의 라벨·동의어로 찾는다.
 * 숫자 셀로 저장돼 앞자리 0이 빠진 사번은 직원명부 사번 자릿수에 맞춰 0을 채워 대조하고, 그렇게 했다고 알린다.
 */
@Component
@RequiredArgsConstructor
public class RosterReferenceChecker {

    private final MigrationFileRepository fileRepository;
    private final StagingRowRepository stagingRowRepository;
    private final MappingService mappingService;

    /** 사번 → 이름들 (직원명부에서 사번이 중복이면 이름이 둘 이상. 그 중복은 직원명부 쪽 차단 오류로 따로 나온다) */
    private record Roster(Map<String, Set<String>> namesByEmpNo, Map<String, List<String>> empNosByName, int width) {
    }

    public List<ValidationIssue> check(MigrationFile file, List<ParsedTable.Row> rows) {
        if (file.getEntity() == MigrationEntity.HR_EMPLOYEE || file.getEntity() == MigrationEntity.HR_ORG) {
            return List.of();
        }
        Optional<Roster> rosterOpt = roster(file.getMigrationId());
        Map<String, String> fieldToColumn = mappingService.fieldToColumn(file.getFileId());
        String empCol = fieldToColumn.getOrDefault("empNo", findColumn(file, "empNo"));
        if (rosterOpt.isEmpty() || empCol == null) {
            return List.of();
        }
        Roster roster = rosterOpt.get();
        String nameCol = fieldToColumn.getOrDefault("name", findColumn(file, "name"));

        List<ValidationIssue> issues = new ArrayList<>();
        int padded = 0;
        for (ParsedTable.Row row : rows) {
            String raw = row.values().get(empCol);
            String name = nameCol == null ? null : row.values().get(nameCol);
            if (raw == null) {
                if (name != null) {
                    List<String> byName = roster.empNosByName().getOrDefault(name, List.of());
                    String suggestion = byName.size() == 1 ? byName.get(0) : null;
                    issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), "empNo", empCol, Severity.WARN, IssueCode.EMP_KEY_MISSING,
                            "사번이 비어 있습니다 (이름 '" + name + "')" + (suggestion == null ? "" : ". 직원명부에서 이름으로 찾은 사번: " + suggestion),
                            null, suggestion));
                }
                continue;
            }
            String key = raw;
            if (raw.matches("\\d+") && raw.length() < roster.width()) {
                key = "0".repeat(roster.width() - raw.length()) + raw;
                padded++;
            }
            Set<String> rosterNames = roster.namesByEmpNo().get(key);
            if (rosterNames == null) {
                issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), "empNo", empCol, Severity.WARN, IssueCode.EMP_NOT_IN_ROSTER,
                        "직원명부에 없는 사번입니다: '" + raw + "'", raw, null));
            } else if (name != null && !rosterNames.isEmpty() && !rosterNames.contains(name)) {
                String rosterName = String.join(", ", rosterNames);
                issues.add(new ValidationIssue(file.getFileId(), row.rowNo(), "name", nameCol, Severity.WARN, IssueCode.EMP_NAME_MISMATCH,
                        "사번 " + key + "의 이름이 직원명부('" + rosterName + "')와 다릅니다: '" + name + "'", name, rosterName));
            }
        }
        if (padded > 0) {
            issues.add(new ValidationIssue(file.getFileId(), null, "empNo", empCol, Severity.WARN, IssueCode.EMPNO_ZERO_PADDED,
                    "사번 " + padded + "건이 숫자로 저장돼 앞자리 0이 빠져 있어, 직원명부 자리수(" + roster.width() + "자리)에 맞춰 대조했습니다",
                    null, null));
        }
        return issues;
    }

    /** 같은 작업의 직원명부 (여러 개면 마지막에 올린 것). 사번·이름이 매핑돼 있어야 함 */
    private Optional<Roster> roster(Long migrationId) {
        Optional<MigrationFile> file = fileRepository.findByMigrationIdOrderByFileIdAsc(migrationId).stream()
                .filter(f -> f.getEntity() == MigrationEntity.HR_EMPLOYEE)
                .max(Comparator.comparing(MigrationFile::getFileId));
        if (file.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> f2c = mappingService.fieldToColumn(file.get().getFileId());
        String empCol = f2c.get("empNo");
        String nameCol = f2c.get("name");
        if (empCol == null) {
            return Optional.empty();
        }
        Map<String, Set<String>> namesByEmpNo = new HashMap<>();
        Map<String, List<String>> byName = new HashMap<>();
        for (ParsedTable.Row r : stagingRowRepository.findByFileId(file.get().getFileId())) {
            String emp = r.values().get(empCol);
            String name = nameCol == null ? null : r.values().get(nameCol);
            if (emp == null) {
                continue;
            }
            Set<String> names = namesByEmpNo.computeIfAbsent(emp, k -> new LinkedHashSet<>());
            if (name != null) {
                names.add(name);
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(emp);
            }
        }
        int width = namesByEmpNo.keySet().stream().filter(k -> k.matches("\\d+")).mapToInt(String::length).max().orElse(0);
        return Optional.of(new Roster(namesByEmpNo, byName, width));
    }

    /** 매핑이 없는 파일: 직원명부 필드(사번·이름)의 이름·라벨·동의어와 같은 헤더 */
    static String findColumn(MigrationFile file, String field) {
        FieldSpec spec = EntitySchemas.of(MigrationEntity.HR_EMPLOYEE).flatMap(s -> s.field(field)).orElseThrow();
        List<String> names = new ArrayList<>(spec.synonyms());
        names.add(spec.name());
        names.add(spec.label());
        List<String> wanted = names.stream().map(ColumnMappingSuggester::normalize).toList();
        for (String h : file.getHeaders()) {
            if (wanted.contains(ColumnMappingSuggester.normalize(h))) {
                return h;
            }
        }
        return null;
    }
}
