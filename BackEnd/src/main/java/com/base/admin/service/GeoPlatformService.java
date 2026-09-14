package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoPlatformDTO;
import com.base.admin.domain.dto.GeoPlatformQueryDTO;
import com.base.admin.domain.entity.GeoPlatform;

import java.util.List;

public interface GeoPlatformService {

    PageResult<GeoPlatform> list(GeoPlatformQueryDTO query);

    List<GeoPlatform> listAll();

    GeoPlatform getById(Long id);

    void create(GeoPlatformDTO dto);

    void update(GeoPlatformDTO dto);

    void delete(Long id);

    GeoPlatform getOrCreate(String platformName);
}
