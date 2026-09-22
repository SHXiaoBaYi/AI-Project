package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkAppDTO;
import com.base.admin.domain.vo.DingTalkAppVO;
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

    private final JdbcTemplate jdbc;

    public DingTalkAppVO get() {
        Stored stored = loadStored();
        DingTalkAppVO vo = new DingTalkAppVO();
        if (stored == null) {
            vo.setAppId("");
            vo.setAgentId("");
            vo.setClientId("");
            vo.setClientSecretMasked("");
            vo.setHasClientSecret(false);
            vo.setEnabled(0);
            return vo;
        }
        vo.setAppId(stored.appId());
        vo.setAgentId(stored.agentId());
        vo.setClientId(stored.clientId());
        vo.setHasClientSecret(StringUtils.hasText(stored.clientSecret()));
        vo.setClientSecretMasked(mask(stored.clientSecret()));
        vo.setEnabled(stored.enabled());
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
        String user = SecurityUtils.getCurrentUsername();
        Stored current = loadStored();
        if (current == null) {
            jdbc.update("""
                    INSERT INTO sys_dingtalk_app (id, app_id, agent_id, client_id, client_secret, enabled, create_by, is_active)
                    VALUES (1, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getAppId().trim(), dto.getAgentId().trim(), dto.getClientId().trim(), secret, dto.getEnabled(), user);
            return;
        }
        jdbc.update("""
                UPDATE sys_dingtalk_app
                SET app_id = ?, agent_id = ?, client_id = ?, client_secret = ?, enabled = ?, update_by = ?, is_active = 1
                WHERE id = 1
                """, dto.getAppId().trim(), dto.getAgentId().trim(), dto.getClientId().trim(), secret, dto.getEnabled(), user);
    }

    private String storedSecret() {
        Stored stored = loadStored();
        return stored == null || stored.clientSecret() == null ? "" : stored.clientSecret();
    }

    private Stored loadStored() {
        return jdbc.query("""
                SELECT app_id, agent_id, client_id, client_secret, enabled
                FROM sys_dingtalk_app WHERE id = 1 AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new Stored(
                    rs.getString("app_id"),
                    rs.getString("agent_id"),
                    rs.getString("client_id"),
                    rs.getString("client_secret"),
                    rs.getInt("enabled"));
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

    public record Stored(String appId, String agentId, String clientId, String clientSecret, Integer enabled) {
    }

    public record Credential(String clientId, String clientSecret) {
    }
}
