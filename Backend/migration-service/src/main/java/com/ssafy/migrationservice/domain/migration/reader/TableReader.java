package com.ssafy.migrationservice.domain.migration.reader;

import com.ssafy.migrationservice.domain.migration.reader.TableAssembler.RawRow;
import com.ssafy.migrationservice.global.exception.BusinessException;
import com.ssafy.migrationservice.global.exception.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** xlsx·csv 파일을 ParsedTable로 읽는다. 첫 번째 시트만 읽는다. */
@Component
public class TableReader {

    private static final Charset CP949 = Charset.forName("MS949");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public ParsedTable read(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".xlsx")) {
                return TableAssembler.assemble(readXlsx(content));
            }
            if (lower.endsWith(".csv")) {
                return TableAssembler.assemble(readCsv(content));
            }
        } catch (IOException | RuntimeException e) {
            // 확장자만 바꾼 파일, 깨진 xlsx는 POI가 unchecked 예외를 던진다
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.FILE_READ_FAILED);
        }
        throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
    }

    // ---------- xlsx ----------

    private List<RawRow> readXlsx(byte[] content) throws IOException {
        try (InputStream in = new ByteArrayInputStream(content); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<RawRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    cells.add(cellText(row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter));
                }
                rows.add(new RawRow(row.getRowNum() + 1, cells));
            }
            return rows;
        }
    }

    /** 날짜는 ISO 형식으로, 숫자는 지수·".0" 없이, 수식은 저장된 계산값으로 읽는다 */
    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dt = cell.getLocalDateTimeCellValue();
                    yield dt.toLocalTime().equals(LocalTime.MIDNIGHT)
                            ? dt.toLocalDate().toString() : dt.toString();
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            }
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK, ERROR, _NONE -> null;
            default -> formatter.formatCellValue(cell);
        };
    }

    // ---------- csv ----------

    private List<RawRow> readCsv(byte[] content) throws IOException {
        String text = decode(content);
        CSVFormat format = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).build();
        List<RawRow> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            for (CSVRecord record : parser) {
                rows.add(new RawRow((int) record.getRecordNumber(), record.toList()));
            }
        }
        return rows;
    }

    /** UTF-8 BOM → UTF-8, BOM 없으면 UTF-8로 엄격하게 시도하고 실패하면 CP949 (한글 엑셀에서 저장한 CSV) */
    static String decode(byte[] content) {
        if (content.length >= 3 && Arrays.equals(Arrays.copyOf(content, 3), UTF8_BOM)) {
            return new String(content, 3, content.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(content, CP949);
        }
    }
}
