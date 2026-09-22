package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
@RequiredArgsConstructor
public class DingTalkSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_dingtalk_app (
                  id             BIGINT       NOT NULL                COMMENT '固定为 1',
                  app_id         VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '钉钉 AppId',
                  agent_id       VARCHAR(32)  NOT NULL DEFAULT ''     COMMENT '钉钉 AgentId',
                  client_id      VARCHAR(128) NOT NULL DEFAULT ''     COMMENT 'Client ID / AppKey',
                  client_secret  VARCHAR(256) NOT NULL DEFAULT ''     COMMENT 'Client Secret',
                  enabled        TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用 1=启用 0=停用',
                  create_by      VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
                  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by      VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
                  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active      TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉企业内部应用配置'
                """);
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM sys_dingtalk_app WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbc.update("""
                    INSERT INTO sys_dingtalk_app (id, app_id, agent_id, client_id, client_secret, enabled, create_by, is_active)
                    VALUES (1, '', '', '', '', 0, 'system', 1)
                    """);
            log.info("已创建空的钉钉应用配置，请到「系统管理 → 钉钉应用配置」填写");
        }
        jdbc.update("""
                INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES
                (142, '钉钉应用配置', 1, 5, 'dingtalk', 'system/dingtalk/index', 'C', 'system:dingtalk:list', 'LinkOutlined', 0, 0,
                 '保存钉钉企业内部应用凭证，供绑定身份和创建日程使用', 1),
                (143, '钉钉应用编辑', 142, 1, '', '', 'F', 'system:dingtalk:edit', '#', 0, 0, '', 1),
                (144, '钉钉闲忙查询', 142, 2, '', '', 'F', 'system:dingtalk:busy', '#', 0, 0, '工作台查询同事日程闲忙', 1),
                (145, '助手日程', 1, 6, 'dingtalk-schedule', 'system/dingtalk-schedule/index', 'C', 'system:dingtalk:schedule', 'CalendarOutlined', 0, 0,
                 '查看、编辑、取消日程助手创建的钉钉会议/汇报日程', 1),
                (146, '助手日程编辑', 145, 1, '', '', 'F', 'system:dingtalk:schedule:edit', '#', 0, 0, '', 1),
                (147, '助手日程取消', 145, 2, '', '', 'F', 'system:dingtalk:schedule:cancel', '#', 0, 0, '', 1)
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
        jdbc.update("""
                INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (142, 143, 144, 145, 146, 147)
                ON DUPLICATE KEY UPDATE is_active = 1
                """);
        jdbc.update("""
                INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT DISTINCT role_id, 144, 1 FROM sys_role_menu WHERE menu_id = 220 AND is_active = 1
                ON DUPLICATE KEY UPDATE is_active = 1
                """);
        jdbc.update("""
                INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT DISTINCT role_id, m.menu_id, 1
                FROM sys_role_menu rm
                CROSS JOIN (SELECT 145 AS menu_id UNION ALL SELECT 146 UNION ALL SELECT 147) m
                WHERE rm.menu_id = 144 AND rm.is_active = 1
                ON DUPLICATE KEY UPDATE is_active = 1
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_dingtalk_assistant_event (
                  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  kind              VARCHAR(16)  NOT NULL                COMMENT 'MEETING/REPORT',
                  title             VARCHAR(200) NOT NULL DEFAULT ''     COMMENT '主题',
                  description       VARCHAR(1000) DEFAULT ''             COMMENT '描述',
                  location          VARCHAR(200) DEFAULT ''              COMMENT '地点',
                  online_meeting    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否视频会议',
                  start_time        DATETIME     NOT NULL                COMMENT '开始时间',
                  duration_min      INT          NOT NULL DEFAULT 60     COMMENT '时长分钟',
                  end_time          DATETIME     NOT NULL                COMMENT '结束时间',
                  querier_user_id   BIGINT       NOT NULL                COMMENT '查询者用户ID',
                  target_user_id    BIGINT       NOT NULL                COMMENT '对方用户ID',
                  querier_event_id  VARCHAR(64)  NULL                    COMMENT '查询者钉钉日程ID',
                  target_event_id   VARCHAR(64)  NULL                    COMMENT '对方钉钉日程ID',
                  task_id           BIGINT       NULL                    COMMENT '关联任务ID',
                  status            VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/CANCELLED',
                  create_by         VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
                  create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by         VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
                  update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  KEY idx_assistant_event_querier (querier_user_id, start_time),
                  KEY idx_assistant_event_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程助手创建的钉钉日程记录'
                """);
        log.info("钉钉应用配置表与菜单已同步");
    }
}
