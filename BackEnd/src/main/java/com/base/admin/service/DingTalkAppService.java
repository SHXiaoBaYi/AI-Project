package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkAppDTO;
import com.base.admin.domain.vo.DingTalkAppVO;
import com.base.admin.domain.vo.DingTalkLoginConfigVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DingTalkAppService {

    /** 唯一可切换机器人本机/线上的账号 */
    public static final String ROBOT_DEBUG_USERNAME = "wangfangyang";
    public static final String ROUTE_LOCAL = "local";
    public static final String ROUTE_ONLINE = "online";

    private final JdbcTemplate jdbc;

    public DingTalkAppVO get() {
        Stored stored = loadStored();
        DingTalkAppVO vo = new DingTalkAppVO();
        boolean canEditRoute = isCurrentRobotDebugOperator();
        vo.setCanEditRobotRoute(canEditRoute);
        if (stored == null) {
            vo.setAppId("");
            vo.setAgentId("");
            vo.setClientId("");
            vo.setCorpId("");
            vo.setClientSecretMasked("");
            vo.setHasClientSecret(false);
            vo.setEnabled(0);
            vo.setRobotRoute(ROUTE_LOCAL);
            return vo;
        }
        vo.setAppId(stored.appId());
        vo.setAgentId(stored.agentId());
        vo.setClientId(stored.clientId());
        vo.setCorpId(stored.corpId());
        vo.setHasClientSecret(StringUtils.hasText(stored.clientSecret()));
        vo.setClientSecretMasked(mask(stored.clientSecret()));
        vo.setEnabled(stored.enabled());
        vo.setRobotRoute(normalizeRoute(stored.robotRoute()));
        return vo;
    }

    /** 登录页公开配置：不返回密钥。 */
    public DingTalkLoginConfigVO loginConfig() {
        DingTalkLoginConfigVO vo = new DingTalkLoginConfigVO();
        Stored stored = loadStored();
        if (stored == null || stored.enabled() == null || stored.enabled() != 1
                || !StringUtils.hasText(stored.clientId()) || !StringUtils.hasText(stored.clientSecret())) {
            vo.setEnabled(0);
            vo.setClientId("");
            vo.setCorpId("");
            vo.setExclusiveLogin(false);
            vo.setMessage("钉钉扫码登录未配置，请联系管理员在「系统管理 → 钉钉应用配置」启用");
            return vo;
        }
        vo.setEnabled(1);
        vo.setClientId(stored.clientId().trim());
        String corpId = stored.corpId() == null ? "" : stored.corpId().trim();
        vo.setCorpId(corpId);
        vo.setExclusiveLogin(StringUtils.hasText(corpId));
        vo.setMessage("请使用钉钉扫码登录");
        return vo;
    }

    /** 供日程、按手机号查身份使用。未启用或缺少凭证时返回 null。 */
    public Credential credential() {
        Stored stored = loadStored();
        if (stored == null || stored.enabled() == null || stored.enabled() != 1) {
            return null;
        }
        if (!StringUtils.hasText(stored.clientId()) || !StringUtils.hasText(stored.clientSecret())) {
            return null;
        }
        return new Credential(stored.clientId().trim(), stored.clientSecret().trim());
    }

    /** 工作通知需要的 AgentId；未配置时返回 null。 */
    public Long agentId() {
        Stored stored = loadStored();
        if (stored == null || stored.enabled() == null || stored.enabled() != 1) {
            return null;
        }
        if (!StringUtils.hasText(stored.agentId())) {
            return null;
        }
        try {
            return Long.parseLong(stored.agentId().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 钉盘授权等接口需要的企业 CorpId；未配置时返回空串。 */
    public String corpId() {
        Stored stored = loadStored();
        if (stored == null || !StringUtils.hasText(stored.corpId())) {
            return "";
        }
        return stored.corpId().trim();
    }

    /**
     * 按发起人决定 ActionCard H5 走本机还是线上。
     * 仅王方扬可读库里的开关（默认 local）；其他人固定 online。
     */
    public String resolveRobotRoute(Long senderUserId) {
        if (isRobotDebugOperator(senderUserId)) {
            Stored stored = loadStored();
            return stored == null ? ROUTE_LOCAL : normalizeRoute(stored.robotRoute());
        }
        return ROUTE_ONLINE;
    }

    public boolean isRobotDebugOperator(Long userId) {
        if (userId == null) {
            return false;
        }
        String username = jdbc.query(
                "SELECT username FROM sys_user WHERE user_id = ? AND is_active = 1 LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null,
                userId);
        return ROBOT_DEBUG_USERNAME.equalsIgnoreCase(username == null ? "" : username.trim());
    }

    public boolean isCurrentRobotDebugOperator() {
        String username = SecurityUtils.getCurrentUsername();
        return ROBOT_DEBUG_USERNAME.equalsIgnoreCase(username == null ? "" : username.trim());
    }

    /** 测试连接：密钥留空时用已保存的值，不把密钥回传。 */
    public Credential resolveForTest(DingTalkAppDTO dto) {
        String clientId = dto.getClientId().trim();
        String secret = StringUtils.hasText(dto.getClientSecret()) ? dto.getClientSecret().trim() : storedSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("请填写 Client Secret");
        }
        return new Credential(clientId, secret);
    }

    @Transactional
    public void save(DingTalkAppDTO dto) {
        String secret = StringUtils.hasText(dto.getClientSecret()) ? dto.getClientSecret().trim() : storedSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("请填写 Client Secret");
        }
        String corpId = dto.getCorpId() == null ? "" : dto.getCorpId().trim();
        String user = SecurityUtils.getCurrentUsername();
        Stored current = loadStored();
        String robotRoute = current == null ? ROUTE_LOCAL : normalizeRoute(current.robotRoute());
        if (isCurrentRobotDebugOperator() && StringUtils.hasText(dto.getRobotRoute())) {
            robotRoute = normalizeRoute(dto.getRobotRoute());
        }
        if (current == null) {
            jdbc.update("""
                    INSERT INTO sys_dingtalk_app
                      (id, app_id, agent_id, client_id, client_secret, corp_id, enabled, robot_route, create_by, is_active)
                    VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getAppId().trim(), dto.getAgentId().trim(), dto.getClientId().trim(), secret, corpId,
                    dto.getEnabled(), robotRoute, user);
            return;
        }
        jdbc.update("""
                UPDATE sys_dingtalk_app
                SET app_id = ?, agent_id = ?, client_id = ?, client_secret = ?, corp_id = ?,
                    enabled = ?, robot_route = ?, update_by = ?, is_active = 1
                WHERE id = 1
                """, dto.getAppId().trim(), dto.getAgentId().trim(), dto.getClientId().trim(), secret, corpId,
                dto.getEnabled(), robotRoute, user);
    }

    private static String normalizeRoute(String raw) {
        if (ROUTE_ONLINE.equalsIgnoreCase(raw == null ? "" : raw.trim())) {
            return ROUTE_ONLINE;
        }
        return ROUTE_LOCAL;
    }

    private String storedSecret() {
        Stored stored = loadStored();
        return stored == null || stored.clientSecret() == null ? "" : stored.clientSecret();
    }

    private Stored loadStored() {
        return jdbc.query("""
                SELECT app_id, agent_id, client_id, client_secret, corp_id, enabled, robot_route
                FROM sys_dingtalk_app WHERE id = 1 AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            String corpId;
            try {
                corpId = rs.getString("corp_id");
            } catch (Exception ex) {
                corpId = "";
            }
            String robotRoute = ROUTE_LOCAL;
            try {
                robotRoute = rs.getString("robot_route");
            } catch (Exception ignored) {
                // 旧库尚未迁移列
            }
            return new Stored(
                    rs.getString("app_id"),
                    rs.getString("agent_id"),
                    rs.getString("client_id"),
                    rs.getString("client_secret"),
                    corpId == null ? "" : corpId,
                    rs.getInt("enabled"),
                    robotRoute == null ? ROUTE_LOCAL : robotRoute);
        });
    }

    static String mask(String secret) {
        if (!StringUtils.hasText(secret)) {
            return "";
        }
        String value = secret.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    public record Stored(
            String appId,
            String agentId,
            String clientId,
            String clientSecret,
            String corpId,
            Integer enabled,
            String robotRoute) {
    }

    public record Credential(String clientId, String clientSecret) {
    }
}
