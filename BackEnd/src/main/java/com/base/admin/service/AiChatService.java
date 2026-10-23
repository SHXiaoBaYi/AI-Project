package com.base.admin.service;

import com.base.admin.config.AiProperties;
import com.base.admin.domain.entity.SysAiProvider;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.SysAiProviderTestVO;
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
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** 通义突发限流：服务端排队（对 Burst 有效；RPM 超限仍会 429） */
    private static final String DASHSCOPE_WAIT_TIMEOUT = "30";
    /** 某厂商全部候选模型都 429 后的短冷却，避免连点 */
    private static final long RATE_LIMIT_COOLDOWN_MS = 45_000L;

    /** 通义按模型独立限流：某模型异常时换其他模型，换 Key 往往无效 */
    private static final List<String> TONGYI_MODEL_FALLBACKS = List.of(
            "qwen-plus",
            "qwen-flash",
            "qwen-turbo",
            "qwen-turbo-latest"
    );

    private final AiProperties aiProperties;
    private final SysAiProviderService aiProviderService;
    private final ObjectMapper objectMapper;

    /** provider -> 冷却到期时间戳 */
    private final ConcurrentHashMap<String, Long> rateLimitedUntil = new ConcurrentHashMap<>();

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
        return chat(systemPrompt, userPrompt, null, null);
    }

    public String chat(String systemPrompt, String userPrompt, String provider) {
        return chat(systemPrompt, userPrompt, provider, null);
    }

    /**
     * 调用指定厂商的 OpenAI 兼容 chat/completions。
     * 通义在单模型 429 时会自动换同 Key 下的其他千问模型（限流按模型独立）。
     */
    public String chat(String systemPrompt, String userPrompt, String provider, Double temperature) {
        return chat(systemPrompt, userPrompt, provider, temperature, false);
    }

    /**
     * @param deepThink 成文等重任务开启深度思考（通义 enable_thinking；超时加长；不求快求稳）
     */
    public String chat(String systemPrompt, String userPrompt, String provider, Double temperature, boolean deepThink) {
        return chat(systemPrompt, userPrompt, provider, temperature, deepThink, null);
    }

    /**
     * @param topP 核采样上限；null 表示不传（沿用厂商默认）。成文建议约 0.85 与低温搭配压杜撰。
     */
    public String chat(String systemPrompt, String userPrompt, String provider, Double temperature,
                       boolean deepThink, Double topP) {
        boolean explicit = StringUtils.hasText(provider);
        List<String> candidates = explicit
                ? List.of(aiProperties.normalizeProvider(provider))
                : buildFallbackChain(aiProperties.resolveDefaultProvider());

        BusinessException lastBiz = null;
        for (int i = 0; i < candidates.size(); i++) {
            String p = candidates.get(i);
            if (aiProperties.isLocalProvider(p)) {
                throw new BusinessException("本地模板不走云端调用");
            }
            if (isInRateLimitCooldown(p)) {
                log.warn("跳过厂商 [{}]：仍在限流冷却中", aiProperties.resolveDisplayName(p));
                lastBiz = new BusinessException(cooldownMessage(p));
                if (explicit) {
                    throw lastBiz;
                }
                continue;
            }
            try {
                return chatOnce(systemPrompt, userPrompt, p, temperature, deepThink, topP);
            } catch (BusinessException e) {
                lastBiz = e;
                if (!explicit && isRateLimitedMessage(e.getMessage()) && i < candidates.size() - 1) {
                    String next = candidates.get(i + 1);
                    log.warn("厂商 [{}] 限流，自动改用 [{}]",
                            aiProperties.resolveDisplayName(p), aiProperties.resolveDisplayName(next));
                    continue;
                }
                throw e;
            }
        }
        throw lastBiz != null ? lastBiz : new BusinessException("没有可用的 AI 厂商");
    }

    /**
     * 流式 chat：边收边回调 delta（OpenAI 兼容 SSE）。
     * 失败时抛 BusinessException，由调用方决定是否回退非流式。
     */
    public String chatStream(String systemPrompt, String userPrompt, String provider, Double temperature,
                             boolean deepThink, Consumer<String> onDelta) {
        return chatStream(systemPrompt, userPrompt, provider, temperature, deepThink, null, onDelta);
    }

    public String chatStream(String systemPrompt, String userPrompt, String provider, Double temperature,
                             boolean deepThink, Double topP, Consumer<String> onDelta) {
        return chatStream(systemPrompt, userPrompt, provider, temperature, deepThink, topP, onDelta, null);
    }

    /**
     * @param onReasoning 深度思考增量（不入正文）；用于前端提示「思考中」
     */
    public String chatStream(String systemPrompt, String userPrompt, String provider, Double temperature,
                             boolean deepThink, Double topP, Consumer<String> onDelta,
                             Consumer<String> onReasoning) {
        boolean explicit = StringUtils.hasText(provider);
        List<String> candidates = explicit
                ? List.of(aiProperties.normalizeProvider(provider))
                : buildFallbackChain(aiProperties.resolveDefaultProvider());

        BusinessException lastBiz = null;
        for (int i = 0; i < candidates.size(); i++) {
            String p = candidates.get(i);
            if (aiProperties.isLocalProvider(p)) {
                throw new BusinessException("本地模板不走云端调用");
            }
            if (isInRateLimitCooldown(p)) {
                lastBiz = new BusinessException(cooldownMessage(p));
                if (explicit) {
                    throw lastBiz;
                }
                continue;
            }
            try {
                String full = chatOnceStream(systemPrompt, userPrompt, p, temperature, deepThink, topP,
                        onDelta, onReasoning);
                clearRateLimit(p);
                return full;
            } catch (BusinessException e) {
                lastBiz = e;
                if (!explicit && isRateLimitedMessage(e.getMessage()) && i < candidates.size() - 1) {
                    continue;
                }
                throw e;
            }
        }
        throw lastBiz != null ? lastBiz : new BusinessException("没有可用的 AI 厂商");
    }

    /** 探测指定厂商：用极短 prompt 试当前模型及通义备用模型 */
    public SysAiProviderTestVO testProvider(String provider) {
        String p = aiProperties.normalizeProvider(provider);
        if (aiProperties.isLocalProvider(p)) {
            return SysAiProviderTestVO.builder()
                    .success(true)
                    .provider(p)
                    .model("local-template")
                    .httpStatus(200)
                    .message("本地模板无需探测")
                    .build();
        }
        ResolvedEndpoint ep = resolveEndpoint(p);
        List<String> models = modelCandidates(p, ep.model());
        List<String> attempts = new ArrayList<>();
        clearRateLimit(p);
        for (String model : models) {
            try {
                String raw = invokeChatCompletions(ep, model, "你是连通性探测。", "只回复：ok", 0.1, false, null);
                attempts.add(model + " => OK");
                clearRateLimit(p);
                return SysAiProviderTestVO.builder()
                        .success(true)
                        .provider(p)
                        .model(model)
                        .httpStatus(200)
                        .message("连通成功，可用模型：" + model)
                        .attempts(attempts)
                        .build();
            } catch (CallFailedException e) {
                attempts.add(model + " => HTTP " + e.httpStatus() + " " + truncate(e.body(), 120));
                if (!e.rateLimited()) {
                    return SysAiProviderTestVO.builder()
                            .success(false)
                            .provider(p)
                            .model(model)
                            .httpStatus(e.httpStatus())
                            .message(friendlyProbeError(ep.displayName(), e))
                            .attempts(attempts)
                            .build();
                }
            } catch (Exception e) {
                attempts.add(model + " => EX " + truncate(e.getMessage(), 120));
                return SysAiProviderTestVO.builder()
                        .success(false)
                        .provider(p)
                        .model(model)
                        .message("调用异常：" + truncate(e.getMessage(), 200))
                        .attempts(attempts)
                        .build();
            }
        }
        markRateLimited(p);
        return SysAiProviderTestVO.builder()
                .success(false)
                .provider(p)
                .model(models.isEmpty() ? ep.model() : models.getLast())
                .httpStatus(429)
                .message("通义所有候选模型均返回 429。这是阿里云按「主账号 + 模型」限流："
                        + "换同账号新 Key 通常无效；请到百炼控制台看 qwen-plus / qwen-flash 是否可调，"
                        + "或把模型改成控制台里可用的名称，或先用智谱/DeepSeek。")
                .attempts(attempts)
                .build();
    }

    private List<String> buildFallbackChain(String preferred) {
        List<String> chain = new ArrayList<>();
        String first = aiProperties.normalizeProvider(preferred);
        if (!aiProperties.isLocalProvider(first) && isProviderConfigured(first)) {
            chain.add(first);
        }
        for (GeoAiProviderOptionVO opt : aiProviderService.listReadyOptions()) {
            String p = aiProperties.normalizeProvider(opt.getProvider());
            if (aiProperties.isLocalProvider(p) || !opt.isAvailable()) {
                continue;
            }
            if (!chain.contains(p)) {
                chain.add(p);
            }
        }
        for (String p : List.of(
                AiProperties.PROVIDER_ZHIPU,
                AiProperties.PROVIDER_DEEPSEEK,
                AiProperties.PROVIDER_MOONSHOT,
                AiProperties.PROVIDER_TONGYI)) {
            if (isProviderConfigured(p) && !chain.contains(p)) {
                chain.add(p);
            }
        }
        if (chain.isEmpty()) {
            chain.add(first);
        }
        return chain;
    }

    private String chatOnce(String systemPrompt, String userPrompt, String p, Double temperature,
                            boolean deepThink, Double topP) {
        ResolvedEndpoint ep = resolveEndpoint(p);
        List<String> models = modelCandidates(p, ep.model());
        CallFailedException lastRate = null;
        for (String model : models) {
            try {
                log.info("调用 AI [{}] model={} deepThink={} topP={} baseUrl={}",
                        ep.displayName(), model, deepThink, topP, ep.baseUrl());
                String raw = invokeChatCompletions(ep, model, systemPrompt, userPrompt, temperature, deepThink, topP);
                JsonNode root = objectMapper.readTree(raw);
                JsonNode message = root.path("choices").path(0).path("message");
                JsonNode content = message.path("content");
                String text = content.isMissingNode() ? "" : content.asText("");
                // 部分思考模型把正文放 reasoning 旁；content 为空时兜底
                if (!StringUtils.hasText(text) && message.has("reasoning_content")) {
                    text = message.path("reasoning_content").asText("");
                }
                if (!StringUtils.hasText(text)) {
                    throw new BusinessException("AI 返回内容为空（" + ep.displayName() + "）");
                }
                clearRateLimit(p);
                if (!model.equals(ep.model())) {
                    log.warn("厂商 [{}] 配置模型 {} 不可用/限流，已改用 {}", ep.displayName(), ep.model(), model);
                }
                return text.trim();
            } catch (BusinessException e) {
                throw e;
            } catch (CallFailedException e) {
                if (e.rateLimited()) {
                    log.warn("AI 限流 [{}] model={} HTTP {} body={}",
                            ep.displayName(), model, e.httpStatus(), truncate(e.body(), 240));
                    lastRate = e;
                    continue;
                }
                // 深度思考参数不被旧模型接受时，自动降级再试一次
                if (deepThink && e.httpStatus() >= 400 && e.httpStatus() < 500) {
                    log.warn("深度思考调用失败，降级普通模式重试 [{}] model={} HTTP {}",
                            ep.displayName(), model, e.httpStatus());
                    try {
                        String raw = invokeChatCompletions(ep, model, systemPrompt, userPrompt, temperature, false, topP);
                        JsonNode root = objectMapper.readTree(raw);
                        JsonNode content = root.path("choices").path(0).path("message").path("content");
                        if (content.isMissingNode() || !StringUtils.hasText(content.asText())) {
                            throw new BusinessException("AI 返回内容为空（" + ep.displayName() + "）");
                        }
                        clearRateLimit(p);
                        return content.asText().trim();
                    } catch (BusinessException be) {
                        throw be;
                    } catch (Exception ignored) {
                        // fall through to original error handling
                    }
                }
                log.error("调用 AI 失败 [{}] model={} HTTP {} body={}",
                        ep.displayName(), model, e.httpStatus(), truncate(e.body(), 500));
                throw new BusinessException(friendlyAiError(ep.displayName(), e));
            } catch (Exception e) {
                log.error("调用 AI 大模型失败 provider={} model={}", p, model, e);
                if (isRateLimitedMessage(e.getMessage())) {
                    lastRate = new CallFailedException(429, e.getMessage(), true);
                    continue;
                }
                throw new BusinessException(friendlyAiError(ep.displayName(), e));
            }
        }
        markRateLimited(p);
        if (lastRate != null) {
            throw new BusinessException(friendlyRateLimit(ep.displayName(), lastRate.body(), models));
        }
        throw new BusinessException("调用 AI 失败（" + ep.displayName() + "）");
    }

    private String chatOnceStream(String systemPrompt, String userPrompt, String p, Double temperature,
                                  boolean deepThink, Double topP, Consumer<String> onDelta,
                                  Consumer<String> onReasoning) {
        ResolvedEndpoint ep = resolveEndpoint(p);
        List<String> models = modelCandidates(p, ep.model());
        CallFailedException lastRate = null;
        for (String model : models) {
            try {
                log.info("流式调用 AI [{}] model={} deepThink={} topP={}", ep.displayName(), model, deepThink, topP);
                return invokeChatCompletionsStream(ep, model, systemPrompt, userPrompt, temperature, deepThink, topP,
                        onDelta, onReasoning);
            } catch (CallFailedException e) {
                if (e.rateLimited()) {
                    lastRate = e;
                    continue;
                }
                // 通义深度思考流式不稳定时降级非思考流式
                if (deepThink && e.httpStatus() >= 400 && e.httpStatus() < 500) {
                    try {
                        return invokeChatCompletionsStream(ep, model, systemPrompt, userPrompt, temperature, false, topP,
                                onDelta, onReasoning);
                    } catch (Exception ignored) {
                        // fall through
                    }
                }
                throw new BusinessException(friendlyAiError(ep.displayName(), e));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                if (isRateLimitedMessage(e.getMessage())) {
                    lastRate = new CallFailedException(429, e.getMessage(), true);
                    continue;
                }
                throw new BusinessException("流式调用失败（" + ep.displayName() + "）：" + truncate(e.getMessage(), 200));
            }
        }
        markRateLimited(p);
        if (lastRate != null) {
            throw new BusinessException(friendlyRateLimit(ep.displayName(), lastRate.body(), models));
        }
        throw new BusinessException("流式调用 AI 失败（" + ep.displayName() + "）");
    }

    private String invokeChatCompletionsStream(ResolvedEndpoint ep, String model,
                                               String systemPrompt, String userPrompt, Double temperature,
                                               boolean deepThink, Double topP, Consumer<String> onDelta,
                                               Consumer<String> onReasoning) throws Exception {
        int timeoutSec = Math.max(10, aiProperties.getTimeoutSeconds());
        if (AiProperties.PROVIDER_TONGYI.equals(ep.provider())) {
            timeoutSec = Math.max(timeoutSec, deepThink ? 300 : 120);
        } else if (deepThink) {
            timeoutSec = Math.max(timeoutSec, 240);
        }
        Duration timeout = Duration.ofSeconds(timeoutSec);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature != null ? temperature : 0.8);
        putTopP(body, topP);
        body.put("stream", true);
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);
        if (deepThink && AiProperties.PROVIDER_TONGYI.equals(ep.provider())) {
            body.put("enable_thinking", true);
        }
        String json = objectMapper.writeValueAsString(body);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(ep.baseUrl()) + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + ep.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (AiProperties.PROVIDER_TONGYI.equals(ep.provider())) {
            req.header("X-DashScope-Wait-Timeout", deepThink ? "120" : DASHSCOPE_WAIT_TIMEOUT);
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        HttpResponse<InputStream> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
        int status = resp.statusCode();
        if (status == 429) {
            String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new CallFailedException(429, err, true);
        }
        if (status < 200 || status >= 300) {
            String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new CallFailedException(status, err, isRateLimitedMessage(err));
        }

        StringBuilder full = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equalsIgnoreCase(data)) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode delta = root.path("choices").path(0).path("delta");
                    String reasoning = delta.path("reasoning_content").asText("");
                    if (StringUtils.hasText(reasoning) && onReasoning != null) {
                        onReasoning.accept(reasoning);
                    }
                    String piece = delta.path("content").asText("");
                    if (StringUtils.hasText(piece)) {
                        full.append(piece);
                        if (onDelta != null) {
                            onDelta.accept(piece);
                        }
                    }
                } catch (Exception parseEx) {
                    log.debug("跳过无法解析的 SSE 行: {}", truncate(data, 80));
                }
            }
        }
        String text = full.toString().trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("AI 流式返回内容为空（" + ep.displayName() + "）");
        }
        return text;
    }

    private String invokeChatCompletions(ResolvedEndpoint ep, String model,
                                         String systemPrompt, String userPrompt, Double temperature,
                                         boolean deepThink, Double topP) {
        int timeoutSec = Math.max(10, aiProperties.getTimeoutSeconds());
        if (AiProperties.PROVIDER_TONGYI.equals(ep.provider())) {
            timeoutSec = Math.max(timeoutSec, deepThink ? 300 : 90);
        } else if (deepThink) {
            timeoutSec = Math.max(timeoutSec, 240);
        }
        Duration timeout = Duration.ofSeconds(timeoutSec);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        RestClient.Builder clientBuilder = RestClient.builder()
                .baseUrl(ep.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + ep.apiKey());
        if (AiProperties.PROVIDER_TONGYI.equals(ep.provider())) {
            // 深度思考可能排队更久
            clientBuilder.defaultHeader("X-DashScope-Wait-Timeout", deepThink ? "120" : DASHSCOPE_WAIT_TIMEOUT);
        }
        RestClient client = clientBuilder.build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature != null ? temperature : 0.8);
        putTopP(body, topP);
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);
        if (deepThink && AiProperties.PROVIDER_TONGYI.equals(ep.provider())) {
            body.put("enable_thinking", true);
        }

        try {
            String raw = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return raw == null ? "" : raw;
        } catch (RestClientResponseException e) {
            String respBody = e.getResponseBodyAsString();
            throw new CallFailedException(e.getStatusCode().value(), respBody, isRateLimited(e));
        }
    }

    private ResolvedEndpoint resolveEndpoint(String p) {
        Optional<SysAiProvider> dbOpt = aiProviderService.findReady(p);
        if (dbOpt.isPresent()) {
            SysAiProvider row = dbOpt.get();
            String displayName = StringUtils.hasText(row.getProviderName())
                    ? row.getProviderName() : aiProperties.resolveDisplayName(p);
            String apiKey = row.getApiKey().trim();
            String model = StringUtils.hasText(row.getModel()) ? row.getModel().trim() : aiProperties.resolveModel(p);
            String baseUrl = StringUtils.hasText(row.getBaseUrl())
                    ? trimSlash(row.getBaseUrl()) : aiProperties.resolveBaseUrl(p);
            return new ResolvedEndpoint(p, displayName, apiKey, model, baseUrl);
        }
        if (aiProperties.isProviderConfigured(p)) {
            return new ResolvedEndpoint(
                    p,
                    aiProperties.resolveDisplayName(p),
                    aiProperties.resolveApiKey(p),
                    aiProperties.resolveModel(p),
                    aiProperties.resolveBaseUrl(p));
        }
        throw new BusinessException("未配置「" + aiProperties.resolveDisplayName(p) + "」的 API Key，请到「系统管理 → AI模型配置」中填写");
    }

    private List<String> modelCandidates(String provider, String preferred) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (StringUtils.hasText(preferred)) {
            set.add(preferred.trim());
        }
        if (AiProperties.PROVIDER_TONGYI.equals(provider)) {
            set.addAll(TONGYI_MODEL_FALLBACKS);
        }
        return new ArrayList<>(set);
    }

    private boolean isInRateLimitCooldown(String provider) {
        Long until = rateLimitedUntil.get(provider);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            rateLimitedUntil.remove(provider, until);
            return false;
        }
        return true;
    }

    private void markRateLimited(String provider) {
        rateLimitedUntil.put(provider, System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS);
    }

    private void clearRateLimit(String provider) {
        rateLimitedUntil.remove(provider);
    }

    private String cooldownMessage(String provider) {
        return "「" + aiProperties.resolveDisplayName(provider)
                + "」刚触发限流冷却。换同账号新 Key 通常无效（按主账号+模型计量）；"
                + "请到「AI模型配置」把通义模型改成 qwen-plus / qwen-flash，或先用智谱/DeepSeek，约 1 分钟后再试。";
    }

    private static boolean isRateLimited(RestClientResponseException e) {
        if (e.getStatusCode().value() == 429) {
            return true;
        }
        return isRateLimitedMessage(e.getResponseBodyAsString())
                || isRateLimitedMessage(e.getMessage());
    }

    private static boolean isRateLimitedMessage(String msg) {
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("429")
                || m.contains("too many requests")
                || m.contains("limit_requests")
                || m.contains("rate limit")
                || m.contains("rate_limit")
                || m.contains("throttling")
                || m.contains("exceeded your current request limit");
    }

    private static String friendlyRateLimit(String displayName, String respBody, List<String> triedModels) {
        String requestId = extractRequestId(respBody);
        String models = triedModels == null || triedModels.isEmpty() ? "-" : String.join("/", triedModels);
        String tip = "「" + displayName + "」候选模型均被阿里云返回 429（已试：" + models + "）。"
                + "限流按主账号+模型独立计算，换同账号新 Key 往往无效；"
                + "请到百炼控制台核对该模型调用量/是否开通，或改用 qwen-plus、qwen-flash，或先用智谱/DeepSeek。";
        if (StringUtils.hasText(requestId)) {
            tip += " request_id=" + requestId;
        }
        return tip;
    }

    private static String friendlyProbeError(String displayName, CallFailedException e) {
        if (e.rateLimited()) {
            return friendlyRateLimit(displayName, e.body(), List.of());
        }
        return "探测失败（" + displayName + "）HTTP " + e.httpStatus() + ": " + truncate(e.body(), 280);
    }

    private static String extractRequestId(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        int idx = body.indexOf("request_id");
        if (idx < 0) {
            return "";
        }
        int colon = body.indexOf(':', idx);
        int q1 = body.indexOf('"', colon + 1);
        int q2 = q1 >= 0 ? body.indexOf('"', q1 + 1) : -1;
        if (q1 >= 0 && q2 > q1) {
            return body.substring(q1 + 1, q2);
        }
        return "";
    }

    private static String friendlyAiError(String displayName, Exception e) {
        String detail = e == null ? "" : e.getMessage();
        if (e instanceof CallFailedException cfe) {
            detail = cfe.httpStatus() + ": " + truncate(cfe.body(), 280);
        } else if (e instanceof RestClientResponseException re) {
            detail = re.getStatusCode().value() + " " + re.getStatusText()
                    + (StringUtils.hasText(re.getResponseBodyAsString())
                    ? ": " + truncate(re.getResponseBodyAsString(), 280) : "");
        }
        return "调用 AI 失败（" + displayName + "）: " + truncate(nz(detail), 400);
    }

    /** null 或越界时不传，避免部分厂商校验失败 */
    private static void putTopP(Map<String, Object> body, Double topP) {
        if (topP == null || topP <= 0 || topP > 1) {
            return;
        }
        body.put("top_p", topP);
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String trimSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private record ResolvedEndpoint(String provider, String displayName, String apiKey, String model, String baseUrl) {
    }

    private static final class CallFailedException extends RuntimeException {
        private final int httpStatus;
        private final String body;
        private final boolean rateLimited;

        CallFailedException(int httpStatus, String body, boolean rateLimited) {
            super("HTTP " + httpStatus);
            this.httpStatus = httpStatus;
            this.body = body == null ? "" : body;
            this.rateLimited = rateLimited;
        }

        int httpStatus() {
            return httpStatus;
        }

        String body() {
            return body;
        }

        boolean rateLimited() {
            return rateLimited;
        }
    }
}
