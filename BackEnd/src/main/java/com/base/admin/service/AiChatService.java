package com.base.admin.service;

import com.base.admin.config.AiProperties;
import com.base.admin.domain.entity.SysAiProvider;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiProperties aiProperties;
    private final SysAiProviderService aiProviderService;
    private final ObjectMapper objectMapper;

    /** 库内或配置文件中任一云厂商已配 Key */
    public boolean isEnabled() {
        if (aiProviderService.listReadyOptions().stream()
                .anyMatch(o -> o.isAvailable() && !AiProperties.PROVIDER_LOCAL.equals(o.getProvider()))) {
            return true;
        }
        if (aiProperties.isEnabled() && StringUtils.hasText(aiProperties.resolveApiKey(aiProperties.resolveDefaultProvider()))) {
            return true;
        }
        return aiProperties.listProviderOptions().stream()
                .anyMatch(o -> o.isAvailable() && !AiProperties.PROVIDER_LOCAL.equals(o.getProvider()));
    }

    public boolean isProviderConfigured(String provider) {
        String p = aiProperties.normalizeProvider(provider);
        if (aiProperties.isLocalProvider(p)) {
            return true;
        }
        if (aiProviderService.findReady(p).isPresent()) {
            return true;
        }
        return aiProperties.isProviderConfigured(p);
    }

    public List<AiProperties.ProviderOption> listProviderOptions() {
        return aiProperties.listProviderOptions();
    }

    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, null);
    }

    /**
     * 调用指定厂商的 OpenAI 兼容 chat/completions。
     * 优先使用系统管理中配置的 API Key；未配时回退到 xby.ai 配置文件。
     */
    public String chat(String systemPrompt, String userPrompt, String provider) {
        String p = aiProperties.normalizeProvider(provider);
        if (aiProperties.isLocalProvider(p)) {
            throw new BusinessException("本地模板不走云端调用");
        }

        Optional<SysAiProvider> dbOpt = aiProviderService.findReady(p);
        String displayName;
        String baseUrl;
        String model;
        String apiKey;

        if (dbOpt.isPresent()) {
            SysAiProvider row = dbOpt.get();
            displayName = StringUtils.hasText(row.getProviderName()) ? row.getProviderName() : aiProperties.resolveDisplayName(p);
            apiKey = row.getApiKey().trim();
            model = StringUtils.hasText(row.getModel()) ? row.getModel().trim() : aiProperties.resolveModel(p);
            baseUrl = StringUtils.hasText(row.getBaseUrl()) ? trimSlash(row.getBaseUrl()) : aiProperties.resolveBaseUrl(p);
        } else if (aiProperties.isProviderConfigured(p)) {
            displayName = aiProperties.resolveDisplayName(p);
            apiKey = aiProperties.resolveApiKey(p);
            model = aiProperties.resolveModel(p);
            baseUrl = aiProperties.resolveBaseUrl(p);
        } else {
            throw new BusinessException("未配置「" + aiProperties.resolveDisplayName(p) + "」的 API Key，请到「系统管理 → AI模型配置」中填写");
        }

        try {
            Duration timeout = Duration.ofSeconds(Math.max(10, aiProperties.getTimeoutSeconds()));
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(timeout);

            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(requestFactory)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0.8);
            List<Map<String, String>> messages = new ArrayList<>();
            if (StringUtils.hasText(systemPrompt)) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", messages);

            log.info("调用 AI [{}] model={} baseUrl={}", displayName, model, baseUrl);
            String raw = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !StringUtils.hasText(content.asText())) {
                throw new BusinessException("AI 返回内容为空（" + displayName + "）");
            }
            return content.asText().trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 大模型失败 provider={} model={}", p, model, e);
            throw new BusinessException("调用 AI 失败（" + displayName + "）: " + e.getMessage());
        }
    }

    private static String trimSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
