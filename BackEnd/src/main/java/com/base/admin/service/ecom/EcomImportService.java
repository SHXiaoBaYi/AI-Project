package com.base.admin.service.ecom;

import com.base.admin.domain.enums.EcomReportType;
import com.base.admin.domain.vo.EcomImportConflictVO;
import com.base.admin.domain.vo.EcomImportResultVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.GeoImportProgress;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EcomImportService {

    private final JdbcTemplate jdbc;
    private final EcomAccessService accessService;
    private final EcomBoardService boardService;
    private final ObjectMapper objectMapper;

    @Transactional
    public EcomImportResultVO importFile(byte[] bytes, String fileName, boolean ignoreLocked, boolean forceUpdate) {
        accessService.assertAccess();
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("请上传文件");
        }
        EcomJdFileParser.Detected detected = EcomFileDetector.detect(fileName);
        EcomJdFileParser.ParsedTable table = EcomJdFileParser.parseTable(bytes, fileName);
        if (table.rows.isEmpty()) {
            throw new BusinessException("文件没有可导入的数据行");
        }

        Long shopId = ensureShop(detected);
        accessService.assertPlatformAccess(detected.platform);
        accessService.assertShopAccess(shopId);
        String shopName = loadShopName(shopId);

        List<PreparedRow> prepared = prepareRows(detected.reportType, table.rows, detected);
        GeoImportProgress.report(0, prepared.size());

        List<EcomImportConflictVO> locked = new ArrayList<>();
        List<PreparedRow> writable = new ArrayList<>();
        for (PreparedRow row : prepared) {
            if (isLocked(detected.reportType, shopId, row)) {
                EcomImportConflictVO c = new EcomImportConflictVO();
                c.setBizDate(row.lockDate == null ? "" : row.lockDate.toString());
                c.setDimension(row.dimension);
                c.setReason("该记录所在日期已归档，不可覆盖");
                locked.add(c);
                if (forceUpdate) {
                    writable.add(row);
                }
            } else {
                writable.add(row);
            }
        }

        EcomImportResultVO result = baseResult(detected, shopId, shopName);
        result.setTotalRows(prepared.size());
        result.setLockedConflicts(locked);

        if (!locked.isEmpty() && !ignoreLocked && !forceUpdate) {
            result.setNeedConfirm(true);
            result.setMessage("存在已归档数据，请选择跳过锁行或强制覆盖并更新统计");
            result.setSkippedLockedRows(locked.size());
            return result;
        }

        long batchId = insertBatch(detected, shopId, fileName,
                forceUpdate ? "force_archive" : (ignoreLocked ? "skip_locked" : "overwrite"),
                prepared.size());

        int success = 0;
        int fail = 0;
        int forced = 0;
        for (int i = 0; i < writable.size(); i++) {
            PreparedRow row = writable.get(i);
            try {
                boolean wasLocked = locked.stream().anyMatch(c ->
                        Objects.equals(c.getBizDate(), row.lockDate == null ? "" : row.lockDate.toString())
                                && Objects.equals(c.getDimension(), row.dimension));
                upsertRow(detected.reportType, shopId, detected.platform, row, batchId);
                success++;
                if (wasLocked && forceUpdate) {
                    forced++;
                }
            } catch (Exception e) {
                fail++;
            }
            GeoImportProgress.report(i + 1, writable.size());
        }

        int skipped = ignoreLocked ? locked.size() : 0;
        if (forceUpdate) {
            skipped = 0;
        } else if (!ignoreLocked) {
            skipped = 0;
        }

        // ignoreLocked：锁行未写入，计入 skipped
        if (ignoreLocked && !forceUpdate) {
            skipped = locked.size();
        }

        String skippedJson = toJson(locked);
        jdbc.update("""
                UPDATE ecom_import_batch
                SET status = ?, success_rows = ?, skipped_rows = ?, fail_rows = ?,
                    skipped_detail = ?, message = ?, import_mode = ?
                WHERE id = ?
                """,
                fail > 0 && success == 0 ? "failed" : (skipped > 0 ? "partial" : "success"),
                success, skipped, fail, skippedJson,
                buildMessage(success, skipped, fail, forced),
                forceUpdate ? "force_archive" : (ignoreLocked ? "skip_locked" : "overwrite"),
                batchId);

        result.setNeedConfirm(false);
        result.setBatchId(batchId);
        result.setSuccessRows(success);
        result.setSkippedLockedRows(skipped);
        result.setFailRows(fail);
        result.setForcedRows(forced);
        String msg = buildMessage(success, skipped, fail, forced);
        if (forceUpdate && success > 0) {
            Set<LocalDate> dates = new HashSet<>();
            for (PreparedRow row : writable) {
                if (row.lockDate != null) {
                    dates.add(row.lockDate);
                }
            }
            Map<String, Object> board = boardService.rebuildForDates(dates, shopId);
            msg = msg + "；已重算看板快照 " + board.getOrDefault("snapshotCount", 0) + " 条";
            jdbc.update("UPDATE ecom_import_batch SET message = ? WHERE id = ?", msg, batchId);
        }
        result.setMessage(msg);
        if (skipped > 0) {
            result.setLockedConflicts(locked);
        } else if (!forceUpdate) {
            result.setLockedConflicts(List.of());
        }
        return result;
    }

    private List<PreparedRow> prepareRows(EcomReportType type, List<Map<String, String>> rows,
                                          EcomJdFileParser.Detected detected) {
        List<PreparedRow> list = new ArrayList<>();
        for (Map<String, String> raw : rows) {
            PreparedRow row = new PreparedRow();
            row.metrics = EcomJdFileParser.mainMetrics(raw);
            switch (type.getGrain()) {
                case "shop_day" -> {
                    String dateRaw = first(raw, "时间", "日期");
                    row.lockDate = StringUtils.hasText(dateRaw)
                            ? EcomJdFileParser.parseFlexibleDate(dateRaw)
                            : detected.periodStart;
                    row.dimension = type.getCode().contains("user") ? "用户洞察" : "店铺日汇总";
                }
                case "traffic_source" -> {
                    row.lockDate = EcomJdFileParser.parseFlexibleDate(first(raw, "时间", "日期"));
                    row.channelL1 = nz(raw.get("一级渠道"));
                    row.channelL2 = nz(raw.get("二级渠道"));
                    row.channelL3 = nz(raw.get("三级渠道"));
                    row.channelL4 = nz(raw.get("四级渠道"));
                    row.dimension = String.join("/", row.channelL1, row.channelL2, row.channelL3, row.channelL4);
                }
                case "spu_day" -> {
                    row.lockDate = EcomJdFileParser.parseFlexibleDate(first(raw, "时间", "日期"));
                    row.spu = nz(first(raw, "SPU", "商品ID", "商品编码"));
                    row.spuName = nz(first(raw, "SPU名称", "商品名称", "商品标题"));
                    row.cateL1 = nz(raw.get("一级类目"));
                    row.cateL2 = nz(raw.get("二级类目"));
                    row.cateL3 = nz(raw.get("三级类目"));
                    row.goodsNo = nz(first(raw, "货号", "商家编码"));
                    row.dimension = row.spu + " " + row.spuName;
                }
                case "ad_plan" -> {
                    row.lockDate = detected.periodEnd != null ? detected.periodEnd : detected.periodStart;
                    row.periodStart = detected.periodStart;
                    row.periodEnd = detected.periodEnd;
                    row.planId = nz(first(raw, "ID", "计划ID"));
                    row.planName = nz(first(raw, "商品计划名称", "推广计划"));
                    row.planType = nz(raw.get("计划类型"));
                    row.dimension = row.planId + " " + row.planName;
                }
                case "ad_effect" -> {
                    row.lockDate = EcomJdFileParser.parseFlexibleDate(first(raw, "点击时间", "时间", "日期"));
                    row.planId = nz(first(raw, "计划ID", "ID"));
                    row.planName = nz(first(raw, "推广计划", "商品计划名称"));
                    row.dimension = row.planId + " " + row.planName;
                }
                default -> throw new BusinessException("不支持的报表类型");
            }
            if (row.lockDate == null && detected.periodStart != null) {
                row.lockDate = detected.periodStart;
            }
            if (row.lockDate == null) {
                throw new BusinessException("无法解析业务日期，请检查文件内容");
            }
            list.add(row);
        }
        return list;
    }

    private boolean isLocked(EcomReportType type, Long shopId, PreparedRow row) {
        Integer locked = switch (type.getGrain()) {
            case "shop_day" -> jdbc.query(
                    """
                            SELECT stat_locked FROM ecom_fact_shop_day
                            WHERE shop_id = ? AND report_type = ? AND biz_date = ? AND is_active = 1 LIMIT 1
                            """,
                    rs -> rs.next() ? rs.getInt(1) : 0, shopId, type.getCode(), row.lockDate);
            case "traffic_source" -> jdbc.query(
                    """
                            SELECT stat_locked FROM ecom_fact_traffic_source
                            WHERE shop_id = ? AND biz_date = ? AND channel_l1 = ? AND channel_l2 = ?
                              AND channel_l3 = ? AND channel_l4 = ? AND is_active = 1 LIMIT 1
                            """,
                    rs -> rs.next() ? rs.getInt(1) : 0,
                    shopId, row.lockDate, row.channelL1, row.channelL2, row.channelL3, row.channelL4);
            case "spu_day" -> jdbc.query(
                    """
                            SELECT stat_locked FROM ecom_fact_spu_day
                            WHERE shop_id = ? AND biz_date = ? AND spu = ? AND is_active = 1 LIMIT 1
                            """,
                    rs -> rs.next() ? rs.getInt(1) : 0, shopId, row.lockDate, row.spu);
            case "ad_plan" -> jdbc.query(
                    """
                            SELECT stat_locked FROM ecom_fact_ad_plan
                            WHERE shop_id = ? AND plan_id = ? AND period_start <=> ? AND period_end <=> ?
                              AND is_active = 1 LIMIT 1
                            """,
                    rs -> rs.next() ? rs.getInt(1) : 0, shopId, row.planId, row.periodStart, row.periodEnd);
            case "ad_effect" -> jdbc.query(
                    """
                            SELECT stat_locked FROM ecom_fact_ad_effect
                            WHERE shop_id = ? AND click_date = ? AND plan_id = ? AND is_active = 1 LIMIT 1
                            """,
                    rs -> rs.next() ? rs.getInt(1) : 0, shopId, row.lockDate, row.planId);
            default -> 0;
        };
        return locked != null && locked == 1;
    }

    private void upsertRow(EcomReportType type, Long shopId, String platform, PreparedRow row,
                           long batchId) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(row.metrics);
        switch (type.getGrain()) {
            case "shop_day" -> jdbc.update("""
                    INSERT INTO ecom_fact_shop_day
                      (platform, shop_id, report_type, biz_date, metrics_json, stat_locked, import_batch_id, is_active)
                    VALUES (?,?,?,?,?,0,?,1)
                    ON DUPLICATE KEY UPDATE
                      metrics_json = VALUES(metrics_json),
                      import_batch_id = VALUES(import_batch_id),
                      update_time = CURRENT_TIMESTAMP,
                      is_active = 1
                    """, platform, shopId, type.getCode(), row.lockDate, json, batchId);
            case "traffic_source" -> jdbc.update("""
                    INSERT INTO ecom_fact_traffic_source
                      (platform, shop_id, biz_date, channel_l1, channel_l2, channel_l3, channel_l4,
                       metrics_json, stat_locked, import_batch_id, is_active)
                    VALUES (?,?,?,?,?,?,?,?,0,?,1)
                    ON DUPLICATE KEY UPDATE
                      metrics_json = VALUES(metrics_json),
                      import_batch_id = VALUES(import_batch_id),
                      update_time = CURRENT_TIMESTAMP,
                      is_active = 1
                    """, platform, shopId, row.lockDate, row.channelL1, row.channelL2, row.channelL3, row.channelL4,
                    json, batchId);
            case "spu_day" -> jdbc.update("""
                    INSERT INTO ecom_fact_spu_day
                      (platform, shop_id, biz_date, spu, spu_name, cate_l1, cate_l2, cate_l3, goods_no,
                       metrics_json, stat_locked, import_batch_id, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,0,?,1)
                    ON DUPLICATE KEY UPDATE
                      spu_name = VALUES(spu_name), cate_l1 = VALUES(cate_l1), cate_l2 = VALUES(cate_l2),
                      cate_l3 = VALUES(cate_l3), goods_no = VALUES(goods_no),
                      metrics_json = VALUES(metrics_json),
                      import_batch_id = VALUES(import_batch_id),
                      update_time = CURRENT_TIMESTAMP,
                      is_active = 1
                    """, platform, shopId, row.lockDate, row.spu, row.spuName, row.cateL1, row.cateL2, row.cateL3,
                    row.goodsNo, json, batchId);
            case "ad_plan" -> jdbc.update("""
                    INSERT INTO ecom_fact_ad_plan
                      (platform, shop_id, plan_id, plan_name, plan_type, period_start, period_end,
                       metrics_json, stat_locked, import_batch_id, is_active)
                    VALUES (?,?,?,?,?,?,?,?,0,?,1)
                    ON DUPLICATE KEY UPDATE
                      plan_name = VALUES(plan_name), plan_type = VALUES(plan_type),
                      metrics_json = VALUES(metrics_json),
                      import_batch_id = VALUES(import_batch_id),
                      update_time = CURRENT_TIMESTAMP,
                      is_active = 1
                    """, platform, shopId, row.planId, row.planName, row.planType, row.periodStart, row.periodEnd,
                    json, batchId);
            case "ad_effect" -> jdbc.update("""
                    INSERT INTO ecom_fact_ad_effect
                      (platform, shop_id, click_date, plan_id, plan_name,
                       metrics_json, stat_locked, import_batch_id, is_active)
                    VALUES (?,?,?,?,?,?,0,?,1)
                    ON DUPLICATE KEY UPDATE
                      plan_name = VALUES(plan_name),
                      metrics_json = VALUES(metrics_json),
                      import_batch_id = VALUES(import_batch_id),
                      update_time = CURRENT_TIMESTAMP,
                      is_active = 1
                    """, platform, shopId, row.lockDate, row.planId, row.planName, json, batchId);
            default -> throw new BusinessException("不支持的报表粒度");
        }
    }

    private Long ensureShop(EcomJdFileParser.Detected detected) {
        String platform = detected.platform;
        String code = detected.shopCode;
        String name = StringUtils.hasText(detected.shopName) ? detected.shopName : "";
        if (!StringUtils.hasText(code)) {
            if (StringUtils.hasText(name)) {
                code = "name:" + name;
            } else if (detected.reportType == EcomReportType.JD_USER_INSIGHT) {
                code = "jd-default";
                name = StringUtils.hasText(name) ? name : "京东默认店";
            } else {
                throw new BusinessException("文件名无法识别店铺编码，请使用带店铺ID的京东导出文件");
            }
        }
        Long id = jdbc.query("""
                SELECT id FROM ecom_shop WHERE platform = ? AND shop_code = ? AND is_active = 1 LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, platform, code);
        if (id != null) {
            if (StringUtils.hasText(detected.shopName)) {
                jdbc.update("UPDATE ecom_shop SET shop_name = ? WHERE id = ? AND (shop_name = '' OR shop_name IS NULL)",
                        detected.shopName, id);
            }
            return id;
        }
        KeyHolder keys = new GeneratedKeyHolder();
        String shopName = StringUtils.hasText(name) ? name : code;
        String by = accessService.currentUsername();
        String finalCode = code;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO ecom_shop (platform, shop_code, shop_name, create_by, is_active)
                    VALUES (?,?,?,?,1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, platform);
            ps.setString(2, finalCode);
            ps.setString(3, shopName);
            ps.setString(4, by);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new BusinessException("自动建店失败");
        }
        return key.longValue();
    }

    private String loadShopName(Long shopId) {
        return jdbc.query("SELECT shop_name FROM ecom_shop WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : "", shopId);
    }

    private long insertBatch(EcomJdFileParser.Detected d, Long shopId, String fileName, String mode, int total) {
        KeyHolder keys = new GeneratedKeyHolder();
        String by = accessService.currentUsername();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO ecom_import_batch
                      (platform, shop_id, report_type, period_start, period_end, file_name,
                       import_mode, status, total_rows, create_by, is_active)
                    VALUES (?,?,?,?,?,?,?,'running',?,?,1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, d.platform);
            if (shopId == null) {
                ps.setObject(2, null);
            } else {
                ps.setLong(2, shopId);
            }
            ps.setString(3, d.reportType.getCode());
            ps.setObject(4, d.periodStart);
            ps.setObject(5, d.periodEnd);
            ps.setString(6, fileName);
            ps.setString(7, mode);
            ps.setInt(8, total);
            ps.setString(9, by);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new BusinessException("创建导入批次失败");
        }
        return key.longValue();
    }

    private EcomImportResultVO baseResult(EcomJdFileParser.Detected d, Long shopId, String shopName) {
        EcomImportResultVO r = new EcomImportResultVO();
        r.setPlatform(d.platform);
        r.setShopId(shopId);
        r.setShopCode(d.shopCode);
        r.setShopName(shopName);
        r.setReportType(d.reportType.getCode());
        r.setReportLabel(d.reportType.getLabel());
        return r;
    }

    private String buildMessage(int success, int skipped, int fail, int forced) {
        StringBuilder sb = new StringBuilder();
        sb.append("写入 ").append(success).append(" 行");
        if (skipped > 0) {
            sb.append("，跳过已归档 ").append(skipped).append(" 行");
        }
        if (forced > 0) {
            sb.append("，强制覆盖 ").append(forced).append(" 行");
        }
        if (fail > 0) {
            sb.append("，失败 ").append(fail).append(" 行");
        }
        return sb.toString();
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String first(Map<String, String> row, String... keys) {
        for (String k : keys) {
            String v = row.get(k);
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return "";
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }

    private static class PreparedRow {
        LocalDate lockDate;
        LocalDate periodStart;
        LocalDate periodEnd;
        String dimension = "";
        String channelL1 = "";
        String channelL2 = "";
        String channelL3 = "";
        String channelL4 = "";
        String spu = "";
        String spuName = "";
        String cateL1 = "";
        String cateL2 = "";
        String cateL3 = "";
        String goodsNo = "";
        String planId = "";
        String planName = "";
        String planType = "";
        Map<String, Object> metrics = Map.of();
    }
}
