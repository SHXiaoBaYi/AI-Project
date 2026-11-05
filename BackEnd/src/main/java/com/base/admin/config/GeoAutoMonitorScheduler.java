package com.base.admin.config;

import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.service.GeoAutoMonitorExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 自动监测定时扫描：每 30s 扫一次到期任务（enabled=1 且 next_run_time 到点）。
 * 真正的执行由 GeoAutoMonitorExecutor 异步完成；这里只负责触发与容错。
 */
@Slf4j
@Component
@Order(110)
@RequiredArgsConstructor
public class GeoAutoMonitorScheduler implements ApplicationRunner {

    private final GeoAutoMonitorProperties properties;
    private final GeoAutoMonitorExecutor executor;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("GEO 自动监测定时扫描未启用（geo.auto-monitor.enabled=false）");
            return;
        }
        log.info("GEO 自动监测调度器就绪（间隔 {}ms）", properties.getScanMs());
    }

    @Scheduled(fixedDelayString = "${geo.auto-monitor.scan-ms:30000}", initialDelayString = "15000")
    public void scan() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            executor.forEachDueTask(task -> {
                if (executor.isRunning(task.getId())) {
                    return;
                }
                try {
                    GeoMonitorRun run = executor.startRun(task.getId(), GeoMonitorRun.TRIGGER_SCHEDULED);
                    log.info("自动监测定时触发：taskId={} runId={}", task.getId(), run.getId());
                } catch (Exception e) {
                    // 触发失败（如厂商未配置）：顺延下次运行，避免每 30s 反复报错
                    log.warn("自动监测任务触发失败 taskId={}：{}", task.getId(), e.getMessage());
                    executor.scheduleNextRun(task.getId());
                }
            });
        } catch (Exception e) {
            log.error("自动监测定时扫描异常", e);
        }
    }
}
