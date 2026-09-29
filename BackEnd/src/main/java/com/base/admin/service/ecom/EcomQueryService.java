package com.base.admin.service.ecom;

import com.base.admin.common.PageResult;
import com.base.admin.domain.enums.EcomReportType;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EcomQueryService {

    private final JdbcTemplate jdbc;
    private final EcomAccessService accessService;
    private final ObjectMapper objectMapper;

    public PageResult<Map<String, Object>> pageFacts(String reportType, Long shopId, String platform,
                                                     String startDate, String endDate,
                                                     Integer pageNum, Integer pageSize) {
        accessService.assertAccess();
        EcomReportType type = EcomReportType.fromCode(reportType);
        if (type == null) {
            throw new BusinessException("未知报表类型");
        }
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        int offset = (pn - 1) * ps;

        String table;
        String dateCol;
        String extraSelect;
        StringBuilder where = new StringBuilder(" WHERE f.is_active = 1 ");
        List<Object> args = new ArrayList<>();

        switch (type.getGrain()) {
            case "shop_day" -> {
                table = "ecom_fact_shop_day";
                dateCol = "biz_date";
                extraSelect = "";
                where.append(" AND f.report_type = ? ");
                args.add(type.getCode());
            }
            case "traffic_source" -> {
                table = "ecom_fact_traffic_source";
                dateCol = "biz_date";
                extraSelect = "f.channel_l1, f.channel_l2, f.channel_l3, f.channel_l4";
            }
            case "spu_day" -> {
                table = "ecom_fact_spu_day";
                dateCol = "biz_date";
                extraSelect = "f.spu, f.spu_name, f.cate_l1, f.cate_l2, f.cate_l3, f.goods_no";
            }
            case "ad_plan" -> {
                table = "ecom_fact_ad_plan";
                dateCol = "period_end";
                extraSelect = "f.plan_id, f.plan_name, f.plan_type, f.period_start, f.period_end";
            }
            case "ad_effect" -> {
                table = "ecom_fact_ad_effect";
                dateCol = "click_date";
                extraSelect = "f.plan_id, f.plan_name, f.click_date";
            }
            default -> throw new BusinessException("不支持的粒度");
        }

        if (shopId != null) {
            where.append(" AND f.shop_id = ? ");
            args.add(shopId);
        }
        if (StringUtils.hasText(platform)) {
            where.append(" AND f.platform = ? ");
            args.add(platform.trim());
        }
        if (StringUtils.hasText(startDate)) {
            where.append(" AND f.").append(dateCol).append(" >= ? ");
            args.add(startDate.trim());
        }
        if (StringUtils.hasText(endDate)) {
            where.append(" AND f.").append(dateCol).append(" <= ? ");
            args.add(endDate.trim());
        }
        accessService.appendScopeFilter(where, args, "f");

        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM " + table + " f " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(ps);
        pageArgs.add(offset);
        String reportTypeExpr = "shop_day".equals(type.getGrain())
                ? "f.report_type"
                : "COALESCE(b.report_type, '" + type.getCode().replace("'", "") + "')";
        String extraCols = StringUtils.hasText(extraSelect) ? ", " + extraSelect : "";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT f.id, f.platform, f.shop_id, s.shop_name, s.shop_code,
                       f.%s AS biz_date, f.stat_locked, f.metrics_json, f.import_batch_id, f.update_time,
                       %s AS report_type,
                       COALESCE(b.create_time, f.create_time) AS import_time,
                       COALESCE(NULLIF(b.create_by, ''), '') AS import_by
                       %s
                FROM %s f
                LEFT JOIN ecom_shop s ON s.id = f.shop_id
                LEFT JOIN ecom_import_batch b ON b.id = f.import_batch_id
                %s
                ORDER BY f.%s DESC, f.id DESC
                LIMIT ? OFFSET ?
                """.formatted(dateCol, reportTypeExpr, extraCols, table, where, dateCol), pageArgs.toArray());

        for (Map<String, Object> row : rows) {
            Object json = row.get("metrics_json");
            row.put("metrics", parseMetrics(json));
            row.remove("metrics_json");
            if (row.get("report_type") == null) {
                row.put("report_type", type.getCode());
            }
            EcomReportType labeled = EcomReportType.fromCode(String.valueOf(row.get("report_type")));
            row.put("report_type_label", labeled != null ? labeled.getLabel() : type.getLabel());
        }
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    private Map<String, Object> parseMetrics(Object json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(json), new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
