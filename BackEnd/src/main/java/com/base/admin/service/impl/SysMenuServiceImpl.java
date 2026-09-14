package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.MenuDTO;
import com.base.admin.domain.entity.SysMenu;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.vo.MenuVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.SysMenuMapper;
import com.base.admin.mapper.SysRoleMapper;
import com.base.admin.mapper.SysRoleMenuMapper;
import com.base.admin.service.SysMenuService;
import com.base.admin.util.TreeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @Override
    public List<SysMenu> list() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder));
    }

    @Override
    public List<MenuVO> tree() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder));
        return TreeUtil.buildMenuTree(menus);
    }

    @Override
    public SysMenu getById(Long menuId) {
        return menuMapper.selectById(menuId);
    }

    @Override
    public void create(MenuDTO dto) {
        SysMenu menu = new SysMenu();
        menu.setMenuName(dto.getMenuName());
        menu.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        menu.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        menu.setPath(dto.getPath());
        menu.setComponent(dto.getComponent());
        menu.setMenuType(dto.getMenuType());
        menu.setPerms(dto.getPerms());
        menu.setIcon(dto.getIcon());
        menu.setVisible(dto.getVisible() != null ? dto.getVisible() : 0);
        menu.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
        menu.setRemark(dto.getRemark());
        menuMapper.insert(menu);

        // Automatically assign the new menu to the admin role so that
        // role management permission tree stays in sync with what admin
        // actually sees via /auth/info (which bypasses sys_role_menu).
        List<SysRole> adminRoles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, Constants.ADMIN_ROLE_KEY));
        for (SysRole adminRole : adminRoles) {
            roleMenuMapper.insertRoleMenu(adminRole.getRoleId(), menu.getMenuId());
        }
    }

    @Override
    public void update(MenuDTO dto) {
        SysMenu menu = menuMapper.selectById(dto.getMenuId());
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        menu.setMenuName(dto.getMenuName());
        menu.setParentId(dto.getParentId());
        menu.setSortOrder(dto.getSortOrder());
        menu.setPath(dto.getPath());
        menu.setComponent(dto.getComponent());
        menu.setMenuType(dto.getMenuType());
        menu.setPerms(dto.getPerms());
        menu.setIcon(dto.getIcon());
        menu.setVisible(dto.getVisible());
        menu.setStatus(dto.getStatus());
        menu.setRemark(dto.getRemark());
        menuMapper.updateById(menu);
    }

    @Override
    public void delete(Long menuId) {
        long childCount = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, menuId));
        if (childCount > 0) {
            throw new BusinessException("请先删除子菜单");
        }
        // Clean up role-menu associations before deleting the menu
        roleMenuMapper.deleteByMenuId(menuId);
        menuMapper.deleteById(menuId);
    }
}
