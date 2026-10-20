package com.base.admin.robot;

import com.base.admin.service.DingTalkRobotAssistantService;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 机器人统一入站：群 @ / 单聊 → 会话 → Router → Handler。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotIngressService {

    private final RobotSessionService sessionService;
    private final RobotIntentRouter intentRouter;
    private final RobotMessageLogService messageLogService;
    private final LinkMonitorRobotHandler linkMonitorRobotHandler;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<DingTalkRobotAssistantService> dingTalkRobotAssistantService;

    private final ExecutorService worker = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "robot-ingress");
        t.setDaemon(true);
        return t;
    });

    public void handle(ChatbotMessage message) {
        if (message == null) {
            return;
        }
        String conversationType = blank(message.getConversationType());
        if (!"1".equals(conversationType) && !"2".equals(conversationType)) {
            conversationType = "1";
        }
        String conversationId = blank(readString(message, "getConversationId", "getOpenConversationId"));
        String staffId = blank(message.getSenderStaffId());
        String webhook = message.getSessionWebhook();
        String text = extractText(message);
        text = stripAtMentions(text);

        if ("2".equals(conversationType)) {
            if (!isMentioned(message, text)) {
                log.info("robot ignore group message without @ bot conversationId={}", conversationId);
                return;
            }
            upsertGroupProfile(conversationId);
        }

        DingTalkRobotAssistantService assistant = dingTalkRobotAssistantService.getObject();
        if (!StringUtils.hasText(text)) {
            assistant.replyPlain(webhook,
                    "请发文字。可说：查某人闲忙 / 帮我监控一个链接 / 删除监控 / 帮助");
            return;
        }

        messageLogService.logIn(staffId, conversationId, conversationType, "", text);
        String finalType = conversationType;
        String finalText = text.trim();
        worker.execute(() -> {
            DingTalkRobotAssistantService asst = dingTalkRobotAssistantService.getObject();
            try {
                RobotReply reply = process(finalType, conversationId, staffId, finalText);
                asst.replyRobot(webhook, reply);
                messageLogService.logOut(staffId, conversationId, finalType, "",
                        reply.markdown(), true, "");
            } catch (Exception ex) {
                log.warn("robot ingress failed: {}", ex.getMessage(), ex);
                asst.replyPlain(webhook,
                        "处理失败：" + (ex.getMessage() == null ? "请稍后重试" : ex.getMessage()));
                messageLogService.logOut(staffId, conversationId, finalType, "", "", false, ex.getMessage());
            }
        });
    }

    private RobotReply process(String conversationType, String conversationId, String staffId, String text) {
        DingTalkRobotAssistantService assistant = dingTalkRobotAssistantService.getObject();
        RobotReply del = linkMonitorRobotHandler.handleDeleteCommand(text, staffId);
        if (del != null) {
            return del;
        }
        if (assistant.isLegacyCommand(text)) {
            return assistant.handleLegacyCommandAsReply(staffId, text);
        }

        RobotSessionService.Session session = sessionService.getOrCreate(conversationType, conversationId, staffId);
        RobotIntentRouter.RouteResult route = intentRouter.route(session, text);
        String intent = route.intentCode();
        Long userId = findUserIdByStaff(staffId);

        if (RobotProductRules.INTENT_HELP.equals(intent)) {
            if ("cancel".equals(route.utteranceType())) {
                sessionService.clear(session.sessionKey());
                return RobotReply.text("已退出当前多轮对话。");
            }
            return helpReply();
        }
        if (RobotProductRules.INTENT_LINK_ADD.equals(intent)) {
            return linkMonitorRobotHandler.handleAdd(session, text, route.utteranceType(), staffId, userId);
        }
        if (RobotProductRules.INTENT_LINK_DELETE.equals(intent)) {
            return linkMonitorRobotHandler.handleDelete(staffId);
        }
        if (RobotProductRules.INTENT_CALENDAR_FREEBUSY.equals(intent)) {
            if ("2".equals(conversationType) && !RobotProductRules.CALENDAR_ALLOWED_IN_GROUP) {
                return RobotReply.text("群聊暂未开放闲忙查询。");
            }
            return assistant.processAskAsRobotReply(staffId, text);
        }
        return helpReply();
    }

    private RobotReply helpReply() {
        List<String> lines = jdbc.query("""
                SELECT intent_name, sample_utterances FROM robot_intent
                WHERE enabled = 1 AND is_active = 1 ORDER BY sort_order ASC, id ASC
                """, (rs, i) -> "- " + rs.getString(1) + "：例如「"
                + firstSample(rs.getString(2)) + "」");
        StringBuilder sb = new StringBuilder("我是企业机器人，目前支持：\n");
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        sb.append("\n群里请先 @ 我再说话。链接监控为全局列表，每天 09:30 探测能否打开。");
        return RobotReply.text(sb.toString());
    }

    private static String firstSample(String samples) {
        if (!StringUtils.hasText(samples)) {
            return "";
        }
        String[] parts = samples.split("[;；]");
        return parts[0].trim();
    }

    private Long findUserIdByStaff(String staffId) {
        if (!StringUtils.hasText(staffId)) {
            return null;
        }
        return jdbc.query("""
                SELECT user_id FROM sys_user
                WHERE dingtalk_userid = ? AND is_active = 1 LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, staffId);
    }

    private void upsertGroupProfile(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        jdbc.update("""
                INSERT INTO robot_group_profile (open_conversation_id, title, enabled, is_active)
                VALUES (?, '', 1, 1)
                ON DUPLICATE KEY UPDATE update_time = NOW(), is_active = 1
                """, conversationId);
    }

    private static boolean isMentioned(ChatbotMessage message, String text) {
        Object inAt = readObject(message, "getIsInAtList", "isInAtList");
        if (Boolean.TRUE.equals(inAt)) {
            return true;
        }
        if (Boolean.FALSE.equals(inAt)) {
            return StringUtils.hasText(text) && text.contains("@");
        }
        // 无法判断时：Stream 通常只推送 @ 机器人的群消息
        return true;
    }

    private static String extractText(ChatbotMessage message) {
        try {
            Object text = message.getText();
            if (text == null) {
                return "";
            }
            Method content = text.getClass().getMethod("getContent");
            Object v = content.invoke(text);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String stripAtMentions(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("@[\\w\\u4e00-\\u9fa5\\-]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static String readString(Object target, String... getters) {
        Object v = readObject(target, getters);
        return v == null ? "" : String.valueOf(v);
    }

    private static Object readObject(Object target, String... getters) {
        if (target == null || getters == null) {
            return null;
        }
        for (String g : getters) {
            try {
                Method m = target.getClass().getMethod(g);
                return m.invoke(target);
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private static String blank(String s) {
        return s == null ? "" : s.trim();
    }
}
