package com.base.admin.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * 日监测、内容投放导入模板。列位置与现有 Excel 解析器一致，表头标必填，橙色行为模板示例。
 */
public final class GeoExcelTemplateWriter {

    /** 含此标记的行在导入时跳过，避免把模板示例写进正式数据 */
    public static final String SAMPLE_MARK = "【模板示例】";

    private GeoExcelTemplateWriter() {}

    public static void writeDaily(OutputStream out, List<String> aiPlatforms) throws IOException {
        List<String> platforms = normalizePlatforms(aiPlatforms);
        int n = platforms.size();
        try (Workbook wb = new XSSFWorkbook()) {
            Styles styles = new Styles(wb);
            Sheet sheet = wb.createSheet("日监测导入");
            String[] metrics = {"提及情况", "排名情况", "是否推荐", "截图", "负面/错误内容", "出现的竞品"};

            Row row0 = sheet.createRow(0);
            Row row1 = sheet.createRow(1);
            Row row2 = sheet.createRow(2);
            text(row0, 0, "巡查日期（必填）", styles.required);
            text(row1, 0, "巡查维度", styles.header);
            text(row2, 0, "序号", styles.header);
            text(row2, 1, "开始优化时间", styles.header);
            text(row2, 2, "话题（必填）", styles.required);
            text(row2, 3, "目标问题（必填）", styles.required);

            int start = 4;
            int width = n * metrics.length;
            text(row0, start, "2026-09-01", styles.required);
            if (width > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, start, start + width - 1));
            }
            for (int m = 0; m < metrics.length; m++) {
                int col = start + m * n;
                text(row1, col, metrics[m], styles.header);
                if (n > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(1, 1, col, col + n - 1));
                }
                for (int p = 0; p < n; p++) {
                    // 第3行平台名会被导入器读取，不能加「必填」后缀
                    text(row2, col + p, platforms.get(p), m == 0 ? styles.required : styles.header);
                }
            }

            Row sample = sheet.createRow(3);
            text(sample, 0, "1", styles.sample);
            text(sample, 1, SAMPLE_MARK + "9月第1周", styles.sample);
            text(sample, 2, "新疆特产", styles.sample);
            text(sample, 3, SAMPLE_MARK + "新疆适合寄内地的礼品有哪些", styles.sample);
            for (int p = 0; p < n; p++) {
                text(sample, start + p, p % 2 == 0 ? "是" : "否", styles.sample);
                text(sample, start + n + p, p == 0 ? "3" : "", styles.sample);
                text(sample, start + 2 * n + p, p == 0 ? "出现且推荐" : "未出现", styles.sample);
                text(sample, start + 3 * n + p, p == 0 ? "https://chat.deepseek.com/share/template-demo" : "", styles.sample);
                text(sample, start + 4 * n + p, "", styles.sample);
                text(sample, start + 5 * n + p, p == 0 ? "竞品A" : "", styles.sample);
            }

            sheet.createFreezePane(4, 3);
            sheet.setColumnWidth(0, 16 * 256);
            sheet.setColumnWidth(1, 28 * 256);
            sheet.setColumnWidth(2, 18 * 256);
            sheet.setColumnWidth(3, 42 * 256);
            for (int c = start; c < start + width; c++) {
                sheet.setColumnWidth(c, 16 * 256);
            }

            String joined = String.join("、", platforms);
            writeInstructions(wb, styles, "填写说明", List.of(
                    List.of("巡查日期", "是", "第1行、每个日期块的第1列，格式 yyyy-MM-dd。一个日期占「平台数 × 6」列", "2026-09-01"),
                    List.of("平台名", "是", "第3行已列出当前全部 AI 平台：" + joined + "。每个指标下都有这些列，请逐列填写，不要改平台名", joined),
                    List.of("话题", "是", "C列。与话题管理中的名称一致则自动关联；没有则自动建档并关联。同一话题的后续行可留空，沿用上一行", "新疆特产"),
                    List.of("目标问题", "是", "D列，不能为空。这是导入识别数据行的依据", "新疆适合寄内地的礼品有哪些"),
                    List.of("开始优化时间", "否", "B列，仅作话题周期备注，可空", "9月第1周"),
                    List.of("序号", "否", "A列，导入不读取，方便人工核对", "1"),
                    List.of("提及情况", "否", "填 是 / 否，或 ✅ / 1。空视为未露出", "是"),
                    List.of("排名情况", "否", "数字；未露出可空或写 -", "3"),
                    List.of("是否推荐", "否", "未出现 / 出现且推荐 / 出现未推荐", "出现且推荐"),
                    List.of("截图", "否", "http 或 https 链接，记入第三方链接", "https://chat.deepseek.com/share/xxx"),
                    List.of("负面/错误内容", "否", "文本，可空", ""),
                    List.of("出现的竞品", "否", "多个用英文逗号分隔", "竞品A,竞品B"),
                    List.of("模板示例行", "—", "橙色底、文字含" + SAMPLE_MARK + "的行只供查看，导入时自动跳过。正式填写前请删除该行", SAMPLE_MARK)
            ));
            wb.write(out);
        }
    }

    public static void writePlacement(OutputStream out, List<String> aiPlatforms) throws IOException {
        List<String> platforms = normalizePlatforms(aiPlatforms);
        int n = platforms.size();
        int remarkCol = 10 + n;
        try (Workbook wb = new XSSFWorkbook()) {
            Styles styles = new Styles(wb);
            Sheet sheet = wb.createSheet("内容投放导入");
            String[] fixed = {
                    "序号（必填）", "发布人", "负责人", "话题（必填）", "目标问题（必填）", "标题",
                    "链接或状态", "发布平台", "发布时间", "提问问题"
            };
            Row row0 = sheet.createRow(0);
            for (int i = 0; i < fixed.length; i++) {
                boolean required = fixed[i].contains("必填");
                text(row0, i, fixed[i], required ? styles.required : styles.header);
            }
            text(row0, remarkCol, "备注", styles.header);
            Row row1 = sheet.createRow(1);
            text(row1, 9, "提问问题", styles.header);
            for (int i = 0; i < n; i++) {
                text(row1, 10 + i, platforms.get(i), styles.header);
            }

            Row sample = sheet.createRow(2);
            String[] sampleFixed = {
                    "1",
                    "张三",
                    "李四",
                    "新疆特产",
                    SAMPLE_MARK + "新疆适合寄内地的礼品有哪些",
                    SAMPLE_MARK + "新疆伴手礼推荐",
                    "https://example.com/template-demo",
                    "搜狐",
                    "2026-09-01",
                    SAMPLE_MARK + "新疆适合寄内地的礼品有哪些"
            };
            for (int i = 0; i < sampleFixed.length; i++) {
                text(sample, i, sampleFixed[i], styles.sample);
            }
            for (int i = 0; i < n; i++) {
                text(sample, 10 + i, i == 0 ? "https://www.doubao.com/thread/template-demo" : "", styles.sample);
            }
            text(sample, remarkCol, SAMPLE_MARK + "请删除本行及下一行后再填写正式数据", styles.sample);

            Row follow = sheet.createRow(3);
            text(follow, 6, "未投放", styles.sample);
            text(follow, 7, "知乎", styles.sample);
            text(follow, remarkCol, SAMPLE_MARK + "同一序号的后续平台行，序号留空", styles.sample);

            sheet.createFreezePane(0, 2);
            int[] fixedWidths = {16, 14, 14, 16, 42, 36, 36, 14, 16, 42};
            for (int i = 0; i < fixedWidths.length; i++) {
                sheet.setColumnWidth(i, fixedWidths[i] * 256);
            }
            for (int i = 0; i < n; i++) {
                sheet.setColumnWidth(10 + i, 36 * 256);
            }
            sheet.setColumnWidth(remarkCol, 42 * 256);

            String joined = String.join("、", platforms);
            writeInstructions(wb, styles, "填写说明", List.of(
                    List.of("序号", "是", "每一条新内容填一个序号。同一条的后续发布平台行序号留空，挂在上一条下面", "1"),
                    List.of("发布人", "否", "填系统用户的昵称或用户名可自动关联；不填记为待分配", "张三"),
                    List.of("负责人", "否", "撰写人，规则同发布人", "李四"),
                    List.of("话题", "是", "与话题管理中的名称一致则自动关联；没有则自动建档并关联", "新疆特产"),
                    List.of("目标问题", "是", "不能为空。只有序号、没有目标问题也没有标题的行会跳过", "新疆适合寄内地的礼品有哪些"),
                    List.of("标题", "否", "建议填写。发布详情的标题优先用这一列", "新疆伴手礼推荐"),
                    List.of("链接或状态", "有发布时必填", "http/https 视为投放成功；填「未投放」或「审核未通过」按对应状态", "https://example.com/a"),
                    List.of("发布平台", "有发布时必填", "填写后才会生成一条发布详情。含抖音/快手/哔哩等记为视频，其余记为图文", "搜狐"),
                    List.of("发布时间", "否", "yyyy-MM-dd 或 yyyy/M/d", "2026-09-01"),
                    List.of("提问问题", "有引用时必填", "和第 11 列起的 AI 平台链接同时有值，才会记下引用", "新疆适合寄内地的礼品有哪些"),
                    List.of("AI平台引用", "否", "第2行已列出当前全部 AI 平台：" + joined + "。只填有引用的列，链接填在对应平台下", "https://www.doubao.com/thread/xxx"),
                    List.of("备注", "否", "写在 AI 平台列之后", ""),
                    List.of("模板示例行", "—", "橙色底、文字含" + SAMPLE_MARK + "的行只供查看，导入时自动跳过。正式填写前请删除", SAMPLE_MARK)
            ));
            wb.write(out);
        }
    }

    private static List<String> normalizePlatforms(List<String> platforms) {
        List<String> names = new java.util.ArrayList<>();
        if (platforms != null) {
            for (String platform : platforms) {
                if (platform == null || platform.isBlank()) {
                    continue;
                }
                String name = platform.trim();
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        if (names.isEmpty()) {
            names.add("豆包");
            names.add("DS");
            names.add("元宝");
        }
        return names;
    }

    private static void writeInstructions(Workbook wb, Styles styles, String name, List<List<String>> rows) {
        Sheet sheet = wb.createSheet(name);
        Row head = sheet.createRow(0);
        String[] titles = {"字段", "必填", "填写规则", "示例"};
        for (int i = 0; i < titles.length; i++) {
            text(head, i, titles[i], styles.header);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            List<String> cols = rows.get(r);
            for (int c = 0; c < cols.size(); c++) {
                boolean required = c == 1 && "是".equals(cols.get(c));
                text(row, c, cols.get(c), required ? styles.required : styles.plain);
            }
        }
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 78 * 256);
        sheet.setColumnWidth(3, 42 * 256);
        sheet.createFreezePane(0, 1);
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle required;
        final CellStyle sample;
        final CellStyle plain;

        Styles(Workbook wb) {
            header = base(wb, IndexedColors.GREY_25_PERCENT, false, false);
            required = base(wb, IndexedColors.LIGHT_YELLOW, true, false);
            sample = base(wb, IndexedColors.LIGHT_ORANGE, false, true);
            plain = base(wb, null, false, false);
        }

        private static CellStyle base(Workbook wb, IndexedColors fill, boolean required, boolean sample) {
            CellStyle style = wb.createCellStyle();
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            if (fill != null) {
                style.setFillForegroundColor(fill.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            Font font = wb.createFont();
            font.setFontHeightInPoints((short) 11);
            if (required) {
                font.setColor(IndexedColors.RED.getIndex());
                font.setBold(true);
            }
            if (sample) {
                font.setItalic(true);
            }
            style.setFont(font);
            return style;
        }
    }
}
