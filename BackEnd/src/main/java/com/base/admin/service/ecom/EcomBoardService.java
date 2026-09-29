package com.base.admin.service.ecom;

import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EcomBoardService {

    private final JdbcTemplate jdbc;
    private final EcomAccessService accessService;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listBoard(String periodType, String platform, Long shopId,
                                               String startDate, String endDate) {
        accessService.assertAccess();
        String type = StringUtils.hasText(periodType) ? periodType.trim() : "week";
        StringBuilder sql = new StringBuilder("""
                SELECT b.*, s.shop_name, s.shop_code,
                       t.target_gmv, t.target_order
                FROM ecom_board_period_stat b
                LEFT JOIN ecom_shop s ON s.id = b.shop_id
                LEFT JOIN ecom_target t ON t.platform = b.platform AND t.shop_id = b.shop_id
                  AND t.period_type = b.period_type AND t.period_key = b.period_key AND t.is_active = 1
                WHERE b.is_active = 1 AND b.period_type = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(type);
        if (StringUtils.hasText(platform) && !"all".equalsIgnoreCase(platform)) {
            sql.append(" AND b.platform = ? ");
            args.add(platform.trim());
        }
        if (shopId != null) {
            sql.append(" AND b.shop_id = ? ");
            args.add(shopId);
        }
        if (StringUtils.hasText(startDate)) {
            sql.append(" AND b.period_end >= ? ");
            args.add(startDate.trim());
        }
        if (StringUtils.hasText(endDate)) {
            sql.append(" AND b.period_start <= ? ");
            args.add(endDate.trim());
        }
        accessService.appendScopeFilter(sql, args, "b");
        sql.append(" ORDER BY b.period_start DESC, b.platform, b.shop_id ");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            BigDecimal gmv = bd(row.get("gmv"));
            BigDecimal target = bd(row.get("target_gmv"));
            if (target.compareTo(BigDecimal.ZERO) > 0) {
                row.put("gmvAchieveRate", gmv.multiply(BigDecimal.valueOf(100))
                        .divide(target, 2, RoundingMode.HALF_UP));
            } else {
                row.put("gmvAchieveRate", null);
            }
        }
        return rows;
    }

    /** 跨平台对比：同一周期按平台汇总 */
    public List<Map<String, Object>> compareByPlatform(String periodType, String periodKey) {
        accessService.assertAccess();
        String type = StringUtils.hasText(periodType) ? periodType.trim() : "week";
        if (!StringUtils.hasText(periodKey)) {
            throw new BusinessException("periodKey 不能为空");
        }
        return jdbc.queryForList("""
                SELECT b.platform,
                       SUM(b.gmv) AS gmv,
                       SUM(b.order_cnt) AS order_cnt,
                       SUM(b.buyer_cnt) AS buyer_cnt,
                       SUM(b.visitor_cnt) AS visitor_cnt,
                       SUM(b.refund_amt) AS refund_amt,
                       MAX(b.period_label) AS period_label,
                       MIN(b.period_start) AS period_start,
                       MAX(b.period_end) AS period_end
                FROM ecom_board_period_stat b
                WHERE b.is_active = 1 AND b.period_type = ? AND b.period_key = ?
                GROUP BY b.platform
                ORDER BY gmv DESC
                """, type, periodKey.trim());
    }

    @Transactional
    public Map<String, Object> persistRange(String periodType, LocalDate start, LocalDate end, Long shopId) {
        accessService.assertAccess();
        String type = "month".equalsIgnoreCase(periodType) ? "month" : "week";
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException("日期区间无效");
        }
        int snapshots = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            LocalDate pStart;
            LocalDate pEnd;
            String key;
            String label;
            if ("month".equals(type)) {
                pStart = cursor.with(TemporalAdjusters.firstDayOfMonth());
                pEnd = cursor.with(TemporalAdjusters.lastDayOfMonth());
                key = pStart.getYear() + "-" + String.format("%02d", pStart.getMonthValue());
                label = pStart.getYear() + "年" + pStart.getMonthValue() + "月";
                cursor = pEnd.plusDays(1);
            } else {
                pStart = cursor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                pEnd = cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                int week = pStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                int weekYear = pStart.get(IsoFields.WEEK_BASED_YEAR);
                key = weekYear + "-W" + String.format("%02d", week);
                label = weekYear + "年第" + week + "周";
                cursor = pEnd.plusDays(1);
            }
            snapshots += upsertPeriod(type, key, label, pStart, pEnd, shopId);
        }
        return Map.of("periodType", type, "snapshotCount", snapshots);
    }

    /** 强制导入后：按涉及日期重算周/月看板 */
    @Transactional
    public Map<String, Object> rebuildForDates(Collection<LocalDate> dates, Long shopId) {
        if (dates == null || dates.isEmpty()) {
            return Map.of("snapshotCount", 0);
        }
        LocalDate min = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate max = dates.stream().max(LocalDate::compareTo).orElseThrow();
        Map<String, Object> week = persistRange("week", min, max, shopId);
        Map<String, Object> month = persistRange("month", min, max, shopId);
        int total = ((Number) week.get("snapshotCount")).intValue()
                + ((Number) month.get("snapshotCount")).intValue();
        return Map.of("snapshotCount", total, "week", week, "month", month);
    }

    private int upsertPeriod(String periodType, String periodKey, String label,
                             LocalDate start, LocalDate end, Long shopIdFilter) {
        String shopFilter = shopIdFilter == null ? "" : " AND shop_id = " + shopIdFilter;
        List<Map<String, Object>> shops = jdbc.queryForList("""
                SELECT DISTINCT platform, shop_id FROM ecom_fact_shop_day
                WHERE is_active = 1 AND biz_date BETWEEN ? AND ? %s
                """.formatted(shopFilter), start, end);
        int n = 0;
        for (Map<String, Object> shop : shops) {
            String platform = String.valueOf(shop.get("platform"));
            Long shopId = ((Number) shop.get("shop_id")).longValue();
            Agg trade = aggMetrics(shopId, start, end, "%_trade_overview");
            Agg traffic = aggMetrics(shopId, start, end, "%_traffic_overview");
            BigDecimal gmv = trade.metric("成交金额");
            BigDecimal orderCnt = trade.metric("成交单量");
            BigDecimal buyerCnt = trade.metric("成交客户数");
            BigDecimal refund = trade.metric("退款金额");
            BigDecimal visitor = traffic.metric("店铺访客数");
            if (visitor.compareTo(BigDecimal.ZERO) == 0) {
                visitor = trade.metric("店铺访客数");
            }
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("tradeKeys", trade.values.keySet());
            String json;
            try {
                json = objectMapper.writeValueAsString(ext);
            } catch (Exception e) {
                json = "{}";
            }
            jdbc.update("""
                    INSERT INTO ecom_board_period_stat
                      (platform, shop_id, period_type, period_key, period_label, period_start, period_end,
                       gmv, order_cnt, buyer_cnt, visitor_cnt, refund_amt, metrics_json, locked_at, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, NOW(), 1)
                    ON DUPLICATE KEY UPDATE
                      period_label = VALUES(period_label),
                      period_start = VALUES(period_start),
                      period_end = VALUES(period_end),
                      gmv = VALUES(gmv),
                      order_cnt = VALUES(order_cnt),
                      buyer_cnt = VALUES(buyer_cnt),
                      visitor_cnt = VALUES(visitor_cnt),
                      refund_amt = VALUES(refund_amt),
                      metrics_json = VALUES(metrics_json),
                      locked_at = NOW(),
                      is_active = 1
                    """, platform, shopId, periodType, periodKey, label, start, end,
                    gmv, orderCnt, buyerCnt, visitor, refund, json);
            n++;
        }
        return n;
    }

    private Agg aggMetrics(Long shopId, LocalDate start, LocalDate end, String reportLike) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT metrics_json FROM ecom_fact_shop_day
                WHERE shop_id = ? AND biz_date BETWEEN ? AND ? AND is_active = 1
                  AND report_type LIKE ?
                """, shopId, start, end, reportLike);
        Agg agg = new Agg();
        for (Map<String, Object> row : rows) {
            try {
                Map<String, Object> m = objectMapper.readValue(String.valueOf(row.get("metrics_json")), Map.class);
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    agg.add(e.getKey(), e.getValue());
                }
            } catch (Exception ignored) {
            }
        }
        return agg;
    }

    private static BigDecimal bd(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static final class Agg {
        private final Map<String, BigDecimal> values = new LinkedHashMap<>();

        void add(String key, Object raw) {
            if (key == null || raw == null) {
                return;
            }
            BigDecimal n;
            try {
                n = new BigDecimal(String.valueOf(raw).replace(",", "").replace("%", ""));
            } catch (Exception e) {
                return;
            }
            values.merge(key, n, BigDecimal::add);
        }

        BigDecimal metric(String key) {
            return values.getOrDefault(key, BigDecimal.ZERO);
        }
    }
}
