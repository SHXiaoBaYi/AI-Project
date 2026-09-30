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

    /**
     * 面试邀约简历上传的钉盘 spaceId（可选覆盖，走 Drive 上传）。
     * 留空则 POST /v1.0/storage/spaces（ownerType=APP）取应用存储空间。
     */
    private String resumeSpaceId = "";

    /**
     * 机器人 ActionCard 打开的 H5 根地址（含部署前缀，无尾斜杠）。
     * 例：http://121.40.119.134/shxby 。留空则尝试从 xby.upload-public-base 去掉 /api 推导。
     */
    private String h5BaseUrl = "";

    /**
     * 王方扬选「本机」时 ActionCard 打开的 H5 根。
     * 例：http://127.0.0.1:3000 。留空则用 h5-base-url，再空则用 http://127.0.0.1:3000。
     */
    private String localH5BaseUrl = "";

    @Data
    public static class Robot {
        /** 是否启动 Stream 长连接；本地可关，生产常驻服务建议开 */
        private boolean streamEnabled = true;
    }
}
