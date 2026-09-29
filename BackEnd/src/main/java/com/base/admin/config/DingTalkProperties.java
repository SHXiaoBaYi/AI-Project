package com.base.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dingtalk")
public class DingTalkProperties {

    /** 未配置企业内部应用时保持 false，邀约会记失败存档，不会假装成功 */
    private boolean enabled = false;

    private String clientId = "";

    private String clientSecret = "";

    /** 机器人 Stream 入站（单聊日程助手） */
    private Robot robot = new Robot();

    @Data
    public static class Robot {
        /** 是否启动 Stream 长连接；本地可关，生产常驻服务建议开 */
        private boolean streamEnabled = true;
    }
}
