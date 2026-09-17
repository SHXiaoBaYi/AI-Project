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

/**
 * 为 GEO / 任务等主表增加 is_demo 演示标。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class DemoSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    private static final String[][] TABLES = {
            {"geo_topic", "remark"},
            {"geo_monitor_daily", "competitors"},
            {"geo_year_target", "remark"},
            {"geo_content_placement", "remark"},
            {"geo_content_placement_item", "remark"},
            {"geo_content_placement_cite", "remark"},
            {"sys_task", "remark"},
    };

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (String[] t : TABLES) {
                ensureDemoColumn(connection, t[0], t[1]);
            }
        }
        log.info("演示标 is_demo 列已同步");
    }

    private void ensureDemoColumn(Connection connection, String table, String afterColumn) throws Exception {
        if (columnExists(connection, table, "is_demo")) {
            return;
        }
        if (!tableExists(connection, table)) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE `" + table + "` ADD COLUMN is_demo TINYINT NOT NULL DEFAULT 0 "
                    + "COMMENT '是否演示数据 1=是 0=否' AFTER `" + afterColumn + "`");
            st.execute("CREATE INDEX idx_is_demo ON `" + table + "` (is_demo)");
        }
        log.info("已为 {} 增加 is_demo", table);
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
