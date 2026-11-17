package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoPlatformAccountDTO;
import com.base.admin.domain.dto.GeoPlatformAccountQueryDTO;
import com.base.admin.domain.vo.GeoPlatformAccountListVO;

import java.util.List;

public interface GeoPlatformAccountService {

    PageResult<GeoPlatformAccountListVO> list(GeoPlatformAccountQueryDTO query);

    GeoPlatformAccountListVO getById(Long id);

    void create(GeoPlatformAccountDTO dto);

    void update(GeoPlatformAccountDTO dto);

    void delete(Long id);

    void deleteBatch(List<Long> ids);

    /** 加入或移出浏览器监测候选池，不删除账号和登录态文件。 */
    void setBrowserEnabled(Long id, Integer enabled);
}
