package com.zhijian.risk.excel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelFilesTest {
    @Test
    void roundTripKeepsHeaderAndSkipsBlankRows() {
        byte[] bytes = ExcelFiles.workbook("机构名单", List.of("全称", "类型"), List.of("从第 2 行填写"));
        org.apache.poi.xssf.usermodel.XSSFWorkbook book = open(bytes);
        org.apache.poi.ss.usermodel.Sheet sheet = book.getSheetAt(0);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(2);
        row.createCell(0).setCellValue("示例机构");
        row.createCell(1).setCellValue("助贷");
        byte[] filled = write(book);

        List<ExcelFiles.SheetRow> rows = ExcelFiles.read(filled);
        assertEquals(1, rows.size());
        assertEquals(3, rows.get(0).rowNumber());
        assertEquals("示例机构", ExcelFiles.cell(rows.get(0).cells(), "全称"));
        assertEquals("助贷", ExcelFiles.cell(rows.get(0).cells(), "类型"));
        assertTrue(ExcelFiles.hasHeader(rows, "全称"));
    }

    @Test
    void rejectsEmptyFile() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ExcelFiles.read(new byte[0]));
        assertEquals("请选择 Excel 文件", error.getMessage());
    }

    private static org.apache.poi.xssf.usermodel.XSSFWorkbook open(byte[] bytes) {
        try {
            return new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] write(org.apache.poi.xssf.usermodel.XSSFWorkbook book) {
        try (book; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            book.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
