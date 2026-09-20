package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoPlatformDTO;
import com.base.admin.domain.dto.GeoPlatformQueryDTO;
import com.base.admin.domain.entity.GeoPlatform;

import java.util.List;

public interface GeoPlatformService {

    PageResult<GeoPlatform> list(GeoPlatformQueryDTO query);

    List<GeoPlatform> listAll();

    List<GeoPlatform> listByType(String platformType);

    GeoPlatform getById(Long id);

    void create(GeoPlatformDTO dto);

    void update(GeoPlatformDTO dto);

    void delete(Long id);

    void deleteBatch(List<Long> ids);

    /** 按 AI平台 类型 getOrCreate（日监测/导入默认） */
    GeoPlatform getOrCreate(String platformName);

    GeoPlatform getOrCreate(String platformName, String platformType);
}
