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
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v24_geo_review_task.sql"));
            ensureTaskSourceColumns(connection);
            cleanupDuplicateGeoReviewTasks(connection);
        } catch (Exception e) {
            log.error("Task schema migrate failed", e);
            throw e;
        }
        log.info("任务模块表与菜单已同步");
    }

    /**
     * 清理「审核文章」脏任务：
     * 1) 同投放同阶段仅保留当前审核记录（优先 pending，否则最大 id）对应任务，其余软删；
     * 2) 同一审核记录+负责人多条有效任务只留一条（优先未完成，否则最大 id）。
     */
    private void cleanupDuplicateGeoReviewTasks(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_review") || !tableExists(connection, "sys_task")) {
            return;
        }
        boolean hasStage = columnExists(connection, "geo_content_review", "stage_no");
        String stageExpr = hasStage ? "IFNULL(r.stage_no, 1)" : "1";
        String stageExprK = hasStage ? "IFNULL(stage_no, 1)" : "1";
        try (Statement st = connection.createStatement()) {
            int superseded = st.executeUpdate("""
                    UPDATE sys_task t
                    INNER JOIN (
                      SELECT r.id AS old_id
                      FROM geo_content_review r
                      INNER JOIN (
                        SELECT placement_id, %s AS stage_key,
                               COALESCE(MAX(CASE WHEN status = 'pending' THEN id END), MAX(id)) AS keep_id
                        FROM geo_content_review
                        WHERE is_active = 1
                        GROUP BY placement_id, %s
                      ) k ON r.placement_id = k.placement_id
                         AND %s = k.stage_key
                         AND r.id <> k.keep_id
                      WHERE r.is_active = 1
                    ) x ON t.biz_id = x.old_id
                    SET t.is_active = 0,
                        t.status = CASE WHEN t.status IN ('已完成', '已取消') THEN t.status ELSE '已取消' END,
                        t.update_by = 'cleanup-geo-review-task'
                    WHERE t.is_active = 1
                      AND t.biz_type = 'geo_content_review'
                      AND t.task_type = '审核文章'
                    """.formatted(stageExprK, stageExprK, stageExpr));
            if (superseded > 0) {
                log.info("已软删 {} 条被同阶段新审核记录替代的审核任务", superseded);
            }

            int dupOwner = st.executeUpdate("""
                    UPDATE sys_task t
                    INNER JOIN (
                      SELECT id FROM (
                        SELECT id,
                               ROW_NUMBER() OVER (
                                 PARTITION BY biz_id, owner_user_id
                                 ORDER BY CASE WHEN status IN ('已完成', '已取消') THEN 1 ELSE 0 END ASC, id DESC
                               ) AS rn
                        FROM sys_task
                        WHERE is_active = 1
                          AND biz_type = 'geo_content_review'
                          AND task_type = '审核文章'
                      ) ranked
                      WHERE rn > 1
                    ) d ON t.id = d.id
                    SET t.is_active = 0,
                        t.status = CASE WHEN t.status IN ('已完成', '已取消') THEN t.status ELSE '已取消' END,
                        t.update_by = 'cleanup-geo-review-task'
                    """);
            if (dupOwner > 0) {
                log.info("已软删 {} 条同一审核记录下重复的审核任务", dupOwner);
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        String sql = """
                SELECT COUNT(1) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """;
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** 任务来源人：历史/系统任务默认「系统」，手动创建时可指定用户 */
    private void ensureTaskSourceColumns(Connection connection) throws Exception {
        if (!columnExists(connection, "sys_task", "source_user_id")) {
            try (Statement st = connection.createStatement()) {
                st.execute("""
                        ALTER TABLE sys_task
                          ADD COLUMN source_user_id BIGINT NULL
                          COMMENT '任务来源人用户ID（空=系统）'
                          AFTER creator_name
                        """);
            }
            log.info("已为 sys_task 增加 source_user_id");
        }
        if (!columnExists(connection, "sys_task", "source_name")) {
            try (Statement st = connection.createStatement()) {
                st.execute("""
                        ALTER TABLE sys_task
                          ADD COLUMN source_name VARCHAR(64) NOT NULL DEFAULT '系统'
                          COMMENT '任务来源人展示名'
                          AFTER source_user_id
                        """);
            }
            log.info("已为 sys_task 增加 source_name");
        }
        try (Statement st = connection.createStatement()) {
            int n = st.executeUpdate("""
                    UPDATE sys_task
                       SET source_name = '系统'
                     WHERE source_name IS NULL OR source_name = ''
                    """);
            if (n > 0) {
                log.info("已回填 {} 条任务来源人为系统", n);
            }
        }
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
