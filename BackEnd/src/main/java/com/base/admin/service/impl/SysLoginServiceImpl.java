package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.DingTalkLoginDTO;
import com.base.admin.domain.dto.LoginDTO;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.entity.SysUserRole;
import com.base.admin.domain.vo.DingTalkLoginConfigVO;
import com.base.admin.domain.vo.LoginVO;
import com.base.admin.domain.vo.MenuVO;
import com.base.admin.domain.vo.UserInfoVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.SysMenuMapper;
import com.base.admin.mapper.SysRoleMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.mapper.SysUserRoleMapper;
import com.base.admin.security.JwtUtils;
import com.base.admin.service.DingTalkAppService;
import com.base.admin.service.DingTalkAuthService;
import com.base.admin.service.DingTalkCalendarClient;
import com.base.admin.service.OnlineSessionService;
import com.base.admin.service.SysLoginService;
import com.base.admin.util.TreeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysLoginServiceImpl implements SysLoginService {

    private final JwtUtils jwtUtils;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final OnlineSessionService onlineSessionService;
    private final DingTalkAppService dingTalkAppService;
    private final DingTalkAuthService dingTalkAuthService;
    private final DingTalkCalendarClient dingTalkCalendarClient;
    private final JdbcTemplate jdbc;

    @Override
    public LoginVO login(LoginDTO dto, String ip, String userAgent) {
        throw new BusinessException("本系统仅支持钉钉扫码登录");
    }

    @Override
    public DingTalkLoginConfigVO dingTalkLoginConfig() {
        return dingTalkAppService.loginConfig();
    }

    @Override
    public LoginVO loginByDingTalk(DingTalkLoginDTO dto, String ip, String userAgent) {
        DingTalkAuthService.UserProfile profile = dingTalkAuthService.resolveByAuthCode(dto.getAuthCode());
        BoundUser bound = resolveSystemUser(profile);
        SysUser user = userMapper.selectById(bound.userId());
        if (user == null || (user.getIsActive() != null && user.getIsActive() == 0)) {
            throw new BusinessException("系统账号不存在或已删除");
        }
        if (Integer.valueOf(Constants.STATUS_DISABLED).equals(user.getStatus())) {
            throw new BusinessException("该账户已被禁用，请联系管理员");
        }
        boolean force = Boolean.TRUE.equals(dto.getForce());
        if (!force && onlineSessionService.hasActiveSession(user.getUserId())) {
            throw new BusinessException(Constants.CODE_LOGIN_CONFLICT, "该账号已在其他设备登录，是否强制对方下线？");
        }
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token = jwtUtils.generateToken(user.getUserId(), user.getUsername(), tokenId);
        onlineSessionService.saveSession(user.getUserId(), user.getUsername(), tokenId, ip, userAgent);
        return LoginVO.builder().token(token).build();
    }

    private BoundUser resolveSystemUser(DingTalkAuthService.UserProfile profile) {
        BoundUser byUnion = findByUnionId(profile.unionId());
        if (byUnion != null) {
            return byUnion;
        }
        String dingUserId = dingTalkCalendarClient.useridByUnionId(profile.unionId());
        if (StringUtils.hasText(dingUserId)) {
            BoundUser byUserId = findByDingUserId(dingUserId);
            if (byUserId != null) {
                upsertBind(byUserId.userId(), dingUserId, profile.unionId());
                return byUserId;
            }
        }
        if (StringUtils.hasText(profile.mobile())) {
            BoundUser byPhone = findByPhone(profile.mobile());
            if (byPhone != null) {
                String userid = StringUtils.hasText(dingUserId) ? dingUserId : "";
                if (!StringUtils.hasText(userid) && dingTalkCalendarClient.configured()) {
                    try {
                        DingTalkCalendarClient.DingIdentity identity =
                                dingTalkCalendarClient.resolveByMobile(profile.mobile(), profile.nick());
                        if (identity != null) {
                            userid = identity.userId();
                            if (StringUtils.hasText(identity.unionId())) {
                                upsertBind(byPhone.userId(), userid, identity.unionId());
                                return byPhone;
                            }
                        }
                    } catch (Exception ignored) {
                        // 用扫码拿到的 unionId 绑定即可
                    }
                }
                if (!StringUtils.hasText(userid)) {
                    userid = "u_" + profile.unionId();
                }
                upsertBind(byPhone.userId(), userid, profile.unionId());
                return byPhone;
            }
        }
        throw new BusinessException("钉钉账号未绑定系统用户。请管理员在「用户管理」为该手机号用户绑定钉钉后再登录");
    }

    private BoundUser findByUnionId(String unionId) {
        return jdbc.query("""
                SELECT u.user_id, u.username
                FROM hr_user_dingtalk d
                INNER JOIN sys_user u ON u.user_id = d.user_id AND u.is_active = 1
                WHERE d.dingtalk_union_id = ? AND d.is_active = 1
                LIMIT 1
                """, rs -> rs.next() ? new BoundUser(rs.getLong("user_id"), rs.getString("username")) : null, unionId);
    }

    private BoundUser findByDingUserId(String dingUserId) {
        return jdbc.query("""
                SELECT u.user_id, u.username
                FROM hr_user_dingtalk d
                INNER JOIN sys_user u ON u.user_id = d.user_id AND u.is_active = 1
                WHERE d.dingtalk_user_id = ? AND d.is_active = 1
                LIMIT 1
                """, rs -> rs.next() ? new BoundUser(rs.getLong("user_id"), rs.getString("username")) : null, dingUserId);
    }

    private BoundUser findByPhone(String phone) {
        return jdbc.query("""
                SELECT user_id, username FROM sys_user
                WHERE is_active = 1 AND REPLACE(REPLACE(phone, ' ', ''), '-', '') = ?
                LIMIT 1
                """, rs -> rs.next() ? new BoundUser(rs.getLong("user_id"), rs.getString("username")) : null, phone);
    }

    private void upsertBind(Long userId, String dingUserId, String unionId) {
        jdbc.update("""
                INSERT INTO hr_user_dingtalk (user_id, dingtalk_user_id, dingtalk_union_id, create_by, is_active)
                VALUES (?, ?, ?, 'dingtalk-login', 1)
                ON DUPLICATE KEY UPDATE dingtalk_user_id = VALUES(dingtalk_user_id),
                  dingtalk_union_id = VALUES(dingtalk_union_id), is_active = 1,
                  update_by = 'dingtalk-login'
                """, userId, dingUserId, unionId);
    }

    @Override
    public void logout(Long userId) {
        onlineSessionService.removeSession(userId);
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

    private record BoundUser(Long userId, String username) {
    }
}
