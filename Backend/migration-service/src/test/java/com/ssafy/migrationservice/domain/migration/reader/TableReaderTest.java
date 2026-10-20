package com.ssafy.migrationservice.domain.migration.reader;

import com.ssafy.migrationservice.global.exception.BusinessException;
import com.ssafy.migrationservice.global.exception.ErrorCode;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableReaderTest {

    private final TableReader reader = new TableReader();

    @Test
    void xlsx_제목행을_건너뛰고_날짜_숫자_수식_앞자리0을_보존한다() throws IOException {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("2026년 직원 명부");          // 제목 행
            Row header = sheet.createRow(2);                                              // 1행은 빈 행
            header.createCell(0).setCellValue("사번");
            header.createCell(1).setCellValue("이름");
            header.createCell(2).setCellValue("입사일");
            header.createCell(3).setCellValue("기본급");
            header.createCell(4).setCellValue("연봉");

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy.mm.dd"));
            Row r = sheet.createRow(3);
            r.createCell(0).setCellValue("00123");
            r.createCell(1).setCellValue("홍길동");
            r.createCell(2).setCellValue(LocalDate.of(2024, 1, 2));
            r.getCell(2).setCellStyle(dateStyle);
            r.createCell(3).setCellValue(3500000);
            r.createCell(4).setCellFormula("D4*12");
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            sheet.createRow(4);                                                           // 빈 행
            wb.write(out);
            xlsx = out.toByteArray();
        }

        ParsedTable table = reader.read("직원명부.xlsx", xlsx);

        assertThat(table.headers()).containsExactly("사번", "이름", "입사일", "기본급", "연봉");
        assertThat(table.rows()).hasSize(1);
        ParsedTable.Row row = table.rows().get(0);
        assertThat(row.rowNo()).isEqualTo(4);
        assertThat(row.values()).containsEntry("사번", "00123")
                .containsEntry("입사일", "2024-01-02")
                .containsEntry("기본급", "3500000")
                .containsEntry("연봉", "42000000");
    }

    @Test
    void csv_UTF8_BOM을_읽는다() {
        byte[] body = "사번,이름\n00123,홍길동\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);

        ParsedTable table = reader.read("a.csv", withBom);

        assertThat(table.headers()).containsExactly("사번", "이름");
        assertThat(table.rows().get(0).values()).containsEntry("이름", "홍길동");
    }

    @Test
    void csv_CP949로_저장된_한글을_읽는다() {
        byte[] cp949 = "사번,이름\n00123,홍길동\n".getBytes(Charset.forName("MS949"));

        ParsedTable table = reader.read("a.csv", cp949);

        assertThat(table.headers()).containsExactly("사번", "이름");
        assertThat(table.rows().get(0).values()).containsEntry("이름", "홍길동");
    }

    @Test
    void 빈_헤더와_중복_헤더를_정리한다() {
        byte[] csv = "이름,,이름\n홍길동,x,길동\n".getBytes(StandardCharsets.UTF_8);

        ParsedTable table = reader.read("a.csv", csv);

        assertThat(table.headers()).containsExactly("이름", "컬럼2", "이름_2");
    }

    @Test
    void 헤더가_없으면_실패한다() {
        byte[] csv = "제목만 있음\n\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read("a.csv", csv))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_NO_HEADER);
    }

    @Test
    void 깨진_xlsx는_읽기_실패로_처리한다() {
        assertThatThrownBy(() -> reader.read("a.xlsx", "abc".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_READ_FAILED);
    }

    @Test
    void 지원하지_않는_확장자는_실패한다() {
        assertThatThrownBy(() -> reader.read("a.xls", new byte[]{1}))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
    }
}
