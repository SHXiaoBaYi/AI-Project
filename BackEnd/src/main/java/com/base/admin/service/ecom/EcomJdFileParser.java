package com.base.admin.service.ecom;

import com.base.admin.domain.enums.EcomReportType;
import com.base.admin.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 京东导出文件识别与解析（只保留主指标列）。 */
public final class EcomJdFileParser {

    private static final Pattern SHOP_CODE = Pattern.compile("^(\\d{5,})_");
    private static final Pattern RANGE_DASH = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})_(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern RANGE_COMPACT = Pattern.compile("(\\d{8})_(\\d{8})");
    private static final Pattern USER_INSIGHT = Pattern.compile("用户洞察-数据概览-(\\d{4}\\.\\d{2}\\.\\d{2})");
    private static final Pattern SHOP_NAME_CSV = Pattern.compile("^(.+?)_(全站营销|报表中心)_");

    private EcomJdFileParser() {
    }

    public static boolean isCompareOrYoyColumn(String header) {
        if (!StringUtils.hasText(header)) {
            return true;
        }
        String h = header.trim();
        return h.endsWith("-对比日") || h.endsWith("-较对比日") || h.endsWith("同比") || h.contains("同比");
    }

    public static Detected detect(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名不能为空");
        }
        String name = fileName.trim();
        Detected d = new Detected();
        d.fileName = name;
        d.platform = "jd";

        Matcher shop = SHOP_CODE.matcher(name);
        if (shop.find()) {
            d.shopCode = shop.group(1);
        }
        Matcher shopName = SHOP_NAME_CSV.matcher(name);
        if (shopName.find()) {
            d.shopName = shopName.group(1);
        }

        if (name.contains("交易概况")) {
            d.reportType = EcomReportType.JD_TRADE_OVERVIEW;
        } else if (name.contains("商品概况")) {
            d.reportType = EcomReportType.JD_SPU_OVERVIEW;
        } else if (name.contains("流量概况")) {
            d.reportType = EcomReportType.JD_TRAFFIC_OVERVIEW;
        } else if (name.contains("流量来源") || name.contains("场域来源")) {
            d.reportType = EcomReportType.JD_TRAFFIC_SOURCE;
        } else if (name.contains("商品明细")) {
            d.reportType = EcomReportType.JD_SPU_DETAIL;
        } else if (name.contains("用户洞察")) {
            d.reportType = EcomReportType.JD_USER_INSIGHT;
            Matcher ui = USER_INSIGHT.matcher(name);
            if (ui.find()) {
                d.periodStart = parseFlexibleDate(ui.group(1));
                d.periodEnd = d.periodStart;
            }
        } else if (name.contains("单品推广")) {
            d.reportType = EcomReportType.JD_AD_SKU_PLAN;
        } else if (name.contains("报表中心") && name.contains("计划")) {
            d.reportType = EcomReportType.JD_AD_PLAN_EFFECT;
        } else {
            throw new BusinessException("无法识别报表类型，请使用京东原始导出文件名：" + name);
        }

        Matcher rd = RANGE_DASH.matcher(name);
        if (rd.find()) {
            d.periodStart = LocalDate.parse(rd.group(1));
            d.periodEnd = LocalDate.parse(rd.group(2));
        } else {
            Matcher rc = RANGE_COMPACT.matcher(name);
            if (rc.find()) {
                d.periodStart = parseCompact(rc.group(1));
                d.periodEnd = parseCompact(rc.group(2));
            }
        }
        return d;
    }

    public static ParsedTable parseTable(byte[] bytes, String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(bytes);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseXlsx(bytes);
        }
        throw new BusinessException("仅支持 xlsx / csv 文件");
    }

    private static ParsedTable parseXlsx(byte[] bytes) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException("Excel 表头为空");
            }
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(fmt.formatCellValue(headerRow.getCell(c)).trim());
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < headers.size(); c++) {
                    String h = headers.get(c);
                    if (!StringUtils.hasText(h)) {
                        continue;
                    }
                    String v = fmt.formatCellValue(row.getCell(c)).trim();
                    if (StringUtils.hasText(v)) {
                        any = true;
                    }
                    map.put(h, v);
                }
                if (any) {
                    rows.add(map);
                }
            }
            ParsedTable t = new ParsedTable();
            t.headers = headers;
            t.rows = rows;
            return t;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("解析 Excel 失败：" + e.getMessage());
        }
    }

    private static ParsedTable parseCsv(byte[] bytes) {
        Charset cs = detectCharset(bytes);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), cs))) {
            String headerLine = br.readLine();
            if (!StringUtils.hasText(headerLine)) {
                throw new BusinessException("CSV 表头为空");
            }
            List<String> headers = splitCsvLine(headerLine);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (!StringUtils.hasText(line.trim())) {
                    continue;
                }
                List<String> cols = splitCsvLine(line);
                Map<String, String> map = new LinkedHashMap<>();
                boolean any = false;
                for (int i = 0; i < headers.size(); i++) {
                    String h = headers.get(i);
                    String v = i < cols.size() ? cols.get(i).trim() : "";
                    if (StringUtils.hasText(v)) {
                        any = true;
                    }
                    map.put(h, v);
                }
                if (any) {
                    rows.add(map);
                }
            }
            ParsedTable t = new ParsedTable();
            t.headers = headers;
            t.rows = rows;
            return t;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("解析 CSV 失败：" + e.getMessage());
        }
    }

    private static Charset detectCharset(byte[] bytes) {
        // 优先 GBK（京东广告导出常见），若解码后含大量替换符则回退 UTF-8
        String gbk = new String(bytes, 0, Math.min(bytes.length, 4096), Charset.forName("GBK"));
        if (gbk.contains("\uFFFD")) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName("GBK");
    }

    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (ch == ',' && !inQuote) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }

    public static Map<String, Object> mainMetrics(Map<String, String> row) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : row.entrySet()) {
            String h = e.getKey();
            if (!StringUtils.hasText(h) || isCompareOrYoyColumn(h)) {
                continue;
            }
            if (isDimensionHeader(h)) {
                continue;
            }
            String raw = e.getValue();
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            metrics.put(h.trim(), coerceNumber(raw.trim()));
        }
        return metrics;
    }

    public static boolean isDimensionHeader(String h) {
        return Set.of("时间", "日期", "点击时间", "SPU", "SPU名称", "一级类目", "二级类目", "三级类目", "货号",
                "一级渠道", "二级渠道", "三级渠道", "四级渠道",
                "计划类型", "商品计划名称", "ID", "商品图片", "状态", "创建时间", "出价方式",
                "计划ID", "推广计划").contains(h.trim());
    }

    public static LocalDate parseFlexibleDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String v = raw.trim()
                .replace('/', '-')
                .replace('.', '-');
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy-M-d"),
                DateTimeFormatter.ofPattern("yyyyMMdd"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        for (DateTimeFormatter f : fmts) {
            try {
                if (f.toString().contains("HH")) {
                    return java.time.LocalDateTime.parse(v.length() > 19 ? v.substring(0, 19) : v, f).toLocalDate();
                }
                if (v.length() >= 10 && f == DateTimeFormatter.ISO_LOCAL_DATE) {
                    return LocalDate.parse(v.substring(0, 10));
                }
                return LocalDate.parse(v.length() > 10 && !v.contains("-") ? v.substring(0, 8) : v, f);
            } catch (DateTimeParseException ignored) {
            }
        }
        // 尝试截取前 10 位
        if (v.length() >= 10) {
            try {
                return LocalDate.parse(v.substring(0, 10));
            } catch (Exception ignored) {
            }
        }
        throw new BusinessException("无法解析日期：" + raw);
    }

    private static LocalDate parseCompact(String yyyymmdd) {
        return LocalDate.parse(yyyymmdd, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static Object coerceNumber(String raw) {
        String v = raw.replace(",", "").replace("%", "").trim();
        if (v.isEmpty() || "-".equals(v)) {
            return raw;
        }
        try {
            if (v.contains(".")) {
                return Double.parseDouble(v);
            }
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    public static class Detected {
        public String fileName;
        public String platform;
        public String shopCode;
        public String shopName;
        public EcomReportType reportType;
        public LocalDate periodStart;
        public LocalDate periodEnd;
    }

    public static class ParsedTable {
        public List<String> headers = List.of();
        public List<Map<String, String>> rows = List.of();
    }
}
