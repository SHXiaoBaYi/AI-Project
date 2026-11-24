package com.base.admin.util;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoDailyImportLayoutTest {

    @Test
    void templateGivesEachPlatformItsOwnLinkColumn() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GeoExcelTemplateWriter.writeDaily(out, List.of("豆包", "DS", "元宝"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheet("张三");
            int end = sheet.getRow(2).getLastCellNum();
            GeoDailyImportLayout.MetricBases bases = GeoDailyImportLayout.resolve(sheet, 1, 4, end);
            assertEquals(3, bases.platformCount());
            assertEquals(4, bases.mentionBase());
            assertEquals(7, bases.rankBase());
            assertEquals(10, bases.recommendBase());
            assertEquals(13, bases.shotBase());
            assertEquals(16, bases.linkBase());
            assertEquals(19, bases.negativeBase());
            assertEquals(22, bases.competitorBase());
            assertEquals("链接", ExcelCellUtils.str(sheet, 1, 16));
            assertEquals("豆包", ExcelCellUtils.str(sheet, 2, 16));
            assertEquals("DS", ExcelCellUtils.str(sheet, 2, 17));
            assertEquals("元宝", ExcelCellUtils.str(sheet, 2, 18));
            assertEquals("https://chat.deepseek.com/share/template-demo", ExcelCellUtils.str(sheet, 3, 16));
            assertEquals("", ExcelCellUtils.str(sheet, 3, 13));
            assertTrue(ExcelCellUtils.str(sheet, 1, 17).contains("链接"));
        }
    }

    @Test
    void legacySixMetricsKeepLinkEmptyAndOldColumnOffsets() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("旧表");
            String[] metrics = {"提及情况", "排名情况", "是否被推荐", "截图", "负面/错误内容", "出现的竞品"};
            int n = 2;
            int start = 4;
            var row = sheet.createRow(1);
            for (int m = 0; m < metrics.length; m++) {
                int col = start + m * n;
                row.createCell(col).setCellValue(metrics[m]);
                sheet.addMergedRegion(new CellRangeAddress(1, 1, col, col + n - 1));
            }
            GeoDailyImportLayout.MetricBases bases = GeoDailyImportLayout.resolve(sheet, 1, start, start + metrics.length * n);
            assertEquals(2, bases.platformCount());
            assertEquals(-1, bases.linkBase());
            assertEquals(start + 3 * n, bases.shotBase());
            assertEquals(start + 4 * n, bases.negativeBase());
            assertEquals(start + 5 * n, bases.competitorBase());
        }
    }

    @Test
    void unmergedLinkHeaderDoesNotStealNegativeColumn() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("未合并");
            var row = sheet.createRow(1);
            row.createCell(4).setCellValue("提及情况");
            row.createCell(7).setCellValue("排名情况");
            row.createCell(10).setCellValue("是否被推荐");
            row.createCell(13).setCellValue("截图");
            row.createCell(16).setCellValue("链接");
            row.createCell(19).setCellValue("负面/错误内容");
            row.createCell(22).setCellValue("出现的竞品");
            GeoDailyImportLayout.MetricBases bases = GeoDailyImportLayout.resolve(sheet, 1, 4, 25);
            assertEquals(3, bases.platformCount());
            assertEquals(16, bases.linkBase());
            assertEquals(19, bases.negativeBase());
            assertEquals(13, bases.shotBase());
        }
    }
}
