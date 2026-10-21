package com.base.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 防止生产误用测试 ECS 公网地址（历史写死 170.82 导致答疑配图等全挂）。
 * 对照：测试 121.40.170.82 / 生产 121.40.119.134。
 */
@Component
public class EnvPublicBaseGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnvPublicBaseGuard.class);
    private static final String TEST_HOST = "121.40.170.82";
    private static final String PROD_HOST = "121.40.119.134";

    private final Environment environment;

    @Value("${xby.upload-public-base:}")
    private String uploadPublicBase;

    @Value("${dingtalk.h5-base-url:}")
    private String h5BaseUrl;

    public EnvPublicBaseGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = false;
        for (String p : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p)) {
                prod = true;
                break;
            }
        }
        if (!prod) {
            log.info("附件公网根 upload-public-base={}（非 prod，允许测试机）",
                    StringUtils.hasText(uploadPublicBase) ? uploadPublicBase : "(空)");
            return;
        }
        assertNotTestHost("xby.upload-public-base", uploadPublicBase);
        assertNotTestHost("dingtalk.h5-base-url", h5BaseUrl);
        if (StringUtils.hasText(uploadPublicBase) && !uploadPublicBase.contains(PROD_HOST)
                && !uploadPublicBase.contains("nadaer.net")) {
            log.warn("生产 upload-public-base={} 未包含已知生产主机 {}，请确认是否故意使用域名",
                    uploadPublicBase, PROD_HOST);
        }
        log.info("生产环境公网地址校验通过 upload-public-base={} h5-base-url={}",
                uploadPublicBase, h5BaseUrl);
    }

    private static void assertNotTestHost(String name, String value) {
        if (StringUtils.hasText(value) && value.contains(TEST_HOST)) {
            throw new IllegalStateException(
                    "生产环境禁止使用测试 ECS 地址 " + TEST_HOST + "（配置项 " + name + "=" + value
                            + "）。请使用 application-prod.yml 中的 http://" + PROD_HOST + "/shxby");
        }
    }
}
