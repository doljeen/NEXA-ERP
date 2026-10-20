package com.ssafy.migrationservice.domain.migration.reader;

import java.util.List;
import java.util.Map;

/**
 * 엑셀·CSV를 읽은 결과. 모든 값은 문자열로 보존하고 타입 해석은 검증 단계에서 한다.
 * rowNo는 원본 파일 기준 행 번호(1부터)라서 오류 위치를 그대로 안내할 수 있다.
 */
public record ParsedTable(List<String> headers, List<Row> rows) {

    public record Row(int rowNo, Map<String, String> values) {
    }
}
