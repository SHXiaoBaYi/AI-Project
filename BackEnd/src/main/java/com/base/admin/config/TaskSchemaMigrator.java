package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class TaskSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v12_sys_task.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v14_sys_task_type.sql"));
            ensureTaskTypeBizColumns(connection);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v15_task_type_biz_binding.sql"));
            ensureSpawnTaskTypeColumn(connection);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v16_task_flow.sql"));
            ensureRequireProofColumn(connection);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v18_task_complete_proof.sql"));
        } catch (Exception e) {
            log.error("Task schema migrate failed", e);
            throw e;
        }
        log.info("任务模块表与菜单已同步");
    }

    private void ensureRequireProofColumn(Connection connection) throws Exception {
        if (!columnExists(connection, "sys_task_type", "require_proof")) {
            try (Statement st = connection.createStatement()) {
                st.execute("""
                        ALTER TABLE sys_task_type
                          ADD COLUMN require_proof TINYINT NOT NULL DEFAULT 0
                          COMMENT '完成时是否必须上传证明附件 1=是 0=否'
                          AFTER spawn_task_type
                        """);
            }
            log.info("已为 sys_task_type 增加 require_proof");
        }
    }

    private void ensureTaskTypeBizColumns(Connection connection) throws Exception {
        if (!columnExists(connection, "sys_task_type", "biz_type")) {
            try (Statement st = connection.createStatement()) {
                st.execute("""
                        ALTER TABLE sys_task_type
                          ADD COLUMN biz_type VARCHAR(64) NOT NULL DEFAULT ''
                          COMMENT '关联业务类型（如 geo_content_placement，空=无外源回写）'
                          AFTER remark
                        """);
            }
            log.info("已为 sys_task_type 增加 biz_type");
        }
        if (!columnExists(connection, "sys_task_type", "assign_field")) {
            try (Statement st = connection.createStatement()) {
                st.execute("""
                        ALTER TABLE sys_task_type
                          ADD COLUMN assign_field VARCHAR(64) NOT NULL DEFAULT ''
                          COMMENT '分配回写业务字段编码（publisher/writer 等）'
                          AFTER biz_type
                        """);
            }
            log.info("已为 sys_task_type 增加 assign_field");
        }
    }

    private void ensureSpawnTaskTypeColumn(Connection connection) throws Exception {
        if (!columnExists(connection, "sys_task_type", "spawn_task_type")) {
            try (Statement st = connection.createStatement()) {
                st.execute("""
                        ALTER TABLE sys_task_type
                          ADD COLUMN spawn_task_type VARCHAR(64) NOT NULL DEFAULT ''
                          COMMENT '分配完成后派发的下一任务类型名称'
                          AFTER assign_field
                        """);
            }
            log.info("已为 sys_task_type 增加 spawn_task_type");
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        String sql = """
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
