package com.base.admin.service.ecom;

import com.base.admin.domain.enums.EcomReportType;
import com.base.admin.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 按文件名识别平台与报表类型（京东精细规则 + 天猫/抖音约定命名）。 */
public final class EcomFileDetector {

    private static final Pattern SHOP_CODE = Pattern.compile("(?:^|_)(\\d{5,})(?:_|\\.)");
    private static final Pattern RANGE_DASH = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})_(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern RANGE_COMPACT = Pattern.compile("(\\d{8})_(\\d{8})");

    private EcomFileDetector() {
    }

    public static EcomJdFileParser.Detected detect(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名不能为空");
        }
        String name = fileName.trim();
        String lower = name.toLowerCase(Locale.ROOT);

        if (lower.contains("天猫") || lower.startsWith("tmall") || lower.contains("_tmall_")) {
            return detectGeneric(name, "tmall");
        }
        if (lower.contains("抖音") || lower.startsWith("douyin") || lower.startsWith("dy_") || lower.contains("_douyin_")) {
            return detectGeneric(name, "douyin");
        }

        EcomJdFileParser.Detected d = EcomJdFileParser.detect(name);
        if (d.reportType != null) {
            d.platform = d.reportType.getPlatform();
        }
        return d;
    }

    private static EcomJdFileParser.Detected detectGeneric(String name, String platform) {
        EcomJdFileParser.Detected d = new EcomJdFileParser.Detected();
        d.fileName = name;
        d.platform = platform;
        Matcher shop = SHOP_CODE.matcher(name);
        if (shop.find()) {
            d.shopCode = shop.group(1);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("商品明细") || lower.contains("spu") || lower.contains("item_detail")) {
            d.reportType = EcomReportType.fromCode(platform + "_spu_detail");
        } else if (lower.contains("流量") || lower.contains("traffic") || lower.contains("访客")) {
            d.reportType = EcomReportType.fromCode(platform + "_traffic_overview");
        } else if (lower.contains("交易") || lower.contains("成交") || lower.contains("trade") || lower.contains("gmv")) {
            d.reportType = EcomReportType.fromCode(platform + "_trade_overview");
        } else {
            throw new BusinessException("无法识别" + platform + "报表类型。文件名请包含：交易/流量/商品明细，并以平台名开头，例如：天猫_交易概况_123456_2026-09-01_2026-09-07.xlsx");
        }
        Matcher dash = RANGE_DASH.matcher(name);
        if (dash.find()) {
            d.periodStart = LocalDate.parse(dash.group(1));
            d.periodEnd = LocalDate.parse(dash.group(2));
        } else {
            Matcher compact = RANGE_COMPACT.matcher(name);
            if (compact.find()) {
                d.periodStart = LocalDate.parse(compact.group(1), DateTimeFormatter.BASIC_ISO_DATE);
                d.periodEnd = LocalDate.parse(compact.group(2), DateTimeFormatter.BASIC_ISO_DATE);
            }
        }
        if (!StringUtils.hasText(d.shopCode)) {
            throw new BusinessException("文件名需包含店铺数字编码，例如：天猫_交易概况_123456_2026-09-01_2026-09-07.xlsx");
        }
        if (d.reportType == null) {
            throw new BusinessException("报表类型无效");
        }
        return d;
    }
}
