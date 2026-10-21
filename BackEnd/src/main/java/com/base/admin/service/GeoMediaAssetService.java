package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoMediaAssetBatchMetaDTO;
import com.base.admin.domain.dto.GeoMediaAssetDTO;
import com.base.admin.domain.dto.GeoMediaAssetQueryDTO;
import com.base.admin.domain.entity.GeoMediaAsset;
import com.base.admin.domain.entity.GeoMediaAssetTopic;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.vo.GeoMediaAssetVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoMediaAssetMapper;
import com.base.admin.mapper.GeoMediaAssetTopicMapper;
import com.base.admin.mapper.GeoTopicMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoMediaAssetService {

    private final GeoMediaAssetMapper assetMapper;
    private final GeoMediaAssetTopicMapper assetTopicMapper;
    private final GeoTopicMapper topicMapper;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public PageResult<GeoMediaAssetVO> list(GeoMediaAssetQueryDTO query) {
        Set<Long> topicFilterIds = null;
        if (query.getTopicId() != null) {
            List<Long> ids = assetMapper.listAssetIdsByTopic(query.getTopicId());
            topicFilterIds = new HashSet<>(ids == null ? List.of() : ids);
            if (topicFilterIds.isEmpty()) {
                return new PageResult<>(0, Collections.emptyList());
            }
        }
        LambdaQueryWrapper<GeoMediaAsset> w = new LambdaQueryWrapper<GeoMediaAsset>()
                .like(StringUtils.hasText(query.getTitle()), GeoMediaAsset::getTitle, query.getTitle())
                .eq(StringUtils.hasText(query.getStatus()), GeoMediaAsset::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getSourceType()), GeoMediaAsset::getSourceType, query.getSourceType())
                .eq(query.getBrandVisible() != null, GeoMediaAsset::getBrandVisible, query.getBrandVisible())
                .in(topicFilterIds != null, GeoMediaAsset::getId, topicFilterIds)
                .and(StringUtils.hasText(query.getKeyword()), q -> q
                        .like(GeoMediaAsset::getTitle, query.getKeyword())
                        .or().like(GeoMediaAsset::getUsageHint, query.getKeyword())
                        .or().like(GeoMediaAsset::getTagsJson, query.getKeyword())
                        .or().like(GeoMediaAsset::getSceneTagsJson, query.getKeyword())
                        .or().like(GeoMediaAsset::getCaption, query.getKeyword()))
                .orderByDesc(GeoMediaAsset::getId);
        Page<GeoMediaAsset> page = assetMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), w);
        List<GeoMediaAssetVO> rows = page.getRecords().stream().map(this::toVo).toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    public GeoMediaAssetVO get(Long id) {
        return toVo(require(id));
    }

    @Transactional
    public Long create(GeoMediaAssetDTO dto) {
        validateDto(dto);
        GeoMediaAsset asset = fromDto(dto);
        assetMapper.insert(asset);
        replaceTopics(asset.getId(), dto.getTopicIds());
        return asset.getId();
    }

    @Transactional
    public void update(GeoMediaAssetDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("素材ID不能为空");
        }
        validateDto(dto);
        require(dto.getId());
        GeoMediaAsset asset = fromDto(dto);
        asset.setId(dto.getId());
        assetMapper.updateById(asset);
        replaceTopics(dto.getId(), dto.getTopicIds() == null ? List.of() : dto.getTopicIds());
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        assetMapper.deleteById(id);
        assetTopicMapper.delete(new LambdaQueryWrapper<GeoMediaAssetTopic>()
                .eq(GeoMediaAssetTopic::getAssetId, id));
    }

    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            delete(id);
        }
    }

    public String upload(MultipartFile file) {
        return fileStorageService.saveGeoImage(file);
    }

    /**
     * 批量上传：每张图入库一条素材，标题默认用文件名；可带统一用途/标签。
     */
    @Transactional
    public List<Long> batchUpload(MultipartFile[] files, String usageHint, List<String> tags,
                                  List<String> sceneTags, String sourceType, String licenseNote) {
        if (files == null || files.length == 0) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (files.length > 40) {
            throw new BusinessException("单次最多上传 40 张");
        }
        String source = StringUtils.hasText(sourceType) ? sourceType.trim() : "own";
        if (!"own".equals(source) && !"stock_licensed".equals(source)) {
            throw new BusinessException("素材来源仅支持 own 或 stock_licensed");
        }
        if ("stock_licensed".equals(source) && !StringUtils.hasText(licenseNote)) {
            throw new BusinessException("授权库存图请填写授权说明");
        }
        List<Long> ids = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String path = fileStorageService.saveGeoImage(file);
            String original = file.getOriginalFilename() == null ? "未命名" : file.getOriginalFilename();
            String title = original.contains(".")
                    ? original.substring(0, original.lastIndexOf('.'))
                    : original;
            if (!StringUtils.hasText(title)) {
                title = "未命名素材";
            }
            if (title.length() > 180) {
                title = title.substring(0, 180);
            }
            GeoMediaAsset asset = new GeoMediaAsset();
            asset.setTitle(title.trim());
            asset.setFileUrl(path);
            asset.setThumbUrl("");
            asset.setSourceType(source);
            asset.setLicenseNote(licenseNote == null ? "" : licenseNote.trim());
            asset.setBrandVisible(0);
            asset.setCaption("");
            asset.setAltText(title.trim());
            asset.setTagsJson(writeTags(tags));
            asset.setUsageHint(usageHint == null ? "" : usageHint.trim());
            asset.setSceneTagsJson(writeTags(sceneTags));
            asset.setStatus("active");
            asset.setRemark("");
            assetMapper.insert(asset);
            ids.add(asset.getId());
        }
        if (ids.isEmpty()) {
            throw new BusinessException("没有成功上传的图片");
        }
        return ids;
    }

    /** 批量改语义/用途（只更新传入的非 null 字段） */
    @Transactional
    public int batchUpdateMeta(GeoMediaAssetBatchMetaDTO dto) {
        if (dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException("请选择素材");
        }
        if (dto.getTopicIds() != null && !dto.getTopicIds().isEmpty()) {
            assertTopicsExist(dto.getTopicIds());
        }
        if (StringUtils.hasText(dto.getSourceType())) {
            String source = dto.getSourceType().trim();
            if (!"own".equals(source) && !"stock_licensed".equals(source)) {
                throw new BusinessException("素材来源仅支持 own 或 stock_licensed");
            }
        }
        int n = 0;
        for (Long id : dto.getIds()) {
            GeoMediaAsset asset = assetMapper.selectById(id);
            if (asset == null) {
                continue;
            }
            if (dto.getUsageHint() != null) {
                asset.setUsageHint(dto.getUsageHint().trim());
            }
            if (dto.getTags() != null) {
                asset.setTagsJson(writeTags(dto.getTags()));
            }
            if (dto.getSceneTags() != null) {
                asset.setSceneTagsJson(writeTags(dto.getSceneTags()));
            }
            if (dto.getBrandVisible() != null) {
                asset.setBrandVisible(dto.getBrandVisible() == 1 ? 1 : 0);
            }
            if (StringUtils.hasText(dto.getStatus())) {
                asset.setStatus(dto.getStatus().trim());
            }
            if (StringUtils.hasText(dto.getSourceType())) {
                asset.setSourceType(dto.getSourceType().trim());
            }
            if (dto.getLicenseNote() != null) {
                asset.setLicenseNote(dto.getLicenseNote().trim());
            }
            assetMapper.updateById(asset);
            if (dto.getTopicIds() != null) {
                replaceTopics(id, dto.getTopicIds());
            }
            n++;
        }
        return n;
    }

    /**
     * 语义召回：按用途/标签/标题与上下文匹配打分；话题关联仅作软加分。
     */
    public List<GeoMediaAsset> recallForArticle(Long topicId, String contextText, int limit) {
        int pool = Math.max(40, Math.min(limit * 4, 200));
        List<GeoMediaAsset> all = assetMapper.listActive(pool);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        Set<Long> topicLinked = new HashSet<>();
        if (topicId != null) {
            List<Long> ids = assetMapper.listAssetIdsByTopic(topicId);
            if (ids != null) {
                topicLinked.addAll(ids);
            }
        }
        Set<String> tokens = tokenize(contextText);
        List<Scored> scored = new ArrayList<>();
        for (GeoMediaAsset a : all) {
            int score = 1;
            if (topicLinked.contains(a.getId())) {
                score += 8;
            }
            String hay = (nz(a.getTitle()) + " " + nz(a.getCaption()) + " " + nz(a.getUsageHint())
                    + " " + nz(a.getTagsJson()) + " " + nz(a.getSceneTagsJson())
                    + " " + nz(a.getAltText())).toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (t.length() >= 2 && hay.contains(t)) {
                    score += 3;
                }
            }
            // 有用途说明的素材优先进入候选
            if (StringUtils.hasText(a.getUsageHint())) {
                score += 2;
            }
            scored.add(new Scored(a, score));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparing(s -> -s.asset().getId()));
        int cap = Math.max(1, Math.min(limit, 40));
        return scored.stream().limit(cap).map(Scored::asset).toList();
    }

    private void validateDto(GeoMediaAssetDTO dto) {
        String source = StringUtils.hasText(dto.getSourceType()) ? dto.getSourceType().trim() : "own";
        if (!"own".equals(source) && !"stock_licensed".equals(source)) {
            throw new BusinessException("素材来源仅支持 own（自有）或 stock_licensed（授权库存图）");
        }
        if ("stock_licensed".equals(source) && !StringUtils.hasText(dto.getLicenseNote())) {
            throw new BusinessException("授权库存图请填写授权说明");
        }
        if (dto.getTopicIds() != null && !dto.getTopicIds().isEmpty()) {
            assertTopicsExist(dto.getTopicIds());
        }
    }

    private void assertTopicsExist(List<Long> topicIds) {
        Set<Long> uniq = new HashSet<>(topicIds);
        Long cnt = topicMapper.selectCount(new LambdaQueryWrapper<GeoTopic>().in(GeoTopic::getId, uniq));
        if (cnt == null || cnt.intValue() != uniq.size()) {
            throw new BusinessException("关联话题不存在或已删除");
        }
    }

    private void replaceTopics(Long assetId, List<Long> topicIds) {
        assetTopicMapper.delete(new LambdaQueryWrapper<GeoMediaAssetTopic>()
                .eq(GeoMediaAssetTopic::getAssetId, assetId));
        if (topicIds == null || topicIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long topicId : new HashSet<>(topicIds)) {
            if (topicId == null) {
                continue;
            }
            GeoMediaAssetTopic link = new GeoMediaAssetTopic();
            link.setAssetId(assetId);
            link.setTopicId(topicId);
            link.setCreateTime(now);
            assetTopicMapper.insert(link);
        }
    }

    private GeoMediaAsset require(Long id) {
        GeoMediaAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException("素材不存在");
        }
        return asset;
    }

    private GeoMediaAsset fromDto(GeoMediaAssetDTO dto) {
        GeoMediaAsset asset = new GeoMediaAsset();
        asset.setTitle(dto.getTitle().trim());
        asset.setFileUrl(dto.getFileUrl().trim());
        asset.setThumbUrl(dto.getThumbUrl() == null ? "" : dto.getThumbUrl().trim());
        asset.setSourceType(StringUtils.hasText(dto.getSourceType()) ? dto.getSourceType().trim() : "own");
        asset.setLicenseNote(dto.getLicenseNote() == null ? "" : dto.getLicenseNote().trim());
        asset.setBrandVisible(dto.getBrandVisible() == null ? 0 : (dto.getBrandVisible() == 1 ? 1 : 0));
        asset.setCaption(dto.getCaption() == null ? "" : dto.getCaption().trim());
        asset.setAltText(dto.getAltText() == null ? "" : dto.getAltText().trim());
        asset.setTagsJson(writeTags(dto.getTags()));
        asset.setUsageHint(dto.getUsageHint() == null ? "" : dto.getUsageHint().trim());
        asset.setSceneTagsJson(writeTags(dto.getSceneTags()));
        asset.setWidthPx(dto.getWidthPx());
        asset.setHeightPx(dto.getHeightPx());
        asset.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus().trim() : "active");
        asset.setRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
        return asset;
    }

    private GeoMediaAssetVO toVo(GeoMediaAsset asset) {
        GeoMediaAssetVO vo = new GeoMediaAssetVO();
        vo.setId(asset.getId());
        vo.setTitle(asset.getTitle());
        vo.setFileUrl(asset.getFileUrl());
        vo.setPublicUrl(fileStorageService.toPublicUrl(asset.getFileUrl()));
        vo.setThumbUrl(asset.getThumbUrl());
        vo.setSourceType(asset.getSourceType());
        vo.setLicenseNote(asset.getLicenseNote());
        vo.setBrandVisible(asset.getBrandVisible());
        vo.setCaption(asset.getCaption());
        vo.setAltText(asset.getAltText());
        vo.setTags(readTags(asset.getTagsJson()));
        vo.setUsageHint(asset.getUsageHint());
        vo.setSceneTags(readTags(asset.getSceneTagsJson()));
        vo.setWidthPx(asset.getWidthPx());
        vo.setHeightPx(asset.getHeightPx());
        vo.setStatus(asset.getStatus());
        vo.setRemark(asset.getRemark());
        vo.setCreateTime(asset.getCreateTime());
        vo.setUpdateTime(asset.getUpdateTime());
        List<Long> topicIds = assetTopicMapper.listTopicIds(asset.getId());
        vo.setTopicIds(topicIds);
        if (topicIds != null && !topicIds.isEmpty()) {
            List<GeoTopic> topics = topicMapper.selectBatchIds(topicIds);
            Map<Long, String> nameMap = new HashMap<>();
            for (GeoTopic t : topics) {
                nameMap.put(t.getId(), t.getTopicName());
            }
            vo.setTopicNames(topicIds.stream().map(nameMap::get).filter(Objects::nonNull).toList());
        } else {
            vo.setTopicNames(List.of());
        }
        return vo;
    }

    private String writeTags(List<String> tags) {
        try {
            List<String> clean = tags == null ? List.of() : tags.stream()
                    .filter(StringUtils::hasText)
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
            return objectMapper.writeValueAsString(clean);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readTags(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Set<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s,，、;；|/\\\\]+"))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .limit(40)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private record Scored(GeoMediaAsset asset, int score) {}
}
