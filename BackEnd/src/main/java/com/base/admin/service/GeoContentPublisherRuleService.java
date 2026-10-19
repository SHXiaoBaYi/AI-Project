package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentPublisherRuleDTO;
import com.base.admin.domain.dto.GeoContentPublisherRuleQueryDTO;
import com.base.admin.domain.entity.GeoContentPublisherRule;

import java.util.List;
import java.util.Optional;

public interface GeoContentPublisherRuleService {

    PageResult<GeoContentPublisherRule> list(GeoContentPublisherRuleQueryDTO query);

    void create(GeoContentPublisherRuleDTO dto);

    void update(GeoContentPublisherRuleDTO dto);

    void delete(Long id);

    void deleteBatch(List<Long> ids);

    /**
     * 当前生效的默认发布人（未指定发布人的文章自动套用）。
     */
    Optional<GeoContentPublisherRule> resolveDefault();

    /** 供前端新增目标问题时预填；无默认规则时返回 empty */
    Optional<GeoContentPublisherRule> getDefaultForForm();
}
