package com.ssafy.migrationservice.domain.migration.validation;

import com.ssafy.migrationservice.domain.migration.entity.MigrationFile;
import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import com.ssafy.migrationservice.domain.migration.mapping.MappingService;
import com.ssafy.migrationservice.domain.migration.reader.ParsedTable;
import com.ssafy.migrationservice.domain.migration.repository.MigrationFileRepository;
import com.ssafy.migrationservice.domain.migration.repository.StagingRowRepository;
import com.ssafy.migrationservice.domain.migration.validation.issue.IssueCode;
import com.ssafy.migrationservice.domain.migration.validation.issue.Severity;
import com.ssafy.migrationservice.domain.migration.validation.issue.ValidationIssue;
import com.ssafy.migrationservice.domain.migration.validation.schema.EntitySchemas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 조직 파일의 부서장 사번을 같은 작업의 직원명부와 대조 (user-service 부서장 지정 규칙과 같게).
 * - 직원명부에 없는 사번. 이름을 적었고 명부에 그 이름이 한 명뿐이면 사번으로 바꾸자고 제안
 * - 재직 중이 아님 (명부 재직상태가 휴직·퇴직이거나 퇴사일이 지남)
 * - 그 부서나 하위 부서 소속이 아님. 명부의 부서 값을 이 파일의 부서코드·부서명으로 찾고, 못 찾으면 보지 않는다
 * 직원명부를 나중에 올리면 MigrationService가 조직 파일도 다시 검증한다.
 */
@Component
@RequiredArgsConstructor
public class DeptHeadChecker {

    private static final int MAX_DEPTH = 20;

    private final MigrationFileRepository fileRepository;
    private final StagingRowRepository stagingRowRepository;
    private final MappingService mappingService;

    /** 명부의 한 사람. 사번이 명부에 두 번 이상 있으면 어느 줄이 맞는지 몰라 소속·재직은 보지 않는다 */
    private record Member(String name, String dept, boolean employed, boolean duplicated) {
    }

    public List<ValidationIssue> check(MigrationFile file, List<ParsedTable.Row> rows) {
        if (file.getEntity() != MigrationEntity.HR_ORG) {
            return List.of();
        }
        Map<String, String> f2c = mappingService.fieldToColumn(file.getFileId());
        String headCol = f2c.get("headEmpNo");
        String codeCol = f2c.get("deptCode");
        if (headCol == null || codeCol == null) {
            return List.of();
        }
        Optional<Map<String, Member>> rosterOpt = roster(file.getMigrationId());
        if (rosterOpt.isEmpty()) {
            return List.of();
        }
        Map<String, Member> roster = rosterOpt.get();
        Map<String, List<String>> empNosByName = new HashMap<>();
        roster.forEach((emp, m) -> {
            if (m.name() != null) {
                empNosByName.computeIfAbsent(m.name(), k -> new ArrayList<>()).add(emp);
            }
        });

        // 명부의 부서 값(부서코드 또는 부서명) → 부서코드. 같은 이름의 부서가 둘이면 이름으로는 찾지 않는다
        String nameCol = f2c.get("name");
        String parentCol = f2c.get("parentDeptCode");
        Map<String, String> codeByLabel = new HashMap<>();
        Map<String, String> parentOf = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (ParsedTable.Row row : rows) {
            String code = row.values().get(codeCol);
            if (code == null) {
                continue;
            }
            codeByLabel.put(code, code);
            String name = nameCol == null ? null : row.values().get(nameCol);
            if (name != null && codeByLabel.putIfAbsent(name, code) != null && !code.equals(codeByLabel.get(name))) {
                ambiguous.add(name);
            }
            if (parentCol != null && row.values().get(parentCol) != null) {
                parentOf.put(code, row.values().get(parentCol));
            }
        }
        ambiguous.forEach(codeByLabel::remove);

        List<ValidationIssue> issues = new ArrayList<>();
        for (ParsedTable.Row row : rows) {
            String head = row.values().get(headCol);
            String code = row.values().get(codeCol);
            if (head == null) {
                continue;
            }
            String empNo = head;
            if (!roster.containsKey(head)) {
                List<String> byName = empNosByName.getOrDefault(head, List.of());
                if (byName.size() != 1) {
                    issues.add(issue(file, row, headCol, Severity.WARN, IssueCode.HEAD_NOT_IN_ROSTER,
                            "부서장 '" + head + "'이(가) 직원명부에 없습니다" + (byName.size() > 1 ? " (같은 이름이 " + byName.size() + "명)" : "")
                                    + ". 부서장 열에는 사번을 적어 주세요", head, null));
                    continue;
                }
                empNo = byName.get(0);
                issues.add(issue(file, row, headCol, Severity.FIX, IssueCode.HEAD_NAME_GIVEN,
                        "부서장에 이름이 적혀 있습니다. 직원명부로 보면 사번 '" + empNo + "'입니다", head, empNo));
            }
            Member m = roster.get(empNo);
            if (m.duplicated()) {
                continue;
            }
            String who = (m.name() == null ? "" : m.name()) + "(" + empNo + ")";
            if (!m.employed()) {
                issues.add(issue(file, row, headCol, Severity.WARN, IssueCode.HEAD_NOT_EMPLOYED,
                        "부서장 " + who + "은(는) 직원명부에서 재직 중이 아닙니다. 재직자만 부서장이 될 수 있습니다", head, null));
            }
            String memberDept = m.dept() == null ? null : codeByLabel.get(m.dept());
            if (memberDept != null && code != null && !inSubtree(memberDept, code, parentOf)) {
                issues.add(issue(file, row, headCol, Severity.WARN, IssueCode.HEAD_OUTSIDE_DEPT,
                        "부서장 " + who + "의 명부 소속은 '" + m.dept() + "'입니다. 이 부서나 하위 부서 소속만 부서장이 될 수 있습니다 (겸직이면 소속을 확인해 주세요)",
                        head, null));
            }
        }
        return issues;
    }

