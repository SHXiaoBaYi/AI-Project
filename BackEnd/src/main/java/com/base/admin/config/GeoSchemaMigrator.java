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

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }
}
