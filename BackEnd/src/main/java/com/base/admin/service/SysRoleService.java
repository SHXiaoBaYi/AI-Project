package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.RoleDTO;
import com.base.admin.domain.dto.RoleMenuDTO;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.vo.RoleVO;

public interface SysRoleService {

    PageResult<RoleVO> list(String roleName, String roleKey, Integer status, Integer pageNum, Integer pageSize);

    RoleVO getById(Long roleId);

    void create(RoleDTO dto);

    void update(RoleDTO dto);

    void delete(Long roleId);

    void assignMenus(RoleMenuDTO dto);
}
