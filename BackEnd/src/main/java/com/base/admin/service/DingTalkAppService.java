package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
import com.base.admin.domain.dto.DingTalkAppDTO;
import com.base.admin.domain.vo.DingTalkAppVO;
import com.base.admin.domain.vo.DingTalkDriveSpaceVO;
import com.base.admin.domain.vo.DingTalkLoginConfigVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class DingTalkAppService {

    /** 可切换机器人本机/线上的账号（共用同一 robot_route 开关） */
    public static final Set<String> ROBOT_DEBUG_USERNAMES = Set.of("wangfangyang", "thh", "tbb");
    public static final String ROUTE_LOCAL = "local";
    public static final String ROUTE_ONLINE = "online";

    private final JdbcTemplate jdbc;
    private final DingTalkProperties dingTalkProperties;
    private final DingTalkCalendarClient dingTalkCalendarClient;

    public DingTalkAppService(
            JdbcTemplate jdbc,
            DingTalkProperties dingTalkProperties,
            @Lazy DingTalkCalendarClient dingTalkCalendarClient) {
        this.jdbc = jdbc;
        this.dingTalkProperties = dingTalkProperties;
        this.dingTalkCalendarClient = dingTalkCalendarClient;
    }

    public DingTalkAppVO get() {
        Stored stored = loadStored();
        DingTalkAppVO vo = new DingTalkAppVO();
        boolean canEditRoute = isCurrentRobotDebugOperator();
        vo.setCanEditRobotRoute(canEditRoute);
        vo.setResumeSpaceIdAutoBound(false);
        if (stored == null) {
            vo.setAppId("");
            vo.setAgentId("");
            vo.setClientId("");
            vo.setCorpId("");
            vo.setClientSecretMasked("");
            vo.setHasClientSecret(false);
            vo.setEnabled(0);
            vo.setRobotRoute(ROUTE_LOCAL);
            vo.setResumeSpaceId(blankToEmpty(dingTalkProperties.getResumeSpaceId()));
            return vo;
        }
        vo.setAppId(stored.appId());
        vo.setAgentId(stored.agentId());
        vo.setClientId(stored.clientId());
        vo.setCorpId(stored.corpId());
        if (!StringUtils.hasText(vo.getCorpId()) && StringUtils.hasText(dingTalkProperties.getCorpId())) {
            vo.setCorpId(dingTalkProperties.getCorpId().trim());
        }
        vo.setHasClientSecret(StringUtils.hasText(stored.clientSecret()));
        vo.setClientSecretMasked(mask(stored.clientSecret()));
        vo.setEnabled(stored.enabled());
        vo.setRobotRoute(normalizeRoute(stored.robotRoute()));
        String resumeSpaceId = StringUtils.hasText(stored.resumeSpaceId())
                ? stored.resumeSpaceId().trim()
                : blankToEmpty(dingTalkProperties.getResumeSpaceId());
        if (!StringUtils.hasText(resumeSpaceId) && stored.enabled() != null && stored.enabled() == 1) {
            String bound = tryAutoBindResumeSpaceId();
            if (StringUtils.hasText(bound)) {
                resumeSpaceId = bound;
                vo.setResumeSpaceIdAutoBound(true);
            }
        }
        vo.setResumeSpaceId(resumeSpaceId);
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
        if (!StringUtils.hasText(corpId) && StringUtils.hasText(dingTalkProperties.getCorpId())) {
            corpId = dingTalkProperties.getCorpId().trim();
        }
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

    /** 钉盘授权、免登等接口需要的企业 CorpId；库表优先，其次 yml dingtalk.corp-id。 */
    public String corpId() {
        Stored stored = loadStored();
        if (stored != null && StringUtils.hasText(stored.corpId())) {
            return stored.corpId().trim();
        }
        if (StringUtils.hasText(dingTalkProperties.getCorpId())) {
            return dingTalkProperties.getCorpId().trim();
        }
        return "";
    }

    /**
     * 面试简历企业钉盘 spaceId：库表优先，其次 yml {@code dingtalk.resume-space-id}。
     */
    public String resumeSpaceId() {
        Stored stored = loadStored();
        if (stored != null && StringUtils.hasText(stored.resumeSpaceId())) {
            return stored.resumeSpaceId().trim();
        }
        return blankToEmpty(dingTalkProperties.getResumeSpaceId());
    }

    /**
     * 按发起人决定 ActionCard H5 走本机还是线上。
     * wangfangyang / thh / tbb 共用库里的开关（默认 local）；其他人固定 online。
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
        return isRobotDebugUsername(username);
    }

    public boolean isCurrentRobotDebugOperator() {
        return isRobotDebugUsername(SecurityUtils.getCurrentUsername());
    }

    private static boolean isRobotDebugUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return ROBOT_DEBUG_USERNAMES.contains(username.trim().toLowerCase(Locale.ROOT));
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

    /** 列出企业钉盘 org 空间；unionId 为空时用当前用户或任意已绑定用户。 */
    public List<DingTalkDriveSpaceVO> listOrgDriveSpaces(String unionIdOverride) {
        if (credential() == null) {
            throw new BusinessException("请先启用并保存钉钉应用凭证");
        }
        String unionId = StringUtils.hasText(unionIdOverride) ? unionIdOverride.trim() : resolveAnyBoundUnionId();
        if (!StringUtils.hasText(unionId)) {
            throw new BusinessException("没有可用的钉钉 unionId。请先绑定当前用户钉钉，或指定已绑定员工后再拉取企业盘");
        }
        return dingTalkCalendarClient.listOrgDriveSpaces(unionId);
    }

    /**
     * 拉取企业盘并写入推荐 spaceId（若库中尚未配置）。
     *
     * @return 绑定后的 spaceId；失败返回空
     */
    public String tryAutoBindResumeSpaceId() {
        try {
            String existing = resumeSpaceId();
            if (StringUtils.hasText(existing)) {
                return existing;
            }
            List<DingTalkDriveSpaceVO> spaces = listOrgDriveSpaces(null);
            String preferred = pickPreferredSpaceId(spaces);
            if (!StringUtils.hasText(preferred)) {
                return "";
            }
            jdbc.update("UPDATE sys_dingtalk_app SET resume_space_id = ?, update_by = ? WHERE id = 1 AND is_active = 1",
                    preferred, blankToEmpty(SecurityUtils.getCurrentUsername()));
            log.info("已自动绑定面试简历企业钉盘 resume_space_id={}", preferred);
            return preferred;
        } catch (Exception ex) {
            log.warn("自动绑定企业钉盘 spaceId 失败: {}", ex.getMessage());
            return "";
        }
    }

    static String pickPreferredSpaceId(List<DingTalkDriveSpaceVO> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return "";
        }
        for (DingTalkDriveSpaceVO space : spaces) {
            if (space != null && Boolean.TRUE.equals(space.getPreferred()) && StringUtils.hasText(space.getSpaceId())) {
                return space.getSpaceId().trim();
            }
        }
        for (DingTalkDriveSpaceVO space : spaces) {
            if (space != null && StringUtils.hasText(space.getSpaceId())) {
                return space.getSpaceId().trim();
            }
        }
        return "";
    }

    @Transactional
    public void save(DingTalkAppDTO dto) {
        String secret = StringUtils.hasText(dto.getClientSecret()) ? dto.getClientSecret().trim() : storedSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("请填写 Client Secret");
        }
        String corpId = dto.getCorpId() == null ? "" : dto.getCorpId().trim();
        if (!StringUtils.hasText(corpId)) {
            throw new BusinessException("请填写企业 CorpId（钉钉开放平台「应用信息 / 企业信息」里的 CorpId，不是 ClientId）");
        }
        String user = SecurityUtils.getCurrentUsername();
        Stored current = loadStored();
        String robotRoute = current == null ? ROUTE_LOCAL : normalizeRoute(current.robotRoute());
        if (isCurrentRobotDebugOperator() && StringUtils.hasText(dto.getRobotRoute())) {
            robotRoute = normalizeRoute(dto.getRobotRoute());
        }
        String resumeSpaceId = dto.getResumeSpaceId() == null ? "" : dto.getResumeSpaceId().trim();
        if (current == null) {
            jdbc.update("""
                    INSERT INTO sys_dingtalk_app
                      (id, app_id, agent_id, client_id, client_secret, corp_id, enabled, robot_route, resume_space_id, create_by, is_active)
                    VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getAppId().trim(), dto.getAgentId().trim(), dto.getClientId().trim(), secret, corpId,
                    dto.getEnabled(), robotRoute, resumeSpaceId, user);
            return;
        }
        jdbc.update("""
                UPDATE sys_dingtalk_app
                SET app_id = ?, agent_id = ?, client_id = ?, client_secret = ?, corp_id = ?,
                    enabled = ?, robot_route = ?, resume_space_id = ?, update_by = ?, is_active = 1
                WHERE id = 1
                """, dto.getAppId().trim(), dto.getAgentId().trim(), dto.getClientId().trim(), secret, corpId,
                dto.getEnabled(), robotRoute, resumeSpaceId, user);
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

    private String resolveAnyBoundUnionId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            String mine = jdbc.query("""
                    SELECT dingtalk_union_id FROM hr_user_dingtalk
                    WHERE user_id = ? AND is_active = 1
                      AND dingtalk_union_id IS NOT NULL AND dingtalk_union_id <> ''
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getString(1) : null, userId);
            if (StringUtils.hasText(mine)) {
                return mine.trim();
            }
        }
        String any = jdbc.query("""
                SELECT dingtalk_union_id FROM hr_user_dingtalk
                WHERE is_active = 1 AND dingtalk_union_id IS NOT NULL AND dingtalk_union_id <> ''
                ORDER BY user_id ASC LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null);
        return any == null ? "" : any.trim();
    }

    private Stored loadStored() {
        return jdbc.query("""
                SELECT app_id, agent_id, client_id, client_secret, corp_id, enabled, robot_route, resume_space_id
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
            String resumeSpaceId = "";
            try {
                resumeSpaceId = rs.getString("resume_space_id");
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
                    robotRoute == null ? ROUTE_LOCAL : robotRoute,
                    resumeSpaceId == null ? "" : resumeSpaceId);
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

    private static String blankToEmpty(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : "";
    }

    public record Stored(
            String appId,
            String agentId,
            String clientId,
            String clientSecret,
            String corpId,
            Integer enabled,
            String robotRoute,
            String resumeSpaceId) {
    }

    public record Credential(String clientId, String clientSecret) {
    }
}
