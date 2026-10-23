package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoReviewKbDTO;
import com.base.admin.domain.dto.GeoReviewKbProductAttrs;
import com.base.admin.domain.dto.GeoReviewKbQueryDTO;
import com.base.admin.domain.dto.GeoReviewKbStoreAttrs;
import com.base.admin.domain.entity.GeoReviewKb;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoReviewKbMapper;
import com.base.admin.util.QueryWrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoReviewKbService {

    public static final String CAT_WRITING = "writing_rule";
    public static final String CAT_AI = "ai_friendly";
    public static final String CAT_STRUCTURE = "article_structure";
    public static final String CAT_BRAND = "brand";
    public static final String CAT_STORE = "store";
    public static final String CAT_PRODUCT = "product";
    public static final String CAT_FAQ = "faq";

    private static final int PROMPT_BUDGET = 12000;

    private static final Set<String> LEGACY_CODES = Set.of(
            "writing-notes", "stores", "products", "faq",
            "ai-friendly", "article-structure", "brand-promo",
            "as-01", "as-02", "as-03", "as-04", "as-05", "as-06",
            "product-rugao", "product-logistics", "product-bingcha-old",
            "brand-scan-todo",
            // 旧「系列一口径 + 规格汇总」已拆成 series + sku
            "product-rugao-north", "product-rugao-south", "product-rugao-sku",
            "product-xiaojie", "product-xiaojie-def", "product-xiaojie-sku",
            "product-jinjie", "product-jinjie-sku",
            "product-bingcha", "product-zhuifeng", "product-caobiao",
            "product-yangxian", "product-shuanpian"
    );

    private static final String ATOMIC_SEEDS_PATH = "geo-review-kb/atomic-seeds.json";

    private final GeoReviewKbMapper kbMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    private volatile boolean migrated;

    public PageResult<GeoReviewKb> list(GeoReviewKbQueryDTO query) {
        ensureSeeded();
        String category = StringUtils.hasText(query.getCategory())
                ? normalizeCategory(query.getCategory()) : null;
        LambdaQueryWrapper<GeoReviewKb> qw = new LambdaQueryWrapper<GeoReviewKb>()
                .eq(category != null, GeoReviewKb::getCategory, category)
                .like(StringUtils.hasText(query.getTitle()), GeoReviewKb::getTitle, query.getTitle())
                .eq(query.getEnabled() != null, GeoReviewKb::getEnabled, query.getEnabled())
                .notIn(GeoReviewKb::getCode, LEGACY_CODES)
                .orderByAsc(GeoReviewKb::getSortOrder)
                .orderByAsc(GeoReviewKb::getId);
        QueryWrappers.applyCreateTimeRange(qw, query, GeoReviewKb::getCreateTime);
        Page<GeoReviewKb> page = kbMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), qw);
        page.getRecords().forEach(this::hydrateAttrs);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(cat(CAT_WRITING, "撰写注意点/禁词"));
        list.add(cat(CAT_AI, "AI友好度"));
        list.add(cat(CAT_STRUCTURE, "文章结构"));
        list.add(cat(CAT_BRAND, "品牌事实"));
        list.add(cat(CAT_STORE, "门店信息"));
        list.add(cat(CAT_PRODUCT, "产品系列"));
        list.add(cat(CAT_FAQ, "事实问答"));
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(GeoReviewKbDTO dto) {
        GeoReviewKb row = new GeoReviewKb();
        apply(row, dto, true);
        kbMapper.insert(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(GeoReviewKbDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        GeoReviewKb row = kbMapper.selectById(dto.getId());
        if (row == null) {
            throw new BusinessException("知识条目不存在");
        }
        apply(row, dto, false);
        kbMapper.updateById(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException("ID不能为空");
        }
        kbMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的条目");
        }
        kbMapper.deleteBatchIds(ids);
    }

    @Transactional(rollbackFor = Exception.class)
    public int seedDefaults(boolean forceUpdateContent) {
        int n = 0;
        List<AtomicSeed> seeds = loadAtomicSeeds();
        for (AtomicSeed seed : seeds) {
            n += upsertSeed(seed, forceUpdateContent);
        }
        n += purgeLegacyCodes();
        migrated = true;
        log.info("审稿知识库种子同步完成：upsert/处理 {}，当前原子种子登记 {} 条", n, seeds.size());
        return n;
    }

    public String buildReviewPromptContext() {
        ensureSeeded();
        List<GeoReviewKb> rows = kbMapper.selectList(new LambdaQueryWrapper<GeoReviewKb>()
                .eq(GeoReviewKb::getEnabled, 1)
                .notIn(GeoReviewKb::getCode, LEGACY_CODES)
                .orderByAsc(GeoReviewKb::getSortOrder)
                .orderByAsc(GeoReviewKb::getId));
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下为小巴依 GEO 内容审核知识库（线上可维护）。冲突时以「撰写注意点」为准。\n");
        String lastCat = null;
        for (GeoReviewKb row : rows) {
            String cat = row.getCategory();
            if (!cat.equals(lastCat)) {
                sb.append("\n## ").append(categoryLabel(cat)).append('\n');
                lastCat = cat;
            }
            String body;
            if (CAT_STORE.equals(cat)) {
                body = formatStoreLine(row.getTitle(), parseStoreAttrs(row.getAttrs()));
            } else if (CAT_PRODUCT.equals(cat)) {
                body = formatProductLine(row.getTitle(), parseProductAttrs(row.getAttrs()), row.getContent());
            } else {
                body = compactContent(row.getContent());
            }
            String block = "- **" + nz(row.getTitle()) + "**：" + body + "\n";
            if (sb.length() + block.length() > PROMPT_BUDGET) {
                sb.append("\n（后续知识条目已省略，请以已列出的硬性口径为准）\n");
                break;
            }
            sb.append(block);
        }
        return sb.toString().trim();
    }

    public synchronized void ensureSeeded() {
        if (migrated) {
            return;
        }
        seedDefaults(false);
    }

    private int upsertSeed(AtomicSeed seed, boolean forceUpdateContent) {
        String attrsJson = seedAttrsJson(seed);
        String content = CAT_STORE.equals(seed.category())
                ? composeStoreContent(seed.title(), seed.attrs())
                : (CAT_PRODUCT.equals(seed.category()) && seed.productAttrs() != null
                ? composeProductContent(seed.title(), seed.productAttrs(), seed.content())
                : nz(seed.content()));
        // 含逻辑删除行：uk_code 仍占用，MP 默认 select 看不到
        GeoReviewKb existing = findByCodeIncludingDeleted(seed.code());
        if (existing == null) {
            GeoReviewKb row = new GeoReviewKb();
            row.setCategory(seed.category());
            row.setCode(seed.code());
            row.setTitle(seed.title());
            row.setContent(content);
            row.setAttrs(attrsJson);
            row.setSortOrder(seed.sortOrder());
            row.setEnabled(1);
            row.setSourceFile(seed.sourceFile() == null ? "" : seed.sourceFile());
            row.setRemark("系统种子");
            row.setIsActive(1);
            try {
                kbMapper.insert(row);
            } catch (DuplicateKeyException e) {
                existing = findByCodeIncludingDeleted(seed.code());
                if (existing == null) {
                    throw e;
                }
            }
            if (existing == null) {
                return 1;
            }
        }
        boolean systemRow = "系统种子".equals(existing.getRemark());
        if (forceUpdateContent || systemRow) {
            // 未改备注的系统种子允许同步刷新正文；手工改过备注的条目跳过，避免冲掉运营修改
            existing.setTitle(seed.title());
            existing.setContent(content);
            existing.setAttrs(attrsJson);
            existing.setCategory(seed.category());
            existing.setSortOrder(seed.sortOrder());
            existing.setSourceFile(seed.sourceFile() == null ? "" : seed.sourceFile());
            existing.setEnabled(1);
            existing.setIsActive(1);
            if (!StringUtils.hasText(existing.getRemark())) {
                existing.setRemark("系统种子");
            }
            reviveOrUpdate(existing);
            return 1;
        }
        // 门店缺 attrs 时补齐；产品在缺 attrs 或缺少系列结构化字段时补齐（不冲掉运营改过的备注行正文以外的手工 attrs 若已完整）
        if (CAT_STORE.equals(seed.category())
                && !StringUtils.hasText(existing.getAttrs()) && StringUtils.hasText(attrsJson)) {
            existing.setAttrs(attrsJson);
            existing.setContent(content);
            existing.setEnabled(1);
            existing.setIsActive(1);
            reviveOrUpdate(existing);
            return 1;
        }
        if (CAT_PRODUCT.equals(seed.category()) && StringUtils.hasText(attrsJson)
                && productAttrsNeedSync(existing.getAttrs(), attrsJson)) {
            existing.setAttrs(attrsJson);
            if (!StringUtils.hasText(existing.getContent()) || "系统种子".equals(existing.getRemark())) {
                existing.setContent(content);
            }
            existing.setTitle(seed.title());
            existing.setSortOrder(seed.sortOrder());
            existing.setEnabled(1);
            existing.setIsActive(1);
            reviveOrUpdate(existing);
            return 1;
        }
        if (existing.getEnabled() == null || existing.getEnabled() == 0
                || existing.getIsActive() == null || existing.getIsActive() == 0) {
            existing.setEnabled(1);
            existing.setIsActive(1);
            reviveOrUpdate(existing);
            return 1;
        }
        return 0;
    }

    private String seedAttrsJson(AtomicSeed seed) {
        if (CAT_STORE.equals(seed.category())) {
            return writeStoreAttrsJson(seed.attrs());
        }
        if (CAT_PRODUCT.equals(seed.category())) {
            return writeProductAttrsJson(seed.productAttrs());
        }
        return null;
    }

    /** 产品 attrs 为空、或缺少系列/图片等关键结构化字段时需要从种子同步 */
    private static boolean productAttrsNeedSync(String existingAttrs, String seedAttrsJson) {
        if (!StringUtils.hasText(existingAttrs)) {
            return true;
        }
        String ex = existingAttrs;
        // 旧产品行只有正文、没有 seriesCode/kind
        if (!ex.contains("\"seriesCode\"") && seedAttrsJson.contains("\"seriesCode\"")) {
            return true;
        }
        if (!ex.contains("\"kind\"") && seedAttrsJson.contains("\"kind\"")) {
            return true;
        }
        if (!ex.contains("\"imageUrl\"") && seedAttrsJson.contains("\"imageUrl\"")) {
            return true;
        }
        return false;
    }

    private GeoReviewKb findByCodeIncludingDeleted(String code) {
        List<GeoReviewKb> rows = jdbc.query(
                """
                        SELECT id, category, code, title, content, attrs, sort_order, enabled, source_file, remark,
                               create_by, create_time, update_by, update_time, is_active
                        FROM geo_review_kb WHERE code = ? LIMIT 1
                        """,
                (rs, i) -> {
                    GeoReviewKb row = new GeoReviewKb();
                    row.setId(rs.getLong("id"));
                    row.setCategory(rs.getString("category"));
                    row.setCode(rs.getString("code"));
                    row.setTitle(rs.getString("title"));
                    row.setContent(rs.getString("content"));
                    row.setAttrs(rs.getString("attrs"));
                    row.setSortOrder(rs.getInt("sort_order"));
                    row.setEnabled(rs.getInt("enabled"));
                    row.setSourceFile(rs.getString("source_file"));
                    row.setRemark(rs.getString("remark"));
                    row.setCreateBy(rs.getString("create_by"));
                    row.setUpdateBy(rs.getString("update_by"));
                    row.setIsActive(rs.getInt("is_active"));
                    return row;
                },
                code);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void reviveOrUpdate(GeoReviewKb existing) {
        if (existing.getId() == null) {
            kbMapper.updateById(existing);
            return;
        }
        // 直接按主键写回，恢复软删行
        jdbc.update("""
                        UPDATE geo_review_kb
                        SET category=?, title=?, content=?, attrs=?, sort_order=?, enabled=?,
                            source_file=?, remark=?, is_active=1, update_time=NOW()
                        WHERE id=?
                        """,
                existing.getCategory(),
                existing.getTitle(),
                existing.getContent(),
                existing.getAttrs(),
                existing.getSortOrder(),
                existing.getEnabled(),
                existing.getSourceFile(),
                existing.getRemark(),
                existing.getId());
    }

    private int purgeLegacyCodes() {
        // 物理删旧整篇，避免软删后 uk_code 挡新种子
        String placeholders = String.join(",", LEGACY_CODES.stream().map(c -> "?").toList());
        Object[] args = LEGACY_CODES.toArray();
        int deleted = jdbc.update("DELETE FROM geo_review_kb WHERE code IN (" + placeholders + ")", args);
        if (deleted > 0) {
            log.info("已物理删除旧整篇审稿知识 {} 条：{}", deleted, LEGACY_CODES);
        }
        return deleted;
    }

    private List<AtomicSeed> loadAtomicSeeds() {
        try {
            ClassPathResource res = new ClassPathResource(ATOMIC_SEEDS_PATH);
            if (!res.exists()) {
                log.warn("原子种子文件缺失: {}", ATOMIC_SEEDS_PATH);
                return List.of();
            }
            try (InputStream in = res.getInputStream()) {
                return objectMapper.readValue(in, new TypeReference<>() {
                });
            }
        } catch (Exception e) {
            log.error("加载原子种子失败: {}", e.getMessage());
            throw new BusinessException("加载审稿知识库原子种子失败");
        }
    }

    private void apply(GeoReviewKb row, GeoReviewKbDTO dto, boolean creating) {
        String category = normalizeCategory(dto.getCategory());
        row.setCategory(category);
        String code = dto.getCode() == null ? "" : dto.getCode().trim();
        if (!StringUtils.hasText(code)) {
            code = category + "-" + System.currentTimeMillis();
        }
        if (LEGACY_CODES.contains(code)) {
            throw new BusinessException("编码已退役，请使用新的单条编码");
        }
        if (creating || !code.equals(row.getCode())) {
            Long dup = kbMapper.selectCount(new LambdaQueryWrapper<GeoReviewKb>()
                    .eq(GeoReviewKb::getCode, code)
                    .ne(dto.getId() != null, GeoReviewKb::getId, dto.getId()));
            if (dup != null && dup > 0) {
                throw new BusinessException("编码已存在：" + code);
            }
        }
        row.setCode(code);
        row.setTitle(dto.getTitle().trim());
        if (CAT_STORE.equals(category)) {
            GeoReviewKbStoreAttrs store = normalizeStoreAttrs(dto.getStoreAttrs());
            if (!hasAnyStoreField(store)) {
                throw new BusinessException("门店请至少填写营业时间、地址、电话或覆盖范围之一");
            }
            row.setAttrs(writeStoreAttrsJson(store));
            row.setContent(composeStoreContent(dto.getTitle().trim(), store));
            row.setStoreAttrs(store);
            row.setProductAttrs(null);
        } else if (CAT_PRODUCT.equals(category)) {
            GeoReviewKbProductAttrs product = normalizeProductAttrs(dto.getProductAttrs());
            if (!hasAnyProductField(product) && !StringUtils.hasText(dto.getContent())) {
                throw new BusinessException("产品请填写系列/规格信息或内容");
            }
            row.setAttrs(writeProductAttrsJson(product));
            row.setContent(composeProductContent(dto.getTitle().trim(), product, dto.getContent()));
            row.setProductAttrs(product);
            row.setStoreAttrs(null);
        } else {
            if (!StringUtils.hasText(dto.getContent())) {
                throw new BusinessException("内容不能为空");
            }
            row.setContent(dto.getContent());
            row.setAttrs(null);
            row.setStoreAttrs(null);
            row.setProductAttrs(null);
        }
        row.setSortOrder(dto.getSortOrder() == null ? 100 : dto.getSortOrder());
        row.setEnabled(dto.getEnabled() == null || dto.getEnabled() != 0 ? 1 : 0);
        row.setSourceFile(dto.getSourceFile() == null ? "" : dto.getSourceFile().trim());
        row.setRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
    }

    private void hydrateAttrs(GeoReviewKb row) {
        if (row == null) {
            return;
        }
        if (CAT_STORE.equals(row.getCategory())) {
            row.setStoreAttrs(parseStoreAttrs(row.getAttrs()));
        } else if (CAT_PRODUCT.equals(row.getCategory())) {
            row.setProductAttrs(parseProductAttrs(row.getAttrs()));
        }
    }

    private GeoReviewKbStoreAttrs parseStoreAttrs(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, GeoReviewKbStoreAttrs.class);
        } catch (Exception e) {
            log.warn("解析门店 attrs 失败: {}", e.getMessage());
            return null;
        }
    }

    private GeoReviewKbProductAttrs parseProductAttrs(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, GeoReviewKbProductAttrs.class);
        } catch (Exception e) {
            log.warn("解析产品 attrs 失败: {}", e.getMessage());
            return null;
        }
    }

    private String writeStoreAttrsJson(GeoReviewKbStoreAttrs attrs) {
        if (attrs == null || !hasAnyStoreField(attrs)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalizeStoreAttrs(attrs));
        } catch (Exception e) {
            throw new BusinessException("门店结构化字段序列化失败");
        }
    }

    private String writeProductAttrsJson(GeoReviewKbProductAttrs attrs) {
        if (attrs == null || !hasAnyProductField(attrs)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalizeProductAttrs(attrs));
        } catch (Exception e) {
            throw new BusinessException("产品结构化字段序列化失败");
        }
    }

    private static GeoReviewKbStoreAttrs normalizeStoreAttrs(GeoReviewKbStoreAttrs raw) {
        GeoReviewKbStoreAttrs a = raw == null ? new GeoReviewKbStoreAttrs() : raw;
        GeoReviewKbStoreAttrs out = new GeoReviewKbStoreAttrs();
        out.setOpenHours(trimToNull(a.getOpenHours()));
        out.setAddress(trimToNull(a.getAddress()));
        out.setLandline(trimToNull(a.getLandline()));
        out.setMobile(trimToNull(a.getMobile()));
        out.setCoverage(trimToNull(a.getCoverage()));
        return out;
    }

    private static boolean hasAnyStoreField(GeoReviewKbStoreAttrs a) {
        if (a == null) {
            return false;
        }
        return StringUtils.hasText(a.getOpenHours())
                || StringUtils.hasText(a.getAddress())
                || StringUtils.hasText(a.getLandline())
                || StringUtils.hasText(a.getMobile())
                || StringUtils.hasText(a.getCoverage());
    }

    private static String composeStoreContent(String title, GeoReviewKbStoreAttrs a) {
        GeoReviewKbStoreAttrs s = normalizeStoreAttrs(a);
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(title)) {
            sb.append(title.trim());
        }
        if (StringUtils.hasText(s.getOpenHours())) {
            sb.append(sb.isEmpty() ? "" : "\n").append("营业时间：").append(s.getOpenHours());
        }
        if (StringUtils.hasText(s.getAddress())) {
            sb.append(sb.isEmpty() ? "" : "\n").append("地址：").append(s.getAddress());
        }
        if (StringUtils.hasText(s.getLandline())) {
            sb.append(sb.isEmpty() ? "" : "\n").append("座机：").append(s.getLandline());
        }
        if (StringUtils.hasText(s.getMobile())) {
            sb.append(sb.isEmpty() ? "" : "\n").append("手机：").append(s.getMobile());
        }
        if (StringUtils.hasText(s.getCoverage())) {
            sb.append(sb.isEmpty() ? "" : "\n").append("覆盖范围：").append(s.getCoverage());
        }
        return sb.toString();
    }

    private static String formatStoreLine(String title, GeoReviewKbStoreAttrs a) {
        if (a == null) {
            return nz(title);
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(a.getOpenHours())) {
            parts.add("营业 " + a.getOpenHours());
        }
        if (StringUtils.hasText(a.getAddress())) {
            parts.add(a.getAddress());
        }
        if (StringUtils.hasText(a.getLandline())) {
            parts.add("座机 " + a.getLandline());
        }
        if (StringUtils.hasText(a.getMobile())) {
            parts.add("手机 " + a.getMobile());
        }
        if (StringUtils.hasText(a.getCoverage())) {
            parts.add("覆盖 " + a.getCoverage());
        }
        return parts.isEmpty() ? nz(title) : String.join("｜", parts);
    }

    private static GeoReviewKbProductAttrs normalizeProductAttrs(GeoReviewKbProductAttrs raw) {
        GeoReviewKbProductAttrs a = raw == null ? new GeoReviewKbProductAttrs() : raw;
        GeoReviewKbProductAttrs out = new GeoReviewKbProductAttrs();
        out.setKind(trimToNull(a.getKind()));
        out.setSeriesCode(trimToNull(a.getSeriesCode()));
        out.setSeriesName(trimToNull(a.getSeriesName()));
        out.setNetWeight(trimToNull(a.getNetWeight()));
        out.setGrossWeight(trimToNull(a.getGrossWeight()));
        out.setSpecJin(trimToNull(a.getSpecJin()));
        out.setPriceYuan(trimToNull(a.getPriceYuan()));
        out.setParts(trimToNull(a.getParts()));
        out.setImageUrl(trimToNull(a.getImageUrl()));
        out.setHandbookPage(a.getHandbookPage());
        return out;
    }

    private static boolean hasAnyProductField(GeoReviewKbProductAttrs a) {
        if (a == null) {
            return false;
        }
        return StringUtils.hasText(a.getKind())
                || StringUtils.hasText(a.getSeriesCode())
                || StringUtils.hasText(a.getSeriesName())
                || StringUtils.hasText(a.getNetWeight())
                || StringUtils.hasText(a.getGrossWeight())
                || StringUtils.hasText(a.getSpecJin())
                || StringUtils.hasText(a.getPriceYuan())
                || StringUtils.hasText(a.getParts())
                || StringUtils.hasText(a.getImageUrl())
                || a.getHandbookPage() != null;
    }

    private static String composeProductContent(String title, GeoReviewKbProductAttrs raw, String fallback) {
        GeoReviewKbProductAttrs a = normalizeProductAttrs(raw);
        if ("series".equalsIgnoreCase(nz(a.getKind())) && StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        List<String> bits = new ArrayList<>();
        if (StringUtils.hasText(a.getSeriesName())) {
            bits.add("系列 " + a.getSeriesName());
        }
        if (StringUtils.hasText(a.getSpecJin())) {
            bits.add("规格 " + a.getSpecJin());
        }
        if (StringUtils.hasText(a.getNetWeight())) {
            bits.add("净重 " + a.getNetWeight());
        }
        if (StringUtils.hasText(a.getGrossWeight())) {
            bits.add("毛重 " + a.getGrossWeight());
        }
        if (StringUtils.hasText(a.getPriceYuan())) {
            bits.add("参考价 " + a.getPriceYuan() + "（以最新线上为准）");
        }
        if (StringUtils.hasText(a.getParts())) {
            bits.add("部位：" + a.getParts());
        }
        if (!bits.isEmpty()) {
            return String.join("；", bits);
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return nz(title);
    }

    private static String formatProductLine(String title, GeoReviewKbProductAttrs a, String content) {
        if (a == null) {
            return compactContent(content);
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(a.getSeriesName())) {
            parts.add(a.getSeriesName());
        }
        if (StringUtils.hasText(a.getSpecJin())) {
            parts.add(a.getSpecJin());
        }
        if (StringUtils.hasText(a.getNetWeight())) {
            parts.add("净重" + a.getNetWeight());
        }
        if (StringUtils.hasText(a.getPriceYuan())) {
            parts.add("参考价" + a.getPriceYuan());
        }
        if (parts.isEmpty()) {
            return compactContent(content);
        }
        return String.join("｜", parts);
    }

    private static String normalizeCategory(String raw) {
        String c = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case CAT_WRITING, CAT_AI, CAT_STRUCTURE, CAT_BRAND, CAT_STORE, CAT_PRODUCT, CAT_FAQ -> c;
            default -> throw new BusinessException("不支持的分类：" + raw);
        };
    }

    private static String categoryLabel(String category) {
        return switch (category == null ? "" : category) {
            case CAT_WRITING -> "撰写注意点";
            case CAT_AI -> "AI友好度";
            case CAT_STRUCTURE -> "文章结构";
            case CAT_BRAND -> "品牌事实";
            case CAT_STORE -> "门店";
            case CAT_PRODUCT -> "产品";
            case CAT_FAQ -> "问答";
            default -> category;
        };
    }

    private static Map<String, Object> cat(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("shape", "atomic");
        return m;
    }

    private static String compactContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public record AtomicSeed(
            String category,
            String code,
            String title,
            int sortOrder,
            String content,
            String sourceFile,
            GeoReviewKbStoreAttrs attrs,
            GeoReviewKbProductAttrs productAttrs
    ) {
    }
}
