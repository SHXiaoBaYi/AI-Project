package com.base.admin.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置 —— 增强后端反序列化健壮性，不信任前端传入的数据格式。
 */
@Configuration
@RequiredArgsConstructor
public class JacksonConfig {

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void configure() {
        // "": 空字符串当 null 处理，兼容前端 "全部" 选项传空串等场景
        objectMapper.addHandler(new EmptyStringAsNullHandler());
        // 未知属性不抛异常
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
