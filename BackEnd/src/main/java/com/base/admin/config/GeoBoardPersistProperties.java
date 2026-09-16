package com.base.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.board-persist")
public class GeoBoardPersistProperties {

    /** 是否启用定时落库 */
    private boolean enabled = true;

    /** 启动时立即执行一轮（本地联调） */
    private boolean onStartup = false;

    private int lookbackWeeks = 26;

    private int lookbackMonths = 12;

    private int lookbackYears = 2;

    private String weeklyCron = "0 0 2 ? * MON";

    private String monthlyCron = "0 10 2 1 * ?";

    private String yearlyCron = "0 20 2 1 1 ?";

    private String contentWeeklyCron = "0 15 2 ? * MON";

    private String contentMonthlyCron = "0 25 2 1 * ?";
}
