package com.base.admin.service;

import com.base.admin.config.GeoBrowserProbeProperties;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * C 端网页版浏览器采集器（对标模力指数「问答明细」取证链路）。
 *
 * <p>流程：打开平台提问页 →（可选）开启深度思考 → 输入问题 → 等待流式输出稳定 →
 * 提取回答正文 / 思考过程 / 引用信源链接 → 点分享拿官方会话链接 → 整页截图存证。
 *
 * <p>工程约束：
 * <ul>
 *   <li>Playwright 对象统一在单线程内创建/使用/销毁，避免跨线程调用问题；</li>
 *   <li>选择器全部来自 {@link GeoBrowserProbeProperties} 可配置，C 端改版不用改代码；</li>
 *   <li>登录态用 Playwright storageState 文件按平台复用；</li>
 *   <li>每一步都写入诊断 JSON，失败也返回已取得的截图/部分内容，便于排查选择器失效。</li>
 * </ul>
 */
@Slf4j
@Service
public class GeoBrowserProbeService {

    /** 引用信源 */
    public record Citation(String title, String site, String url, String context) {
    }

    /** 账号登录态结果（模拟登录 / 缓存复用 / 手工上传） */
    public record LoginOutcome(
            boolean success,
            String error,
            String platform,
            String accountLabel,
            String source,
            Path statePath,
            String screenshotUrl,
            String diagnostic) {
    }

    /** 登录被阻断时在单线程内抛出，由 doProbe 的 catch 转成可操作错误（不再空跑提问） */
    private static class LoginBlockedException extends RuntimeException {
        private final String diagnostic;
        private final String screenshotUrl;

        LoginBlockedException(String message, String diagnostic, String screenshotUrl) {
            super(message);
            this.diagnostic = diagnostic;
            this.screenshotUrl = screenshotUrl;
        }
    }

    /** 采集结果（含诊断） */
    public record ProbeOutcome(
            boolean success,
            String error,
            String answerText,
            String reasoningText,
            List<Citation> citations,
            String probeUrl,
            String probeUrlSource,
            String screenshotUrl,
            String screenshotType,
            String pageUrl,
            String modelLabel,
            boolean deepThinkApplied,
            String diagnostic,
            String platform,
            String question) {
    }

    /** 页面内提取：回答兜底（选择器失效时找文本最密集的正文块） */
    private static final String FALLBACK_ANSWER_JS = """
            () => {
              const nodes = Array.from(document.querySelectorAll('div,article,section,main'));
              const scored = [];
              for (const n of nodes) {
                const text = (n.innerText || '').trim();
                const len = text.length;
                if (len < 120 || len > 30000) continue;
                const blocks = n.querySelectorAll('div,article,section,p').length;
                scored.push({ text, score: len - blocks * 30 });
              }
              scored.sort((a, b) => b.score - a.score);
              return JSON.stringify({ text: scored.length ? scored[0].text : '' });
            }
            """;

    /** 页面内提取：引用信源外链 */
    private static final String CITATION_JS = """
            () => {
              const out = [];
              const seen = new Set();
              document.querySelectorAll("a[href^='http']").forEach(a => {
                const url = a.href || '';
                const title = (a.innerText || a.getAttribute('title') || '').trim();
                if (!url || seen.has(url)) return;
                seen.add(url);
                out.push({ title: title.slice(0, 200), url });
              });
              return JSON.stringify(out.slice(0, 30));
            }
            """;

    /** 页面内提取：思考过程文本 */
    private static final String REASONING_JS = """
            () => {
              const sels = "[class*='reasoning'],[class*='thinking'],[class*='thought'],[data-testid*='reasoning']";
              const parts = [];
              document.querySelectorAll(sels).forEach(n => {
                const t = (n.innerText || '').trim();
                if (t.length > 20) parts.push(t);
              });
              return JSON.stringify({ text: parts.join('\\n\\n').slice(0, 20000) });
            }
            """;

    private final GeoBrowserProbeProperties properties;
    private final ObjectMapper objectMapper;
    private final GeoBrowserAccountService accountService;

    @Value("${xby.upload-dir:./uploads}")
    private String uploadDir;

