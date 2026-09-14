package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoTopicDTO;
import com.base.admin.domain.dto.GeoTopicQueryDTO;
import com.base.admin.domain.entity.GeoTopic;

import java.util.List;

public interface GeoTopicService {

    PageResult<GeoTopic> list(GeoTopicQueryDTO query);

    List<GeoTopic> listAll();

    GeoTopic getById(Long id);

    void create(GeoTopicDTO dto);

    void update(GeoTopicDTO dto);

    void delete(Long id);

    GeoTopic getOrCreate(String topicName, String optimizeWeek);
}
