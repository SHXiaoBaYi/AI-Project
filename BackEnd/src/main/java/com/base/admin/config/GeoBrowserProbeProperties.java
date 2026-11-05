package com.base.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GEO 自动监测浏览器采集配置（对标模力指数「问答明细」取证链路）。
 *
 * <p>设计要点：C 端页面 DOM 变动频繁，所有选择器都放在配置里；
 * application.yml 里用 geo.auto-monitor.browser.profiles 按 name 覆盖内置默认值，
 * 未覆盖的字段沿用默认。未登录平台可在 storage-state-dir 下放 {平台名}.json
 * （Playwright storageState 格式）复用登录态。
 */
@Data
@Component
@ConfigurationProperties(prefix = "geo.auto-monitor.browser")
public class GeoBrowserProbeProperties {

    /** 是否启用浏览器采集（关闭时任务统一记为 CALL_FAILED，便于临时降级） */
    private boolean enabled = true;

    /** 无头模式；本地排查可设 false 观察真实页面 */
    private boolean headless = true;

    /**
     * Chromium 内核浏览器可执行文件。为空时使用 Playwright 自带 Chromium；
     * 本机可直接指向 Chrome/Edge，省去下载浏览器。
     */
    private String executablePath = "";

    /** 各平台登录态 storageState JSON 目录，文件名 = {平台名}.json */
    private String storageStateDir = "./uploads/geo/auto-monitor/state";

    /** 回答等待上限（毫秒） */
    private long answerTimeoutMs = 120_000;

    /** 页面导航超时（毫秒） */
    private long navigationTimeoutMs = 45_000;

    /** 回答文本连续多少次采样不变即判定流式输出结束 */
    private int stableChecks = 3;

    /** 稳定检测采样间隔（毫秒） */
    private long stableIntervalMs = 1_000;

    /** 浏览器视口 */
    private int viewportWidth = 1440;
    private int viewportHeight = 900;

    /** 采集串行化：同一时刻只允许一个页面采集，避免多会话互相污染 */
    private int maxConcurrentPages = 1;

    /** 登录态缓存有效期（毫秒）；过期后下次采集自动用平台账号重新模拟登录 */
    private long stateTtlMs = 12 * 60 * 60_000L;

    /** 单次模拟登录最长等待（毫秒） */
    private long loginTimeoutMs = 60_000;

    /** 登录提交后等待登录态落定的时间（毫秒） */
    private long loginSettleMs = 2_000;

    /** 整页截图最长边限制（防止超长回答生成超大图片），0=不限制 */
    private int screenshotMaxHeight = 20_000;

    /** 自定义 User-Agent，为空用浏览器默认 */
    private String userAgent = "";

    /** 平台适配器覆盖配置（按 name 合并到内置默认） */
    private List<PlatformProfile> profiles = new ArrayList<>();

    /** 单个平台适配器 */
    @Data
    public static class PlatformProfile {

        /** 平台名，须与 geo_platform.platform_name 一致 */
        private String name;

        /** C 端提问入口 */
        private String url = "";

        /** 提问输入框 */
        private String inputSelector = "textarea, [contenteditable='true'], [role='textbox']";

        /** 发送按钮；留空则用回车提交 */
        private String submitSelector = "";

        /** 回答容器（取最后一个） */
        private String answerSelector = "[data-testid*='message'], [class*='message-content'], [class*='answer'], [class*='markdown']";

        /** 仍在生成中的标志元素；存在则继续等待 */
        private String streamingSelector = "button[aria-label*='停止'], [data-testid*='stop'], [class*='stop-generating']";

        /** 深度思考开关；留空表示该平台不支持 */
        private String deepThinkSelector = "";

        /** 思考过程展开控件（回答完成后点击） */
        private String reasoningSelector = "text=/已深度思考|思考过程|查看思考/";

        /** 思考过程文本容器 */
        private String reasoningContentSelector = "[class*='reasoning'], [class*='thinking'], [class*='thought']";

        /** 分享按钮；点击后生成官方分享链接 */
        private String shareSelector = "text=/分享|分享链接|Share/";

        /** 分享弹窗里的链接输入框/文本 */
        private String shareLinkSelector = "input[value^='http'], [class*='share-link'] a, [class*='share'] a[href^='http']";

        /** 引用信源链接（默认回答区域内的外链） */
        private String citationSelector = "a[href^='http']";

        /** 出现任一可见元素即判定为「未登录」，直接返回可操作提示，避免把登录页当成回答 */
        private List<String> loginRequiredSelectors = new ArrayList<>();

        /** 提问前需要关闭的运营弹窗/引导层（按顺序各尝试点一次） */
        private List<String> dismissSelectors = new ArrayList<>();

        /** 回答正文里命中任一片段即视为页面 UI 文案而非真实回答，防止 DOM 兜底误判成功 */
        private List<String> answerRejectPatterns = new ArrayList<>();

        /* ===== v28 账号密码模拟登录适配器（全部可配置，C 端改版不用改代码） ===== */

        /** 登录页地址；留空则用提问页 url 并点击登录入口 */
        private String loginUrl = "";

        /** 登录入口按钮（未登录页上的「登录」），点击后弹出登录框 */
        private String loginEntrySelector = "";

        /** 可选：切到「账号密码登录」的 tab / 单选 */
        private String loginTabSelector = "";

        /** 账号输入框 */
        private String loginAccountSelector = "";

        /** 密码输入框 */
        private String loginPasswordSelector = "";

