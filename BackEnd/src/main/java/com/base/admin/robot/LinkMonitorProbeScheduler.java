package com.base.admin.robot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 链接监控探测：按每条 interval_sec（默认 86400=24h）到期执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkMonitorProbeScheduler {

    private final LinkMonitorService linkMonitorService;

    @Scheduled(cron = "${robot.link-monitor.probe-cron:0 */10 * * * ?}")
    public void probeDue() {
        List<Long> ids = linkMonitorService.dueMonitorIds();
        if (ids.isEmpty()) {
            return;
        }
        log.info("link monitor probe due count={}", ids.size());
        for (Long id : ids) {
            try {
                linkMonitorService.probeOne(id);
            } catch (Exception ex) {
                log.warn("probe monitor {} failed: {}", id, ex.getMessage());
            }
        }
    }
}
