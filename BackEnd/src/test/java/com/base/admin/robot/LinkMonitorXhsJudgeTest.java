package com.base.admin.robot;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 小红书匿名页：有笔记载荷或具体标题算有效；登录弹框和「暂时无法浏览」不算失效。
 */
class LinkMonitorXhsJudgeTest {

    private static final String NOTE_ID = "abc123456";
    private static final String NOTE_URL = "https://www.xiaohongshu.com/explore/" + NOTE_ID;

    @Test
    void loginPopupDoesNotKillNoteWhenPayloadPresent() {
        String html = """
                <title>听劝！这支好用 - 小红书</title>
                <script>window.__INITIAL_STATE__={"note":{"noteDetailMap":{"%s":{"note":{"noteId":"%s","title":"听劝","desc":"hello","imageList":[]}}}}}</script>
                <button>立即登录</button>
                """.formatted(NOTE_ID, NOTE_ID);
        LinkMonitorService.XhsJudgement j = LinkMonitorService.judgeXiaohongshuPage(
                NOTE_URL, 200, URI.create(NOTE_URL), html);
        assertEquals(LinkMonitorService.XhsVerdict.OK, j.verdict());
    }

    @Test
    void explicitNullNoteWithoutTitleIsDead() {
        String html = """
                <title>小红书</title>
                <script>window.__INITIAL_STATE__={"note":{"noteDetailMap":{"%s":{"note":null}}}}</script>
                """.formatted(NOTE_ID);
        LinkMonitorService.XhsJudgement j = LinkMonitorService.judgeXiaohongshuPage(
                NOTE_URL, 200, URI.create(NOTE_URL), html);
        assertEquals(LinkMonitorService.XhsVerdict.DEAD, j.verdict());
        assertTrue(j.reason().contains("笔记数据为空"));
    }

    @Test
    void temporaryUnreadableIsUnknown() {
        String html = """
                <title>小红书 - 你的生活兴趣社区</title>
                <div>当前笔记暂时无法浏览</div>
                """;
        LinkMonitorService.XhsJudgement j = LinkMonitorService.judgeXiaohongshuPage(
                NOTE_URL, 200, URI.create(NOTE_URL), html);
        assertEquals(LinkMonitorService.XhsVerdict.UNKNOWN, j.verdict());
    }

    @Test
    void deletedCopyIsDead() {
        String html = "<title>小红书</title><div>你访问的页面不见了</div>";
        LinkMonitorService.XhsJudgement j = LinkMonitorService.judgeXiaohongshuPage(
                NOTE_URL, 200, URI.create(NOTE_URL), html);
        assertEquals(LinkMonitorService.XhsVerdict.DEAD, j.verdict());
    }

    @Test
    void loginLandingIsUnknown() {
        LinkMonitorService.XhsJudgement j = LinkMonitorService.judgeXiaohongshuPage(
                NOTE_URL, 200, URI.create("https://www.xiaohongshu.com/login?redirectPath=/explore/" + NOTE_ID),
                "<title>登录 - 小红书</title>");
        assertEquals(LinkMonitorService.XhsVerdict.UNKNOWN, j.verdict());
    }

    @Test
    void ogTitleAloneIsOk() {
        String html = """
                <title>小红书</title>
                <meta property="og:title" content="听劝！这支好用 - 小红书" />
                """;
        LinkMonitorService.XhsJudgement j = LinkMonitorService.judgeXiaohongshuPage(
                NOTE_URL, 200, URI.create(NOTE_URL), html);
        assertEquals(LinkMonitorService.XhsVerdict.OK, j.verdict());
    }

    @Test
    void redirectToAnotherNoteIsDead() {
        LinkMonitorService.XhsJudgement j = LinkMonitorService.judgeXiaohongshuPage(
                NOTE_URL, 200, URI.create("https://www.xiaohongshu.com/explore/zzzz999999"),
                "<title>别的笔记 - 小红书</title>");
        assertEquals(LinkMonitorService.XhsVerdict.DEAD, j.verdict());
    }

    @Test
    void http403IsUnknownNotDead() {
        LinkMonitorService.XhsJudgement j = LinkMonitorService.judgeXiaohongshuPage(
                NOTE_URL, 403, URI.create(NOTE_URL), "");
        assertEquals(LinkMonitorService.XhsVerdict.UNKNOWN, j.verdict());
    }

    @Test
    void alternateUrlsKeepXsecToken() {
        List<String> urls = LinkMonitorService.xhsAlternateNoteUrls(
                NOTE_ID, "foo=1&xsec_token=abc&xsec_source=pc_share");
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).contains("/explore/" + NOTE_ID));
        assertTrue(urls.get(0).contains("xsec_token=abc"));
        assertTrue(urls.get(0).contains("xsec_source=pc_share"));
        assertFalse(urls.get(0).contains("foo=1"));
        assertTrue(LinkMonitorService.isXiaohongshuUrl("https://xhslink.com/a/abc"));
        assertFalse(LinkMonitorService.isXiaohongshuUrl("https://example.com/explore/" + NOTE_ID));
    }
}
