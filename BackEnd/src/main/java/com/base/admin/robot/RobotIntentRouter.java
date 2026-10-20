package com.base.admin.robot;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 轻量 Router：会话态优先，再规则分类。后续可替换为 LLM 分类。
 */
@Component
@RequiredArgsConstructor
public class RobotIntentRouter {

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;

    public record RouteResult(String intentCode, double confidence, String utteranceType) {
    }

    public RouteResult route(RobotSessionService.Session session, String text) {
        String t = text == null ? "" : text.trim();
        if (session != null && StringUtils.hasText(session.state())
                && !RobotProductRules.STATE_IDLE.equals(session.state())) {
            if (RobotProductRules.STATE_WAIT_URL.equals(session.state())) {
                return new RouteResult(RobotProductRules.INTENT_LINK_ADD, 1.0, "provide_url");
            }
            if (RobotProductRules.STATE_WAIT_MORE.equals(session.state())) {
                if (isYesMore(t)) {
                    return new RouteResult(RobotProductRules.INTENT_LINK_ADD, 1.0, "confirm_more_yes");
                }
                if (isNoMore(t) || isCancel(t)) {
                    return new RouteResult(RobotProductRules.INTENT_LINK_ADD, 1.0,
                            isCancel(t) ? "cancel" : "confirm_more_no");
                }
                if (URL.matcher(t).find()) {
                    return new RouteResult(RobotProductRules.INTENT_LINK_ADD, 0.95, "provide_url");
                }
                return new RouteResult(RobotProductRules.INTENT_LINK_ADD, 0.8, "confirm_more_unclear");
            }
        }
        if (isCancel(t)) {
            return new RouteResult(RobotProductRules.INTENT_HELP, 0.5, "cancel");
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (isHelp(lower)) {
            return new RouteResult(RobotProductRules.INTENT_HELP, 0.95, "start");
        }
        if (isDeleteMonitor(lower)) {
            return enabledOrFallback(RobotProductRules.INTENT_LINK_DELETE, 0.92, "start");
        }
        if (isAddMonitor(lower) || (URL.matcher(t).find() && lower.contains("监控"))) {
            return enabledOrFallback(RobotProductRules.INTENT_LINK_ADD, 0.92, "start");
        }
        if (isIntentEnabled(RobotProductRules.INTENT_CALENDAR_FREEBUSY)) {
            return new RouteResult(RobotProductRules.INTENT_CALENDAR_FREEBUSY, 0.8, "start");
        }
        return new RouteResult(RobotProductRules.INTENT_HELP, 0.4, "start");
    }

    private RouteResult enabledOrFallback(String code, double conf, String type) {
        if (isIntentEnabled(code)) {
            return new RouteResult(code, conf, type);
        }
        return new RouteResult(RobotProductRules.INTENT_HELP, 0.3, "disabled");
    }

    public boolean isIntentEnabled(String intentCode) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM robot_intent
                WHERE intent_code = ? AND enabled = 1 AND is_active = 1
                """, Integer.class, intentCode);
        return n != null && n > 0;
    }

    private static boolean isHelp(String lower) {
        return lower.equals("帮助") || lower.equals("菜单") || lower.equals("help")
                || lower.contains("你能做") || lower.contains("你会什么") || lower.contains("功能");
    }

    private static boolean isAddMonitor(String lower) {
        return lower.contains("监控") && (lower.contains("链接") || lower.contains("网址")
                || lower.contains("网站") || lower.contains("添加") || lower.contains("加一个")
                || lower.contains("帮我") || lower.contains("要监控"));
    }

    private static boolean isDeleteMonitor(String lower) {
        return (lower.contains("删") || lower.contains("取消") || lower.contains("移除") || lower.contains("去掉"))
                && lower.contains("监控");
    }

    private static boolean isYesMore(String t) {
        String s = t.trim();
        return s.equals("需要") || s.equals("要") || s.equals("是") || s.equals("继续")
                || s.equals("追加") || s.contains("还要") || s.contains("再加");
    }

    private static boolean isNoMore(String t) {
        String s = t.trim();
        return s.equals("不需要") || s.equals("不用") || s.equals("否") || s.equals("没有了")
                || s.equals("完成") || s.equals("就这些") || s.equals("好了");
    }

    private static boolean isCancel(String t) {
        String s = t.trim();
        return s.equals("取消") || s.equals("算了") || s.equals("退出");
    }
}
