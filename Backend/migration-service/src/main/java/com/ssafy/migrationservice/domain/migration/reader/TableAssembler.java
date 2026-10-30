package com.ssafy.migrationservice.domain.migration.reader;

import com.ssafy.migrationservice.global.exception.BusinessException;
import com.ssafy.migrationservice.global.exception.ErrorCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 원시 행 목록 → 헤더 + 데이터 행. 엑셀·CSV 공통 규칙을 한 곳에 둔다.
 * - 헤더: 위쪽 30행에서 '글자' 칸(숫자·날짜·시각이 아닌 값)이 가장 많은 행의 절반 이상인 첫 행.
 *   제목·작성일·요율·묶음 머리글 행은 글자 칸이 적어서 건너뛴다
 * - 헤더 바로 위 행의 가로 병합(묶음 머리글)은, 헤더 이름이 겹치는 열에만 앞에 붙인다 (예: 07월 + 연장 → "07월 연장")
 * - 빈 헤더는 "컬럼N", 중복 헤더는 "이름_2" 식으로 바꿈
 * - 완전히 빈 행은 버리고, 합계·소계 행은 데이터에서 뺀다
 */
final class TableAssembler {

    private static final int MIN_HEADER_CELLS = 2;
    private static final int HEADER_SCAN_ROWS = 30;
    private static final Pattern NOT_TEXT = Pattern.compile(
            "-?[\\d,]+(\\.\\d+)?%?|\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}.*|\\d{1,2}:\\d{2}(:\\d{2})?");
    private static final Pattern GRAND_TOTAL = Pattern.compile("합계|총계|총합계|total|grandtotal");
    private static final Pattern SUB_TOTAL = Pattern.compile("소계|계|subtotal");
    private static final int TOTAL_LABEL_COLUMNS = 3;

    private TableAssembler() {
    }

