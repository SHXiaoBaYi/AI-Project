package com.base.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xby.ai")
public class AiProperties {

    /** 是否启用外部大模型；关闭时走本地启发式生成 */
    private boolean enabled = false;

    /** OpenAI 兼容接口，如 https://api.deepseek.com/v1 */
    private String baseUrl = "https://api.deepseek.com/v1";

    private String apiKey = "";

    private String model = "deepseek-chat";

    /** 请求超时秒 */
    private int timeoutSeconds = 60;
}
