package com.ssafy.migrationservice.domain.migration.repository;

import com.ssafy.migrationservice.domain.migration.reader.ParsedTable;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 업로드 행 원본을 임시 테이블(staging_row)에 저장한다.
 * 행 수가 많아 JPA 대신 JDBC 배치 insert를 쓰고, 값은 원본 헤더 → 문자열 jsonb로 보관한다.
 */
@Repository
@RequiredArgsConstructor
public class StagingRowRepository {

    private static final int BATCH_SIZE = 1000;
    private static final TypeReference<LinkedHashMap<String, String>> ROW_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void saveAll(Long fileId, List<ParsedTable.Row> rows) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO staging_row (file_id, row_no, data) VALUES (?, ?, ?::jsonb)",
                rows, BATCH_SIZE,
                (ps, row) -> {
                    ps.setLong(1, fileId);
                    ps.setInt(2, row.rowNo());
                    ps.setString(3, objectMapper.writeValueAsString(row.values()));
                });
    }

    public List<ParsedTable.Row> findByFileId(Long fileId) {
        return jdbcTemplate.query(
                "SELECT row_no, data::text AS data FROM staging_row WHERE file_id = ? ORDER BY row_no",
                (rs, i) -> new ParsedTable.Row(rs.getInt("row_no"), objectMapper.readValue(rs.getString("data"), ROW_TYPE)),
                fileId);
    }
}
