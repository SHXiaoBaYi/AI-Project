package com.base.admin.service;

import com.base.admin.domain.dto.MenuDTO;
import com.base.admin.domain.entity.SysMenu;
import com.base.admin.domain.vo.MenuVO;

import java.util.List;

public interface SysMenuService {

    List<SysMenu> list();

    List<MenuVO> tree();

    SysMenu getById(Long menuId);

    void create(MenuDTO dto);

    void update(MenuDTO dto);

    void delete(Long menuId);
}
