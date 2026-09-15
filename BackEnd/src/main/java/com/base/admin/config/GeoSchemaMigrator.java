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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GeoSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v3_geo_platform.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v4_geo_daily_board.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v5_geo_board_snapshot.sql"));
            ensureBoardLockedColumn(connection);
            ensureOwnerNameColumn(connection);
            ensureTermTypeColumn(connection);
            ensureOwnerUserIdColumn(connection);
        } catch (Exception e) {
            log.error("GEO schema migrate failed", e);
            throw e;
        }
        log.info("GEO 平台主数据、看板落库与菜单已同步");
    }

    private void ensureBoardLockedColumn(Connection connection) throws Exception {
        if (columnExists(connection, "geo_monitor_daily", "board_locked")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_monitor_daily
                      ADD COLUMN board_locked TINYINT NOT NULL DEFAULT 0
                      COMMENT '是否已被周/月/年统计 1=已统计不可改 0=未统计'
                      AFTER competitors
                    """);
        }
        log.info("已为 geo_monitor_daily 增加 board_locked 字段");
    }

    private void ensureOwnerNameColumn(Connection connection) throws Exception {
        if (columnExists(connection, "geo_monitor_daily", "owner_name")) {
            log.info("geo_monitor_daily.owner_name 已存在，跳过");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_monitor_daily
                      ADD COLUMN owner_name VARCHAR(64) NULL
                      COMMENT '负责人（与关键字绑定）'
                      AFTER keyword
                    """);
        }
        log.info("已为 geo_monitor_daily 增加 owner_name 字段");
    }

    private void ensureTermTypeColumn(Connection connection) throws Exception {
        if (!columnExists(connection, "geo_monitor_daily", "term_type")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_monitor_daily
                          ADD COLUMN term_type VARCHAR(16) NOT NULL DEFAULT '日巡查'
                          COMMENT '长短词：日巡查/周巡查'
                          AFTER inspect_date
                        """);
            }
            log.info("已为 geo_monitor_daily 增加 term_type 字段");
        } else {
            log.info("geo_monitor_daily.term_type 已存在，跳过建列");
        }
        // 兜底：历史空值统一补成日巡查
        try (Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate("""
                    UPDATE geo_monitor_daily
                    SET term_type = '日巡查'
                    WHERE term_type IS NULL OR term_type = ''
                    """);
            if (updated > 0) {
                log.info("已将 {} 条空长短词补全为日巡查", updated);
            }
        }
    }

    private void ensureOwnerUserIdColumn(Connection connection) throws Exception {
        if (columnExists(connection, "geo_monitor_daily", "owner_user_id")) {
            log.info("geo_monitor_daily.owner_user_id 已存在，跳过");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_monitor_daily
                      ADD COLUMN owner_user_id BIGINT NULL
                      COMMENT '负责人用户ID（关联 sys_user）'
                      AFTER keyword
                    """);
        }
        log.info("已为 geo_monitor_daily 增加 owner_user_id 字段");
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        // 优先用 INFORMATION_SCHEMA，避免 RDS/驱动 catalog 不一致导致误判
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT COUNT(1) AS cnt
                     FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = '%s'
                       AND COLUMN_NAME = '%s'
                     """.formatted(table, column))) {
            if (rs.next() && rs.getInt("cnt") > 0) {
                return true;
            }
        }
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet rs = metaData.getColumns(catalog, null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getColumns(catalog, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }
}
