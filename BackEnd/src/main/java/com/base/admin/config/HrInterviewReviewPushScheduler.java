package com.base.admin.config;

import com.base.admin.service.HrInterviewReviewPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hr.review-push", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HrInterviewReviewPushScheduler {

    private final HrInterviewReviewPushService pushService;

    /** 每 15 分钟扫一次邀约，催填评价 */
    @Scheduled(cron = "${hr.review-push.cron:0 */15 * * * ?}", zone = "Asia/Shanghai")
    public void tick() {
        try {
            pushService.scanAndPush();
        } catch (Exception ex) {
            log.warn("hr interview review push scan failed: {}", ex.getMessage());
        }
    }
}
