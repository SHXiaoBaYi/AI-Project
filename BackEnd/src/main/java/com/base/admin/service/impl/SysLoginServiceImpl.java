package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.DingTalkLoginDTO;
import com.base.admin.domain.dto.LoginDTO;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.entity.SysUserRole;
import com.base.admin.domain.vo.DingTalkLoginConfigVO;
import com.base.admin.domain.vo.LoginOptionsVO;
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
import com.base.admin.service.DingTalkAppService;
import com.base.admin.service.DingTalkAuthService;
import com.base.admin.service.DingTalkCalendarClient;
import com.base.admin.service.OnlineSessionService;
import com.base.admin.service.SysLoginService;
import com.base.admin.util.TreeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Override
    public LoginVO login(LoginDTO dto, String ip, String userAgent, HttpServletRequest request) {
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
        boolean force = Boolean.TRUE.equals(dto.getForce());
        if (!force && onlineSessionService.hasActiveSession(loginUser.getUserId())) {
            throw new BusinessException(Constants.CODE_LOGIN_CONFLICT, "该账号已在其他设备登录，是否强制对方下线？");
        }
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token = jwtUtils.generateToken(loginUser.getUserId(), loginUser.getUsername(), tokenId);
        onlineSessionService.saveSession(loginUser.getUserId(), loginUser.getUsername(), tokenId, ip, userAgent);
        return LoginVO.builder().token(token).build();
    }

    @Override
    public LoginOptionsVO loginOptions(HttpServletRequest request) {
        LoginOptionsVO vo = new LoginOptionsVO();
        vo.setPasswordLoginEnabled(true);
        DingTalkLoginConfigVO ding = dingTalkAppService.loginConfig();
        vo.setDingTalkEnabled(ding.getEnabled() != null && ding.getEnabled() == 1);
        vo.setClientId(ding.getClientId());
        vo.setCorpId(ding.getCorpId());
        vo.setExclusiveLogin(ding.getExclusiveLogin());
        if (Boolean.TRUE.equals(vo.getDingTalkEnabled())) {
            vo.setMessage("可使用账号密码或钉钉扫码登录");
        } else {
            vo.setMessage("请使用账号密码登录");
        }
        return vo;
    }

    @Override
    public DingTalkLoginConfigVO dingTalkLoginConfig() {
        return dingTalkAppService.loginConfig();
    }

    @Override
    @Transactional
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
        // 企业账号扫码通过但系统无此用户 → 自动注册为普通账号（hr_user）
        if (!StringUtils.hasText(dingUserId)) {
            throw new BusinessException("未能确认你为本企业钉钉成员，无法自动注册。请联系管理员");
        }
        return createUserFromDingTalk(profile, dingUserId);
    }

    private BoundUser createUserFromDingTalk(DingTalkAuthService.UserProfile profile, String dingUserId) {
        Long roleId = jdbc.query("SELECT role_id FROM sys_role WHERE role_key = ? AND is_active = 1 AND status = 0 LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, Constants.HR_USER_ROLE_KEY);
        if (roleId == null) {
            throw new BusinessException("系统缺少「普通账号」角色（hr_user），请联系管理员初始化角色后再登录");
        }
        String username = uniqueUsername(profile, dingUserId);
        String nickname = StringUtils.hasText(profile.nick()) ? profile.nick().trim() : username;
        String phone = StringUtils.hasText(profile.mobile()) ? profile.mobile().trim() : null;
        // 密码登录已关闭，写入随机不可用哈希即可
        String passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordHash);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setAvatar(StringUtils.hasText(profile.avatar()) ? profile.avatar().trim() : null);
        user.setGender(0);
        user.setStatus(Constants.STATUS_ACTIVE);
        user.setRemark("钉钉扫码自动注册");
        userMapper.insert(user);

        userRoleMapper.insertUserRole(user.getUserId(), roleId);

        upsertBind(user.getUserId(), dingUserId, profile.unionId());
        return new BoundUser(user.getUserId(), user.getUsername());
    }

    private String uniqueUsername(DingTalkAuthService.UserProfile profile, String dingUserId) {
        String base;
        if (StringUtils.hasText(profile.mobile())) {
            base = profile.mobile().trim();
        } else {
            String raw = dingUserId.replaceAll("[^a-zA-Z0-9_]", "");
            if (!StringUtils.hasText(raw)) {
                raw = profile.unionId().replaceAll("[^a-zA-Z0-9_]", "");
            }
            if (raw.length() > 28) {
                raw = raw.substring(raw.length() - 28);
            }
            base = "dt_" + raw;
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String candidate = base;
        int i = 0;
        while (true) {
            Long exists = userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, candidate));
            if (exists == null || exists == 0) {
                return candidate;
            }
            i++;
            String suffix = "_" + i;
            int max = Math.max(1, 40 - suffix.length());
            candidate = base.substring(0, Math.min(base.length(), max)) + suffix;
            if (i > 50) {
                return "dt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
        }
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
