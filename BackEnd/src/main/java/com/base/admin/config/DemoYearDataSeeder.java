package com.base.admin.config;

import com.base.admin.service.DemoYearDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时按需刷入 2014/2015 演示数据。
 * <ul>
 *   <li>demo.seed=true（默认）：若尚无演示数据则刷入</li>
 *   <li>demo.reseed=true：强制清理后重刷</li>
 *   <li>demo.rebuild-board=true：仅按现有日监测重建周/月/年快照</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class DemoYearDataSeeder implements ApplicationRunner {

    private final DemoYearDataService demoYearDataService;

    @Value("${demo.seed:true}")
    private boolean seedEnabled;

    @Value("${demo.reseed:false}")
    private boolean reseed;

    @Value("${demo.rebuild-board:false}")
    private boolean rebuildBoard;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (rebuildBoard) {
                String summary = demoYearDataService.rebuildBoardsOnly();
                log.info("演示看板重建: {}", summary);
                return;
            }
            if (!seedEnabled && !reseed) {
                log.info("演示数据刷入已关闭（demo.seed=false）");
                return;
            }
            String summary = demoYearDataService.seed(reseed);
            log.info("演示数据: {}", summary);
        } catch (Exception e) {
            log.error("演示数据刷入失败", e);
        }
    }
}
