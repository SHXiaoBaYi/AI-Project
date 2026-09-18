package com.base.admin.config;

import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.service.GeoMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 仅在 geo.daily.ingest=true 时把 GEO 目录下的监测宽表直接写入日监测，不走上传接口。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "geo.daily.ingest", havingValue = "true")
public class GeoDailyWorkbookIngestRunner implements ApplicationRunner {

    private final GeoMonitorService monitorService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path path = Path.of("..", "GEO", "GEO优化监测9.18.xlsx").toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            path = Path.of("GEO", "GEO优化监测9.18.xlsx").toAbsolutePath().normalize();
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("找不到监测表: " + path);
        }
        log.info("开始清洗日监测宽表: {}", path);
        releaseStaleTransactions();
        int removed = jdbcTemplate.update("DELETE FROM geo_monitor_daily");
        log.info("已清掉原有日监测 {} 条", removed);
        GeoImportResultVO result;
        try (var in = Files.newInputStream(path)) {
            result = monitorService.importDaily(in);
        }
        if (result == null) {
            throw new IllegalStateException("日监测清洗没有返回结果");
        }
        log.info("日监测清洗完成: 写入 {}，新增 {}，更新 {}，失败 {}",
                result.getTotalCount(), result.getInsertCount(), result.getUpdateCount(), result.getFailureCount());
        int shown = 0;
        for (GeoImportResultVO.GeoImportErrorVO error : result.getErrors()) {
            if (shown++ >= 30) {
                log.warn("其余错误省略，共 {} 条", result.getErrors().size());
                break;
            }
            log.warn("第 {} 行 {} : {}", error.getRowIndex(), error.getField(), error.getMessage());
        }
    }

    /** 清掉超过 1 分钟仍未结束的事务，避免上次中断的连接占着日监测表。 */
    private void releaseStaleTransactions() {
        try {
            var rows = jdbcTemplate.queryForList("""
                    SELECT trx_mysql_thread_id AS id, trx_started AS started, LEFT(IFNULL(trx_query, ''), 80) AS q
                    FROM information_schema.innodb_trx
                    WHERE trx_started < DATE_SUB(NOW(), INTERVAL 60 SECOND)
                      AND trx_mysql_thread_id <> CONNECTION_ID()
                    """);
            for (var row : rows) {
                Object id = row.get("id");
                log.warn("结束卡住的数据库事务 {} started={} query={}", id, row.get("started"), row.get("q"));
                jdbcTemplate.execute("KILL " + id);
            }
        } catch (Exception e) {
            log.warn("无法清理卡住的事务: {}", e.getMessage());
        }
    }
}
