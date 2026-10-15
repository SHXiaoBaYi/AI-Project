package com.base.admin.config;

import com.base.admin.service.GeoContentPlacementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 内容投放样例 / 关联补齐 — 仅 {@code geo.content.seed=true} 时启动执行一次。
 * 日常保持关闭，避免每次重启改业务数据。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "geo.content.seed", havingValue = "true")
public class GeoContentPlacementSeeder implements ApplicationRunner {

    private final GeoContentPlacementService contentPlacementService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String summary = contentPlacementService.seedIfEmpty();
            log.info("GEO 内容投放样例: {}", summary);
            int topicFixed = contentPlacementService.backfillTopicRelations();
            if (topicFixed > 0) {
                log.info("GEO 内容投放话题关联补齐 {} 条", topicFixed);
            }
            int progressFixed = contentPlacementService.backfillPlacementProgress();
            if (progressFixed > 0) {
                log.info("GEO 内容投放进度回填 {} 条", progressFixed);
            }
        } catch (Exception e) {
            log.error("GEO 内容投放样例刷入失败", e);
        }
    }
}
