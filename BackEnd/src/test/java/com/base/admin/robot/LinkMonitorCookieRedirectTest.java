package com.base.admin.robot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短链跨主机跳转必须还能带上探测 Cookie，且不能收第三方域的 Set-Cookie。
 */
class LinkMonitorCookieRedirectTest {

    @Test
    void shortLinkHostAndNoteHostBothMatchConfiguredSuffixes() {
        String suffixes = "xiaohongshu.com,xhslink.com";
        assertTrue(LinkMonitorService.hostMatchesCookieSuffix("xhslink.com", suffixes));
        assertTrue(LinkMonitorService.hostMatchesCookieSuffix("www.xiaohongshu.com", suffixes));
        assertFalse(LinkMonitorService.hostMatchesCookieSuffix("evil.example", suffixes));
    }

    @Test
    void setCookieOnRedirectUpdatesJarAndIgnoresOtherDomains() {
        Map<String, String> jar = LinkMonitorService.parseCookiePairs("web_session=old; a1=keep");
        LinkMonitorService.absorbSetCookies(jar, "www.xiaohongshu.com", List.of(
                "web_session=new; Domain=.xiaohongshu.com; Path=/",
                "track=1; Domain=.doubleclick.net; Path=/"
        ));
        assertEquals("new", jar.get("web_session"));
        assertEquals("keep", jar.get("a1"));
        assertFalse(jar.containsKey("track"));
        assertEquals("web_session=new; a1=keep", LinkMonitorService.formatCookiePairs(jar));
    }

    @Test
    void blocksInternalRedirectHosts() {
        assertTrue(LinkMonitorService.isBlockedProbeHost("127.0.0.1"));
        assertTrue(LinkMonitorService.isBlockedProbeHost("169.254.169.254"));
        assertTrue(LinkMonitorService.isBlockedProbeHost("10.0.0.8"));
        assertFalse(LinkMonitorService.isBlockedProbeHost("www.xiaohongshu.com"));
    }
}
