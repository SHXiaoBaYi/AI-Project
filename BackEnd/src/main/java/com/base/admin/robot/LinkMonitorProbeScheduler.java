package com.base.admin.robot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 链接监控探测：每个自然日 09:30（Asia/Shanghai）检查当日尚未探测的 ACTIVE 链接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkMonitorProbeScheduler {

    private final LinkMonitorService linkMonitorService;

    @Scheduled(cron = "${robot.link-monitor.probe-cron:0 30 9 * * ?}", zone = "${robot.link-monitor.probe-zone:Asia/Shanghai}")
    public void probeDaily() {
        List<Long> ids = linkMonitorService.dueMonitorIdsForNaturalDay();
        if (ids.isEmpty()) {
            log.info("link monitor daily probe: nothing due");
            return;
        }
        log.info("link monitor daily 09:30 probe count={}", ids.size());
        for (Long id : ids) {
            try {
                linkMonitorService.probeOne(id);
            } catch (Exception ex) {
                log.warn("probe monitor {} failed: {}", id, ex.getMessage());
            }
        }
    }
}
