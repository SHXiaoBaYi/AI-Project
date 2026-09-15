package com.base.admin.config;

import com.base.admin.domain.vo.GeoPersistResultVO;
import com.base.admin.service.GeoMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@Order(100)
@RequiredArgsConstructor
public class GeoBoardPersistScheduler implements ApplicationRunner {

    private final GeoBoardPersistProperties properties;
    private final GeoMonitorService monitorService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.isOnStartup()) {
            return;
        }
        log.info("GEO 看板落库：启动时执行一轮（仅已结束的周/月/年）");
        runAll("startup");
    }

    @Scheduled(cron = "${geo.board-persist.weekly-cron:0 0 2 ? * MON}")
    public void persistWeekly() {
        if (!properties.isEnabled()) {
            return;
        }
        runWeekly("cron");
    }

    @Scheduled(cron = "${geo.board-persist.monthly-cron:0 10 2 1 * ?}")
    public void persistMonthly() {
        if (!properties.isEnabled()) {
            return;
        }
        runMonthly("cron");
    }

    @Scheduled(cron = "${geo.board-persist.yearly-cron:0 20 2 1 1 ?}")
    public void persistYearly() {
        if (!properties.isEnabled()) {
            return;
        }
        runYearly("cron");
    }

    private void runAll(String trigger) {
        runWeekly(trigger);
        runMonthly(trigger);
        runYearly(trigger);
    }

    private void runWeekly(String trigger) {
        try {
            GeoPersistResultVO result = monitorService.autoPersistCompletedWeekly(properties.getLookbackWeeks());
            log.info("GEO周报落库[{}] period={}, snapshot={}, lockedDaily={}",
                    trigger, result.getPeriodCount(), result.getSnapshotCount(), result.getLockedDailyCount());
        } catch (Exception e) {
            log.error("GEO周报定时落库失败[{}]", trigger, e);
        }
    }

    private void runMonthly(String trigger) {
        try {
            GeoPersistResultVO result = monitorService.autoPersistCompletedMonthly(properties.getLookbackMonths());
            log.info("GEO月报落库[{}] period={}, snapshot={}, lockedDaily={}",
                    trigger, result.getPeriodCount(), result.getSnapshotCount(), result.getLockedDailyCount());
        } catch (Exception e) {
            log.error("GEO月报定时落库失败[{}]", trigger, e);
        }
    }

    private void runYearly(String trigger) {
        try {
            GeoPersistResultVO result = monitorService.autoPersistCompletedYearly(properties.getLookbackYears());
            log.info("GEO年报落库[{}] period={}, snapshot={}, lockedDaily={}",
                    trigger, result.getPeriodCount(), result.getSnapshotCount(), result.getLockedDailyCount());
        } catch (Exception e) {
            log.error("GEO年报定时落库失败[{}]", trigger, e);
        }
    }
}
