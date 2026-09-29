package com.base.admin.service.ecom;

import com.base.admin.domain.dto.EcomTargetSaveDTO;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EcomTargetService {

    private final JdbcTemplate jdbc;
    private final EcomAccessService accessService;

    public List<Map<String, Object>> list(String platform, Long shopId, String periodType) {
        accessService.assertAccess();
        StringBuilder sql = new StringBuilder("""
                SELECT t.*, s.shop_name FROM ecom_target t
                LEFT JOIN ecom_shop s ON s.id = t.shop_id
                WHERE t.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(platform)) {
            sql.append(" AND t.platform = ? ");
            args.add(platform.trim());
        }
        if (shopId != null) {
            sql.append(" AND t.shop_id = ? ");
            args.add(shopId);
        }
        if (StringUtils.hasText(periodType)) {
            sql.append(" AND t.period_type = ? ");
            args.add(periodType.trim());
        }
        accessService.appendScopeFilter(sql, args, "t");
        sql.append(" ORDER BY t.period_key DESC, t.platform, t.shop_id ");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public void save(EcomTargetSaveDTO dto) {
        accessService.assertAccess();
        if (dto == null || !StringUtils.hasText(dto.getPeriodType()) || !StringUtils.hasText(dto.getPeriodKey())) {
            throw new BusinessException("周期类型与周期键不能为空");
        }
        String platform = StringUtils.hasText(dto.getPlatform()) ? dto.getPlatform().trim() : "jd";
        long shopId = dto.getShopId() == null ? 0L : dto.getShopId();
        if (!"all".equalsIgnoreCase(platform)) {
            accessService.assertPlatformAccess(platform);
        }
        if (shopId > 0) {
            accessService.assertShopAccess(shopId);
        }
        String label = StringUtils.hasText(dto.getPeriodLabel()) ? dto.getPeriodLabel().trim() : dto.getPeriodKey().trim();
        BigDecimal gmv = dto.getTargetGmv() == null ? BigDecimal.ZERO : dto.getTargetGmv();
        BigDecimal order = dto.getTargetOrder() == null ? BigDecimal.ZERO : dto.getTargetOrder();
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        String by = accessService.currentUsername();
        jdbc.update("""
                INSERT INTO ecom_target
                  (platform, shop_id, period_type, period_key, period_label, target_gmv, target_order, remark, create_by, is_active)
                VALUES (?,?,?,?,?,?,?,?,?,1)
                ON DUPLICATE KEY UPDATE
                  period_label = VALUES(period_label),
                  target_gmv = VALUES(target_gmv),
                  target_order = VALUES(target_order),
                  remark = VALUES(remark),
                  update_by = VALUES(create_by),
                  is_active = 1
                """, platform, shopId, dto.getPeriodType().trim(), dto.getPeriodKey().trim(), label,
                gmv, order, remark, by);
    }

    @Transactional
    public void remove(Long id) {
        accessService.assertAccess();
        if (id == null) {
            throw new BusinessException("id 不能为空");
        }
        jdbc.update("UPDATE ecom_target SET is_active = 0 WHERE id = ?", id);
    }
}
