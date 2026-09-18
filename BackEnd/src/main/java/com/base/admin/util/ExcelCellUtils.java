package com.base.admin.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public final class ExcelCellUtils {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelCellUtils() {}

    public static String str(Sheet sheet, int rowIdx, int colIdx) {
        Cell cell = resolvedCell(sheet, rowIdx, colIdx);
        if (cell == null) {
            return "";
        }
        String value = FORMATTER.formatCellValue(cell).trim();
        return value.replace("\n", " ").replace("\r", "");
    }

    /** 读取原始单元格（不展开合并区域），用于识别续行 */
    public static String rawStr(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            return "";
        }
        String value = FORMATTER.formatCellValue(cell).trim();
        return value.replace("\n", " ").replace("\r", "");
    }

    public static String hyperlink(Sheet sheet, int rowIdx, int colIdx) {
        Cell cell = resolvedCell(sheet, rowIdx, colIdx);
        if (cell == null || cell.getHyperlink() == null || cell.getHyperlink().getAddress() == null) {
            return "";
        }
        return cell.getHyperlink().getAddress().trim();
    }

    public static LocalDate date(Sheet sheet, int rowIdx, int colIdx) {
        Cell cell = resolvedCell(sheet, rowIdx, colIdx);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell) || (value > 20000 && value < 80000)) {
                Date d = DateUtil.getJavaDate(value);
                return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        }
        String text = FORMATTER.formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return null;
        }
        text = text.replace('.', '-').replace('/', '-');
        try {
            String[] parts = text.split("-");
            if (parts.length >= 3) {
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            return LocalDate.parse(text.substring(0, Math.min(10, text.length())));
        } catch (Exception e) {
            return null;
        }
    }

    private static Cell resolvedCell(Sheet sheet, int rowIdx, int colIdx) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIdx, colIdx)) {
                Row first = sheet.getRow(region.getFirstRow());
                return first == null ? null : first.getCell(region.getFirstColumn());
            }
        }
        Row row = sheet.getRow(rowIdx);
        return row == null ? null : row.getCell(colIdx);
    }
}
