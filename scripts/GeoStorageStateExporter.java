import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 导出某平台 C 端登录态为 Playwright storageState JSON。
 *
 * <p>用法（由 scripts/export-geo-storage-state.sh 包装，勿直接手工拼 classpath）：
 * <pre>
 *   java --class-path &lt;playwright+driver+driver-bundle&gt; \
 *        GeoStorageStateExporter.java &lt;平台名&gt; &lt;提问页URL&gt; &lt;Chrome路径&gt; &lt;输出json&gt;
 * </pre>
 *
 * <p>流程：用有头 Chrome 打开平台 → 人工登录（含验证码/扫码）→ 回终端按回车 →
 * 把 cookie + localStorage 写成 {平台名}.json。之后在「监测任务 → 采集诊断」上传即可复用。
 */
public class GeoStorageStateExporter {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("用法: <平台名> <提问页URL> <Chrome/Edge路径> <输出json路径>");
            return;
        }
        String platform = args[0];
        String url = args[1];
        String chrome = args[2];
        Path out = Path.of(args[3]);

        Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setExecutablePath(Path.of(chrome))
                .setArgs(List.of("--no-sandbox", "--disable-blink-features=AutomationControlled")));
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1440, 900).setLocale("zh-CN"));
        Page page = context.newPage();
        page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(60_000));

        System.out.println("平台：" + platform);
        System.out.println("页面：" + url);
        System.out.println(">>> 请在弹出的浏览器窗口中完成登录（含验证码/扫码），确认能正常提问后，回到本终端按回车 <<<");
        // 读取一行，避免交互式终端里 System.in.read() 因缓冲导致意外跳过
        new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();

        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        context.storageState(new BrowserContext.StorageStateOptions().setPath(out));
        System.out.println("已保存登录态：" + out.toAbsolutePath());
        System.out.println("请到「监测任务 → 采集诊断」上传该文件，或用文件名 " + platform + ".json 放到 storage-state-dir 下。");

        context.close();
        browser.close();
        playwright.close();
    }
}
