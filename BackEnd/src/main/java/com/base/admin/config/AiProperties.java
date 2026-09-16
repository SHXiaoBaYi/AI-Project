package com.base.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 通用 AI 配置（OpenAI 兼容 chat/completions）。
 * 通过 provider 切换：deepseek / tongyi / zhipu / moonshot / local。
 */
@Data
@Component
@ConfigurationProperties(prefix = "xby.ai")
public class AiProperties {

    public static final String PROVIDER_LOCAL = "local";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_TONGYI = "tongyi";
    public static final String PROVIDER_ZHIPU = "zhipu";
    public static final String PROVIDER_MOONSHOT = "moonshot";

    /** 是否启用外部大模型（作为默认偏好；单次请求可指定厂商） */
    private boolean enabled = false;

    /**
     * 默认厂商：deepseek | tongyi | zhipu | moonshot | local
     */
    private String provider = "tongyi";

    /** 统一 api-key；若某厂商单独配置了 *-api-key，优先用厂商专用 key */
    private String apiKey = "";

    /** 覆盖预设 base-url（一般不用填） */
    private String baseUrl = "";

    /** 覆盖预设 model（一般不用填） */
    private String model = "";

    private String deepseekApiKey = "";
    private String tongyiApiKey = "";
    private String zhipuApiKey = "";
    private String moonshotApiKey = "";

    /** 请求超时秒 */
    private int timeoutSeconds = 60;

    public String normalizeProvider(String raw) {
        if (!StringUtils.hasText(raw)) {
            return resolveDefaultProvider();
        }
        String p = raw.trim().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "qwen", "dashscope" -> PROVIDER_TONGYI;
            case "glm" -> PROVIDER_ZHIPU;
            case "kimi" -> PROVIDER_MOONSHOT;
            case "heuristic", "template" -> PROVIDER_LOCAL;
            default -> p;
        };
    }

    public String resolveDefaultProvider() {
        return normalizeProvider(StringUtils.hasText(provider) ? provider : PROVIDER_TONGYI);
    }

    public boolean isLocalProvider(String provider) {
        return PROVIDER_LOCAL.equals(normalizeProvider(provider));
    }

    public String resolveApiKey(String provider) {
        String p = normalizeProvider(provider);
        if (PROVIDER_LOCAL.equals(p)) {
            return "";
        }
        String specific = switch (p) {
            case PROVIDER_DEEPSEEK -> deepseekApiKey;
            case PROVIDER_TONGYI -> tongyiApiKey;
            case PROVIDER_ZHIPU -> zhipuApiKey;
            case PROVIDER_MOONSHOT -> moonshotApiKey;
            default -> "";
        };
        if (StringUtils.hasText(specific)) {
            return specific.trim();
        }
        return apiKey == null ? "" : apiKey.trim();
    }

    public boolean isProviderConfigured(String provider) {
        String p = normalizeProvider(provider);
        if (PROVIDER_LOCAL.equals(p)) {
            return true;
        }
        return StringUtils.hasText(resolveApiKey(p));
    }

    public String resolveBaseUrl(String provider) {
        if (StringUtils.hasText(baseUrl) && normalizeProvider(provider).equals(resolveDefaultProvider())) {
            return trimSlash(baseUrl);
        }
        return switch (normalizeProvider(provider)) {
            case PROVIDER_TONGYI -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case PROVIDER_ZHIPU -> "https://open.bigmodel.cn/api/paas/v4";
            case PROVIDER_MOONSHOT -> "https://api.moonshot.cn/v1";
            case PROVIDER_DEEPSEEK -> "https://api.deepseek.com/v1";
            default -> "https://api.deepseek.com/v1";
        };
    }

    public String resolveModel(String provider) {
        if (StringUtils.hasText(model) && normalizeProvider(provider).equals(resolveDefaultProvider())) {
            return model.trim();
        }
        return switch (normalizeProvider(provider)) {
            case PROVIDER_TONGYI -> "qwen-turbo";
            case PROVIDER_ZHIPU -> "glm-4-flash";
            case PROVIDER_MOONSHOT -> "moonshot-v1-8k";
            case PROVIDER_DEEPSEEK -> "deepseek-chat";
            case PROVIDER_LOCAL -> "local-template";
            default -> "deepseek-chat";
        };
    }

    public String resolveDisplayName(String provider) {
        return switch (normalizeProvider(provider)) {
            case PROVIDER_TONGYI -> "通义千问";
            case PROVIDER_ZHIPU -> "智谱GLM";
            case PROVIDER_MOONSHOT -> "月之暗面";
            case PROVIDER_DEEPSEEK -> "DeepSeek";
            case PROVIDER_LOCAL -> "本地模板";
            default -> normalizeProvider(provider);
        };
    }

    /** 前端下拉：固定四家云厂商 + 本地模板 */
    public List<ProviderOption> listProviderOptions() {
        List<ProviderOption> list = new ArrayList<>();
        list.add(option(PROVIDER_LOCAL));
        list.add(option(PROVIDER_TONGYI));
        list.add(option(PROVIDER_DEEPSEEK));
        list.add(option(PROVIDER_ZHIPU));
        list.add(option(PROVIDER_MOONSHOT));
        return list;
    }

    private ProviderOption option(String provider) {
        ProviderOption o = new ProviderOption();
        o.setProvider(provider);
        o.setLabel(resolveDisplayName(provider));
        o.setModel(resolveModel(provider));
        o.setAvailable(isProviderConfigured(provider));
        if (!o.isAvailable()) {
            o.setHint("未配置 API Key");
        } else if (PROVIDER_LOCAL.equals(provider)) {
            o.setHint("不调用云端，随机模板扩写");
        } else {
            o.setHint(resolveModel(provider));
        }
        return o;
    }

    private static String trimSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    @Data
    public static class ProviderOption {
        private String provider;
        private String label;
        private String model;
        private boolean available;
        private String hint;
    }
}
