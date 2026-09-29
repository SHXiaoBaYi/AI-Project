package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkAssistantSuggestDTO;
import com.base.admin.domain.vo.DingTalkAssistantSuggestVO;
import com.base.admin.domain.vo.DingTalkBusyUserOptionVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.RobotSecurity;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 钉钉机器人单聊：结论 + 动作卡片。
 * <p>钉钉开放平台不提供「打开自带建日程弹窗」协议，因此卡片不会 API 静默建日程；
 * 引导用户在钉钉日程里新建，建好后再点「同步到系统」落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkRobotAssistantService {

    private static final Pattern AT_MENTION = Pattern.compile("@([^\\s@，,。！!？?\\n]+)");
    private static final Pattern QUOTED = Pattern.compile("「([^」]+)」");
    private static final Pattern PREP_CMD = Pattern.compile("(?i)#P[:：]\\s*([A-Za-z0-9_\\-+=/]+)");
    private static final Pattern SYNC_CMD = Pattern.compile("(?i)#S[:：]\\s*([A-Za-z0-9_\\-+=/]+)");
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CARD_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final int MAX_SLOT_LINES = 5;
    private static final String PREP_PREFIX = "#P:";
    private static final String SYNC_PREFIX = "#S:";
    /** 打开钉钉日程首页（平台未开放「创建日程弹窗」跳转协议） */
    private static final String DING_CALENDAR_OPEN =
            "dingtalk://dingtalkclient/page/link?url="
                    + URLEncoder.encode("https://calendar.dingtalk.com/", StandardCharsets.UTF_8)
                    + "&pc_slide=true";

    private final DingTalkAssistantService dingTalkAssistantService;
    private final DingTalkBusyService dingTalkBusyService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private final ExecutorService worker = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "dingtalk-robot-assistant");
        t.setDaemon(true);
        return t;
    });

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** 最近一次「去建日程」意图，便于用户只回「已创建」也能同步 */
    private final java.util.concurrent.ConcurrentHashMap<Long, BookCommand> lastPrepByUser =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void handleBotMessage(ChatbotMessage message) {
        if (message == null) {
            return;
        }
        String conversationType = message.getConversationType();
        if (StringUtils.hasText(conversationType) && !"1".equals(conversationType.trim())) {
            log.info("dingtalk robot ignore non-DM conversationType={}", conversationType);
            replyText(message.getSessionWebhook(), "日程助手目前只支持单聊。");
            return;
        }
        String text = extractText(message);
        if (!StringUtils.hasText(text)) {
            replyText(message.getSessionWebhook(), "请发文字，例如：查 Bella 最近有没有空安排面试品牌总监");
            return;
        }
        String webhook = message.getSessionWebhook();
        String senderStaffId = message.getSenderStaffId();
        String trimmed = text.trim();
        log.info("dingtalk robot inbound senderStaffId={} nick={} text={}",
                senderStaffId, message.getSenderNick(), abbreviate(trimmed, 120));

        if (isPrepCommand(trimmed)) {
            worker.execute(() -> {
                try {
                    SuggestReply reply = prepareManualBooking(senderStaffId, trimmed);
                    if (reply.buttons().isEmpty()) {
                        replyText(webhook, reply.markdown());
                    } else {
                        replyActionCard(webhook, "去钉钉建日程", reply.markdown(), reply.buttons());
                    }
                } catch (Exception ex) {
                    log.warn("dingtalk robot prep failed: {}", ex.getMessage(), ex);
                    replyText(webhook, "准备失败：" + (ex.getMessage() == null ? "请稍后重试" : ex.getMessage()));
                }
            });
            return;
        }
        if (isSyncCommand(trimmed) || isSyncPhrase(trimmed)) {
            worker.execute(() -> {
                try {
                    replyText(webhook, syncManualBooking(senderStaffId, trimmed));
                } catch (Exception ex) {
                    log.warn("dingtalk robot sync failed: {}", ex.getMessage(), ex);
                    replyText(webhook, "同步失败：" + (ex.getMessage() == null ? "请稍后重试" : ex.getMessage()));
                }
            });
            return;
        }

        replyText(webhook, "收到，正在查询…");
        worker.execute(() -> {
            try {
                SuggestReply reply = processAsk(senderStaffId, trimmed);
                if (reply.buttons().isEmpty()) {
                    replyText(webhook, reply.markdown());
                } else {
                    replyActionCard(webhook, "下一步", reply.markdown(), reply.buttons());
                }
            } catch (Exception ex) {
                log.warn("dingtalk robot process failed: {}", ex.getMessage(), ex);
                replyText(webhook, "查询失败：" + (ex.getMessage() == null ? "请稍后重试" : ex.getMessage()));
            }
        });
    }

    SuggestReply processAsk(String senderStaffId, String text) {
        SenderUser sender = findSender(senderStaffId);
        if (sender == null) {
            return SuggestReply.text("你的钉钉账号还没绑定本系统用户。\n请到系统完成钉钉绑定后再试。");
        }
        List<DingTalkBusyUserOptionVO> users = dingTalkBusyService.listBoundUsers();
        ResolveResult resolve = resolveTarget(text, users);
        if (resolve.error() != null) {
            return SuggestReply.text(resolve.error());
        }
        DingTalkBusyUserOptionVO target = resolve.user();
        if (target.getDingtalkBound() == null || target.getDingtalkBound() != 1) {
            return SuggestReply.text("「" + displayName(target) + "」还未绑定钉钉，暂时查不了闲忙。");
        }

        DingTalkAssistantSuggestDTO dto = new DingTalkAssistantSuggestDTO();
        dto.setTargetUserId(target.getUserId());
        dto.setMessage(text);
        DingTalkAssistantSuggestVO vo = dingTalkAssistantService.suggest(dto);
        return buildSuggestReply(vo);
    }

    SuggestReply prepareManualBooking(String senderStaffId, String text) {
        SenderUser sender = findSender(senderStaffId);
        if (sender == null) {
            return SuggestReply.text("你的钉钉账号未绑定本系统，无法同步日程。");
        }
        BookCommand cmd = parseBookCommand(text, PREP_CMD);
        if (cmd == null) {
            return SuggestReply.text("预约指令无效，请重新查询后点击卡片按钮。");
        }
        lastPrepByUser.put(sender.userId(), cmd);
        String who = loadNickname(cmd.targetUserId());
        String intent = switch (cmd.action()) {
            case "interview" -> "面试邀约";
            case "report" -> "工作汇报";
            default -> "会议";
        };
        String titleHint = switch (cmd.action()) {
            case "interview" -> "面试 · " + (StringUtils.hasText(cmd.jobName()) ? cmd.jobName() : "岗位待填") + " · " + who;
            case "report" -> "工作汇报 · " + who;
            default -> "会议 · " + who;
        };
        String body = "请在钉钉里手动新建日程（不会由机器人自动创建）：\n"
                + "· 类型：" + intent + "\n"
                + "· 建议主题：" + titleHint + "\n"
                + "· 建议时间：" + cmd.start().format(CARD_TIME) + "（" + cmd.durationMin() + " 分钟）\n"
                + "· 参与人：你 + " + who + "\n\n"
                + "先点「打开钉钉日程」新建；建好后再点「已建好，同步系统」。";
        String sync = encodeBookCommand(SYNC_PREFIX, cmd.action(), cmd.targetUserId(), cmd.start(),
                cmd.durationMin(), cmd.jobName());
        List<CardButton> buttons = List.of(
                new CardButton("打开钉钉日程", DING_CALENDAR_OPEN),
                new CardButton("已建好，同步系统", dtmdSendMessage(sync)));
        return new SuggestReply(body, buttons);
    }

    String syncManualBooking(String senderStaffId, String text) {
        SenderUser sender = findSender(senderStaffId);
        if (sender == null) {
            return "你的钉钉账号未绑定本系统，无法同步。";
        }
        BookCommand cmd = parseBookCommand(text, SYNC_CMD);
        if (cmd == null) {
            cmd = lastPrepByUser.get(sender.userId());
        }
        if (cmd == null) {
            return "没有可同步的预约。请先查询可约时段，再点「发起面试邀约 / 邀请开会」。";
        }
        BookCommand finalCmd = cmd;
        return RobotSecurity.runAs(sender.userId(), sender.username(), () -> {
            String who = loadNickname(finalCmd.targetUserId());
            String kind = "report".equals(finalCmd.action()) ? "REPORT" : "MEETING";
            String title;
            String description;
            if ("interview".equals(finalCmd.action())) {
                String job = StringUtils.hasText(finalCmd.jobName()) ? finalCmd.jobName() : "面试";
                title = "面试 · " + job + " · " + who;
                description = "用户在钉钉手动建日程后，由机器人同步到系统（暂无候选人时不建 HR 邀约单）。";
            } else if ("report".equals(finalCmd.action())) {
                title = "工作汇报 · " + who;
                description = "用户在钉钉手动建日程后，由机器人同步到系统。";
            } else {
                title = "会议 · " + who;
                description = "用户在钉钉手动建日程后，由机器人同步到系统。";
            }
            Long recordId = insertAssistantRecord(kind, title, description, finalCmd.start(), finalCmd.durationMin(),
                    sender.userId(), finalCmd.targetUserId(), sender.username());
            lastPrepByUser.remove(sender.userId());
            return "已同步到系统助手日程记录"
                    + (recordId == null ? "。" : "（#" + recordId + "）。")
                    + "\n钉钉侧请确认你已在日历里建好对应日程。";
        });
    }

    private Long insertAssistantRecord(String kind, String title, String description, LocalDateTime start,
                                       int durationMin, Long querierId, Long targetId, String createBy) {
        var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO sys_dingtalk_assistant_event
                    (kind, title, description, location, online_meeting, start_time, duration_min, end_time,
                     querier_user_id, target_user_id, querier_event_id, target_event_id, task_id, status, create_by, is_active)
                    VALUES (?, ?, ?, '', 1, ?, ?, ?, ?, ?, NULL, NULL, NULL, 'SUCCESS', ?, 1)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, kind);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setTimestamp(4, java.sql.Timestamp.valueOf(start));
            ps.setInt(5, durationMin);
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(start.plusMinutes(durationMin)));
            ps.setLong(7, querierId);
            ps.setLong(8, targetId);
            ps.setString(9, createBy == null ? "robot" : createBy);
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? null : key.longValue();
    }

    private SuggestReply buildSuggestReply(DingTalkAssistantSuggestVO vo) {
        if (vo == null) {
            return SuggestReply.text("暂无建议");
        }
        if (StringUtils.hasText(vo.getError()) && (vo.getDayGroups() == null || vo.getDayGroups().isEmpty())) {
            return SuggestReply.text(vo.getError().trim());
        }

        String action = normalizeAction(vo.getIntentAction());
        int duration = vo.getDurationMin() == null ? 60 : vo.getDurationMin();
        String who = StringUtils.hasText(vo.getTargetNickname()) ? vo.getTargetNickname() : "对方";
        String intentLabel = switch (action) {
            case "interview" -> "面试";
            case "report" -> "工作汇报";
            default -> "会议";
        };
        String jobBit = StringUtils.hasText(vo.getJobName()) ? "（「" + vo.getJobName().trim() + "」）" : "";

        List<LocalDateTime> starts = new ArrayList<>();
        List<Integer> durations = new ArrayList<>();
        if (vo.getDayGroups() != null) {
            for (DingTalkAssistantSuggestVO.DayGroup day : vo.getDayGroups()) {
                if (day.getSlots() == null) {
                    continue;
                }
                for (DingTalkAssistantSuggestVO.SlotOption slot : day.getSlots()) {
                    if (starts.size() >= MAX_SLOT_LINES || slot.getStart() == null) {
                        break;
                    }
                    int slotDuration = duration;
                    if (slot.getEnd() != null) {
                        long mins = Duration.between(slot.getStart(), slot.getEnd()).toMinutes();
                        if (mins >= 15 && mins <= 240) {
                            slotDuration = (int) mins;
                        }
                    }
                    starts.add(slot.getStart());
                    durations.add(slotDuration);
                }
                if (starts.size() >= MAX_SLOT_LINES) {
                    break;
                }
            }
        }

        if (starts.isEmpty()) {
            return SuggestReply.text("「" + who + "」近期暂无可约的" + intentLabel + "时段" + jobBit
                    + "。可换一天、缩短时长后再问。");
        }

        StringBuilder body = new StringBuilder();
        body.append("「").append(who).append("」近期有空：共 ").append(starts.size())
                .append(" 个推荐").append(intentLabel).append("时段").append(jobBit).append("：\n");
        for (int i = 0; i < starts.size(); i++) {
            body.append(i + 1).append(". ").append(starts.get(i).format(CARD_TIME))
                    .append("（").append(durations.get(i)).append(" 分）\n");
        }
        body.append("\n点下方动作后，请在钉钉自带日程里新建（机器人不会自动创建）；建好后再同步到系统。");

        // 默认用第一个推荐时段；动作对齐工作台：面试邀约 / 会议 / 汇报
        LocalDateTime firstStart = starts.getFirst();
        int firstDuration = durations.getFirst();
        List<CardButton> buttons = new ArrayList<>();
        if ("interview".equals(action)) {
            buttons.add(new CardButton("发起面试邀约",
                    dtmdSendMessage(encodeBookCommand(PREP_PREFIX, "interview", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName()))));
        } else if ("report".equals(action)) {
            buttons.add(new CardButton("安排工作汇报",
                    dtmdSendMessage(encodeBookCommand(PREP_PREFIX, "report", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName()))));
        } else if ("meeting".equals(action)) {
            buttons.add(new CardButton("邀请开会",
                    dtmdSendMessage(encodeBookCommand(PREP_PREFIX, "meeting", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName()))));
        } else {
            buttons.add(new CardButton("发起面试邀约",
                    dtmdSendMessage(encodeBookCommand(PREP_PREFIX, "interview", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName()))));
            buttons.add(new CardButton("邀请开会",
                    dtmdSendMessage(encodeBookCommand(PREP_PREFIX, "meeting", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName()))));
            buttons.add(new CardButton("安排工作汇报",
                    dtmdSendMessage(encodeBookCommand(PREP_PREFIX, "report", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName()))));
        }
        return new SuggestReply(body.toString().trim(), buttons);
    }

    private static String normalizeAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "meeting";
        }
        String a = raw.trim().toLowerCase(Locale.ROOT);
        if (a.contains("interview") || "面试".equals(a)) {
            return "interview";
        }
        if (a.contains("report") || "汇报".equals(a)) {
            return "report";
        }
        if (a.contains("busy") || "闲忙".equals(a) || "有没有空".equals(a)) {
            return "busy_query";
        }
        return "meeting";
    }

    private String encodeBookCommand(String prefix, String action, Long targetUserId, LocalDateTime start,
                                     int durationMin, String jobName) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("a", action);
            node.put("t", targetUserId);
            node.put("s", start.format(API_TIME));
            node.put("d", durationMin);
            if (StringUtils.hasText(jobName)) {
                node.put("j", jobName.trim());
            }
            String json = objectMapper.writeValueAsString(node);
            String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            return prefix + b64;
        } catch (Exception ex) {
            throw new BusinessException("生成预约指令失败");
        }
    }

    private BookCommand parseBookCommand(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text.replace('\u00A0', ' '));
        if (!m.find()) {
            return null;
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(m.group(1));
            Map<?, ?> node = objectMapper.readValue(raw, Map.class);
            String action = String.valueOf(node.get("a"));
            Long target = Long.valueOf(String.valueOf(node.get("t")));
            LocalDateTime start = LocalDateTime.parse(String.valueOf(node.get("s")), API_TIME);
            int duration = Integer.parseInt(String.valueOf(node.get("d")));
            String job = node.get("j") == null ? null : String.valueOf(node.get("j"));
            return new BookCommand(normalizeAction(action), target, start, duration, job);
        } catch (Exception ex) {
            log.warn("parse book command failed: {}", ex.getMessage());
            return null;
        }
    }

    private static boolean isPrepCommand(String text) {
        return text != null && PREP_CMD.matcher(text).find();
    }

    private static boolean isSyncCommand(String text) {
        return text != null && SYNC_CMD.matcher(text).find();
    }

    private static boolean isSyncPhrase(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String t = text.trim();
        return "已创建".equals(t) || "已建好".equals(t) || "同步".equals(t) || "同步系统".equals(t)
                || "已建好，同步系统".equals(t);
    }

    private static String dtmdSendMessage(String content) {
        return "dtmd://dingtalkclient/sendMessage?content="
                + URLEncoder.encode(content, StandardCharsets.UTF_8);
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
        return ResolveResult.error("没认出要查的同事。请带上姓名，例如：查 Bella 最近有没有空安排面试品牌总监");
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

    private String loadNickname(Long userId) {
        if (userId == null) {
            return "同事";
        }
        String name = jdbc.query("""
                SELECT COALESCE(NULLIF(TRIM(nickname), ''), username) FROM sys_user WHERE user_id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : null, userId);
        return StringUtils.hasText(name) ? name : "同事";
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

    private void replyText(String sessionWebhook, String body) {
        if (!StringUtils.hasText(sessionWebhook) || !StringUtils.hasText(body)) {
            return;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("msgtype", "text");
            root.putObject("text").put("content", body);
            postWebhook(sessionWebhook, root);
        } catch (Exception ex) {
            log.warn("dingtalk robot replyText failed: {}", ex.getMessage());
        }
    }

    private void replyActionCard(String sessionWebhook, String title, String markdown, List<CardButton> buttons) {
        if (!StringUtils.hasText(sessionWebhook) || buttons == null || buttons.isEmpty()) {
            replyText(sessionWebhook, markdown);
            return;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("msgtype", "actionCard");
            ObjectNode card = root.putObject("actionCard");
            card.put("title", title);
            card.put("text", markdown);
            card.put("btnOrientation", "0");
            ArrayNode btns = card.putArray("btns");
            for (CardButton button : buttons) {
                ObjectNode b = btns.addObject();
                b.put("title", button.title());
                b.put("actionURL", button.actionUrl());
            }
            postWebhook(sessionWebhook, root);
        } catch (Exception ex) {
            log.warn("dingtalk robot actionCard failed: {}", ex.getMessage());
            replyText(sessionWebhook, markdown);
        }
    }

    private void postWebhook(String webhook, ObjectNode body) throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            log.warn("dingtalk webhook http {}: {}", response.statusCode(), abbreviate(response.body(), 200));
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

    record BookCommand(String action, Long targetUserId, LocalDateTime start, int durationMin, String jobName) {
    }

    record CardButton(String title, String actionUrl) {
    }

    record SuggestReply(String markdown, List<CardButton> buttons) {
        static SuggestReply text(String markdown) {
            return new SuggestReply(markdown, List.of());
        }
    }
}
