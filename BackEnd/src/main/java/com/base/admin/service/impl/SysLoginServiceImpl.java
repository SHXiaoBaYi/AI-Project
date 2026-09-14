package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.LoginDTO;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.entity.SysUserRole;
import com.base.admin.domain.vo.LoginVO;
import com.base.admin.domain.vo.MenuVO;
import com.base.admin.domain.vo.UserInfoVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.SysMenuMapper;
import com.base.admin.mapper.SysRoleMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.mapper.SysUserRoleMapper;
import com.base.admin.security.JwtUtils;
import com.base.admin.security.LoginUser;
import com.base.admin.service.SysLoginService;
import com.base.admin.util.TreeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysLoginServiceImpl implements SysLoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 前置检查用户状态，防止被 Spring Security 吞掉具体异常
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (Integer.valueOf(Constants.STATUS_DISABLED).equals(user.getStatus())) {
            throw new BusinessException("该账户已被禁用，请联系管理员");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String token = jwtUtils.generateToken(loginUser.getUserId(), loginUser.getUsername());
        return LoginVO.builder().token(token).build();
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        List<SysRole> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds);
        List<String> roleKeys = roles.stream().map(SysRole::getRoleKey).collect(Collectors.toList());

        boolean isAdmin = roleKeys.contains(Constants.ADMIN_ROLE_KEY);

        Set<String> permissions;
        List<MenuVO> menus;
        if (isAdmin) {
            // 管理员直接返回所有激活状态的菜单
            permissions = new HashSet<>();
            permissions.add(Constants.ADMIN_PERM);
            menus = TreeUtil.buildMenuTree(menuMapper.selectAllMenus());
        } else {
            permissions = menuMapper.selectPermsByUserId(userId);
            menus = TreeUtil.buildMenuTree(menuMapper.selectMenusByUserId(userId));
        }

        return UserInfoVO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .roles(roleKeys)
                .permissions(permissions)
                .menus(menus)
                .build();
    }
}