    /** dept가 root이거나 그 하위 부서인지. 위로 올라가며 찾는다 */
    private static boolean inSubtree(String dept, String root, Map<String, String> parentOf) {
        Set<String> visited = new HashSet<>();
        for (String d = dept; d != null && visited.size() < MAX_DEPTH && visited.add(d); d = parentOf.get(d)) {
            if (d.equals(root)) {
                return true;
            }
        }
        return false;
    }

    /** 같은 작업의 가장 최근 직원명부: 사번 → 이름·부서·재직 여부 */
    private Optional<Map<String, Member>> roster(Long migrationId) {
        Optional<MigrationFile> file = fileRepository.findByMigrationIdOrderByFileIdAsc(migrationId).stream()
                .filter(f -> f.getEntity() == MigrationEntity.HR_EMPLOYEE)
                .max(Comparator.comparing(MigrationFile::getFileId));
        if (file.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> f2c = mappingService.fieldToColumn(file.get().getFileId());
        String empCol = f2c.get("empNo");
        if (empCol == null) {
            return Optional.empty();
        }
        Map<String, String> statusCodes = EntitySchemas.of(MigrationEntity.HR_EMPLOYEE)
                .flatMap(s -> s.field("status")).orElseThrow().enumValues();
        LocalDate today = LocalDate.now();
        Map<String, Member> out = new HashMap<>();
        for (ParsedTable.Row r : stagingRowRepository.findByFileId(file.get().getFileId())) {
            String emp = r.values().get(empCol);
            if (emp == null) {
                continue;
            }
            // 재직상태를 알아볼 수 없으면(Y/N, 0/1 …) 퇴사일만 본다
            String status = value(r, f2c.get("status"));
            boolean notEmployed = status != null && statusCodes.containsKey(status) && !statusCodes.get(status).equals("EMPLOYED");
            boolean resigned = FieldValueChecker.parseDate(value(r, f2c.get("resignDate"))).map(d -> !d.isAfter(today)).orElse(false);
            Member m = new Member(value(r, f2c.get("name")), value(r, f2c.get("dept")), !notEmployed && !resigned, false);
            out.merge(emp, m, (a, b) -> new Member(a.name(), a.dept(), a.employed(), true));
        }
        return Optional.of(out);
    }

    private static String value(ParsedTable.Row row, String column) {
        return column == null ? null : row.values().get(column);
    }

    private static ValidationIssue issue(MigrationFile file, ParsedTable.Row row, String column, Severity severity, IssueCode code,
                                         String message, String value, String suggestion) {
        return new ValidationIssue(file.getFileId(), row.rowNo(), "headEmpNo", column, severity, code, message, value, suggestion);
    }
}