        /** 登录提交按钮 */
        private String loginSubmitSelector = "";

        /** 登录成功标志元素（出现即认为登录成功，如头像/新建对话按钮） */
        private String loginSuccessSelector = "";

        /** 登录成功标志 URL 片段（可选，命中即成功） */
        private String loginSuccessUrlPattern = "";

        /** 出现该元素说明需要人工验证码/扫码，直接返回可执行提示，不做无谓等待 */
        private String loginCaptchaSelector = "";

        /** 该平台是否支持深度思考 */
        private boolean deepThinkSupported = true;

        /** 该平台是否支持截图存证 */
        private boolean screenshotSupported = true;

        private boolean enabled = true;
    }

    /** 内置默认适配器：按平台名给一个可运行的起点，生产按 yml 覆盖 */
    private static final List<PlatformProfile> DEFAULT_PROFILES = List.of(
            profile("豆包", "https://www.doubao.com/chat/", true, true),
            profile("DS", "https://chat.deepseek.com/", true, true),
            profile("DeepSeek", "https://chat.deepseek.com/", true, true),
            profile("元宝", "https://yuanbao.tencent.com/", true, true),
            profile("Kimi", "https://www.kimi.com/", true, true),
            profile("通义千问", "https://www.qianwen.com/", true, true),
            profile("百度文心", "https://yiyan.baidu.com/", false, true),
            profile("夸克", "https://www.quark.cn/", false, true),
            profile("小红书", "https://www.xiaohongshu.com/search_result", false, false)
    );

    private static PlatformProfile profile(String name, String url, boolean deepThink, boolean screenshot) {
        PlatformProfile p = new PlatformProfile();
        p.setName(name);
        p.setUrl(url);
        p.setDeepThinkSupported(deepThink);
        p.setScreenshotSupported(screenshot);
        // 各平台 C 端登录页/落地页的通用识别信号；实测豆包未登录时带 to_login_button，
        // 提交问题会被重定向到 ?from_logout=1。命中后直接提示上传 storageState，不再空等 2 分钟。
        p.setLoginRequiredSelectors(new ArrayList<>(List.of(
                "[data-testid='to_login_button']",
                "text=/立即登录|扫码登录|登录后|请先登录|Sign in/"
        )));
        p.setDismissSelectors(new ArrayList<>(List.of(
                "button[aria-label='关闭']",
                "[data-testid='modal-close']",
                "[class*='modal'] [class*='close']"
        )));
        // DOM 兜底会挑「文本最密集的块」，未登录/被弹窗拦截时常常是无障碍拖拽提示或引导页文案。
        p.setAnswerRejectPatterns(new ArrayList<>(List.of(
                "To pick up a draggable item",
                "While dragging",
                "press the space bar",
                "有什么我能帮你的吗",
                "为你推荐",
                "下载电脑版",
                "立即下载",
                "登录后可"
        )));
        // 通用账号密码登录适配器：多数平台是「登录入口 → 账号/密码 → 提交」。
        // 只有扫码/短信验证码的平台（如豆包）会命中 loginCaptchaSelector 并给出可执行提示，
        // 也可在 yml 里按 name 覆盖为真实选择器，或把账号登录方式设为 STORAGE_STATE。
        p.setLoginEntrySelector("[data-testid='to_login_button'], text=/^登录$/");
        p.setLoginTabSelector("text=/密码登录|账号登录/");
        p.setLoginAccountSelector("input[type='text'], input[name='username'], input[placeholder*='账号'], input[placeholder*='手机']");
        p.setLoginPasswordSelector("input[type='password']");
        p.setLoginSubmitSelector("button[type='submit'], text=/^登录$/");
        // 默认靠「登录入口消失」判定成功；平台若有明确头像/用户名元素可显式配置 loginSuccessSelector
        p.setLoginCaptchaSelector("[class*='qrcode'], [class*='captcha'], text=/扫码登录|短信验证码|获取验证码/");
        return p;
    }

    /**
     * 合并内置默认与 yml 覆盖（同 name 时以配置为准，未设置的字段沿用默认）。
     * 用反射按非空字段覆盖，避免为每个选择器写一遍合并代码。
     */
    public Map<String, PlatformProfile> effectiveProfiles() {
        Map<String, PlatformProfile> result = new LinkedHashMap<>();
        for (PlatformProfile base : DEFAULT_PROFILES) {
            PlatformProfile copy = new PlatformProfile();
            org.springframework.beans.BeanUtils.copyProperties(base, copy);
            result.put(copy.getName(), copy);
        }
        for (PlatformProfile override : profiles) {
            if (override == null || !org.springframework.util.StringUtils.hasText(override.getName())) {
                continue;
            }
            PlatformProfile target = result.computeIfAbsent(override.getName(), k -> new PlatformProfile());
            copyNonDefault(override, target);
        }
        return result;
    }

    /** 仅覆盖显式配置过的字段（String 非空、boolean 与默认不同才覆盖） */
    private void copyNonDefault(PlatformProfile from, PlatformProfile to) {
        PlatformProfile blank = new PlatformProfile();
        for (java.lang.reflect.Field field : PlatformProfile.class.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(from);
                Object blankValue = field.get(blank);
                if (value == null || value.equals(blankValue)) {
                    continue;
                }
                field.set(to, value);
            } catch (IllegalAccessException ignored) {
                // 字段均为 public getter 的普通属性，理论上不会走到这里
            }
        }
    }
}
