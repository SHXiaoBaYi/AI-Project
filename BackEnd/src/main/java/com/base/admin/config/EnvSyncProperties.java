package com.base.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xby.env-sync")
public class EnvSyncProperties {

    /** 仅测试环境打开；生产必须为 false */
    private boolean enabled = false;

    private RdsEndpoint prod = new RdsEndpoint();

    @Data
    public static class RdsEndpoint {
        private String host = "rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com";
        private int port = 3306;
        private String database = "base_admin";
        private String username = "xiaobayi0099";
        private String password = "";
    }
}
