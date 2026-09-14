package com.base.admin.config;

import com.base.admin.service.GeoSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "geo.seed", havingValue = "true")
public class GeoSampleDataSeeder implements ApplicationRunner {

    private final GeoSeedService seedService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String summary = seedService.seed();
            log.info("GEO 样例数据已刷入: {}", summary);
        } catch (Exception e) {
            log.error("GEO 样例数据刷入失败", e);
        }
    }
}
