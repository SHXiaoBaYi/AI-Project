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
@Order(Ordered.HIGHEST_PRECEDENCE + 28)
@RequiredArgsConstructor
public class DataScopeSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_user_data_scope (
                  user_id        BIGINT       NOT NULL                COMMENT '被配置的系统用户',
                  mode           VARCHAR(16)  NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT/PERSON/SELF（招聘人员维兼容）',
                  global_all     TINYINT      NOT NULL DEFAULT 0      COMMENT '1=全量业务数据',
                  geo_config     TEXT                                  COMMENT 'GEO 切片 JSON',
                  hr_config      TEXT                                  COMMENT '招聘切片 JSON',
                  task_config    TEXT                                  COMMENT '任务切片 JSON',
                  create_by      VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
                  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by      VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
                  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active      TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户数据权限'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_user_data_scope_target (
                  user_id         BIGINT NOT NULL COMMENT '被配置的系统用户',
                  target_user_id  BIGINT NOT NULL COMMENT '其数据对 user_id 可见的人员',
                  is_active       TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效',
                  PRIMARY KEY (user_id, target_user_id),
                  KEY idx_scope_target (target_user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户数据权限可见人'
                """);
        ensureColumn("global_all",
                "ALTER TABLE sys_user_data_scope ADD COLUMN global_all TINYINT NOT NULL DEFAULT 0 COMMENT '1=全量业务数据' AFTER mode");
        ensureColumn("geo_config",
                "ALTER TABLE sys_user_data_scope ADD COLUMN geo_config TEXT NULL COMMENT 'GEO 切片 JSON' AFTER global_all");
        ensureColumn("hr_config",
                "ALTER TABLE sys_user_data_scope ADD COLUMN hr_config TEXT NULL COMMENT '招聘切片 JSON' AFTER geo_config");
        ensureColumn("task_config",
                "ALTER TABLE sys_user_data_scope ADD COLUMN task_config TEXT NULL COMMENT '任务切片 JSON' AFTER hr_config");
        log.info("用户数据权限表已同步");
    }

    private void ensureColumn(String column, String ddl) {
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_data_scope' AND COLUMN_NAME = ?
                """, Integer.class, column);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute(ddl);
        log.info("已为 sys_user_data_scope 增加 {} 字段", column);
    }
}
