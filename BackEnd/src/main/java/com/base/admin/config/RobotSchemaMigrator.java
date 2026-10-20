package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 机器人多意图：会话 / 意图注册 / 消息流水 / 链接监控。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 28)
@RequiredArgsConstructor
public class RobotSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        createTables();
        seedIntents();
        ensureMenus();
        log.info("机器人多意图表与菜单已同步");
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS robot_intent (
                  id              BIGINT       NOT NULL AUTO_INCREMENT,
                  intent_code     VARCHAR(64)  NOT NULL,
                  intent_name     VARCHAR(100) NOT NULL DEFAULT '',
                  enabled         TINYINT      NOT NULL DEFAULT 1,
                  sample_utterances VARCHAR(1000) DEFAULT '',
                  handler_bean    VARCHAR(100) NOT NULL DEFAULT '',
                  allow_group     TINYINT      NOT NULL DEFAULT 1,
                  allow_dm         TINYINT      NOT NULL DEFAULT 1,
                  sort_order      INT          NOT NULL DEFAULT 0,
                  remark          VARCHAR(500) DEFAULT '',
                  create_by       VARCHAR(50)  DEFAULT '',
                  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_by       VARCHAR(50)  DEFAULT '',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active       TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_robot_intent_code (intent_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人意图注册'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS robot_session (
                  id                 BIGINT       NOT NULL AUTO_INCREMENT,
                  session_key        VARCHAR(190) NOT NULL,
                  staff_id           VARCHAR(64)  NOT NULL DEFAULT '',
                  open_conversation_id VARCHAR(128) NOT NULL DEFAULT '',
                  conversation_type  VARCHAR(8)   NOT NULL DEFAULT '1',
                  state              VARCHAR(32)  NOT NULL DEFAULT 'IDLE',
                  intent_code        VARCHAR(64)  NOT NULL DEFAULT '',
                  slots_json         TEXT         NULL,
                  expire_at          DATETIME     NOT NULL,
                  create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_time        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active          TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_robot_session_key (session_key),
                  KEY idx_robot_session_expire (expire_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人多轮会话'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS robot_message_log (
                  id                 BIGINT       NOT NULL AUTO_INCREMENT,
                  direction          VARCHAR(8)   NOT NULL COMMENT 'IN/OUT',
                  staff_id           VARCHAR(64)  NOT NULL DEFAULT '',
                  open_conversation_id VARCHAR(128) NOT NULL DEFAULT '',
                  conversation_type  VARCHAR(8)   NOT NULL DEFAULT '1',
                  intent_code        VARCHAR(64)  NOT NULL DEFAULT '',
                  content            VARCHAR(2000) NOT NULL DEFAULT '',
                  success            TINYINT      NOT NULL DEFAULT 1,
                  error_msg          VARCHAR(500) DEFAULT '',
                  create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_robot_msg_time (create_time),
                  KEY idx_robot_msg_intent (intent_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人消息流水'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ops_link_monitor (
                  id                 BIGINT       NOT NULL AUTO_INCREMENT,
                  url                VARCHAR(1000) NOT NULL,
                  url_hash           VARCHAR(64)  NOT NULL,
                  name               VARCHAR(200) DEFAULT '',
                  remark             VARCHAR(500) DEFAULT '',
                  scope_type         VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL',
                  source             VARCHAR(16)  NOT NULL DEFAULT 'robot' COMMENT 'robot/admin',
                  open_conversation_id VARCHAR(128) NOT NULL DEFAULT '',
                  conversation_type  VARCHAR(8)   NOT NULL DEFAULT '1',
                  creator_staff_id   VARCHAR(64)  NOT NULL DEFAULT '',
                  creator_user_id    BIGINT       NULL,
                  status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                  interval_sec       INT          NOT NULL DEFAULT 86400,
                  timeout_ms         INT          NOT NULL DEFAULT 10000,
                  last_check_at      DATETIME     NULL,
                  last_ok            TINYINT      NULL,
                  last_status_code   INT          NULL,
                  last_error         VARCHAR(500) DEFAULT '',
                  last_alert_at      DATETIME     NULL,
                  create_by          VARCHAR(50)  DEFAULT '',
                  create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_by          VARCHAR(50)  DEFAULT '',
                  update_time        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active          TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  KEY idx_ops_lm_status (status, last_check_at),
                  KEY idx_ops_lm_hash (url_hash)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链接监控目标'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ops_link_monitor_check_log (
                  id           BIGINT       NOT NULL AUTO_INCREMENT,
                  monitor_id   BIGINT       NOT NULL,
                  checked_at   DATETIME     NOT NULL,
                  ok           TINYINT      NOT NULL,
                  http_status  INT          NULL,
                  latency_ms   INT          NULL,
                  error_msg    VARCHAR(500) DEFAULT '',
                  PRIMARY KEY (id),
                  KEY idx_ops_lm_check_mid (monitor_id, checked_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链接监控探测日志'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ops_link_monitor_alert_log (
                  id           BIGINT       NOT NULL AUTO_INCREMENT,
                  monitor_id   BIGINT       NOT NULL,
                  channel      VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT 'USER/GROUP/ADMIN',
                  target       VARCHAR(128) NOT NULL DEFAULT '',
                  content      VARCHAR(1000) NOT NULL DEFAULT '',
                  success      TINYINT      NOT NULL DEFAULT 1,
                  error_msg    VARCHAR(500) DEFAULT '',
                  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_ops_lm_alert_mid (monitor_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链接监控告警记录'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS robot_group_profile (
                  id                   BIGINT       NOT NULL AUTO_INCREMENT,
                  open_conversation_id VARCHAR(128) NOT NULL,
                  title                VARCHAR(200) DEFAULT '',
                  enabled              TINYINT      NOT NULL DEFAULT 1,
                  remark               VARCHAR(500) DEFAULT '',
                  create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active            TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_robot_group_cid (open_conversation_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人已见钉钉群档案'
                """);
    }

    private void seedIntents() {
        upsertIntent("calendar.freebusy", "查闲忙/约面试开会", 1, "查 Bella 有没有空;安排面试;约开会", "calendarFreeBusyHandler", 1, 1, 10);
        upsertIntent("link_monitor.add", "添加链接监控", 1, "帮我监控一个链接;要监控这个网址", "linkMonitorHandler", 1, 1, 20);
        upsertIntent("link_monitor.delete", "删除链接监控", 1, "删掉监控链接;取消监控", "linkMonitorHandler", 1, 1, 30);
        upsertIntent("robot.help", "帮助/菜单", 1, "你能做什么;帮助;菜单", "helpHandler", 1, 1, 1);
    }

    private void upsertIntent(String code, String name, int enabled, String samples, String handler,
                              int allowGroup, int allowDm, int sort) {
        jdbc.update("""
                INSERT INTO robot_intent (intent_code, intent_name, enabled, sample_utterances, handler_bean,
                  allow_group, allow_dm, sort_order, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'system', 1)
                ON DUPLICATE KEY UPDATE
                  intent_name = VALUES(intent_name),
                  sample_utterances = VALUES(sample_utterances),
                  handler_bean = VALUES(handler_bean),
                  allow_group = VALUES(allow_group),
                  allow_dm = VALUES(allow_dm),
                  sort_order = VALUES(sort_order),
                  is_active = 1
                """, code, name, enabled, samples, handler, allowGroup, allowDm, sort);
    }

    private void ensureMenus() {
        jdbc.update("""
                INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES
                (250, '机器人', 0, 45, '/robot', '', 'M', '', 'RobotOutlined', 0, 0, '多意图机器人后台', 1),
                (251, '意图管理', 250, 1, 'intent', 'robot/intent/index', 'C', 'robot:intent:list', 'ApartmentOutlined', 0, 0, '', 1),
                (252, '意图编辑', 251, 1, '', '', 'F', 'robot:intent:edit', '#', 0, 0, '', 1),
                (253, '会话列表', 250, 2, 'session', 'robot/session/index', 'C', 'robot:session:list', 'MessageOutlined', 0, 0, '', 1),
                (254, '结束会话', 253, 1, '', '', 'F', 'robot:session:end', '#', 0, 0, '', 1),
                (255, '消息流水', 250, 3, 'message', 'robot/message/index', 'C', 'robot:message:list', 'UnorderedListOutlined', 0, 0, '', 1),
                (256, '链接监控', 250, 4, 'link-monitor', 'robot/link-monitor/index', 'C', 'robot:link-monitor:list', 'LinkOutlined', 0, 0, '', 1),
                (257, '监控新增', 256, 1, '', '', 'F', 'robot:link-monitor:add', '#', 0, 0, '', 1),
                (258, '监控编辑', 256, 2, '', '', 'F', 'robot:link-monitor:edit', '#', 0, 0, '', 1),
                (259, '监控删除', 256, 3, '', '', 'F', 'robot:link-monitor:delete', '#', 0, 0, '', 1),
                (260, '立即探测', 256, 4, '', '', 'F', 'robot:link-monitor:probe', '#', 0, 0, '', 1)
                ON DUPLICATE KEY UPDATE
                  menu_name = VALUES(menu_name),
                  parent_id = VALUES(parent_id),
                  sort_order = VALUES(sort_order),
                  path = VALUES(path),
                  component = VALUES(component),
                  menu_type = VALUES(menu_type),
                  perms = VALUES(perms),
                  icon = VALUES(icon),
                  remark = VALUES(remark),
                  visible = 0,
                  status = 0,
                  is_active = 1
                """);
        jdbc.update("""
                INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 250 AND 260
                """);
    }
}
