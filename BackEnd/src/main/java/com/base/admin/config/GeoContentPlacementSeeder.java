package com.base.admin.config;

import com.base.admin.service.GeoContentPlacementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
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
