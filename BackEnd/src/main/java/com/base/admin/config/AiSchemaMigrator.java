package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class AiSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ensureAiProviderTable(connection);
            seedAiProviders(connection);
            ensureAiProviderMenu(connection);
        } catch (Exception e) {
            log.error("AI schema migrate failed", e);
            throw e;
        }
        log.info("AI 厂商配置表与菜单已同步");
    }

    private void ensureAiProviderTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sys_ai_provider (
                      id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                      provider       VARCHAR(32)   NOT NULL                COMMENT '厂商标识 tongyi/deepseek/zhipu/moonshot',
                      provider_name  VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '展示名',
                      api_key        VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT 'API Key',
                      model          VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '模型名',
                      base_url       VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '兼容接口 baseUrl，空则用内置预设',
                      enabled        TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用 1=启用 0=停用',
                      sort_order     INT           NOT NULL DEFAULT 0      COMMENT '排序',
                      remark         VARCHAR(500)  DEFAULT ''              COMMENT '备注',
                      create_by      VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
                      create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_by      VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
                      update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      is_active      TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_provider (provider)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI厂商API Key配置'
                    """);
        }
    }

    private void seedAiProviders(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_ai_provider (provider, provider_name, api_key, model, base_url, enabled, sort_order, remark, is_active)
                    VALUES
                    ('tongyi', '通义千问', '', 'qwen-turbo', '', 1, 1, '阿里云 DashScope 兼容模式', 1),
                    ('deepseek', 'DeepSeek', '', 'deepseek-chat', '', 1, 2, 'DeepSeek OpenAI 兼容', 1),
                    ('zhipu', '智谱GLM', '', 'glm-4-flash', '', 1, 3, '智谱开放平台', 1),
                    ('moonshot', '月之暗面', '', 'moonshot-v1-8k', '', 1, 4, 'Moonshot / Kimi', 1)
                    ON DUPLICATE KEY UPDATE
                      provider_name = VALUES(provider_name),
                      sort_order = VALUES(sort_order),
                      is_active = 1
                    """);
        }
    }

    private void ensureAiProviderMenu(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (140, 'AI模型配置', 1, 4, 'ai-provider', 'system/ai-provider/index', 'C', 'system:ai:list', 'KeyOutlined', 0, 0,
                     '配置通义/DeepSeek/智谱/月之暗面 API Key', 1),
                    (141, 'AI模型编辑', 140, 1, '', '', 'F', 'system:ai:edit', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = VALUES(parent_id),
                      sort_order = VALUES(sort_order),
                      path = VALUES(path),
                      component = VALUES(component),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      is_active = 1
                    """);
            statement.execute("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (140, 141)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """);
        }
        if (!menuExists(connection, 140)) {
            log.warn("AI模型配置菜单写入后未查到 menu_id=140，请检查 sys_menu");
        }
    }

    private boolean menuExists(Connection connection, long menuId) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(1) AS cnt FROM sys_menu WHERE menu_id = " + menuId)) {
            return rs.next() && rs.getInt("cnt") > 0;
        }
    }
}
