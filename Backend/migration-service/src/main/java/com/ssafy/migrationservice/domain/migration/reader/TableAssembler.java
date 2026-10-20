package com.ssafy.migrationservice.domain.migration.reader;

import com.ssafy.migrationservice.global.exception.BusinessException;
import com.ssafy.migrationservice.global.exception.ErrorCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 원시 행 목록 → 헤더 + 데이터 행. 엑셀·CSV 공통 규칙을 한 곳에 둔다.
 * - 헤더: 값이 2칸 이상 있는 첫 행 (위쪽 제목·작성일 행은 건너뜀)
 * - 빈 헤더는 "컬럼N", 중복 헤더는 "이름_2" 식으로 바꿈
 * - 완전히 빈 행은 버림
 */
final class TableAssembler {

    private static final int MIN_HEADER_CELLS = 2;

    private TableAssembler() {
    }

    static ParsedTable assemble(List<RawRow> rawRows) {
        int headerIdx = findHeaderIndex(rawRows);
        List<String> headers = normalizeHeaders(rawRows.get(headerIdx).cells());

        List<ParsedTable.Row> rows = new ArrayList<>();
        for (int i = headerIdx + 1; i < rawRows.size(); i++) {
            RawRow raw = rawRows.get(i);
            if (raw.isBlank()) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String v = c < raw.cells().size() ? raw.cells().get(c) : null;
                values.put(headers.get(c), blankToNull(v));
            }
            rows.add(new ParsedTable.Row(raw.rowNo(), values));
        }
        return new ParsedTable(headers, rows);
    }

    private static int findHeaderIndex(List<RawRow> rawRows) {
        for (int i = 0; i < rawRows.size(); i++) {
            if (rawRows.get(i).nonBlankCount() >= MIN_HEADER_CELLS) {
                return i;
            }
        }
        throw new BusinessException(ErrorCode.FILE_NO_HEADER);
    }

    private static List<String> normalizeHeaders(List<String> cells) {
        // 오른쪽 끝의 빈 칸은 컬럼으로 치지 않음
        int last = cells.size() - 1;
        while (last >= 0 && blankToNull(cells.get(last)) == null) {
            last--;
        }
        List<String> headers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int c = 0; c <= last; c++) {
            String h = blankToNull(cells.get(c));
            String base = h == null ? "컬럼" + (c + 1) : h.replaceAll("\\s+", " ");
            String name = base;
            for (int n = 2; !seen.add(name); n++) {
                name = base + "_" + n;
            }
            headers.add(name);
        }
        return headers;
    }

    static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.strip();
        return t.isEmpty() ? null : t;
    }

    /** 파일 형식별 리더가 만드는 중간 결과. rowNo는 원본 기준 1부터 */
    record RawRow(int rowNo, List<String> cells) {

        boolean isBlank() {
            return nonBlankCount() == 0;
        }

        long nonBlankCount() {
            return cells.stream().filter(v -> blankToNull(v) != null).count();
        }
    }
}
