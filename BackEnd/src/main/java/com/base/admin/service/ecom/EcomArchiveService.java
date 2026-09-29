package com.base.admin.service.ecom;

import com.base.admin.domain.dto.EcomArchiveWeekDTO;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EcomArchiveService {

    private final JdbcTemplate jdbc;
    private final EcomAccessService accessService;

    @Transactional
    public Map<String, Object> archiveWeek(EcomArchiveWeekDTO dto) {
        accessService.assertAccess();
        String platform = StringUtils.hasText(dto.getPlatform()) ? dto.getPlatform().trim() : "jd";
        LocalDate anchor = StringUtils.hasText(dto.getAnchorDate())
                ? LocalDate.parse(dto.getAnchorDate().trim())
                : LocalDate.now();
        LocalDate start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        int week = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int weekYear = start.get(IsoFields.WEEK_BASED_YEAR);
        String periodKey = weekYear + "-W" + String.format("%02d", week);
        String label = weekYear + "年第" + week + "周";

        accessService.assertPlatformAccess(platform);
        List<Long> shopIds;
        if (dto.getShopId() != null) {
            accessService.assertShopAccess(dto.getShopId());
            shopIds = List.of(dto.getShopId());
        } else {
            StringBuilder sql = new StringBuilder("SELECT id FROM ecom_shop s WHERE s.is_active = 1 AND s.platform = ? ");
            List<Object> args = new ArrayList<>();
            args.add(platform);
            accessService.appendScopeFilter(sql, args, "s", "id");
            shopIds = jdbc.query(sql.toString(), (rs, i) -> rs.getLong(1), args.toArray());
        }
        if (shopIds.isEmpty()) {
            throw new BusinessException("没有可归档的店铺，请先导入数据自动建店");
        }

        String by = accessService.currentUsername();
        int lockedDaily = 0;
        for (Long shopId : shopIds) {
            jdbc.update("""
                    INSERT INTO ecom_stat_period
                      (platform, shop_id, period_type, period_key, period_label, period_start, period_end, archived_by, is_active)
                    VALUES (?,?, 'week', ?,?,?,?,?,1)
                    ON DUPLICATE KEY UPDATE
                      period_label = VALUES(period_label),
                      period_start = VALUES(period_start),
                      period_end = VALUES(period_end),
                      archived_by = VALUES(archived_by),
                      archived_at = CURRENT_TIMESTAMP,
                      is_active = 1
                    """, platform, shopId, periodKey, label, start, end, by);

            lockedDaily += jdbc.update("""
                    UPDATE ecom_fact_shop_day SET stat_locked = 1
                    WHERE shop_id = ? AND biz_date BETWEEN ? AND ? AND is_active = 1
                    """, shopId, start, end);
            lockedDaily += jdbc.update("""
                    UPDATE ecom_fact_traffic_source SET stat_locked = 1
                    WHERE shop_id = ? AND biz_date BETWEEN ? AND ? AND is_active = 1
                    """, shopId, start, end);
            lockedDaily += jdbc.update("""
                    UPDATE ecom_fact_spu_day SET stat_locked = 1
                    WHERE shop_id = ? AND biz_date BETWEEN ? AND ? AND is_active = 1
                    """, shopId, start, end);
            lockedDaily += jdbc.update("""
                    UPDATE ecom_fact_ad_effect SET stat_locked = 1
                    WHERE shop_id = ? AND click_date BETWEEN ? AND ? AND is_active = 1
                    """, shopId, start, end);
            lockedDaily += jdbc.update("""
                    UPDATE ecom_fact_ad_plan SET stat_locked = 1
                    WHERE shop_id = ? AND (
                      (period_start IS NOT NULL AND period_end IS NOT NULL
                        AND period_start <= ? AND period_end >= ?)
                      OR (period_end BETWEEN ? AND ?)
                    ) AND is_active = 1
                    """, shopId, end, start, start, end);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("periodKey", periodKey);
        result.put("periodLabel", label);
        result.put("periodStart", start.toString());
        result.put("periodEnd", end.toString());
        result.put("shopCount", shopIds.size());
        result.put("lockedRowCount", lockedDaily);
        return result;
    }
}
