package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.Constants;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.RoleDTO;
import com.base.admin.domain.dto.RoleMenuDTO;
import com.base.admin.domain.entity.SysMenu;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.entity.SysUserRole;
import com.base.admin.domain.vo.RoleVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.SysMenuMapper;
import com.base.admin.mapper.SysRoleMapper;
import com.base.admin.mapper.SysRoleMenuMapper;
import com.base.admin.mapper.SysUserRoleMapper;
import com.base.admin.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public PageResult<RoleVO> list(String roleName, String roleKey, Integer status,
                                   Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(roleName), SysRole::getRoleName, roleName)
                .like(StringUtils.hasText(roleKey), SysRole::getRoleKey, roleKey)
                .eq(status != null, SysRole::getStatus, status)
                .orderByAsc(SysRole::getSortOrder);

        Page<SysRole> page = roleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<RoleVO> rows = page.getRecords().stream().map(r -> {
            RoleVO vo = new RoleVO();
            vo.setRoleId(r.getRoleId());
            vo.setRoleName(r.getRoleName());
            vo.setRoleKey(r.getRoleKey());
            vo.setSortOrder(r.getSortOrder());
            vo.setStatus(r.getStatus());
            vo.setRemark(r.getRemark());
            vo.setCreateTime(r.getCreateTime());
            vo.setMenuIds(getMenuIdsForRole(r));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public RoleVO getById(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("未找到角色权限");
        }
        RoleVO vo = new RoleVO();
        vo.setRoleId(role.getRoleId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleKey(role.getRoleKey());
        vo.setSortOrder(role.getSortOrder());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreateTime(role.getCreateTime());
        vo.setMenuIds(getMenuIdsForRole(role));
        return vo;
    }

    @Override
    public void create(RoleDTO dto) {
        long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, dto.getRoleKey()));
        if (count > 0) {
            throw new BusinessException("角色已存在");
        }
        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName());
        role.setRoleKey(dto.getRoleKey());
        role.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        role.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
        role.setRemark(dto.getRemark());
        roleMapper.insert(role);
    }

    @Override
    public void update(RoleDTO dto) {
        SysRole role = roleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        role.setRoleName(dto.getRoleName());
        role.setRoleKey(dto.getRoleKey());
        role.setSortOrder(dto.getSortOrder());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());
        roleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void delete(Long roleId) {
        long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
        if (userCount > 0) {
            throw new BusinessException("当前角色已被分配给用户，不允许删除");
        }
        roleMapper.deleteById(roleId);
        roleMenuMapper.deleteByRoleId(roleId);
    }

    @Override
    @Transactional
    public void assignMenus(RoleMenuDTO dto) {
        List<Long> validMenuIds = dto.getMenuIds() != null
                ? new ArrayList<>(dto.getMenuIds()) : new ArrayList<>();

        if (!validMenuIds.isEmpty()) {
            List<SysMenu> allMenus = menuMapper.selectList(null);
            Map<Long, SysMenu> menuMap = allMenus.stream()
                    .collect(Collectors.toMap(SysMenu::getMenuId, m -> m));
            Set<Long> disabledIds = new HashSet<>();

            for (SysMenu menu : allMenus) {
                if (menu.getStatus() == 1) {
                    disabledIds.add(menu.getMenuId());
                    continue;
                }
                // Check if any ancestor is disabled
                Long parentId = menu.getParentId();
                while (parentId != null && parentId != 0) {
                    SysMenu parent = menuMap.get(parentId);
                    if (parent != null && parent.getStatus() == 1) {
                        disabledIds.add(menu.getMenuId());
                        break;
                    }
                    parentId = parent != null ? parent.getParentId() : 0;
                }
            }

            validMenuIds = validMenuIds.stream()
                    .filter(id -> !disabledIds.contains(id))
                    .toList();
        }

        roleMenuMapper.deleteByRoleId(dto.getRoleId());
        for (Long menuId : validMenuIds) {
            roleMenuMapper.insertRoleMenu(dto.getRoleId(), menuId);
        }
    }

    private List<Long> getMenuIdsForRole(SysRole role) {
        if (Constants.ADMIN_ROLE_KEY.equals(role.getRoleKey())) {
            return menuMapper.selectAllActiveMenuIds();
        }
        return roleMenuMapper.selectMenuIdsByRoleId(role.getRoleId());
    }
}
