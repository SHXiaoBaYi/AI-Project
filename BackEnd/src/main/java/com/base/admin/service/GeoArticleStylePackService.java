package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoArticleStylePackDTO;
import com.base.admin.domain.dto.GeoArticleStylePackQueryDTO;
import com.base.admin.domain.entity.GeoArticleStylePack;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoArticleStylePackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeoArticleStylePackService {

    private final GeoArticleStylePackMapper stylePackMapper;

    public PageResult<GeoArticleStylePack> list(GeoArticleStylePackQueryDTO query) {
        LambdaQueryWrapper<GeoArticleStylePack> w = new LambdaQueryWrapper<GeoArticleStylePack>()
                .and(StringUtils.hasText(query.getKeyword()), q -> q
                        .like(GeoArticleStylePack::getName, query.getKeyword())
                        .or()
                        .like(GeoArticleStylePack::getCode, query.getKeyword()))
                .eq(StringUtils.hasText(query.getPlatformHint()), GeoArticleStylePack::getPlatformHint, query.getPlatformHint())
                .eq(query.getEnabled() != null, GeoArticleStylePack::getEnabled, query.getEnabled())
                .orderByAsc(GeoArticleStylePack::getSortOrder)
                .orderByDesc(GeoArticleStylePack::getId);
        Page<GeoArticleStylePack> page = stylePackMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), w);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    public GeoArticleStylePack get(Long id) {
        GeoArticleStylePack row = stylePackMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("风格包不存在");
        }
        return row;
    }

    @Transactional
    public Long create(GeoArticleStylePackDTO dto) {
        assertCodeUnique(dto.getCode().trim(), null);
        GeoArticleStylePack row = fromDto(dto);
        stylePackMapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void update(GeoArticleStylePackDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("风格包ID不能为空");
        }
        get(dto.getId());
        assertCodeUnique(dto.getCode().trim(), dto.getId());
        GeoArticleStylePack row = fromDto(dto);
        row.setId(dto.getId());
        stylePackMapper.updateById(row);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        stylePackMapper.deleteById(id);
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
        Long cnt = stylePackMapper.selectCount(new LambdaQueryWrapper<GeoArticleStylePack>()
                .eq(GeoArticleStylePack::getCode, code)
                .ne(excludeId != null, GeoArticleStylePack::getId, excludeId));
        if (cnt != null && cnt > 0) {
            throw new BusinessException("风格包编码已存在：" + code);
        }
    }

    private GeoArticleStylePack fromDto(GeoArticleStylePackDTO dto) {
        GeoArticleStylePack row = new GeoArticleStylePack();
        row.setCode(dto.getCode().trim());
        row.setName(dto.getName().trim());
        row.setPlatformHint(dto.getPlatformHint() == null ? "" : dto.getPlatformHint().trim());
        row.setToneGuide(dto.getToneGuide());
        row.setStructureGuide(dto.getStructureGuide());
        row.setSamplePhrases(normalizeSamplePhrases(dto.getSamplePhrases()));
        row.setBrandGuide(dto.getBrandGuide() == null ? "" : dto.getBrandGuide().trim());
        row.setEnabled(dto.getEnabled() == null ? 1 : (dto.getEnabled() == 1 ? 1 : 0));
        row.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        return row;
    }

    private String normalizeSamplePhrases(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "[]";
        }
        String t = raw.trim();
        if (!t.startsWith("[")) {
            // 逗号/换行分隔转 JSON 数组
            String[] parts = t.split("[,，\\n]+");
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (String p : parts) {
                String s = p.trim();
                if (s.isEmpty()) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
            sb.append(']');
            return sb.toString();
        }
        return t;
    }
}
