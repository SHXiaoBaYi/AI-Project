package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoSellDomainDTO;
import com.base.admin.domain.dto.GeoSellPointDTO;
import com.base.admin.domain.dto.GeoSellPointPreviewDTO;
import com.base.admin.domain.dto.GeoSellPointQueryDTO;
import com.base.admin.domain.entity.GeoSellAuthority;
import com.base.admin.domain.entity.GeoSellDomain;
import com.base.admin.domain.entity.GeoSellPoint;
import com.base.admin.domain.entity.GeoSellQuality;
import com.base.admin.domain.entity.GeoSellSocial;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoSellDomainVO;
import com.base.admin.domain.vo.GeoSellPointContextVO;
import com.base.admin.domain.vo.GeoSellPointVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoSellAuthorityMapper;
import com.base.admin.mapper.GeoSellDomainMapper;
import com.base.admin.mapper.GeoSellPointMapper;
import com.base.admin.mapper.GeoSellQualityMapper;
import com.base.admin.mapper.GeoSellSocialMapper;
import com.base.admin.util.QueryWrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoSellPointService {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private static final List<SeedDomain> ROOT_SEEDS = List.of(
            new SeedDomain("ranch", "牧场与养殖方式", 1),
            new SeedDomain("matrix_service", "矩阵和客服服务与售后、销售渠道", 2),
            new SeedDomain("quality", "产品体验、生产与品控信息汇总", 3),
            new SeedDomain("social_brand", "社会责任感和品牌", 4),
            new SeedDomain("authority", "专业背书与权威认证", 5)
    );

    private final GeoSellDomainMapper domainMapper;
    private final GeoSellPointMapper pointMapper;
    private final GeoSellQualityMapper qualityMapper;
    private final GeoSellSocialMapper socialMapper;
    private final GeoSellAuthorityMapper authorityMapper;
    private final SellPointContextService contextService;
    private final ObjectMapper objectMapper;

    public List<GeoSellDomainVO> domainTree() {
        ensureRootDomains();
        List<GeoSellDomain> all = domainMapper.selectList(new LambdaQueryWrapper<GeoSellDomain>()
                .orderByAsc(GeoSellDomain::getSortOrder)
                .orderByAsc(GeoSellDomain::getId));
        Map<Long, Integer> directCounts = new HashMap<>();
        List<GeoSellPoint> pointRows = pointMapper.selectList(new LambdaQueryWrapper<GeoSellPoint>()
                .select(GeoSellPoint::getId, GeoSellPoint::getDomainId));
        for (GeoSellPoint p : pointRows) {
            if (p.getDomainId() == null) {
                continue;
            }
            directCounts.merge(p.getDomainId(), 1, Integer::sum);
        }
        Map<Long, String> rootKindById = buildRootKindMap(all);
        Map<Long, GeoSellDomainVO> map = new LinkedHashMap<>();
        for (GeoSellDomain d : all) {
            GeoSellDomainVO vo = toDomainVo(d);
            vo.setRootKind(rootKindById.getOrDefault(d.getId(), "simple"));
            vo.setPointCount(directCounts.getOrDefault(d.getId(), 0));
            map.put(d.getId(), vo);
        }
        List<GeoSellDomainVO> roots = new ArrayList<>();
        for (GeoSellDomainVO vo : map.values()) {
            Long pid = vo.getParentId() == null ? 0L : vo.getParentId();
            if (pid == 0L || !map.containsKey(pid)) {
                roots.add(vo);
            } else {
                map.get(pid).getChildren().add(vo);
            }
        }
        for (GeoSellDomainVO root : roots) {
            rollupPointCount(root);
        }
        return roots;
    }

    private int rollupPointCount(GeoSellDomainVO node) {
        int sum = node.getPointCount() == null ? 0 : node.getPointCount();
        for (GeoSellDomainVO child : node.getChildren()) {
            sum += rollupPointCount(child);
        }
        node.setPointCount(sum);
        return sum;
    }

    public void createDomain(GeoSellDomainDTO dto) {
        GeoSellDomain row = new GeoSellDomain();
        applyDomain(row, dto);
        domainMapper.insert(row);
    }

    public void updateDomain(GeoSellDomainDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("域ID不能为空");
        }
        GeoSellDomain row = domainMapper.selectById(dto.getId());
        if (row == null) {
            throw new BusinessException("卖点域不存在");
        }
        applyDomain(row, dto);
        domainMapper.updateById(row);
    }

    public void deleteDomain(Long id) {
        if (id == null) {
            throw new BusinessException("域ID不能为空");
        }
        long children = domainMapper.selectCount(new LambdaQueryWrapper<GeoSellDomain>()
                .eq(GeoSellDomain::getParentId, id));
        if (children > 0) {
            throw new BusinessException("请先删除子域");
        }
        long points = pointMapper.selectCount(new LambdaQueryWrapper<GeoSellPoint>()
                .eq(GeoSellPoint::getDomainId, id));
        if (points > 0) {
            throw new BusinessException("域下仍有卖点条目，无法删除");
        }
        domainMapper.deleteById(id);
    }

    public PageResult<GeoSellPointVO> listPoints(GeoSellPointQueryDTO query) {
        Set<Long> domainIds = null;
        if (query.getDomainId() != null) {
            domainIds = collectDomainIds(query.getDomainId());
        }
        String status = normalizePointStatus(query.getStatus());
        String level = normalizeEvidenceLevel(query.getEvidenceLevel());
        LambdaQueryWrapper<GeoSellPoint> wrapper = new LambdaQueryWrapper<GeoSellPoint>()
                .in(domainIds != null && !domainIds.isEmpty(), GeoSellPoint::getDomainId, domainIds)
                .like(StringUtils.hasText(query.getTitle()), GeoSellPoint::getTitle, query.getTitle())
                .like(StringUtils.hasText(query.getSourceSheet()), GeoSellPoint::getSourceSheet, query.getSourceSheet())
                .eq(StringUtils.hasText(status), GeoSellPoint::getStatus, status)
                .eq(StringUtils.hasText(level), GeoSellPoint::getEvidenceLevel, level)
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(GeoSellPoint::getTitle, query.getKeyword())
                        .or().like(GeoSellPoint::getClaim, query.getKeyword())
                        .or().like(GeoSellPoint::getEvidence, query.getKeyword())
                        .or().like(GeoSellPoint::getTagsJson, query.getKeyword())
                        .or().like(GeoSellPoint::getKeywordsJson, query.getKeyword())
                        .or().like(GeoSellPoint::getSourceSheet, query.getKeyword()))
                .orderByAsc(GeoSellPoint::getSortOrder)
                .orderByDesc(GeoSellPoint::getId);
        QueryWrappers.applyCreateTimeRange(wrapper, query, GeoSellPoint::getCreateTime);
        Page<GeoSellPoint> page = pointMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, String> domainNames = loadDomainNames(page.getRecords().stream()
                .map(GeoSellPoint::getDomainId).filter(Objects::nonNull).collect(Collectors.toSet()));
        DetailBundle details = loadDetails(page.getRecords().stream().map(GeoSellPoint::getId).toList());
        List<GeoSellPointVO> rows = page.getRecords().stream()
                .map(p -> toPointVo(p, domainNames.get(p.getDomainId()), details))
                .toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createPoint(GeoSellPointDTO dto) {
        requireDomain(dto.getDomainId());
        String kind = resolveKind(dto);
        GeoSellPoint row = new GeoSellPoint();
        applyPointIndex(row, dto, kind);
        if (row.getVersionNo() == null) {
            row.setVersionNo(1);
        }
        pointMapper.insert(row);
        saveDetail(row.getId(), kind, dto, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePoint(GeoSellPointDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("条目ID不能为空");
        }
        GeoSellPoint row = pointMapper.selectById(dto.getId());
        if (row == null) {
            throw new BusinessException("卖点条目不存在");
        }
        requireDomain(dto.getDomainId());
        String kind = resolveKind(dto);
        applyPointIndex(row, dto, kind);
        row.setVersionNo((row.getVersionNo() == null ? 1 : row.getVersionNo()) + 1);
        pointMapper.updateById(row);
        saveDetail(row.getId(), kind, dto, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePoint(Long id) {
        if (id == null) {
            throw new BusinessException("条目ID不能为空");
        }
        deleteDetails(List.of(id));
        pointMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePointBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        deleteDetails(ids);
        pointMapper.deleteBatchIds(ids);
    }

    public GeoSellPointContextVO preview(GeoSellPointPreviewDTO dto) {
        if (dto == null) {
            dto = new GeoSellPointPreviewDTO();
        }
        return contextService.build(dto.getTopicName(), dto.getTargetQuestion(), dto.getLimit());
    }

    @Transactional(rollbackFor = Exception.class)
    public GeoImportResultVO importExcel(InputStream in) {
        ensureRootDomains();
        GeoImportResultVO result = new GeoImportResultVO();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Map<String, Long> rootByCode = rootDomainIds();
            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                String sheetName = sheet.getSheetName() == null ? "" : sheet.getSheetName().trim();
                SeedDomain seed = matchSeed(sheetName);
                if (seed == null) {
                    result.getErrors().add(new GeoImportResultVO.GeoImportErrorVO(
                            0, "sheet", "未识别的 sheet：" + sheetName));
                    result.setFailureCount(result.getFailureCount() + 1);
                    continue;
                }
                Long rootId = rootByCode.get(seed.code());
                importSheet(sheet, sheetName, rootId, seed.code(), result);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("导入失败：" + e.getMessage());
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public GeoImportResultVO importFromProjectDocs() {
        Path path = resolveDocsPath();
        if (path == null || !Files.isRegularFile(path)) {
            throw new BusinessException("未找到 docs/小巴依卖点.xlsx，请手动上传导入");
        }
        try (InputStream in = Files.newInputStream(path)) {
            return importExcel(in);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取卖点Excel失败：" + e.getMessage());
        }
    }

    private Path resolveDocsPath() {
        List<Path> candidates = List.of(
                Paths.get("docs", "小巴依卖点.xlsx"),
                Paths.get("..", "docs", "小巴依卖点.xlsx"),
                Paths.get(System.getProperty("user.dir", "."), "docs", "小巴依卖点.xlsx"),
                Paths.get(System.getProperty("user.dir", "."), "..", "docs", "小巴依卖点.xlsx")
        );
        for (Path p : candidates) {
            Path abs = p.toAbsolutePath().normalize();
            if (Files.isRegularFile(abs)) {
                return abs;
            }
        }
        return null;
    }

    private void importSheet(Sheet sheet, String sheetName, Long rootId, String rootCode, GeoImportResultVO result) {
        if (StringUtils.hasText(sheetName)) {
            List<GeoSellPoint> old = pointMapper.selectList(new LambdaQueryWrapper<GeoSellPoint>()
                    .eq(GeoSellPoint::getSourceSheet, sheetName)
                    .select(GeoSellPoint::getId));
            if (!old.isEmpty()) {
                deleteDetails(old.stream().map(GeoSellPoint::getId).toList());
                pointMapper.delete(new LambdaQueryWrapper<GeoSellPoint>()
                        .eq(GeoSellPoint::getSourceSheet, sheetName));
            }
        }
        Map<String, Long> childCache = new HashMap<>();
        Set<Long> rootScope = rootId == null ? new HashSet<>() : new HashSet<>(collectDomainIds(rootId));
        String kind = kindOfRootCode(rootCode);
        String lastCat = "";
        String lastSub = "";
        int lastRow = sheet.getLastRowNum();
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            List<String> cells = readRow(row, 8);
            if (cells.stream().allMatch(s -> !StringUtils.hasText(s))) {
                continue;
            }
            String c0 = cells.get(0);
            String c1 = cells.get(1);
            String c2 = cells.get(2);
            String c3 = cells.get(3);
            String c4 = cells.get(4);
            if ("改".equals(c0) || isHeaderRow(c0, c1, c2, sheetName)) {
                continue;
            }
            if (StringUtils.hasText(c0)) {
                lastCat = c0;
            }

            ParsedRow parsed = parseRow(rootCode, kind, lastCat, lastSub, c0, c1, c2, c3, c4);
            if (parsed == null) {
                continue;
            }
            if (StringUtils.hasText(parsed.subCat())) {
                lastSub = parsed.subCat();
            }
            result.setTotalCount(result.getTotalCount() + 1);
            try {
                Long domainId = rootId == null ? 0L : rootId;
                if (domainId == 0L) {
                    throw new BusinessException("卖点根域不存在: " + rootCode);
                }
                if (StringUtils.hasText(parsed.category())) {
                    domainId = ensureChildDomain(domainId, parsed.category(), childCache);
                    rootScope.add(domainId);
                }
                if ("quality".equals(kind) && StringUtils.hasText(parsed.subCat())) {
                    domainId = ensureChildDomain(domainId, parsed.subCat(), childCache);
                    rootScope.add(domainId);
                }
                upsertPoint(domainId, rootScope, parsed, sheetName, r + 1, result);
            } catch (Exception e) {
                result.setFailureCount(result.getFailureCount() + 1);
                result.getErrors().add(new GeoImportResultVO.GeoImportErrorVO(
                        r + 1, "row", e.getMessage()));
            }
        }
    }

    private ParsedRow parseRow(String rootCode, String kind, String lastCat, String lastSub,
                               String c0, String c1, String c2, String c3, String c4) {
        return switch (rootCode) {
            case "ranch" -> {
                String cat = StringUtils.hasText(c0) ? c0 : lastCat;
                String title = firstNonBlank(c1, truncate(c2, 40));
                String evidence = firstNonBlank(c2, c1);
                if (!StringUtils.hasText(title) || !StringUtils.hasText(evidence)) {
                    yield null;
                }
                yield ParsedRow.simple(cat, null, safeTitle(title), firstSentence(evidence), evidence,
                        guessLevel(evidence), List.of(nz(cat)));
            }
            case "matrix_service" -> {
                String cat = StringUtils.hasText(c0) ? c0 : lastCat;
                String title = c1;
                String evidence = c2;
                if (!StringUtils.hasText(title) || !StringUtils.hasText(evidence)) {
                    yield null;
                }
                yield ParsedRow.simple(cat, null, safeTitle(title), firstSentence(evidence), evidence,
                        guessLevel(evidence), List.of(nz(cat), safeTitle(title)));
            }
            case "quality" -> {
                String cat = StringUtils.hasText(c0) ? c0 : lastCat;
                String sub = StringUtils.hasText(c1) ? c1 : lastSub;
                String item = c2;
                String standard = c3;
                if (!StringUtils.hasText(item) || !StringUtils.hasText(standard)) {
                    yield null;
                }
                yield ParsedRow.quality(cat, sub, nz(cat), nz(sub), safeTitle(item), standard,
                        List.of(nz(cat), nz(sub), safeTitle(item)));
            }
            case "social_brand" -> {
                String cat = StringUtils.hasText(c0) ? c0 : lastCat;
                String event = c1;
                String action = c2;
                String remark = c3;
                if (!StringUtils.hasText(event) || !StringUtils.hasText(action)) {
                    yield null;
                }
                yield ParsedRow.social(cat, null, nz(cat), safeTitle(event), action, nz(remark),
                        List.of(nz(cat), "公益", "品牌"));
            }
            case "authority" -> parseAuthorityRow(c0, c1, c2, c3, c4);
            default -> null;
        };
    }

    private ParsedRow parseAuthorityRow(String c0, String c1, String c2, String c3, String c4) {
        boolean wide = StringUtils.hasText(c1) || StringUtils.hasText(c2) || StringUtils.hasText(c3);
        if (!wide) {
            if (!StringUtils.hasText(c0)) {
                return null;
            }
            if (c0.contains("专业背书与权威认证") && c0.length() < 40) {
                return null;
            }
            String title = safeTitle(firstLineTitle(c0));
            return ParsedRow.authority("专业背书总述", null, title, "", "", "", c0,
                    List.of("背书", "认证"));
        }
        String channel = firstLineTitle(c0);
        if (!StringUtils.hasText(channel)) {
            return null;
        }
        if (!StringUtils.hasText(c1) && !StringUtils.hasText(c2) && !StringUtils.hasText(c3)
                && !StringUtils.hasText(c4)) {
            return null;
        }
        return ParsedRow.authority("权威认证", null, safeTitle(channel), nz(c1), nz(c2), nz(c3), nz(c4),
                List.of("背书", "认证", safeTitle(channel)));
    }

    private static String firstLineTitle(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String t = text.trim().replace("\r\n", "\n");
        int nl = t.indexOf('\n');
        String first = nl >= 0 ? t.substring(0, nl).trim() : t;
        return first;
    }

    private static String safeTitle(String title) {
        return truncate(nz(title).replace('\n', ' ').replace('\r', ' ').trim(), 180);
    }

    private void upsertPoint(Long domainId, Set<Long> rootScope, ParsedRow parsed, String sheetName, int rowIndex,
                             GeoImportResultVO result) {
        String title = safeTitle(parsed.displayTitle());
        if (!StringUtils.hasText(title)) {
            throw new BusinessException("标题为空，跳过");
        }
        String claim = truncate(parsed.aiClaim(), 500);
        String evidence = parsed.aiEvidence();
        GeoSellPoint existing = pointMapper.selectOne(new LambdaQueryWrapper<GeoSellPoint>()
                .eq(GeoSellPoint::getDomainId, domainId)
                .eq(GeoSellPoint::getTitle, title)
                .eq(GeoSellPoint::getKind, parsed.kind())
                .last("LIMIT 1"));
        if (existing == null && rootScope != null && !rootScope.isEmpty()) {
            existing = pointMapper.selectOne(new LambdaQueryWrapper<GeoSellPoint>()
                    .in(GeoSellPoint::getDomainId, rootScope)
                    .eq(GeoSellPoint::getTitle, title)
                    .eq(GeoSellPoint::getKind, parsed.kind())
                    .orderByAsc(GeoSellPoint::getId)
                    .last("LIMIT 1"));
        }
        Long pointId;
        if (existing == null) {
            GeoSellPoint row = new GeoSellPoint();
            row.setDomainId(domainId);
            row.setKind(parsed.kind());
            row.setTitle(title);
            row.setClaim(claim);
            row.setEvidence(evidence);
            row.setTagsJson(toJson(cleanTags(parsed.tags())));
            row.setKeywordsJson(toJson(cleanTags(parsed.tags())));
            row.setEvidenceLevel(parsed.level());
            row.setSourceSheet(sheetName);
            row.setSourceRow(rowIndex);
            row.setStatus("active");
            row.setSortOrder(rowIndex);
            row.setVersionNo(1);
            pointMapper.insert(row);
            pointId = row.getId();
            result.setInsertCount(result.getInsertCount() + 1);
            writeDetailFromParsed(pointId, parsed, true);
        } else {
            existing.setDomainId(domainId);
            existing.setKind(parsed.kind());
            existing.setClaim(claim);
            existing.setEvidence(evidence);
            existing.setTagsJson(toJson(cleanTags(parsed.tags())));
            existing.setKeywordsJson(toJson(cleanTags(parsed.tags())));
            existing.setEvidenceLevel(parsed.level());
            existing.setSourceSheet(sheetName);
            existing.setSourceRow(rowIndex);
            existing.setStatus("active");
            existing.setVersionNo((existing.getVersionNo() == null ? 1 : existing.getVersionNo()) + 1);
            pointMapper.updateById(existing);
            pointId = existing.getId();
            result.setUpdateCount(result.getUpdateCount() + 1);
            writeDetailFromParsed(pointId, parsed, false);
        }
    }

    private void writeDetailFromParsed(Long pointId, ParsedRow parsed, boolean insert) {
        switch (parsed.kind()) {
            case "quality" -> {
                GeoSellQuality q = new GeoSellQuality();
                q.setPointId(pointId);
                q.setModuleName(nz(parsed.moduleName()));
                q.setSectionName(nz(parsed.sectionName()));
                q.setItemName(nz(parsed.itemName()));
                q.setStandardText(nz(parsed.standardText()));
                if (insert || qualityMapper.selectById(pointId) == null) {
                    qualityMapper.insert(q);
                } else {
                    qualityMapper.updateById(q);
                }
            }
            case "social" -> {
                GeoSellSocial s = new GeoSellSocial();
                s.setPointId(pointId);
                s.setDimension(nz(parsed.dimension()));
                s.setEventName(nz(parsed.eventName()));
                s.setActionContent(nz(parsed.actionContent()));
                s.setEventRemark(nz(parsed.eventRemark()));
                if (insert || socialMapper.selectById(pointId) == null) {
                    socialMapper.insert(s);
                } else {
                    socialMapper.updateById(s);
                }
            }
            case "authority" -> {
                GeoSellAuthority a = new GeoSellAuthority();
                a.setPointId(pointId);
                a.setChannel(nz(parsed.channel()));
                a.setAdmission(nz(parsed.admission()));
                a.setProcessText(nz(parsed.processText()));
                a.setMeaning(nz(parsed.meaning()));
                a.setExtraNote(nz(parsed.extraNote()));
                if (insert || authorityMapper.selectById(pointId) == null) {
                    authorityMapper.insert(a);
                } else {
                    authorityMapper.updateById(a);
                }
            }
            default -> {
                // simple：无明细表
            }
        }
    }

    private Long ensureChildDomain(Long parentId, String name, Map<String, Long> cache) {
        String normalized = truncate(nz(name).replace('\n', ' ').replace('\r', ' ').trim(), 100);
        if (!StringUtils.hasText(normalized)) {
            return parentId;
        }
        String key = parentId + "::" + normalized;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        GeoSellDomain existing = domainMapper.selectOne(new LambdaQueryWrapper<GeoSellDomain>()
                .eq(GeoSellDomain::getParentId, parentId)
                .eq(GeoSellDomain::getName, normalized)
                .last("LIMIT 1"));
        if (existing != null) {
            cache.put(key, existing.getId());
            return existing.getId();
        }
        GeoSellDomain child = new GeoSellDomain();
        child.setParentId(parentId);
        child.setName(normalized);
        child.setCode("");
        child.setSortOrder(0);
        child.setEnabled(1);
        child.setRemark("");
        domainMapper.insert(child);
        cache.put(key, child.getId());
        return child.getId();
    }

    private void ensureRootDomains() {
        for (SeedDomain seed : ROOT_SEEDS) {
            Long count = domainMapper.selectCount(new LambdaQueryWrapper<GeoSellDomain>()
                    .eq(GeoSellDomain::getCode, seed.code())
                    .eq(GeoSellDomain::getParentId, 0L));
            if (count != null && count > 0) {
                continue;
            }
            GeoSellDomain row = new GeoSellDomain();
            row.setParentId(0L);
            row.setName(seed.name());
            row.setCode(seed.code());
            row.setSortOrder(seed.sort());
            row.setEnabled(1);
            row.setRemark("来自卖点Excel根域");
            domainMapper.insert(row);
        }
    }

    private Map<String, Long> rootDomainIds() {
        ensureRootDomains();
        Map<String, Long> map = new HashMap<>();
        for (GeoSellDomain d : domainMapper.selectList(new LambdaQueryWrapper<GeoSellDomain>()
                .eq(GeoSellDomain::getParentId, 0L))) {
            if (StringUtils.hasText(d.getCode())) {
                map.put(d.getCode(), d.getId());
            }
        }
        return map;
    }

    private SeedDomain matchSeed(String sheetName) {
        for (SeedDomain seed : ROOT_SEEDS) {
            if (sheetName.contains(seed.name()) || seed.name().contains(sheetName)
                    || sheetName.contains(seed.code())) {
                return seed;
            }
        }
        if (sheetName.contains("牧场")) {
            return ROOT_SEEDS.get(0);
        }
        if (sheetName.contains("矩阵") || sheetName.contains("客服") || sheetName.contains("渠道")) {
            return ROOT_SEEDS.get(1);
        }
        if (sheetName.contains("品控") || sheetName.contains("体验") || sheetName.contains("生产")) {
            return ROOT_SEEDS.get(2);
        }
        if (sheetName.contains("社会") || sheetName.contains("品牌")) {
            return ROOT_SEEDS.get(3);
        }
        if (sheetName.contains("背书") || sheetName.contains("认证") || sheetName.contains("权威")) {
            return ROOT_SEEDS.get(4);
        }
        return null;
    }

    private boolean isHeaderRow(String c0, String c1, String c2, String sheetName) {
        String joined = (nz(c0) + nz(c1) + nz(c2)).toLowerCase(Locale.ROOT);
        return joined.contains("维度") || joined.contains("模块") || joined.contains("分类")
                || joined.contains("填写说明") || joined.contains("信息总表")
                || "牧场信息总表".equals(c0)
                || "背书渠道".equals(c0.trim())
                || (joined.contains("背书渠道") && joined.contains("准入标准"));
    }

    private Set<Long> collectDomainIds(Long rootId) {
        List<GeoSellDomain> all = domainMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, List<Long>> children = new HashMap<>();
        for (GeoSellDomain d : all) {
            Long pid = d.getParentId() == null ? 0L : d.getParentId();
            children.computeIfAbsent(pid, k -> new ArrayList<>()).add(d.getId());
        }
        Set<Long> ids = new HashSet<>();
        collect(rootId, children, ids);
        return ids;
    }

    private void collect(Long id, Map<Long, List<Long>> children, Set<Long> out) {
        out.add(id);
        for (Long child : children.getOrDefault(id, List.of())) {
            collect(child, children, out);
        }
    }

    private Map<Long, String> loadDomainNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return domainMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GeoSellDomain::getId, GeoSellDomain::getName, (a, b) -> a));
    }

    private Map<Long, String> buildRootKindMap(List<GeoSellDomain> all) {
        Map<Long, GeoSellDomain> byId = all.stream()
                .collect(Collectors.toMap(GeoSellDomain::getId, d -> d, (a, b) -> a));
        Map<Long, String> result = new HashMap<>();
        for (GeoSellDomain d : all) {
            GeoSellDomain cur = d;
            while (cur != null) {
                Long pid = cur.getParentId() == null ? 0L : cur.getParentId();
                if (pid == 0L) {
                    result.put(d.getId(), kindOfRootCode(cur.getCode()));
                    break;
                }
                cur = byId.get(pid);
            }
            result.putIfAbsent(d.getId(), "simple");
        }
        return result;
    }

    private String resolveKind(GeoSellPointDTO dto) {
        if (StringUtils.hasText(dto.getKind())) {
            return normalizeKind(dto.getKind());
        }
        return resolveKindByDomainId(dto.getDomainId());
    }

    private String resolveKindByDomainId(Long domainId) {
        if (domainId == null) {
            return "simple";
        }
        List<GeoSellDomain> all = domainMapper.selectList(new LambdaQueryWrapper<>());
        return buildRootKindMap(all).getOrDefault(domainId, "simple");
    }

    private static String kindOfRootCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "simple";
        }
        return switch (code.trim()) {
            case "quality" -> "quality";
            case "social_brand", "social" -> "social";
            case "authority" -> "authority";
            default -> "simple";
        };
    }

    private static String normalizeKind(String kind) {
        String k = kind.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "quality" -> "quality";
            case "social", "social_brand" -> "social";
            case "authority" -> "authority";
            default -> "simple";
        };
    }

    private void requireDomain(Long domainId) {
        if (domainId == null || domainMapper.selectById(domainId) == null) {
            throw new BusinessException("卖点域不存在");
        }
    }

    private void applyDomain(GeoSellDomain row, GeoSellDomainDTO dto) {
        row.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        row.setName(dto.getName().trim());
        row.setCode(dto.getCode() == null ? "" : dto.getCode().trim());
        row.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        row.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        row.setRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
    }

    /** 索引表：title/claim/evidence 仅作展示与 AI 召回摘要，原生字段写明细表 */
    private void applyPointIndex(GeoSellPoint row, GeoSellPointDTO dto, String kind) {
        row.setDomainId(dto.getDomainId());
        row.setKind(kind);
        String status = dto.getStatus();
        if (!StringUtils.hasText(status)) {
            status = "active";
        }
        row.setStatus(status.trim());
        row.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        if (row.getSourceSheet() == null) {
            row.setSourceSheet("");
        }
        switch (kind) {
            case "quality" -> {
                if (!StringUtils.hasText(dto.getItemName()) || !StringUtils.hasText(dto.getStandardText())) {
                    throw new BusinessException("品控条目需填写「项目」和「内容/标准/要求」");
                }
                row.setTitle(safeTitle(dto.getItemName()));
                row.setClaim(firstSentence(dto.getStandardText()));
                row.setEvidence(nz(dto.getStandardText()));
                row.setEvidenceLevel("hard");
                row.setTagsJson(toJson(cleanTags(List.of(nz(dto.getModuleName()), nz(dto.getSectionName()),
                        safeTitle(dto.getItemName())))));
                row.setKeywordsJson(row.getTagsJson());
            }
            case "social" -> {
                if (!StringUtils.hasText(dto.getEventName()) || !StringUtils.hasText(dto.getActionContent())) {
                    throw new BusinessException("社会/品牌条目需填写「事件名」和「行动内容」");
                }
                row.setTitle(safeTitle(dto.getEventName()));
                row.setClaim(firstSentence(dto.getActionContent()));
                row.setEvidence(joinNonBlank("\n", dto.getActionContent(), dto.getEventRemark()));
                row.setEvidenceLevel("soft");
                row.setTagsJson(toJson(cleanTags(List.of(nz(dto.getDimension()), "公益", "品牌"))));
                row.setKeywordsJson(row.getTagsJson());
            }
            case "authority" -> {
                String channel = firstNonBlank(dto.getChannel(), dto.getTitle());
                if (!StringUtils.hasText(channel)) {
                    throw new BusinessException("权威背书条目需填写「背书渠道」");
                }
                String body = joinNonBlank("\n\n",
                        labelBlock("准入标准", dto.getAdmission()),
                        labelBlock("遴选流程", dto.getProcessText()),
                        labelBlock("背书意义", dto.getMeaning()),
                        StringUtils.hasText(dto.getExtraNote()) ? dto.getExtraNote().trim() : null);
                if (!StringUtils.hasText(body)) {
                    throw new BusinessException("权威背书条目至少填写一项内容");
                }
                row.setTitle(safeTitle(channel));
                row.setClaim(firstSentence(body));
                row.setEvidence(body);
                row.setEvidenceLevel("hard");
                row.setTagsJson(toJson(cleanTags(List.of("背书", "认证", safeTitle(channel)))));
                row.setKeywordsJson(row.getTagsJson());
            }
            default -> {
                if (!StringUtils.hasText(dto.getTitle())) {
                    throw new BusinessException("短标题不能为空");
                }
                row.setTitle(safeTitle(dto.getTitle()));
                row.setClaim(truncate(dto.getClaim() == null ? "" : dto.getClaim().trim(), 500));
                row.setEvidence(dto.getEvidence() == null ? "" : dto.getEvidence());
                row.setTagsJson(toJson(cleanTags(dto.getTags())));
                row.setKeywordsJson(toJson(cleanTags(dto.getKeywords() != null ? dto.getKeywords() : dto.getTags())));
                String level = dto.getEvidenceLevel();
                row.setEvidenceLevel("hard".equalsIgnoreCase(level) ? "hard" : "soft");
            }
        }
    }

    private void saveDetail(Long pointId, String kind, GeoSellPointDTO dto, boolean insert) {
        // 换类型时清掉其他明细，避免脏数据
        if (!"quality".equals(kind)) {
            qualityMapper.deleteById(pointId);
        }
        if (!"social".equals(kind)) {
            socialMapper.deleteById(pointId);
        }
        if (!"authority".equals(kind)) {
            authorityMapper.deleteById(pointId);
        }
        switch (kind) {
            case "quality" -> {
                GeoSellQuality q = new GeoSellQuality();
                q.setPointId(pointId);
                q.setModuleName(nz(dto.getModuleName()));
                q.setSectionName(nz(dto.getSectionName()));
                q.setItemName(safeTitle(dto.getItemName()));
                q.setStandardText(nz(dto.getStandardText()));
                if (insert || qualityMapper.selectById(pointId) == null) {
                    qualityMapper.insert(q);
                } else {
                    qualityMapper.updateById(q);
                }
            }
            case "social" -> {
                GeoSellSocial s = new GeoSellSocial();
                s.setPointId(pointId);
                s.setDimension(nz(dto.getDimension()));
                s.setEventName(safeTitle(dto.getEventName()));
                s.setActionContent(nz(dto.getActionContent()));
                s.setEventRemark(nz(dto.getEventRemark()));
                if (insert || socialMapper.selectById(pointId) == null) {
                    socialMapper.insert(s);
                } else {
                    socialMapper.updateById(s);
                }
            }
            case "authority" -> {
                GeoSellAuthority a = new GeoSellAuthority();
                a.setPointId(pointId);
                a.setChannel(safeTitle(firstNonBlank(dto.getChannel(), dto.getTitle())));
                a.setAdmission(nz(dto.getAdmission()));
                a.setProcessText(nz(dto.getProcessText()));
                a.setMeaning(nz(dto.getMeaning()));
                a.setExtraNote(nz(dto.getExtraNote()));
                if (insert || authorityMapper.selectById(pointId) == null) {
                    authorityMapper.insert(a);
                } else {
                    authorityMapper.updateById(a);
                }
            }
            default -> {
            }
        }
    }

    private void deleteDetails(List<Long> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        qualityMapper.deleteBatchIds(pointIds);
        socialMapper.deleteBatchIds(pointIds);
        authorityMapper.deleteBatchIds(pointIds);
    }

    private DetailBundle loadDetails(List<Long> pointIds) {
        DetailBundle bundle = new DetailBundle();
        if (pointIds == null || pointIds.isEmpty()) {
            return bundle;
        }
        for (GeoSellQuality q : qualityMapper.selectBatchIds(pointIds)) {
            bundle.quality.put(q.getPointId(), q);
        }
        for (GeoSellSocial s : socialMapper.selectBatchIds(pointIds)) {
            bundle.social.put(s.getPointId(), s);
        }
        for (GeoSellAuthority a : authorityMapper.selectBatchIds(pointIds)) {
            bundle.authority.put(a.getPointId(), a);
        }
        return bundle;
    }

    private GeoSellDomainVO toDomainVo(GeoSellDomain d) {
        GeoSellDomainVO vo = new GeoSellDomainVO();
        vo.setId(d.getId());
        vo.setParentId(d.getParentId());
        vo.setName(d.getName());
        vo.setCode(d.getCode());
        vo.setSortOrder(d.getSortOrder());
        vo.setEnabled(d.getEnabled());
        vo.setRemark(d.getRemark());
        return vo;
    }

    private GeoSellPointVO toPointVo(GeoSellPoint p, String domainName, DetailBundle details) {
        GeoSellPointVO vo = new GeoSellPointVO();
        vo.setId(p.getId());
        vo.setDomainId(p.getDomainId());
        vo.setDomainName(domainName);
        vo.setKind(StringUtils.hasText(p.getKind()) ? p.getKind() : "simple");
        vo.setTitle(p.getTitle());
        vo.setClaim(p.getClaim());
        vo.setEvidence(p.getEvidence());
        vo.setTags(parseJsonList(p.getTagsJson()));
        vo.setKeywords(parseJsonList(p.getKeywordsJson()));
        vo.setEvidenceLevel(p.getEvidenceLevel());
        vo.setStatus(p.getStatus());
        vo.setSortOrder(p.getSortOrder());
        vo.setSourceSheet(p.getSourceSheet());
        vo.setSourceRow(p.getSourceRow());
        vo.setEvidenceLen(p.getEvidence() == null ? 0 : p.getEvidence().length());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        GeoSellQuality q = details.quality.get(p.getId());
        if (q != null) {
            vo.setModuleName(q.getModuleName());
            vo.setSectionName(q.getSectionName());
            vo.setItemName(q.getItemName());
            vo.setStandardText(q.getStandardText());
            vo.setEvidenceLen(q.getStandardText() == null ? 0 : q.getStandardText().length());
        }
        GeoSellSocial s = details.social.get(p.getId());
        if (s != null) {
            vo.setDimension(s.getDimension());
            vo.setEventName(s.getEventName());
            vo.setActionContent(s.getActionContent());
            vo.setEventRemark(s.getEventRemark());
            vo.setEvidenceLen(s.getActionContent() == null ? 0 : s.getActionContent().length());
        }
        GeoSellAuthority a = details.authority.get(p.getId());
        if (a != null) {
            vo.setChannel(a.getChannel());
            vo.setAdmission(a.getAdmission());
            vo.setProcessText(a.getProcessText());
            vo.setMeaning(a.getMeaning());
            vo.setExtraNote(a.getExtraNote());
            int len = nz(a.getAdmission()).length() + nz(a.getProcessText()).length()
                    + nz(a.getMeaning()).length() + nz(a.getExtraNote()).length();
            vo.setEvidenceLen(len);
        }
        return vo;
    }

    private List<String> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> s.length() <= 40)
                .distinct()
                .limit(20)
                .toList();
    }

    private static List<String> readRow(Row row, int cols) {
        List<String> cells = new ArrayList<>(cols);
        for (int c = 0; c < cols; c++) {
            cells.add(cellText(row.getCell(c)));
        }
        return cells;
    }

    private static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return FORMATTER.formatCellValue(cell).trim()
                .replace('\u200B', ' ')
                .replace('\u00A0', ' ')
                .trim();
    }

    private static String firstNonBlank(String... parts) {
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                return p.trim();
            }
        }
        return "";
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!StringUtils.hasText(p)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(sep);
            }
            sb.append(p.trim());
        }
        return sb.toString();
    }

    private static String labelBlock(String label, String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        return "【" + label + "】\n" + body.trim();
    }

    private static String firstSentence(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String t = text.replace('\n', ' ').replace('\r', ' ').trim();
        int idx = -1;
        for (char ch : new char[]{'。', '！', '？', '.', '!', '?'}) {
            int i = t.indexOf(ch);
            if (i >= 0 && (idx < 0 || i < idx)) {
                idx = i;
            }
        }
        if (idx >= 0 && idx < 80) {
            return t.substring(0, idx + 1);
        }
        return truncate(t, 80);
    }

    private static String guessLevel(String evidence) {
        if (!StringUtils.hasText(evidence)) {
            return "soft";
        }
        String e = evidence.toLowerCase(Locale.ROOT);
        if (e.contains("认证") || e.contains("检测") || e.contains("%") || e.contains("国标")
                || e.contains("iso") || e.contains("十年") || e.contains("合规")) {
            return "hard";
        }
        return "soft";
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String normalizePointStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String s = status.trim();
        if ("生效".equals(s) || "active".equalsIgnoreCase(s)) {
            return "active";
        }
        if ("草稿".equals(s) || "draft".equalsIgnoreCase(s)) {
            return "draft";
        }
        if ("停用".equals(s) || "disabled".equalsIgnoreCase(s)) {
            return "disabled";
        }
        return s;
    }

    private static String normalizeEvidenceLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return null;
        }
        String s = level.trim();
        if ("软性".equals(s) || "soft".equalsIgnoreCase(s)) {
            return "soft";
        }
        if ("硬事实".equals(s) || "hard".equalsIgnoreCase(s)) {
            return "hard";
        }
        return s;
    }

    private record SeedDomain(String code, String name, int sort) {
    }

    /** 导入解析结果：按 kind 填对应字段；title/claim/evidence 仅作 AI 摘要 */
    private record ParsedRow(
            String kind,
            String category,
            String subCat,
            String title,
            String claim,
            String evidence,
            String level,
            List<String> tags,
            String moduleName,
            String sectionName,
            String itemName,
            String standardText,
            String dimension,
            String eventName,
            String actionContent,
            String eventRemark,
            String channel,
            String admission,
            String processText,
            String meaning,
            String extraNote
    ) {
        static ParsedRow simple(String category, String subCat, String title, String claim,
                                String evidence, String level, List<String> tags) {
            return new ParsedRow("simple", category, subCat, title, claim, evidence, level, tags,
                    null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        static ParsedRow quality(String category, String subCat, String module, String section,
                                 String item, String standard, List<String> tags) {
            return new ParsedRow("quality", category, subCat, item, firstSentence(standard), standard, "hard", tags,
                    module, section, item, standard, null, null, null, null, null, null, null, null, null);
        }

        static ParsedRow social(String category, String subCat, String dimension, String event,
                                String action, String remark, List<String> tags) {
            String body = joinNonBlank("\n", action, remark);
            return new ParsedRow("social", category, subCat, event, firstSentence(action), body, "soft", tags,
                    null, null, null, null, dimension, event, action, remark, null, null, null, null, null);
        }

        static ParsedRow authority(String category, String subCat, String channel, String admission,
                                   String process, String meaning, String extra, List<String> tags) {
            String body = joinNonBlank("\n\n",
                    labelBlock("准入标准", admission),
                    labelBlock("遴选流程", process),
                    labelBlock("背书意义", meaning),
                    StringUtils.hasText(extra) ? extra.trim() : null);
            if (!StringUtils.hasText(body) && StringUtils.hasText(extra)) {
                body = extra.trim();
            }
            return new ParsedRow("authority", category, subCat, channel, firstSentence(body), body, "hard", tags,
                    null, null, null, null, null, null, null, null, channel, admission, process, meaning, extra);
        }

        String displayTitle() {
            return title;
        }

        String aiClaim() {
            return claim == null ? "" : claim;
        }

        String aiEvidence() {
            return evidence == null ? "" : evidence;
        }
    }

    private static final class DetailBundle {
        final Map<Long, GeoSellQuality> quality = new HashMap<>();
        final Map<Long, GeoSellSocial> social = new HashMap<>();
        final Map<Long, GeoSellAuthority> authority = new HashMap<>();
    }
}