    static ParsedTable assemble(List<RawRow> rawRows, String sheetName) {
        int headerIdx = findHeaderIndex(rawRows);
        RawRow headerRow = rawRows.get(headerIdx);
        RawRow above = headerIdx > 0 ? rawRows.get(headerIdx - 1) : null;
        Map<Integer, String> groups = above != null && above.rowNo() == headerRow.rowNo() - 1 ? above.groups() : Map.of();
        List<String> headers = normalizeHeaders(headerRow.cells(), groups);

        List<ParsedTable.Row> rows = new ArrayList<>();
        List<Integer> totals = new ArrayList<>();
        List<Integer> afterTotal = new ArrayList<>();
        boolean pastGrandTotal = false;
        for (int i = headerIdx + 1; i < rawRows.size(); i++) {
            RawRow raw = rawRows.get(i);
            if (raw.isBlank()) {
                continue;
            }
            String total = totalLabel(raw);
            if (total != null) {
                totals.add(raw.rowNo());
                pastGrandTotal |= GRAND_TOTAL.matcher(total).matches();
                continue;
            }
            if (pastGrandTotal) {
                afterTotal.add(raw.rowNo());
            }
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, String> formulas = new HashMap<>();
            Map<String, String> errors = new HashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String h = headers.get(c);
                values.put(h, blankToNull(raw.cell(c)));
                if (raw.formula(c) != null) {
                    formulas.put(h, raw.formula(c));
                }
                if (raw.error(c) != null) {
                    errors.put(h, raw.error(c));
                }
            }
            rows.add(new ParsedTable.Row(raw.rowNo(), values, formulas, errors));
        }
        return new ParsedTable(headers, rows, sheetName, headerRow.rowNo(), totals, afterTotal);
    }

    static ParsedTable assemble(List<RawRow> rawRows) {
        return assemble(rawRows, null);
    }

    private static int findHeaderIndex(List<RawRow> rawRows) {
        int scan = Math.min(rawRows.size(), HEADER_SCAN_ROWS);
        long max = 0;
        for (int i = 0; i < scan; i++) {
            max = Math.max(max, rawRows.get(i).textCount());
        }
        // 글자 칸이 가장 많은 행의 절반 이상인 첫 행 (데이터 행이 헤더보다 글자 칸이 조금 많아도 헤더를 고른다)
        for (int i = 0; i < scan && max >= MIN_HEADER_CELLS; i++) {
            long score = rawRows.get(i).textCount();
            if (score >= MIN_HEADER_CELLS && score * 2 >= max) {
                // 가로 병합된 묶음 머리글 행이면 바로 아래(글자 칸이 더 많은) 행이 진짜 헤더
                while (i + 1 < rawRows.size() && !rawRows.get(i).groups().isEmpty()
                        && rawRows.get(i + 1).textCount() > rawRows.get(i).textCount()) {
                    i++;
                }
                return i;
            }
        }
        // 글자 칸이 2개 넘는 행이 없으면 (숫자만 있는 표 등) 값이 2칸 이상인 첫 행
        for (int i = 0; i < rawRows.size(); i++) {
            if (rawRows.get(i).nonBlankCount() >= MIN_HEADER_CELLS) {
                return i;
            }
        }
        throw new BusinessException(ErrorCode.FILE_NO_HEADER);
    }

    private static List<String> normalizeHeaders(List<String> cells, Map<Integer, String> groups) {
        // 오른쪽 끝의 빈 칸은 컬럼으로 치지 않음
        int last = cells.size() - 1;
        while (last >= 0 && blankToNull(cells.get(last)) == null) {
            last--;
        }
        List<String> base = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (int c = 0; c <= last; c++) {
            String h = blankToNull(cells.get(c));
            String name = h == null ? null : h.replaceAll("\\s+", " ");
            base.add(name);
            if (name != null) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        List<String> headers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int c = 0; c <= last; c++) {
            String name = base.get(c);
            String group = blankToNull(groups.get(c));
            if (name != null && counts.get(name) > 1 && group != null && !group.equals(name)) {
                name = group.replaceAll("\\s+", " ") + " " + name;
            }
            String b = name == null ? "컬럼" + (c + 1) : name;
            String unique = b;
            for (int n = 2; !seen.add(unique); n++) {
                unique = b + "_" + n;
            }
            headers.add(unique);
        }
        return headers;
    }

    /** 앞쪽 몇 칸 안에 '합계'·'소계' 같은 글자만 있는 행이면 그 글자, 아니면 null */
    private static String totalLabel(RawRow raw) {
        for (int c = 0; c < Math.min(TOTAL_LABEL_COLUMNS, raw.cells().size()); c++) {
            String v = blankToNull(raw.cell(c));
            if (v == null) {
                continue;
            }
            String t = v.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (GRAND_TOTAL.matcher(t).matches() || SUB_TOTAL.matcher(t).matches()) {
                return t;
            }
        }
        return null;
    }

    static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.strip();
        return t.isEmpty() ? null : t;
    }

    /**
     * 파일 형식별 리더가 만드는 중간 결과. rowNo는 원본 기준 1부터.
     * formulas·errors는 셀 위치가 cells와 같은 목록 (CSV는 비어 있음), groups는 이 행의 가로 병합 열 → 머리글
     */
    record RawRow(int rowNo, List<String> cells, List<String> formulas, List<String> errors, Map<Integer, String> groups) {

        RawRow(int rowNo, List<String> cells) {
            this(rowNo, cells, List.of(), List.of(), Map.of());
        }

        String cell(int c) {
            return c < cells.size() ? cells.get(c) : null;
        }

        String formula(int c) {
            return c < formulas.size() ? formulas.get(c) : null;
        }

        String error(int c) {
            return c < errors.size() ? errors.get(c) : null;
        }

        boolean isBlank() {
            return nonBlankCount() == 0 && errors.stream().allMatch(e -> e == null);
        }

        long nonBlankCount() {
            return cells.stream().filter(v -> blankToNull(v) != null).count();
        }

        long textCount() {
            return cells.stream().map(TableAssembler::blankToNull)
                    .filter(v -> v != null && !NOT_TEXT.matcher(v).matches())
                    .count();
        }
    }
}
