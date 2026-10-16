package com.base.admin.config;

import com.base.admin.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 防止 local/dev/test 误连生产 RDS、prod 误连测试 RDS。
 * 测试库外网 DNS 未开通时，本机必须经 SSH 隧道连 127.0.0.1，禁止改回生产外网地址「凑合用」。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RdsEnvironmentGuard implements ApplicationRunner {

    private final Environment environment;

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    @Override
    public void run(ApplicationArguments args) {
        String profiles = Arrays.stream(environment.getActiveProfiles())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(profiles)) {
            profiles = String.join(",", environment.getDefaultProfiles());
        }
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        boolean prodProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p));
        boolean hitsProd = url.contains(Constants.RDS_PROD_INSTANCE_MARKER.toLowerCase(Locale.ROOT));
        boolean hitsTest = url.contains(Constants.RDS_TEST_INSTANCE_MARKER.toLowerCase(Locale.ROOT))
                || url.contains("127.0.0.1") || url.contains("localhost");

        if (!prodProfile && hitsProd) {
            throw new IllegalStateException(
                    "当前 profile=[" + profiles + "] 禁止连接生产 RDS（JDBC 含 "
                            + Constants.RDS_PROD_INSTANCE_MARKER + "）。"
                            + "本机请先开隧道 deploy/tunnel-test-rds.ps1，再用 application-local.yml 连 127.0.0.1:13306。"
                            + "日常前端开发也可只开前端，默认代理测试 ECS，无需起本机后端。");
        }
        if (prodProfile && hitsTest && !hitsProd) {
            throw new IllegalStateException(
                    "当前 profile=prod 禁止连接测试 RDS（JDBC 未指向生产实例 "
                            + Constants.RDS_PROD_INSTANCE_MARKER + "）。");
        }

        String hostHint = extractHostHint(jdbcUrl);
        log.info("RDS 环境校验通过 profile=[{}] jdbcHost={}", profiles, hostHint);
    }

    private static String extractHostHint(String url) {
        if (!StringUtils.hasText(url)) {
            return "(empty)";
        }
        int start = url.indexOf("://");
        if (start < 0) {
            return url;
        }
        String rest = url.substring(start + 3);
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        int q = rest.indexOf('?');
        if (q >= 0) {
            rest = rest.substring(0, q);
        }
        return rest;
    }
}
