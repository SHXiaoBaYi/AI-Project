package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.SysTaskTypeDTO;
import com.base.admin.domain.dto.SysTaskTypeQueryDTO;
import com.base.admin.domain.entity.SysTaskType;

import java.util.List;

public interface SysTaskTypeService {

    PageResult<SysTaskType> list(SysTaskTypeQueryDTO query);

    List<SysTaskType> listOptions();

    void create(SysTaskTypeDTO dto);

    void update(SysTaskTypeDTO dto);

    void delete(Long id);

    /** 校验类型名是否存在；空则返回默认类型名 */
    String resolveTypeName(String raw);

    SysTaskType getByTypeName(String typeName);
}
