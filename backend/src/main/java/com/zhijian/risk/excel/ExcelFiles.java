package com.zhijian.risk.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读写 xlsx。只读第一个工作表，第 1 行是表头；一次最多 2000 行数据。
 */
public final class ExcelFiles {
    public static final int MAX_ROWS = 2000;

    private ExcelFiles() {}

    public record SheetRow(int rowNumber, Map<String, String> cells) {}

    public static byte[] workbook(String sheetName, List<String> headers, List<String> notes) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet(sheetName);
            CellStyle head = book.createCellStyle();
            Font font = book.createFont();
            font.setBold(true);
            head.setFont(font);
            CellStyle text = book.createCellStyle();
            text.setDataFormat(book.createDataFormat().getFormat("@"));
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(head);
                sheet.setDefaultColumnStyle(i, text);
                sheet.setColumnWidth(i, 18 * 256);
            }
            sheet.createFreezePane(0, 1);
            Sheet help = book.createSheet("填写说明");
            for (int i = 0; i < notes.size(); i++) {
                help.createRow(i).createCell(0).setCellValue(notes.get(i));
            }
            help.setColumnWidth(0, 80 * 256);
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 失败", e);
        }
    }

    public static List<SheetRow> read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("请选择 Excel 文件");
        }
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("表格是空的");
            }
            Sheet sheet = book.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("缺少表头");
            }
            DataFormatter formatter = new DataFormatter();
            int columns = header.getLastCellNum();
            List<String> titles = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                titles.add(formatter.formatCellValue(header.getCell(c)).trim());
            }
            List<SheetRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> cells = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < titles.size(); c++) {
                    String title = titles.get(c);
                    if (title.isEmpty()) continue;
                    String value = formatter.formatCellValue(row.getCell(c)).trim();
                    if (!value.isEmpty()) any = true;
                    cells.put(title, value);
                }
                if (any) rows.add(new SheetRow(r + 1, cells));
            }
            if (rows.size() > MAX_ROWS) {
                throw new IllegalArgumentException("一次最多导入 " + MAX_ROWS + " 行");
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取 Excel，请使用下载的模板");
        }
    }

    public static String cell(Map<String, String> cells, String... names) {
        for (String name : names) {
            for (Map.Entry<String, String> entry : cells.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    public static boolean hasHeader(List<SheetRow> rows, String name) {
        return !rows.isEmpty() && rows.get(0).cells().keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }
}