    /** Playwright 专用单线程：Browser/Page 生命周期都在这一个线程内 */
    private final ExecutorService probeThread = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "geo-browser-probe");
        thread.setDaemon(true);
        return thread;
    });

    private Playwright playwright;
    private Browser browser;

    public GeoBrowserProbeService(GeoBrowserProbeProperties properties, ObjectMapper objectMapper,
                                  GeoBrowserAccountService accountService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.accountService = accountService;
    }

    /**
     * 对外：按平台账号模拟登录 / 刷新登录态。与采集共用同一个 Playwright 单线程，
     * 避免两套浏览器实例互相抢资源；登录成功会把 storageState 落盘并回写账号状态。
     */
    public LoginOutcome ensureLogin(String platform, boolean forceRefresh, Long accountId) {
        if (!properties.isEnabled()) {
            return loginOutcomeFail(platform, "浏览器采集已关闭（geo.auto-monitor.browser.enabled=false）", new LinkedHashMap<>());
        }
        Future<LoginOutcome> future = probeThread.submit(() -> doEnsureLogin(platform, forceRefresh, accountId));
        long budgetMs = properties.getLoginTimeoutMs() + properties.getNavigationTimeoutMs() + 30_000;
        try {
            return future.get(budgetMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            Map<String, Object> diag = new LinkedHashMap<>();
            diag.put("stage", "LOGIN_OUTER_TIMEOUT");
            diag.put("timeoutMs", budgetMs);
            return loginOutcomeFail(platform, "登录超时：超过 " + (budgetMs / 1000) + "s，请检查登录页可达性与登录适配器选择器", diag);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return loginOutcomeFail(platform, "登录被中断", new LinkedHashMap<>());
        } catch (Exception e) {
            future.cancel(true);
            return loginOutcomeFail(platform, "登录异常：" + rootMessage(e), new LinkedHashMap<>());
        }
    }

    private LoginOutcome loginOutcomeFail(String platform, String error, Map<String, Object> diag) {
        diag.put("error", error);
        return new LoginOutcome(false, error, platform, "", "", null, "", writeJson(diag));
    }

    /**
     * 采集一次问答。始终返回 outcome（失败也带诊断），不直接抛异常，方便执行引擎逐项记录。
     */
    public ProbeOutcome probe(String platform, String question, boolean deepThink,
                              boolean screenshot, String region, Long resultId) {
        if (!properties.isEnabled()) {
            return failed(platform, question, "浏览器采集已关闭（geo.auto-monitor.browser.enabled=false）", new LinkedHashMap<>());
        }
        GeoBrowserProbeProperties.PlatformProfile profile = properties.effectiveProfiles().get(platform);
        if (profile == null || !profile.isEnabled() || !StringUtils.hasText(profile.getUrl())) {
            return failed(platform, question,
                    "平台「" + platform + "」尚未配置浏览器采集适配器，请在 geo.auto-monitor.browser.profiles 中补充 URL 与选择器",
                    new LinkedHashMap<>());
        }
        // 诊断表由采集线程写入；外层超时/异常时也能拿到「卡在哪一步」的现场，而不是空诊断。
        Map<String, Object> diag = Collections.synchronizedMap(new LinkedHashMap<>());
        Future<ProbeOutcome> future = probeThread.submit(
                () -> doProbe(profile, question, deepThink, screenshot, region, resultId, diag));
        // 预算需覆盖「可能发生的账号登录 + 导航 + 等待回答 + 取证」，否则登录耗时会误判为采集超时
        long budgetMs = properties.getAnswerTimeoutMs() + properties.getNavigationTimeoutMs() * 2
                + properties.getLoginTimeoutMs() + 60_000;
        try {
            return future.get(budgetMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            diag.put("stage", "OUTER_TIMEOUT");
            diag.put("timeoutMs", budgetMs);
            return failed(platform, question,
                    "采集超时：超过 " + (budgetMs / 1000) + "s（多为浏览器启动/下载卡住或页面无响应，详见诊断 stage）", diag);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return failed(platform, question, "采集被中断", diag);
        } catch (Exception e) {
            future.cancel(true);
            return failed(platform, question, "采集异常：" + rootMessage(e), diag);
        }
    }

    /**
     * 登录态来源优先级：账号自动登录缓存 → 账号密码模拟登录 → 账号绑定 STORAGE_STATE 文件 → 手工上传兜底。
     * 全部在同一个 Playwright 单线程内执行；登录态按平台缓存，一批 3 题内只登录一次。
     */
    private LoginOutcome doEnsureLogin(String platform, boolean forceRefresh, Long accountId) {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("platform", platform);
        diag.put("forceRefresh", forceRefresh);
        diag.put("stage", "RESOLVE_ACCOUNT");

        java.util.Optional<com.base.admin.domain.entity.GeoPlatformAccount> accOpt = accountId != null
                ? java.util.Optional.of(accountService.require(accountId))
                : accountService.findUsable(platform);
        if (accOpt.isEmpty()) {
            Path manual = accountService.manualStateFile(platform);
            if (manual != null) {
                diag.put("source", "MANUAL_UPLOAD");
                return new LoginOutcome(true, "", platform, "手工上传登录态", "MANUAL_UPLOAD", manual, "", writeJson(diag));
            }
            diag.put("stage", "NO_ACCOUNT");
            return loginOutcomeFail(platform,
                    "平台「" + platform + "」未配置浏览器自动监测账号：请到「平台账号管理」把该平台账号设为「用于浏览器监测」并填写账号密码，"
                            + "或在「采集诊断」上传 Playwright storageState（" + platform + ".json）", diag);
        }

        com.base.admin.domain.entity.GeoPlatformAccount account = accOpt.get();
        String label = accountService.mask(account.getAccount());
        diag.put("accountId", account.getId());
        diag.put("account", label);
        String type = StringUtils.hasText(account.getBrowserLoginType())
                ? account.getBrowserLoginType() : GeoBrowserAccountService.LOGIN_PASSWORD;

        if (GeoBrowserAccountService.LOGIN_STORAGE_STATE.equalsIgnoreCase(type)) {
            Path file = accountService.accountStateFile(account);
            if (Files.isRegularFile(file)) {
                diag.put("source", "STORAGE_STATE");
                accountService.markReady(account, file);
                return new LoginOutcome(true, "", platform, label, "STORAGE_STATE", file, "", writeJson(diag));
            }
            diag.put("stage", "STATE_FILE_MISSING");
            return loginOutcomeFail(platform, "账号「" + label + "」登录方式为 STORAGE_STATE，但登录态文件不存在："
                    + "请先执行 scripts/export-geo-storage-state.sh 人工导出，或在「采集诊断」上传", diag);
        }

        if (!forceRefresh && accountService.isReady(account)) {
            Path cached = accountService.accountStateFile(account);
            diag.put("source", "CACHED");
            diag.put("statePath", cached.toString());
            return new LoginOutcome(true, "", platform, label, "CACHED", cached, "", writeJson(diag));
        }
        if (!StringUtils.hasText(account.getPassword())) {
            diag.put("stage", "NO_PASSWORD");
            return loginOutcomeFail(platform, "账号「" + label + "」未配置登录密码，请到「平台账号管理」补齐", diag);
        }
        if (forceRefresh && accountService.stateFileExists(account)) {
            accountService.markExpired(account);
        }
        return doPasswordLogin(account, diag);
    }

    /** 用系统维护的平台账号，在服务器无头浏览器里模拟登录并导出 storageState */
    private LoginOutcome doPasswordLogin(com.base.admin.domain.entity.GeoPlatformAccount account,
                                         Map<String, Object> diag) {
        String platform = account.getPlatformName();
        String label = accountService.mask(account.getAccount());
        GeoBrowserProbeProperties.PlatformProfile profile = properties.effectiveProfiles().get(platform);
        if (profile == null) {
            return loginOutcomeFail(platform, "平台「" + platform + "」没有浏览器采集适配器，无法模拟登录", diag);
        }
        if (!StringUtils.hasText(profile.getLoginAccountSelector())
                || !StringUtils.hasText(profile.getLoginPasswordSelector())) {
            diag.put("stage", "NO_LOGIN_ADAPTER");
            return loginOutcomeFail(platform, "平台「" + platform + "」未配置账号密码登录适配器，"
                    + "请在 geo.auto-monitor.browser.profiles 覆盖 login-account-selector / login-password-selector，"
                    + "或把该账号登录方式改为 STORAGE_STATE", diag);
        }

        BrowserContext context = null;
        String screenshot = "";
        try {
            diag.put("stage", "LOGIN_LAUNCH_BROWSER");
            ensureBrowser();
            context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(properties.getViewportWidth(), properties.getViewportHeight())
                    .setLocale("zh-CN"));
            Page page = context.newPage();
            page.setDefaultTimeout(15_000);

            String loginUrl = StringUtils.hasText(profile.getLoginUrl()) ? profile.getLoginUrl() : profile.getUrl();
            diag.put("loginUrl", loginUrl);
            diag.put("stage", "LOGIN_NAVIGATE");
            page.navigate(loginUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(properties.getNavigationTimeoutMs()));
            page.waitForTimeout(1_500);

            // 1) 打开登录框
            boolean entryClicked = false;
            if (StringUtils.hasText(profile.getLoginEntrySelector())) {
                Locator entry = page.locator(profile.getLoginEntrySelector()).first();
                if (entry.count() > 0 && entry.isVisible()) {
                    entry.click(new Locator.ClickOptions().setTimeout(5_000));
                    entryClicked = true;
                    page.waitForTimeout(1_000);
                }
            }
            diag.put("loginEntryClicked", entryClicked);

            // 2) 切到「账号密码登录」tab（部分平台默认是扫码）
            if (StringUtils.hasText(profile.getLoginTabSelector())) {
                clickIfVisible(page, profile.getLoginTabSelector(), diag, "loginTab");
            }

            // 3) 找账号/密码输入框；找不到时区分「需要人工验证码」与「选择器失效」
            Locator accountInput = page.locator(profile.getLoginAccountSelector()).first();
            Locator passwordInput = page.locator(profile.getLoginPasswordSelector()).first();
            boolean inputsReady = false;
            try {
                accountInput.waitFor(new Locator.WaitForOptions().setTimeout(8_000));
                inputsReady = passwordInput.count() > 0;
            } catch (Exception ignored) {
                // 落到下面统一给可执行提示
            }
            diag.put("loginInputsReady", inputsReady);
            if (!inputsReady) {
                boolean captcha = isAnyVisible(page, profile.getLoginCaptchaSelector());
                diag.put("captchaDetected", captcha);
                String hint = captcha
                        ? "该平台当前只提供扫码/短信验证码登录，无头浏览器无法自动完成：请把该账号的浏览器登录方式改为 STORAGE_STATE，"
                        + "并用 scripts/export-geo-storage-state.sh 人工导出登录态"
                        : "未找到账号/密码输入框：请在 geo.auto-monitor.browser.profiles 按平台覆盖 login-*-selector，"
                        + "或把该账号登录方式改为 STORAGE_STATE";
                screenshot = screenshotOnFailure(page, profile, diag);
                accountService.markFailed(account, hint);
                return new LoginOutcome(false, hint, platform, label, "", null, screenshot, writeJson(diag));
            }

            // 4) 填表提交（密码只进浏览器，不写日志/诊断）
            accountInput.fill(account.getAccount());
            passwordInput.fill(account.getPassword());
            diag.put("stage", "LOGIN_SUBMIT");
            boolean submitted = false;
            if (StringUtils.hasText(profile.getLoginSubmitSelector())) {
                Locator button = page.locator(profile.getLoginSubmitSelector()).first();
                if (button.count() > 0 && button.isVisible()) {
                    button.click(new Locator.ClickOptions().setTimeout(5_000));
                    submitted = true;
                }
            }
            if (!submitted) {
                passwordInput.press("Enter");
            }
            diag.put("loginSubmittedBy", submitted ? "click" : "Enter");

            // 5) 等登录成功：成功标志出现 / 登录入口消失 / URL 命中
            diag.put("stage", "LOGIN_WAIT");
            boolean success = false;
            long deadline = System.currentTimeMillis() + properties.getLoginTimeoutMs();
            while (System.currentTimeMillis() < deadline) {
                if (StringUtils.hasText(profile.getLoginSuccessUrlPattern())
                        && page.url().contains(profile.getLoginSuccessUrlPattern())) {
                    success = true;
                    break;
                }
                if (StringUtils.hasText(profile.getLoginSuccessSelector())
                        && isAnyVisible(page, profile.getLoginSuccessSelector())) {
                    success = true;
                    break;
                }
                if (entryClicked && StringUtils.hasText(profile.getLoginEntrySelector())
                        && !isAnyVisible(page, profile.getLoginEntrySelector())) {
                    success = true;
                    break;
                }
                page.waitForTimeout(500);
            }
            diag.put("loginSuccess", success);
            diag.put("landingUrl", page.url());
            if (!success) {
                String error = "账号密码登录未成功（" + (properties.getLoginTimeoutMs() / 1000)
                        + "s 内未检测到登录成功标志）：请核对账号密码、登录适配器选择器，或改用 STORAGE_STATE 登录态";
                screenshot = screenshotOnFailure(page, profile, diag);
                accountService.markFailed(account, error);
                return new LoginOutcome(false, error, platform, label, "", null, screenshot, writeJson(diag));
            }

            // 6) 导出登录态并回写账号，后续采集（同批次/TTL 内）直接复用，不重复登录
            page.waitForTimeout(properties.getLoginSettleMs());
            Path stateFile = accountService.accountStateFile(account);
            Files.createDirectories(stateFile.getParent());
            context.storageState(new BrowserContext.StorageStateOptions().setPath(stateFile));
            accountService.markReady(account, stateFile);
            diag.put("source", "PASSWORD_LOGIN");
            diag.put("statePath", stateFile.toString());
            log.info("平台账号模拟登录成功 platform={} account={} stateFile={}", platform, label, stateFile.getFileName());
            return new LoginOutcome(true, "", platform, label, "PASSWORD_LOGIN", stateFile, "", writeJson(diag));
        } catch (Exception e) {
            String error = "模拟登录异常：" + rootMessage(e);
            diag.put("exception", error);
            accountService.markFailed(account, error);
            return new LoginOutcome(false, error, platform, label, "", null, screenshot, writeJson(diag));
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {
                    // 关闭失败不影响结果
                }
            }
        }
    }

    private boolean isAnyVisible(Page page, String selector) {
        if (!StringUtils.hasText(selector)) {
            return false;
        }
        try {
            Locator locator = page.locator(selector).first();
            return locator.count() > 0 && locator.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    private String screenshotOnFailure(Page page, GeoBrowserProbeProperties.PlatformProfile profile,
                                       Map<String, Object> diag) {
        try {
            return saveScreenshot(page, profile, null, diag);
        } catch (Exception e) {
            diag.put("loginScreenshotError", rootMessage(e));
            return "";
        }
    }

    /** 单线程内执行完整采集流程 */
    private ProbeOutcome doProbe(GeoBrowserProbeProperties.PlatformProfile profile, String question,
                                 boolean deepThink, boolean screenshot, String region, Long resultId,
                                 Map<String, Object> diag) {
        long begin = System.currentTimeMillis();
        diag.put("stage", "INIT");
        diag.put("platform", profile.getName());
        diag.put("entryUrl", profile.getUrl());
        diag.put("question", truncate(question, 300));
        diag.put("region", StringUtils.hasText(region) ? region : "全国随机");
        diag.put("deepThinkRequested", deepThink);
        diag.put("screenshotRequested", screenshot);
        diag.put("deepThinkSupported", profile.isDeepThinkSupported());
        diag.put("screenshotSupported", profile.isScreenshotSupported());

        BrowserContext context = null;
        String answerText = "";
        String reasoningText = "";
        List<Citation> citations = new ArrayList<>();
        String probeUrl = "";
        String probeUrlSource = "";
        String screenshotUrl = "";
        boolean deepThinkApplied = false;
        String pageUrl = "";
        String error = null;
        try {
            diag.put("stage", "LAUNCH_BROWSER");
            ensureBrowser();
            // v28：先按「平台账号」确保登录态（缓存复用 / 账号密码模拟登录 / 手工上传兜底），
            // 未就绪直接失败，不再无登录态空跑提问产生假数据。
            diag.put("stage", "ENSURE_LOGIN");
            LoginOutcome login = doEnsureLogin(profile.getName(), false, null);
            diag.put("loginSource", login.source());
            diag.put("loginAccount", login.accountLabel());
            if (!login.success()) {
                diag.put("loginDiagnostic", login.diagnostic());
                throw new LoginBlockedException(login.error(), login.diagnostic(), login.screenshotUrl());
            }
            diag.put("stage", "NEW_CONTEXT");
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setViewportSize(properties.getViewportWidth(), properties.getViewportHeight())
                    .setLocale("zh-CN");
            if (StringUtils.hasText(properties.getUserAgent())) {
                contextOptions.setUserAgent(properties.getUserAgent());
            }
            if (login.statePath() != null) {
                contextOptions.setStorageStatePath(login.statePath());
                diag.put("storageState", login.statePath().getFileName().toString());
            }
            context = browser.newContext(contextOptions);
            context.setDefaultTimeout(15_000);
            try {
                context.grantPermissions(List.of("clipboard-read", "clipboard-write"));
            } catch (Exception ignored) {
                diag.put("clipboardPermission", "授予失败，分享链接回退到页面URL");
            }
            Page page = context.newPage();
            page.setDefaultTimeout(15_000);

            diag.put("stage", "NAVIGATE");
            long t0 = System.currentTimeMillis();
            page.navigate(profile.getUrl(), new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(properties.getNavigationTimeoutMs()));
            page.waitForTimeout(1_500);
            diag.put("navigateMs", System.currentTimeMillis() - t0);

            // 关闭运营弹窗，避免遮挡输入框 / 发送按钮
            diag.put("stage", "DISMISS_OVERLAYS");
            dismissOverlays(page, profile, diag);

            // 未登录直接返回可操作提示：C 端问答、官方分享链接、整页截图都依赖登录态。
            // 实测豆包未登录时提交问题会跳 ?from_logout=1，页面只剩引导文案，
            // 若不拦住会空等 2 分钟并把无障碍提示当成「回答」。
            boolean loginRequired = detectLoginRequired(page, profile, diag);
            diag.put("loginRequired", loginRequired);
            String shared = "";
            if (loginRequired) {
                error = "平台「" + profile.getName() + "」未登录：请在「监测任务 → 采集诊断」上传该平台 "
                        + "Playwright storageState（" + profile.getName() + ".json）后再采集";
            } else {
                // 深度思考开关（平台不支持则跳过，并在诊断里说明）
                if (deepThink && profile.isDeepThinkSupported() && StringUtils.hasText(profile.getDeepThinkSelector())) {
                    deepThinkApplied = clickIfVisible(page, profile.getDeepThinkSelector(), diag, "deepThinkToggle");
                    diag.put("deepThinkApplied", deepThinkApplied);
                } else {
                    diag.put("deepThinkApplied", false);
                    diag.put("deepThinkSkipReason", !deepThink ? "任务未开启"
                            : !profile.isDeepThinkSupported() ? "该平台不支持深度思考" : "未配置深度思考选择器");
                }

                // 输入并提问
                diag.put("stage", "INPUT_QUESTION");
                long t1 = System.currentTimeMillis();
                Locator input = page.locator(profile.getInputSelector()).first();
                input.waitFor(new Locator.WaitForOptions().setTimeout(properties.getNavigationTimeoutMs()));
                input.fill(question);
                page.waitForTimeout(300);
                boolean submitted = false;
                if (StringUtils.hasText(profile.getSubmitSelector())) {
                    submitted = clickIfVisible(page, profile.getSubmitSelector(), diag, "submitButton");
                }
                if (!submitted) {
                    page.keyboard().press("Enter");
                    diag.put("submitBy", "Enter");
                } else {
                    diag.put("submitBy", "click");
                }
                diag.put("submitMs", System.currentTimeMillis() - t1);

                // 等待流式输出稳定
                diag.put("stage", "WAIT_ANSWER");
                long t2 = System.currentTimeMillis();
                String previous = "";
                int stable = 0;
                long deadline = System.currentTimeMillis() + properties.getAnswerTimeoutMs();
                while (System.currentTimeMillis() < deadline) {
                    String current = readAnswer(page, profile, diag);
                    boolean streaming = isStreaming(page, profile);
                    if (StringUtils.hasText(current) && current.equals(previous) && !streaming) {
                        stable++;
                    } else {
                        stable = 0;
                    }
                    if (stable >= Math.max(1, properties.getStableChecks())
                            && StringUtils.hasText(current)) {
                        break;
                    }
                    previous = current;
                    page.waitForTimeout(properties.getStableIntervalMs());
                }
                answerText = readAnswer(page, profile, diag);
                diag.put("answerWaitMs", System.currentTimeMillis() - t2);
                diag.put("answerLength", answerText.length());
                String rejected = answerRejectReason(answerText, profile);
                if (rejected != null) {
                    diag.put("answerRejected", rejected);
                    answerText = "";
                }
                if (!StringUtils.hasText(answerText)) {
                    error = "未提取到有效回答：请检查 answerSelector 配置、登录态，或确认页面是否被弹窗/风控拦截"
                            + "（诊断里含页面 URL、选择器命中情况与截屏）";
                }

                // 思考过程（点击展开后读文本）
                if (deepThink && StringUtils.hasText(profile.getReasoningSelector())) {
                    clickIfVisible(page, profile.getReasoningSelector(), diag, "reasoningExpand");
                }
                reasoningText = evaluateText(page, REASONING_JS, diag, "reasoning");
                diag.put("reasoningLength", reasoningText.length());

                // 引用信源（页面 DOM 外链）
                citations = readCitations(page, profile, diag);
                diag.put("citationCount", citations.size());

                // 真实会话链接：优先官方分享链接
                diag.put("stage", "SHARE_LINK");
                clickIfVisible(page, profile.getShareSelector(), diag, "shareButton");
                page.waitForTimeout(1_200);
                shared = readShareLink(page, profile, diag);
            }

            pageUrl = safePageUrl(page);
            if (StringUtils.hasText(shared)) {
                probeUrl = shared;
                probeUrlSource = "OFFICIAL_SHARE";
            } else if (error == null && StringUtils.hasText(pageUrl)) {
                probeUrl = pageUrl;
                probeUrlSource = "PAGE_URL";
            } else {
                diag.put("probeUrlOmitted", "采集未成功，不把落地页 URL 冒充真实回答链接");
            }
            diag.put("probeUrlSource", probeUrlSource);
            diag.put("pageUrl", pageUrl);

            // 整页截图存证：成功时是取证，失败时是排障现场（登录墙 / 弹窗 / 风控页一眼可见）
            diag.put("stage", "SCREENSHOT");
            if (screenshot && profile.isScreenshotSupported()) {
                screenshotUrl = saveScreenshot(page, profile, resultId, diag);
            } else {
                diag.put("screenshotSkipReason", !screenshot ? "任务未要求截图" : "该平台不支持截图");
            }
        } catch (LoginBlockedException e) {
            error = e.getMessage();
            diag.put("exceptionType", "LoginBlockedException");
            if (StringUtils.hasText(e.screenshotUrl)) {
                screenshotUrl = e.screenshotUrl;
                diag.put("loginWallScreenshot", e.screenshotUrl);
            }
            log.info("浏览器采集被登录态阻断 platform={}：{}", profile.getName(), error);
        } catch (Exception e) {
            log.warn("浏览器采集失败 platform={}：{}", profile.getName(), rootMessage(e));
            error = "浏览器采集失败：" + rootMessage(e);
            diag.put("exception", error);
            diag.put("exceptionType", e.getClass().getSimpleName());
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {
                    // 关闭失败不影响结果
                }
            }
            diag.put("totalMs", System.currentTimeMillis() - begin);
            if (error == null) {
                diag.put("stage", "DONE");
            } else {
                diag.put("stage", "FAILED");
            }
        }

        String diagnostic = writeJson(diag);
        if (error != null) {
            log.info("浏览器采集未完成 platform={} answerLen={} screenshot={} error={}",
                    profile.getName(), answerText.length(), StringUtils.hasText(screenshotUrl), error);
        }
        return new ProbeOutcome(
                error == null && StringUtils.hasText(answerText),
                error,
                answerText,
                reasoningText,
                citations,
                probeUrl,
                probeUrlSource,
                screenshotUrl,
                StringUtils.hasText(screenshotUrl) ? "BROWSER_SHOT" : "",
                pageUrl,
                profile.getName() + " 网页版",
                deepThinkApplied,
                diagnostic,
                profile.getName(),
                question);
    }

    /** 懒加载浏览器；本机可执行文件路径优先，为空则用 Playwright 自带 Chromium */
    private void ensureBrowser() {
        if (playwright != null && browser != null && browser.isConnected()) {
            return;
        }
        closeBrowserQuietly();
        try {
            boolean useLocalBrowser = StringUtils.hasText(properties.getExecutablePath());
            if (useLocalBrowser) {
                Path exe = Path.of(properties.getExecutablePath());
                if (!Files.exists(exe)) {
                    throw new BusinessException("浏览器可执行文件不存在：" + exe);
                }
                // Playwright Java 在 create() 时会顺带安装自带 Chromium（国内 CDN 常常卡住甚至挂起 10 分钟）。
                // 已指定本机 Chrome/Edge 时必须跳过该下载，否则浏览器启动会一直卡在 install 阶段。
                playwright = Playwright.create(new Playwright.CreateOptions()
                        .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            } else {
                playwright = Playwright.create();
            }
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(properties.isHeadless())
                    .setArgs(List.of("--no-sandbox", "--disable-blink-features=AutomationControlled"));
            if (useLocalBrowser) {
                options.setExecutablePath(Path.of(properties.getExecutablePath()));
            }
            browser = playwright.chromium().launch(options);
            log.info("GEO 浏览器采集器就绪（headless={}, executablePath={}）",
                    properties.isHeadless(),
                    StringUtils.hasText(properties.getExecutablePath()) ? properties.getExecutablePath() : "Playwright 内置 Chromium");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("启动浏览器失败：" + rootMessage(e)
                    + "；请安装 Chromium 或在 geo.auto-monitor.browser.executable-path 指向本机 Chrome/Edge");
        }
    }

    /** 平台登录态文件：{storage-state-dir}/{平台名}.json */
    private Path storageStateFile(String platform) {
        return accountService.manualStateFile(platform);
    }

    /** 读取回答正文：优先配置选择器，失效时用 DOM 启发式兜底 */
    private String readAnswer(Page page, GeoBrowserProbeProperties.PlatformProfile profile, Map<String, Object> diag) {
        try {
            Locator answers = page.locator(profile.getAnswerSelector());
            int count = answers.count();
            diag.put("answerSelectorHits", count);
            if (count > 0) {
                String text = answers.nth(count - 1).innerText();
                if (StringUtils.hasText(text) && text.length() > 40) {
                    diag.put("answerSource", "SELECTOR");
                    return text.trim();
                }
            }
        } catch (Exception e) {
            diag.put("answerSelectorError", rootMessage(e));
        }
        String fallback = evaluateText(page, FALLBACK_ANSWER_JS, diag, "answerFallback");
        if (StringUtils.hasText(fallback)) {
            diag.put("answerSource", "DOM_FALLBACK");
        }
        return fallback;
    }

    /** 生成中标志是否还存在 */
    private boolean isStreaming(Page page, GeoBrowserProbeProperties.PlatformProfile profile) {
        if (!StringUtils.hasText(profile.getStreamingSelector())) {
            return false;
        }
        try {
            Locator locator = page.locator(profile.getStreamingSelector()).first();
            return locator.count() > 0 && locator.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /** 引用信源：页面外链去重 + 过滤平台自身域名 */
    private List<Citation> readCitations(Page page, GeoBrowserProbeProperties.PlatformProfile profile,
                                         Map<String, Object> diag) {
        List<Citation> result = new ArrayList<>();
        try {
            Object raw = page.evaluate(CITATION_JS);
            JsonNode arr = objectMapper.readTree(String.valueOf(raw));
            String selfHost = hostOf(profile.getUrl());
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    String url = node.path("url").asText("").trim();
                    if (!url.startsWith("http")) {
                        continue;
                    }
                    String host = hostOf(url);
                    if (StringUtils.hasText(selfHost) && selfHost.equals(host)) {
                        continue;
                    }
                    String title = node.path("title").asText("").trim();
                    Citation citation = new Citation(
                            StringUtils.hasText(title) ? truncate(title, 200) : host,
                            host,
                            truncate(url, 1000),
                            "");
                    result.add(citation);
                    if (result.size() >= 20) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            diag.put("citationError", rootMessage(e));
        }
        return result;
    }

    /** 分享弹窗链接：输入框 value / a[href] / 剪贴板 */
    private String readShareLink(Page page, GeoBrowserProbeProperties.PlatformProfile profile,
                                 Map<String, Object> diag) {
        if (StringUtils.hasText(profile.getShareLinkSelector())) {
            try {
                Locator links = page.locator(profile.getShareLinkSelector());
                int count = links.count();
                diag.put("shareLinkHits", count);
                for (int i = 0; i < Math.min(count, 5); i++) {
                    Locator one = links.nth(i);
                    for (String candidate : readLinkCandidates(one)) {
                        if (isHttp(candidate)) {
                            return candidate.trim();
                        }
                    }
                }
            } catch (Exception e) {
                diag.put("shareLinkError", rootMessage(e));
            }
        }
        try {
            Object clipboard = page.evaluate("() => navigator.clipboard ? navigator.clipboard.readText() : ''");
            String text = String.valueOf(clipboard);
            if (isHttp(text)) {
                diag.put("shareSource", "CLIPBOARD");
                return text.trim();
            }
        } catch (Exception ignored) {
            // 剪贴板不可用是常态（权限/非安全上下文），静默回退
        }
        return "";
    }

    /** 一个元素上所有可能承载链接的读取方式，逐项容错（a 标签没有 inputValue） */
    private List<String> readLinkCandidates(Locator locator) {
        List<String> values = new ArrayList<>();
        try {
            values.add(locator.getAttribute("href"));
        } catch (Exception ignored) {
            // 非链接元素
        }
        try {
            values.add(locator.inputValue());
        } catch (Exception ignored) {
            // 非输入元素
        }
        try {
            values.add(locator.innerText());
        } catch (Exception ignored) {
            // 元素不可见
        }
        return values;
    }

    /** 整页截图落到 uploads/geo/auto-monitor/{date}/，返回 /uploads/... 相对路径 */
    private String saveScreenshot(Page page, GeoBrowserProbeProperties.PlatformProfile profile,
                                  Long resultId, Map<String, Object> diag) {
        try {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path dir = Path.of(uploadDir, "geo", "auto-monitor", date).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = (resultId == null ? "probe" : "r" + resultId)
                    + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".png";
            Path dest = dir.resolve(name);
            page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(dest));
            long bytes = Files.size(dest);
            diag.put("screenshotBytes", bytes);
            return "/uploads/geo/auto-monitor/" + date + "/" + name;
        } catch (Exception e) {
            diag.put("screenshotError", rootMessage(e));
            return "";
        }
    }

    /** 关闭运营弹窗 / 引导层；关闭失败不阻断采集，只记录诊断 */
    private void dismissOverlays(Page page, GeoBrowserProbeProperties.PlatformProfile profile,
                                 Map<String, Object> diag) {
        List<String> selectors = profile.getDismissSelectors();
        if (selectors == null || selectors.isEmpty()) {
            return;
        }
        List<String> closed = new ArrayList<>();
        for (String selector : selectors) {
            if (!StringUtils.hasText(selector)) {
                continue;
            }
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0 && locator.isVisible()) {
                    locator.click(new Locator.ClickOptions().setTimeout(2_000));
                    closed.add(selector);
                    page.waitForTimeout(300);
                }
            } catch (Exception ignored) {
                // 弹窗不存在或不可点是常态，静默跳过
            }
        }
        diag.put("dismissedOverlays", closed);
    }

    /** 命中任一登录标志即认为未登录（storageState 缺失 / 已失效） */
    private boolean detectLoginRequired(Page page, GeoBrowserProbeProperties.PlatformProfile profile,
                                        Map<String, Object> diag) {
        List<String> selectors = profile.getLoginRequiredSelectors();
        if (selectors == null || selectors.isEmpty()) {
            return false;
        }
        for (String selector : selectors) {
            if (!StringUtils.hasText(selector)) {
                continue;
            }
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0 && locator.isVisible()) {
                    diag.put("loginRequiredSelector", selector);
                    return true;
                }
            } catch (Exception ignored) {
                // 选择器语法不被支持时跳过，按其他标志判断
            }
        }
        return false;
    }

    /** 回答正文命中页面 UI 文案时返回命中的片段，避免 DOM 兜底把引导页当成回答 */
    private String answerRejectReason(String answer, GeoBrowserProbeProperties.PlatformProfile profile) {
        if (!StringUtils.hasText(answer)) {
            return "回答为空";
        }
        if (answer.length() < 60) {
            return "回答过短（" + answer.length() + " 字符）";
        }
        List<String> patterns = profile.getAnswerRejectPatterns();
        if (patterns != null) {
            for (String pattern : patterns) {
                if (StringUtils.hasText(pattern) && answer.contains(pattern)) {
                    return "命中页面 UI 文案「" + pattern + "」";
                }
            }
        }
        return null;
    }

    private boolean clickIfVisible(Page page, String selector, Map<String, Object> diag, String key) {
        if (!StringUtils.hasText(selector)) {
            diag.put(key, "未配置");
            return false;
        }
        try {
            Locator locator = page.locator(selector).first();
            if (locator.count() == 0 || !locator.isVisible()) {
                diag.put(key, "未找到/不可见");
                return false;
            }
            locator.click(new Locator.ClickOptions().setTimeout(5_000));
            diag.put(key, "已点击");
            return true;
        } catch (Exception e) {
            diag.put(key, "点击失败：" + rootMessage(e));
            return false;
        }
    }

    private String evaluateText(Page page, String js, Map<String, Object> diag, String key) {
        try {
            Object raw = page.evaluate(js);
            JsonNode node = objectMapper.readTree(String.valueOf(raw));
            return node.path("text").asText("").trim();
        } catch (Exception e) {
            diag.put(key + "Error", rootMessage(e));
            return "";
        }
    }

    private String safePageUrl(Page page) {
        try {
            return page.url();
        } catch (Exception e) {
            return "";
        }
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isHttp(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith("http");
    }

    private String writeJson(Map<String, Object> diag) {
        try {
            Map<String, Object> snapshot;
            synchronized (diag) {
                snapshot = new LinkedHashMap<>(diag);
            }
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "{\"diagnosticError\":\"" + rootMessage(e) + "\"}";
        }
    }

    /** 供执行引擎构造失败结果（重试全部异常时使用） */
    public ProbeOutcome failed(String platform, String question, String error) {
        Map<String, Object> diag = new LinkedHashMap<>();
        return failed(platform, question, error, diag);
    }

    private ProbeOutcome failed(String platform, String question, String error, Map<String, Object> diag) {
        diag.put("error", error);
        return new ProbeOutcome(false, error, "", "", List.of(), "", "", "", "", "",
                platform + " 网页版", false, writeJson(diag), platform, question);
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
        return truncate(msg.replaceAll("\\s+", " "), 400);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 「提及截图」模式下未提及时删除已生成的截图，避免无效存证占用空间 */
    public void discardScreenshot(String relativeUrl) {
        if (!StringUtils.hasText(relativeUrl) || !relativeUrl.startsWith("/uploads/geo/auto-monitor/")) {
            return;
        }
        try {
            Path file = Path.of(uploadDir, relativeUrl.substring("/uploads/".length()))
                    .toAbsolutePath().normalize();
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            if (file.startsWith(root)) {
                Files.deleteIfExists(file);
            }
        } catch (Exception e) {
            log.debug("删除未提及截图失败：{}", rootMessage(e));
        }
    }

    /** 平台适配器就绪状态（供任务配置页展示） */
    public Map<String, GeoBrowserProbeProperties.PlatformProfile> profiles() {
        return properties.effectiveProfiles();
    }

    /** 平台是否已配置登录态 storageState */
    public boolean hasStorageState(String platform) {
        return storageStateFile(platform) != null;
    }

    /** 保存平台登录态（Playwright storageState JSON），下次采集自动复用 */
    public void saveStorageState(String platform, byte[] json) {
        try {
            Path dir = accountService.stateDir();
            Files.createDirectories(dir);
            String safeName = platform.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path file = dir.resolve(safeName + ".json");
            Files.write(file, json);
            // 手工上传也刷新账号就绪状态，让「平台账号管理」里的登录态灯变绿
            accountService.findUsable(platform).ifPresent(account -> accountService.markReady(account, file));
            log.info("已更新平台登录态 platform={} file={}", platform, file);
        } catch (Exception e) {
            throw new BusinessException("保存登录态失败：" + rootMessage(e));
        }
    }

    @PreDestroy
    public void shutdown() {
        probeThread.shutdownNow();
        closeBrowserQuietly();
    }

    private void closeBrowserQuietly() {
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (Exception ignored) {
            // 关闭失败不影响进程退出
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ignored) {
            // ignore
        }
        browser = null;
        playwright = null;
    }
}
