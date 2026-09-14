package com.base.admin.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.mapper.SysMenuMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.mapper.SysUserRoleMapper;
import com.base.admin.mapper.SysRoleMapper;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.entity.SysUserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        if (Integer.valueOf(Constants.STATUS_DISABLED).equals(user.getStatus())) {
            throw new UsernameNotFoundException("用户已被禁用，请联系管理员: " + username);
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        loginUser.setPassword(user.getPassword());

        Set<String> permissions = loadPermissions(user.getUserId());
        loginUser.setPermissions(permissions);

        return loginUser;
    }

    private Set<String> loadPermissions(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));

        boolean isAdmin = userRoles.stream().anyMatch(ur -> {
            SysRole role = roleMapper.selectById(ur.getRoleId());
            return role != null && Constants.ADMIN_ROLE_KEY.equals(role.getRoleKey());
        });

        if (isAdmin) {
            Set<String> perms = new HashSet<>();
            perms.add(Constants.ADMIN_PERM);
            return perms;
        }

        return menuMapper.selectPermsByUserId(userId);
    }
}
