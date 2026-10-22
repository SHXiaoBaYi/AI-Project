package com.base.admin.robot;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 轻量 Router：会话态优先，再结合近期对话记忆做续聊，再规则分类。
 */
@Component
@RequiredArgsConstructor
public class RobotIntentRouter {

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final RobotSessionService sessionService;

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
        // 异常查询须优先于列表：「哪些有异常」也含「哪些」，勿误判为全量列表
        if (isAbnormalMonitor(lower)) {
            return enabledOrFallback(RobotProductRules.INTENT_LINK_ABNORMAL, 0.95, "start");
        }
        // 查询列表须优先于添加：「有几个在监控的链接」含「监控+链接」勿误判为添加
        if (isListMonitor(lower)) {
            return enabledOrFallback(RobotProductRules.INTENT_LINK_LIST, 0.94, "start");
        }
        if (isAddMonitor(lower) || (URL.matcher(t).find() && lower.contains("监控"))) {
            return enabledOrFallback(RobotProductRules.INTENT_LINK_ADD, 0.92, "start");
        }

        // 完整对话记忆：短续聊句沿用上一意图（如闲忙「改成下午」）
        String lastIntent = lastUserIntent(session);
        if (StringUtils.hasText(lastIntent) && isFollowUpUtterance(t)) {
            if (RobotProductRules.INTENT_CALENDAR_FREEBUSY.equals(lastIntent)
                    && isIntentEnabled(RobotProductRules.INTENT_CALENDAR_FREEBUSY)) {
                return new RouteResult(RobotProductRules.INTENT_CALENDAR_FREEBUSY, 0.75, "follow_up");
            }
            if (RobotProductRules.INTENT_LINK_ADD.equals(lastIntent)
                    && isIntentEnabled(RobotProductRules.INTENT_LINK_ADD)
                    && URL.matcher(t).find()) {
                return new RouteResult(RobotProductRules.INTENT_LINK_ADD, 0.7, "start");
            }
        }

        if (isIntentEnabled(RobotProductRules.INTENT_CALENDAR_FREEBUSY)) {
            return new RouteResult(RobotProductRules.INTENT_CALENDAR_FREEBUSY, 0.8, "start");
        }
        return new RouteResult(RobotProductRules.INTENT_HELP, 0.4, "start");
    }

    private String lastUserIntent(RobotSessionService.Session session) {
        if (session == null) {
            return "";
        }
        if (StringUtils.hasText(session.intentCode())) {
            return session.intentCode().trim();
        }
        List<RobotSessionService.HistoryTurn> hist = sessionService.historyOf(session);
        for (int i = hist.size() - 1; i >= 0; i--) {
            RobotSessionService.HistoryTurn turn = hist.get(i);
            if ("user".equals(turn.role()) && StringUtils.hasText(turn.intent())) {
                return turn.intent().trim();
            }
            if ("assistant".equals(turn.role()) && StringUtils.hasText(turn.intent())) {
                return turn.intent().trim();
            }
        }
        return "";
    }

    /** 短句 / 时间修正类，视为对上一轮的续聊而非新开帮助。 */
    private static boolean isFollowUpUtterance(String t) {
        if (!StringUtils.hasText(t) || t.length() > 40) {
            return false;
        }
        String s = t.trim();
        return s.contains("改成") || s.contains("换成") || s.contains("那")
                || s.contains("明天") || s.contains("后天") || s.contains("下午")
                || s.contains("上午") || s.contains("下周") || s.contains("再查")
                || s.equals("继续") || s.equals("好的") || s.equals("嗯")
                || s.startsWith("还有") || s.startsWith("再");
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
        if (isAbnormalMonitor(lower) || isListMonitor(lower)) {
            return false;
        }
        return lower.contains("监控") && (lower.contains("链接") || lower.contains("网址")
                || lower.contains("网站") || lower.contains("笔记") || lower.contains("添加")
                || lower.contains("加一个") || lower.contains("帮我") || lower.contains("要监控"));
    }

    /**
     * 查询有异常的监控，例如「目前监控的笔记是否有异常」「监控的链接哪些有异常」。
     * 口语里「笔记」按监控链接理解。
     */
    private static boolean isAbnormalMonitor(String lower) {
        if (!lower.contains("监控")) {
            return false;
        }
        // 「帮我监控异常链接」仍属添加，不走异常查询
        if (lower.contains("添加") || lower.contains("加一个") || lower.contains("加个")
                || lower.contains("新增") || lower.contains("帮我") || lower.contains("要监控")
                || lower.contains("帮监控") || lower.contains("加入")) {
            return false;
        }
        return lower.contains("异常") || lower.contains("有问题") || lower.contains("故障")
                || lower.contains("挂了") || lower.contains("打不开") || lower.contains("失败")
                || lower.contains("报错") || lower.contains("坏了") || lower.contains("不通");
    }

    /**
     * 查询当前监控列表，例如「现在我有几个在监控的链接」「看看正在监控的网址」。
     */
    private static boolean isListMonitor(String lower) {
        if (!lower.contains("监控") || isAbnormalMonitor(lower)) {
            return false;
        }
        // 明确添加口吻不走列表
        if (lower.contains("添加") || lower.contains("加一个") || lower.contains("加个")
                || lower.contains("新增") || lower.contains("帮我") || lower.contains("要监控")
                || lower.contains("帮监控") || lower.contains("加入") || lower.contains("加上")
                || (lower.contains("加") && (lower.contains("链接") || lower.contains("网址")
                || lower.contains("网站") || lower.contains("笔记")))) {
            return false;
        }
        return lower.contains("有几个") || lower.contains("有多少") || lower.contains("哪些")
                || lower.contains("列表") || lower.contains("列出")
                || lower.contains("查看") || lower.contains("查下") || lower.contains("查一下")
                || lower.contains("看看") || lower.contains("正在监控") || lower.contains("在监控")
                || lower.contains("监控中") || lower.contains("当前监控") || lower.contains("我的监控")
                || lower.contains("监控了几") || lower.contains("监控多少");
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
