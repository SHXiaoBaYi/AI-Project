package com.base.admin.config;

import com.base.admin.service.HrApplicationStageSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HrStageSyncScheduler {

    private final HrApplicationStageSync stageSync;

    /** 邀约时间一到，把仍停在「已邀约」且未出评价的一面推进为「已到面」。 */
    @Scheduled(cron = "${hr.stage-sync.cron:0 */5 * * * ?}", zone = "Asia/Shanghai")
    public void promoteShowUp() {
        try {
            stageSync.promoteShowUpDue();
        } catch (Exception ex) {
            log.warn("候选人到面阶段同步失败: {}", ex.getMessage());
        }
    }
}
