package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkAssistantSuggestDTO;
import com.base.admin.domain.vo.DingTalkAssistantSuggestVO;
import com.base.admin.domain.vo.DingTalkBusyUserOptionVO;
import com.base.admin.exception.BusinessException;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 钉钉机器人单聊 → 复用日程助手 suggest，文字回复对齐工作台气泡。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkRobotAssistantService {

    private static final Pattern AT_MENTION = Pattern.compile("@([^\\s@，,。！!？?\\n]+)");
    private static final Pattern QUOTED = Pattern.compile("「([^」]+)」");
    private static final DateTimeFormatter SLOT_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final int MAX_SLOT_LINES = 5;

    private final DingTalkAssistantService dingTalkAssistantService;
    private final DingTalkBusyService dingTalkBusyService;
    private final JdbcTemplate jdbc;

    private final ExecutorService worker = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "dingtalk-robot-assistant");
        t.setDaemon(true);
        return t;
    });

    /** Stream 回调入口：先 ACK，再异步查闲忙并回复。 */
    public void handleBotMessage(ChatbotMessage message) {
        if (message == null) {
            return;
        }
        String conversationType = message.getConversationType();
        if (StringUtils.hasText(conversationType) && !"1".equals(conversationType.trim())) {
            log.info("dingtalk robot ignore non-DM conversationType={}", conversationType);
            replyQuietly(message.getSessionWebhook(), "日程助手目前只支持单聊，请私聊我查询同事闲忙。");
            return;
        }
        String text = extractText(message);
        if (!StringUtils.hasText(text)) {
            replyQuietly(message.getSessionWebhook(), "请发文字消息，例如：查 Bella 最近有没有空安排面试品牌总监");
            return;
        }
        String webhook = message.getSessionWebhook();
        String senderStaffId = message.getSenderStaffId();
        log.info("dingtalk robot inbound senderStaffId={} nick={} text={}",
                senderStaffId, message.getSenderNick(), abbreviate(text, 120));
        replyQuietly(webhook, "收到，正在查询闲忙，请稍候…");
        worker.execute(() -> {
            try {
                String reply = processAsk(senderStaffId, text.trim());
                replyQuietly(webhook, reply);
            } catch (Exception ex) {
                log.warn("dingtalk robot process failed: {}", ex.getMessage(), ex);
                replyQuietly(webhook, "查询失败：" + (ex.getMessage() == null ? "请稍后重试" : ex.getMessage()));
            }
        });
    }

    String processAsk(String senderStaffId, String text) {
        SenderUser sender = findSender(senderStaffId);
        if (sender == null) {
            return "你的钉钉账号还没绑定本系统用户，无法使用日程助手。\n"
                    + "请到系统「个人中心 / 用户管理」完成钉钉绑定后再试。";
        }

        List<DingTalkBusyUserOptionVO> users = dingTalkBusyService.listBoundUsers();
        ResolveResult resolve = resolveTarget(text, users);
        if (resolve.error() != null) {
            return resolve.error();
        }
        DingTalkBusyUserOptionVO target = resolve.user();
        if (target.getDingtalkBound() == null || target.getDingtalkBound() != 1) {
            return "「" + displayName(target) + "」还未绑定钉钉，暂时查不了闲忙。";
        }

        DingTalkAssistantSuggestDTO dto = new DingTalkAssistantSuggestDTO();
        dto.setTargetUserId(target.getUserId());
        dto.setMessage(text);
        try {
            DingTalkAssistantSuggestVO vo = dingTalkAssistantService.suggest(dto);
            return formatSuggestReply(vo);
        } catch (BusinessException ex) {
            return ex.getMessage() == null ? "查询失败，请稍后重试" : ex.getMessage();
        }
    }

    private static String formatSuggestReply(DingTalkAssistantSuggestVO vo) {
        if (vo == null) {
            return "暂无建议";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(vo.getRuleSummary())) {
            parts.add(vo.getRuleSummary().trim());
        }
        if (StringUtils.hasText(vo.getBusySummary())) {
            parts.add(vo.getBusySummary().trim());
        }
        if (StringUtils.hasText(vo.getAdviceText())) {
            parts.add(vo.getAdviceText().trim());
        }
        String slots = formatTopSlots(vo);
        if (StringUtils.hasText(slots)) {
            parts.add(slots);
        }
        if (StringUtils.hasText(vo.getNextStepText())) {
            parts.add(vo.getNextStepText().trim());
        }
        parts.add("如需发起面试邀约或建会议，请到工作台「日程助手」继续操作。");
        return String.join("\n\n", parts);
    }

    private static String formatTopSlots(DingTalkAssistantSuggestVO vo) {
        if (vo.getDayGroups() == null || vo.getDayGroups().isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (DingTalkAssistantSuggestVO.DayGroup day : vo.getDayGroups()) {
            if (day.getSlots() == null) {
                continue;
            }
            for (DingTalkAssistantSuggestVO.SlotOption slot : day.getSlots()) {
                if (lines.size() >= MAX_SLOT_LINES) {
                    break;
                }
                if (StringUtils.hasText(slot.getLabel()) && StringUtils.hasText(day.getDayLabel())) {
                    lines.add("· " + day.getDayLabel() + " " + slot.getLabel());
                } else if (slot.getStart() != null && slot.getEnd() != null) {
                    lines.add("· " + slot.getStart().format(SLOT_FMT) + "–" + slot.getEnd().format(DateTimeFormatter.ofPattern("HH:mm")));
                }
            }
            if (lines.size() >= MAX_SLOT_LINES) {
                break;
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        return "推荐可约时段：\n" + String.join("\n", lines);
    }

    ResolveResult resolveTarget(String text, List<DingTalkBusyUserOptionVO> users) {
        if (users == null || users.isEmpty()) {
            return ResolveResult.error("系统里还没有可查询的同事。");
        }
        Matcher at = AT_MENTION.matcher(text);
        List<String> tokens = new ArrayList<>();
        while (at.find()) {
            tokens.add(at.group(1));
        }
        Matcher quoted = QUOTED.matcher(text);
        while (quoted.find()) {
            tokens.add(quoted.group(1));
        }
        for (int i = tokens.size() - 1; i >= 0; i--) {
            DingTalkBusyUserOptionVO hit = matchUserByToken(tokens.get(i), users);
            if (hit != null) {
                return ResolveResult.ok(hit);
            }
        }
        // 无 @：按昵称/用户名在原文中出现匹配（如「查 Bella」）
        List<DingTalkBusyUserOptionVO> candidates = new ArrayList<>();
        List<DingTalkBusyUserOptionVO> sorted = users.stream()
                .sorted(Comparator.comparingInt((DingTalkBusyUserOptionVO u) ->
                        Math.max(len(u.getNickname()), len(u.getUsername()))).reversed())
                .toList();
        Set<Long> seen = new LinkedHashSet<>();
        for (DingTalkBusyUserOptionVO user : sorted) {
            if (nameAppearsInText(text, user.getNickname()) || nameAppearsInText(text, user.getUsername())) {
                if (seen.add(user.getUserId())) {
                    candidates.add(user);
                }
            }
        }
        if (candidates.size() == 1) {
            return ResolveResult.ok(candidates.getFirst());
        }
        if (candidates.size() > 1) {
            String names = candidates.stream().limit(5).map(DingTalkRobotAssistantService::displayName)
                    .reduce((a, b) -> a + "、" + b).orElse("");
            return ResolveResult.error("匹配到多位同事（" + names + "），请写清楚，例如：@Bella 最近有没有空安排面试？");
        }
        return ResolveResult.error("没认出要查的同事。请带上姓名，例如：\n"
                + "· @Bella 最近有没有空安排面试品牌总监\n"
                + "· 查张三这周有没有 60 分钟空闲");
    }

    private static DingTalkBusyUserOptionVO matchUserByToken(String token, List<DingTalkBusyUserOptionVO> users) {
        String t = token == null ? "" : token.trim().replaceAll("[的地得]$", "");
        if (!StringUtils.hasText(t)) {
            return null;
        }
        for (DingTalkBusyUserOptionVO u : users) {
            if (t.equals(u.getNickname()) || t.equals(u.getUsername())) {
                return u;
            }
        }
        List<DingTalkBusyUserOptionVO> starts = users.stream().filter(u ->
                (StringUtils.hasText(u.getNickname())
                        && (u.getNickname().startsWith(t) || t.startsWith(u.getNickname())))
                        || (StringUtils.hasText(u.getUsername()) && u.getUsername().startsWith(t))
        ).toList();
        if (starts.size() == 1) {
            return starts.getFirst();
        }
        List<DingTalkBusyUserOptionVO> includes = users.stream().filter(u ->
                (StringUtils.hasText(u.getNickname()) && u.getNickname().contains(t))
                        || (StringUtils.hasText(u.getUsername()) && u.getUsername().contains(t))
        ).toList();
        if (includes.size() == 1) {
            return includes.getFirst();
        }
        return null;
    }

    private static boolean nameAppearsInText(String text, String name) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(name) || name.trim().length() < 2) {
            return false;
        }
        String n = name.trim();
        // 英文名忽略大小写；中文直接包含
        if (n.chars().allMatch(c -> c < 128)) {
            return Pattern.compile("(?i)\\b" + Pattern.quote(n) + "\\b").matcher(text).find()
                    || text.toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT));
        }
        return text.contains(n);
    }

    private SenderUser findSender(String senderStaffId) {
        if (!StringUtils.hasText(senderStaffId)) {
            return null;
        }
        List<SenderUser> rows = jdbc.query("""
                SELECT u.user_id, u.username, u.nickname, d.dingtalk_user_id, d.dingtalk_union_id
                FROM hr_user_dingtalk d
                INNER JOIN sys_user u ON u.user_id = d.user_id AND u.is_active = 1
                WHERE d.is_active = 1 AND d.dingtalk_user_id = ?
                LIMIT 1
                """, (rs, rowNum) -> new SenderUser(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("dingtalk_user_id"),
                rs.getString("dingtalk_union_id")), senderStaffId.trim());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String extractText(ChatbotMessage message) {
        MessageContent text = message.getText();
        if (text != null && StringUtils.hasText(text.getContent())) {
            return text.getContent().trim();
        }
        MessageContent content = message.getContent();
        if (content != null) {
            if (StringUtils.hasText(content.getContent())) {
                return content.getContent().trim();
            }
            if (StringUtils.hasText(content.getText())) {
                return content.getText().trim();
            }
        }
        return "";
    }

    private static void replyQuietly(String sessionWebhook, String body) {
        if (!StringUtils.hasText(sessionWebhook) || !StringUtils.hasText(body)) {
            return;
        }
        try {
            BotReplier.fromWebhook(sessionWebhook).replyText(body);
        } catch (Exception ex) {
            log.warn("dingtalk robot reply failed: {}", ex.getMessage());
        }
    }

    private static String displayName(DingTalkBusyUserOptionVO u) {
        return StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername();
    }

    private static int len(String s) {
        return s == null ? 0 : s.trim().length();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    record SenderUser(Long userId, String username, String nickname, String dingUserId, String unionId) {
    }

    record ResolveResult(DingTalkBusyUserOptionVO user, String error) {
        static ResolveResult ok(DingTalkBusyUserOptionVO user) {
            return new ResolveResult(user, null);
        }

        static ResolveResult error(String error) {
            return new ResolveResult(null, error);
        }
    }
}
