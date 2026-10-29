package com.base.admin.config;

import com.base.admin.service.HrKpiTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时按历史入职/合格简历全量回填 P0/P1 考核任务。
 */
@Slf4j
@Component
@Order(80)
@RequiredArgsConstructor
public class HrKpiTaskBackfillRunner implements ApplicationRunner {

    private final HrKpiTaskService kpiTaskService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = kpiTaskService.backfillAllHistorical();
            log.info("考核任务历史全量回填完成，处理 P0/P1 需求 {} 个", n);
        } catch (Exception ex) {
            log.warn("考核任务历史回填失败: {}", ex.getMessage());
        }
    }
}
