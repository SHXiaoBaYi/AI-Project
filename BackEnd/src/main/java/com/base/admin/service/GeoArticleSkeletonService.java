package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoArticleSkeletonDTO;
import com.base.admin.domain.dto.GeoArticleSkeletonQueryDTO;
import com.base.admin.domain.entity.GeoArticleSkeleton;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoArticleSkeletonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeoArticleSkeletonService {

    private final GeoArticleSkeletonMapper skeletonMapper;
    private final ObjectMapper objectMapper;

    public PageResult<GeoArticleSkeleton> list(GeoArticleSkeletonQueryDTO query) {
        LambdaQueryWrapper<GeoArticleSkeleton> w = new LambdaQueryWrapper<GeoArticleSkeleton>()
                .and(StringUtils.hasText(query.getKeyword()), q -> q
                        .like(GeoArticleSkeleton::getName, query.getKeyword())
                        .or()
                        .like(GeoArticleSkeleton::getCode, query.getKeyword()))
                .eq(query.getEnabled() != null, GeoArticleSkeleton::getEnabled, query.getEnabled())
                .orderByAsc(GeoArticleSkeleton::getSortOrder)
                .orderByDesc(GeoArticleSkeleton::getId);
        Page<GeoArticleSkeleton> page = skeletonMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), w);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    public GeoArticleSkeleton get(Long id) {
        GeoArticleSkeleton row = skeletonMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("文章骨架不存在");
        }
        return row;
    }

    @Transactional
    public Long create(GeoArticleSkeletonDTO dto) {
        assertCodeUnique(dto.getCode().trim(), null);
        GeoArticleSkeleton row = fromDto(dto);
        skeletonMapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void update(GeoArticleSkeletonDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("骨架ID不能为空");
        }
        get(dto.getId());
        assertCodeUnique(dto.getCode().trim(), dto.getId());
        GeoArticleSkeleton row = fromDto(dto);
        row.setId(dto.getId());
        skeletonMapper.updateById(row);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        skeletonMapper.deleteById(id);
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

    private void assertCodeUnique(String code, Long excludeId) {
        Long cnt = skeletonMapper.selectCount(new LambdaQueryWrapper<GeoArticleSkeleton>()
                .eq(GeoArticleSkeleton::getCode, code)
                .ne(excludeId != null, GeoArticleSkeleton::getId, excludeId));
        if (cnt != null && cnt > 0) {
            throw new BusinessException("骨架编码已存在：" + code);
        }
    }

    private GeoArticleSkeleton fromDto(GeoArticleSkeletonDTO dto) {
        GeoArticleSkeleton row = new GeoArticleSkeleton();
        row.setCode(dto.getCode().trim());
        row.setName(dto.getName().trim());
        row.setSectionSchema(validateJson(dto.getSectionSchema()));
        row.setEnabled(dto.getEnabled() == null ? 1 : (dto.getEnabled() == 1 ? 1 : 0));
        row.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        return row;
    }

    private String validateJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "{}";
        }
        String t = raw.trim();
        try {
            objectMapper.readTree(t);
            return t;
        } catch (Exception e) {
            throw new BusinessException("章节骨架必须是合法 JSON");
        }
    }
}
