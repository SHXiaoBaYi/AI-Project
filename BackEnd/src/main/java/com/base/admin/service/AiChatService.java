package com.base.admin.service;

import com.base.admin.config.AiProperties;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return aiProperties.isEnabled() && StringUtils.hasText(aiProperties.getApiKey());
    }

    /**
     * 调用 OpenAI 兼容 chat/completions，返回助手文本。
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            throw new BusinessException("未配置可用的 AI 大模型（xby.ai.enabled / api-key）");
        }
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(trimSlash(aiProperties.getBaseUrl()))
                    .defaultHeader("Authorization", "Bearer " + aiProperties.getApiKey().trim())
                    .build();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", aiProperties.getModel());
            body.put("temperature", 0.8);
            List<Map<String, String>> messages = new ArrayList<>();
            if (StringUtils.hasText(systemPrompt)) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", messages);

            String raw = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !StringUtils.hasText(content.asText())) {
                throw new BusinessException("AI 返回内容为空");
            }
            return content.asText().trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 大模型失败", e);
            throw new BusinessException("调用 AI 大模型失败: " + e.getMessage());
        }
    }

    private static String trimSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
