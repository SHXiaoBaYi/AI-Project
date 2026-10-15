package com.base.admin.config;

import com.base.admin.service.PlacementTaskSyncService;
import com.base.admin.service.SysTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 投放 ↔ 任务一次性刷数（仅显式开启时启动执行）。
 * <p>
 * 默认关闭：每次重启会把用户已删除的投放联动任务再次生成，属于严重缺陷。
 * 需要补齐历史缺口时临时设 {@code task.placement-sync.on-startup=true}，跑完改回 false。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "task.placement-sync.on-startup", havingValue = "true")
public class PlacementTaskSyncRunner implements ApplicationRunner {

    private final PlacementTaskSyncService placementTaskSyncService;
    private final SysTaskService sysTaskService;

    @Override
    public void run(ApplicationArguments args) {
        try {
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
