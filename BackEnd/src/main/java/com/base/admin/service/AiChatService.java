package com.base.admin.service;

import com.base.admin.config.AiProperties;
import com.base.admin.domain.entity.SysAiProvider;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** 通义突发限流：让服务端排队，避免瞬时直接 429（阿里云官方建议） */
    private static final String DASHSCOPE_WAIT_TIMEOUT = "30";
    /** 某厂商刚被 429 后，短时跳过，避免连点把限额钉死 */
    private static final long RATE_LIMIT_COOLDOWN_MS = 60_000L;

    private final AiProperties aiProperties;
    private final SysAiProviderService aiProviderService;
    private final ObjectMapper objectMapper;

    /** provider -> 冷却到期时间戳 */
    private final ConcurrentHashMap<String, Long> rateLimitedUntil = new ConcurrentHashMap<>();

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
        return chat(systemPrompt, userPrompt, null, null);
    }

    public String chat(String systemPrompt, String userPrompt, String provider) {
        return chat(systemPrompt, userPrompt, provider, null);
    }

    /**
     * 调用指定厂商的 OpenAI 兼容 chat/completions。
     * 优先使用系统管理中配置的 API Key；未配时回退到 xby.ai 配置文件。
     * <p>
     * 未指定厂商时：默认厂商若刚被限流，自动换下一可用厂商，避免通义 429 把整条业务卡住。
     *
     * @param temperature 为空则默认 0.8；答疑意图识别等应传低温度减少瞎猜
     */
    public String chat(String systemPrompt, String userPrompt, String provider, Double temperature) {
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
                continue;
            }
            try {
                return chatOnce(systemPrompt, userPrompt, p, temperature);
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

    private String chatOnce(String systemPrompt, String userPrompt, String p, Double temperature) {
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

        // 通义排队等待可能到 30s，读超时要留余量
        int timeoutSec = Math.max(10, aiProperties.getTimeoutSeconds());
        if (AiProperties.PROVIDER_TONGYI.equals(p)) {
            timeoutSec = Math.max(timeoutSec, 90);
        }
        Duration timeout = Duration.ofSeconds(timeoutSec);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        RestClient.Builder clientBuilder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey);
        if (AiProperties.PROVIDER_TONGYI.equals(p)) {
            clientBuilder.defaultHeader("X-DashScope-Wait-Timeout", DASHSCOPE_WAIT_TIMEOUT);
        }
        RestClient client = clientBuilder.build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature != null ? temperature : 0.8);
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        try {
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
            clearRateLimit(p);
            return content.asText().trim();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String respBody = e.getResponseBodyAsString();
            log.error("调用 AI 失败 [{}] HTTP {} body={}", displayName, e.getStatusCode().value(),
                    truncate(respBody, 500));
            if (isRateLimited(e)) {
                markRateLimited(p);
                throw new BusinessException(friendlyRateLimit(displayName, respBody));
            }
            throw new BusinessException(friendlyAiError(displayName, e));
        } catch (Exception e) {
            log.error("调用 AI 大模型失败 provider={} model={}", p, model, e);
            if (isRateLimitedMessage(e.getMessage())) {
                markRateLimited(p);
                throw new BusinessException(friendlyRateLimit(displayName, e.getMessage()));
            }
            throw new BusinessException(friendlyAiError(displayName, e));
        }
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
                + "」刚触发限流，已冷却中。请改用其他厂商，或约 1 分钟后再试通义。"
                + "（本系统近期几乎未成功调用通义时仍 429，多为阿里云主账号下该 Key/模型配额被其他应用占用。）";
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

    private static String friendlyRateLimit(String displayName, String respBody) {
        String requestId = extractRequestId(respBody);
        String tip = "「" + displayName + "」被阿里云返回限流(429)。"
                + "这通常是主账号下该模型 RPM 已满（含其他系统/脚本共用同一 Key），"
                + "不是本后台「点了几次」这么简单。"
                + "请到百炼控制台核对调用量，或换 DeepSeek/智谱；也可更换通义 API Key。";
        if (StringUtils.hasText(requestId)) {
            tip += " request_id=" + requestId;
        }
        return tip;
    }

    private static String extractRequestId(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        // {"request_id":"..."} 或 "request_id":"..."
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
        if (e instanceof RestClientResponseException re) {
            detail = re.getStatusCode().value() + " " + re.getStatusText()
                    + (StringUtils.hasText(re.getResponseBodyAsString())
                    ? ": " + truncate(re.getResponseBodyAsString(), 280) : "");
        }
        return "调用 AI 失败（" + displayName + "）: " + truncate(nz(detail), 400);
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
}
