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
}
