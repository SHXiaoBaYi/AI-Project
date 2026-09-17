package com.base.admin.config;

import com.base.admin.service.PlacementTaskSyncService;
import com.base.admin.service.SysTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5)
@RequiredArgsConstructor
public class PlacementTaskSyncRunner implements ApplicationRunner {

    private final PlacementTaskSyncService placementTaskSyncService;
    private final SysTaskService sysTaskService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 先回滚误批量派发的子任务，再刷待分配缺口
            String rollback = sysTaskService.rollbackMistakenSpawnBackfill();
            log.info("分配流误派发回滚: {}", rollback);
        } catch (Exception e) {
            log.error("分配流误派发回滚失败", e);
        }
        try {
            String summary = placementTaskSyncService.syncUnassignedPlacementTasks();
            log.info("投放待分配 → 任务列表: {}", summary);
        } catch (Exception e) {
            log.error("投放待分配任务刷数失败", e);
        }
    }
}
