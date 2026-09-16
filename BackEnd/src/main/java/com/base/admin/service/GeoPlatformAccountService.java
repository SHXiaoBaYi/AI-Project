package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoPlatformAccountDTO;
import com.base.admin.domain.dto.GeoPlatformAccountQueryDTO;
import com.base.admin.domain.vo.GeoPlatformAccountListVO;

public interface GeoPlatformAccountService {

    PageResult<GeoPlatformAccountListVO> list(GeoPlatformAccountQueryDTO query);

    GeoPlatformAccountListVO getById(Long id);

    void create(GeoPlatformAccountDTO dto);

    void update(GeoPlatformAccountDTO dto);

    void delete(Long id);
}
