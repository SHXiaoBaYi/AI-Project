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
                  session_webhook      VARCHAR(1000) DEFAULT '' COMMENT '最近一次入站 sessionWebhook，用于群内真实@',
                  session_webhook_expire_at DATETIME NULL COMMENT 'sessionWebhook 过期时间',
                  create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active            TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_robot_group_cid (open_conversation_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人已见钉钉群档案'
                """);
        ensureColumn("robot_group_profile", "session_webhook", """
                ALTER TABLE robot_group_profile
                  ADD COLUMN session_webhook VARCHAR(1000) DEFAULT '' COMMENT '最近一次入站 sessionWebhook，用于群内真实@'
                  AFTER remark
                """);
        ensureColumn("robot_group_profile", "session_webhook_expire_at", """
                ALTER TABLE robot_group_profile
                  ADD COLUMN session_webhook_expire_at DATETIME NULL COMMENT 'sessionWebhook 过期时间'
                  AFTER session_webhook
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ops_link_monitor_probe_cookie (
                  id              TINYINT      NOT NULL DEFAULT 1,
                  cookie_header   MEDIUMTEXT   NULL COMMENT '浏览器 Cookie 头（会话），不含密码',
                  host_suffixes   VARCHAR(500) NOT NULL DEFAULT 'xiaohongshu.com,xhslink.com',
                  enabled         TINYINT      NOT NULL DEFAULT 1,
                  remark          VARCHAR(500) DEFAULT '',
                  last_cookie_alert_at DATETIME NULL COMMENT 'Cookie 失效告警最近一次（按日去重）',
                  update_by       VARCHAR(50)  DEFAULT '',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链接监控可选探测 Cookie'
                """);
        ensureColumn("ops_link_monitor_probe_cookie", "last_cookie_alert_at", """
                ALTER TABLE ops_link_monitor_probe_cookie
                  ADD COLUMN last_cookie_alert_at DATETIME NULL COMMENT 'Cookie 失效告警最近一次（按日去重）'
                  AFTER remark
                """);
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        if (cnt == null || cnt == 0) {
            jdbc.execute(ddl);
        }
    }

    private void seedIntents() {
        upsertIntent("calendar.freebusy", "查闲忙/约面试开会", 1, "查 Bella 有没有空;安排面试;约开会", "calendarFreeBusyHandler", 1, 1, 10);
        upsertIntent("link_monitor.add", "添加链接监控", 1, "帮我监控一个链接;要监控这个网址", "linkMonitorHandler", 1, 1, 20);
        upsertIntent("link_monitor.list", "查询监控链接", 1, "现在我有几个在监控的链接;看看正在监控的网址", "linkMonitorHandler", 1, 1, 25);
        upsertIntent("link_monitor.abnormal", "查询异常监控", 1,
                "目前监控的笔记是否有异常;监控的链接哪些有异常;监控有没有问题",
                "linkMonitorHandler", 1, 1, 26);
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
        SysMenuSeedSupport.assertIdsInRange(SysMenuIdRanges.Owner.ROBOT,
                250, 251, 252, 253, 254, 255, 256, 257, 258, 259, 260);
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 250, "机器人", 0, 45, "/robot", "", "M",
                "", "RobotOutlined", "多意图机器人后台");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 251, "意图管理", 250, 1, "intent",
                "robot/intent/index", "C", "robot:intent:list", "ApartmentOutlined", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 252, "意图编辑", 251, 1, "", "", "F",
                "robot:intent:edit", "#", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 253, "会话列表", 250, 2, "session",
                "robot/session/index", "C", "robot:session:list", "MessageOutlined", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 254, "结束会话", 253, 1, "", "", "F",
                "robot:session:end", "#", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 255, "消息流水", 250, 3, "message",
                "robot/message/index", "C", "robot:message:list", "UnorderedListOutlined", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 256, "链接监控", 250, 4, "link-monitor",
                "robot/link-monitor/index", "C", "robot:link-monitor:list", "LinkOutlined", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 257, "监控新增", 256, 1, "", "", "F",
                "robot:link-monitor:add", "#", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 258, "监控编辑", 256, 2, "", "", "F",
                "robot:link-monitor:edit", "#", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 259, "监控删除", 256, 3, "", "", "F",
                "robot:link-monitor:delete", "#", "");
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.ROBOT, 260, "立即探测", 256, 4, "", "", "F",
                "robot:link-monitor:probe", "#", "");
        jdbc.update("""
                INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 250 AND 260
                """);
    }
}
