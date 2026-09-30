package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
import com.base.admin.domain.dto.DingTalkAssistantMeetingDTO;
import com.base.admin.domain.dto.DingTalkAssistantReportDTO;
import com.base.admin.domain.dto.DingTalkAssistantSuggestDTO;
import com.base.admin.domain.dto.HrInviteCreateDTO;
import com.base.admin.domain.vo.DingTalkAssistantActionResultVO;
import com.base.admin.domain.vo.DingTalkAssistantSuggestVO;
import com.base.admin.domain.vo.DingTalkBusyUserOptionVO;
import com.base.admin.domain.vo.DingTalkIntentExecuteVO;
import com.base.admin.domain.vo.HrInviteSaveVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.RobotSecurity;
import com.base.admin.util.SecurityUtils;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>卡片按钮打开系统 H5（钉钉内免登），按意图创建钉钉日程。
 * 面试邀约会先选候选人，再走 HR 邀约：上传简历到钉盘并把打开链接写入日程描述。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkRobotAssistantService {

    private static final Pattern AT_MENTION = Pattern.compile("@([^\\s@，,。！!？?\\n]+)");
    private static final Pattern QUOTED = Pattern.compile("「([^」]+)」");
    private static final Pattern PREP_CMD = Pattern.compile("(?i)#P[:：]\\s*([A-Za-z0-9_\\-+=/]+)");
    private static final Pattern INVITE_CMD = Pattern.compile("(?i)#I[:：]\\s*([A-Za-z0-9_\\-+=/]+)");
    private static final Pattern SYNC_CMD = Pattern.compile("(?i)#S[:：]\\s*([A-Za-z0-9_\\-+=/]+)");
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CARD_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final int MAX_SLOT_LINES = 5;
    private static final int MAX_CANDIDATE_BUTTONS = 6;
    private static final String PREP_PREFIX = "#P:";
    private static final String INVITE_PREFIX = "#I:";
    private static final String KIND_PREP = "prep";
    private static final String KIND_INVITE = "invite";
    private static final Set<String> INVITEABLE_STAGES = Set.of(
            "SCREEN_PASS", "FIRST_ROUND", "SECOND_ROUND", "R3_PASS", "R4_PASS");

    private final DingTalkAssistantService dingTalkAssistantService;
    private final DingTalkBusyService dingTalkBusyService;
    private final HrInviteService hrInviteService;
    private final DingTalkIntentTicketStore intentTicketStore;
    private final DingTalkProperties dingTalkProperties;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Value("${xby.upload-public-base:http://121.40.119.134/shxby/api}")
    private String uploadPublicBase;

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
                    SuggestReply reply = handlePrepCommand(senderStaffId, trimmed);
                    if (reply.buttons().isEmpty()) {
                        replyText(webhook, reply.markdown());
                    } else {
                        replyActionCard(webhook, "选择候选人", reply.markdown(), reply.buttons());
                    }
                } catch (Exception ex) {
                    log.warn("dingtalk robot prep failed: {}", ex.getMessage(), ex);
                    replyText(webhook, "创建失败：" + (ex.getMessage() == null ? "请稍后重试" : ex.getMessage()));
                }
            });
            return;
        }
        if (isInviteCommand(trimmed)) {
            worker.execute(() -> {
                try {
                    replyText(webhook, createInterviewInvite(senderStaffId, trimmed));
                } catch (Exception ex) {
                    log.warn("dingtalk robot invite failed: {}", ex.getMessage(), ex);
                    replyText(webhook, "发起面试邀约失败：" + (ex.getMessage() == null ? "请稍后重试" : ex.getMessage()));
                }
            });
            return;
        }
        if (isSyncCommand(trimmed) || isSyncPhrase(trimmed)) {
            replyText(webhook, "日程助手已改为自动创建钉钉日程。请重新查询后点「发起面试邀约 / 邀请开会」，无需再手动建日程。");
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
        return buildSuggestReply(vo, sender.userId());
    }

    /**
     * H5 免登后执行 ActionCard 意图：建会议/汇报，或返回候选人列表。
     */
    public DingTalkIntentExecuteVO executeIntent(String ticket) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String username = SecurityUtils.getCurrentUsername();
        if (currentUserId == null) {
            throw new BusinessException("请先登录");
        }
        DingTalkIntentTicketStore.Intent intent = intentTicketStore.consume(ticket);
        if (intent == null) {
            throw new BusinessException("链接已失效，请回到钉钉重新点卡片按钮");
        }
        if (intent.senderUserId() == null || !intent.senderUserId().equals(currentUserId)) {
            throw new BusinessException("该操作仅发起人可用，请用发起查询的钉钉账号打开");
        }
        BookCommand cmd = new BookCommand(
                normalizeAction(intent.action()),
                intent.targetUserId(),
                intent.start(),
                intent.durationMin(),
                intent.jobName(),
                intent.applicationId(),
                intent.roundNo());
        SenderUser sender = new SenderUser(currentUserId, username, loadNickname(currentUserId), null, null);
        lastPrepByUser.put(currentUserId, cmd);

        if (KIND_INVITE.equals(intent.kind())) {
            DingTalkIntentExecuteVO vo = new DingTalkIntentExecuteVO();
            vo.setStatus("done");
            vo.setMessage(createInterviewInviteFromCmd(sender, cmd));
            return vo;
        }

        if ("interview".equals(cmd.action())) {
            return buildCandidatePickIntent(cmd, currentUserId);
        }

        DingTalkIntentExecuteVO vo = new DingTalkIntentExecuteVO();
        vo.setStatus("done");
        vo.setMessage(createMeetingOrReport(sender, cmd));
        return vo;
    }

    private DingTalkIntentExecuteVO buildCandidatePickIntent(BookCommand cmd, Long senderUserId) {
        DingTalkIntentExecuteVO vo = new DingTalkIntentExecuteVO();
        List<CandidateOption> candidates = listInviteableCandidates(cmd.jobName());
        if (candidates.isEmpty()) {
            vo.setStatus("done");
            vo.setMessage("暂无可邀约且已上传简历的候选人"
                    + (StringUtils.hasText(cmd.jobName()) ? "（岗位「" + cmd.jobName() + "」）" : "")
                    + "。请先在招聘里维护候选人简历与阶段。");
            return vo;
        }
        List<DingTalkIntentExecuteVO.Candidate> options = new ArrayList<>();
        for (CandidateOption c : candidates) {
            Integer round = nextInviteRound(c.stage());
            if (round == null) {
                continue;
            }
            String inviteTicket = issueIntentTicket(KIND_INVITE, "interview", cmd.targetUserId(), cmd.start(),
                    cmd.durationMin(), cmd.jobName(), c.applicationId(), round, senderUserId);
            DingTalkIntentExecuteVO.Candidate opt = new DingTalkIntentExecuteVO.Candidate();
            opt.setName(c.name());
            opt.setJobName(StringUtils.hasText(c.jobName()) ? c.jobName() : "未定岗");
            opt.setTicket(inviteTicket);
            options.add(opt);
            if (options.size() >= MAX_CANDIDATE_BUTTONS) {
                break;
            }
        }
        if (options.isEmpty()) {
            vo.setStatus("done");
            vo.setMessage("候选人阶段暂不可邀约，请到招聘面板核对后再试。");
            return vo;
        }
        vo.setStatus("need_candidate");
        String who = loadNickname(cmd.targetUserId());
        vo.setMessage("将为「" + who + "」创建面试钉钉日程（" + cmd.start().format(CARD_TIME)
                + "，" + cmd.durationMin() + " 分）。请选择候选人：");
        vo.setCandidates(options);
        return vo;
    }

    private String createInterviewInviteFromCmd(SenderUser sender, BookCommand cmd) {
        return RobotSecurity.runAs(sender.userId(), sender.username(), () -> {
            if (cmd.applicationId() == null) {
                throw new BusinessException("邀约指令缺少候选人");
            }
            Integer round = cmd.roundNo() != null ? cmd.roundNo() : nextInviteRound(loadApplicationStage(cmd.applicationId()));
            if (round == null) {
                throw new BusinessException("该候选人当前阶段不可发起面试邀约");
            }
            HrInviteCreateDTO dto = new HrInviteCreateDTO();
            dto.setApplicationId(cmd.applicationId());
            dto.setRoundNo(round);
            dto.setInterviewerUserIds(List.of(cmd.targetUserId()));
            dto.setInterviewAt(cmd.start());
            dto.setDurationMin(cmd.durationMin());
            dto.setCreateDingTalkCalendar(true);
            dto.setSendDingTalkWorkNotice(false);
            HrInviteSaveVO saved = hrInviteService.create(dto);
            lastPrepByUser.remove(sender.userId());
            String who = loadNickname(cmd.targetUserId());
            String candidate = loadCandidateName(cmd.applicationId());
            StringBuilder msg = new StringBuilder();
            msg.append("已创建钉钉面试日程，并写入简历钉盘链接（可在钉钉客户端日程描述中打开）。\n")
                    .append("· 候选人：").append(candidate).append("\n")
                    .append("· 面试官：").append(who).append("\n")
                    .append("· 时间：").append(cmd.start().format(CARD_TIME))
                    .append("（").append(cmd.durationMin()).append(" 分）");
            if (saved != null && saved.getId() != null) {
                msg.append("\n· 邀约 #").append(saved.getId());
            }
            if (saved != null && StringUtils.hasText(saved.getWarning())) {
                msg.append("\n注意：").append(saved.getWarning());
            }
            return msg.toString();
        });
    }

    SuggestReply handlePrepCommand(String senderStaffId, String text) {
        SenderUser sender = findSender(senderStaffId);
        if (sender == null) {
            return SuggestReply.text("你的钉钉账号未绑定本系统，无法创建日程。");
        }
        BookCommand cmd = parseBookCommand(text, PREP_CMD);
        if (cmd == null) {
            return SuggestReply.text("预约指令无效，请重新查询后点击卡片按钮。");
        }
        lastPrepByUser.put(sender.userId(), cmd);
        if ("interview".equals(cmd.action())) {
            return buildCandidatePickReply(cmd, sender.userId());
        }
        return SuggestReply.text(createMeetingOrReport(sender, cmd));
    }

    String createInterviewInvite(String senderStaffId, String text) {
        SenderUser sender = findSender(senderStaffId);
        if (sender == null) {
            return "你的钉钉账号未绑定本系统，无法发起邀约。";
        }
        BookCommand cmd = parseBookCommand(text, INVITE_CMD);
        if (cmd == null || cmd.applicationId() == null) {
            return "邀约指令无效，请重新点「发起面试邀约」并选择候选人。";
        }
        return createInterviewInviteFromCmd(sender, cmd);
    }

    private String createMeetingOrReport(SenderUser sender, BookCommand cmd) {
        return RobotSecurity.runAs(sender.userId(), sender.username(), () -> {
            String who = loadNickname(cmd.targetUserId());
            if ("report".equals(cmd.action())) {
                DingTalkAssistantReportDTO dto = new DingTalkAssistantReportDTO();
                dto.setTargetUserId(cmd.targetUserId());
                dto.setStartTime(cmd.start());
                dto.setDurationMin(cmd.durationMin());
                dto.setTitle("工作汇报 · " + who);
                dto.setContent("由钉钉日程助手创建。");
                DingTalkAssistantActionResultVO result = dingTalkAssistantService.createReport(dto);
                lastPrepByUser.remove(sender.userId());
                return StringUtils.hasText(result.getMessage()) ? result.getMessage()
                        : "已创建工作汇报钉钉日程（" + who + "，" + cmd.start().format(CARD_TIME) + "）。";
            }
            DingTalkAssistantMeetingDTO dto = new DingTalkAssistantMeetingDTO();
            dto.setTargetUserId(cmd.targetUserId());
            dto.setTitle("会议 · " + who);
            dto.setStartTime(cmd.start());
            dto.setDurationMin(cmd.durationMin());
            dto.setOnlineMeeting(true);
            dto.setDescription("由钉钉日程助手创建。");
            DingTalkAssistantActionResultVO result = dingTalkAssistantService.createMeeting(dto);
            lastPrepByUser.remove(sender.userId());
            return StringUtils.hasText(result.getMessage()) ? result.getMessage()
                    : "已创建会议钉钉日程（" + who + "，" + cmd.start().format(CARD_TIME) + "）。";
        });
    }

    private SuggestReply buildCandidatePickReply(BookCommand cmd, Long senderUserId) {
        String who = loadNickname(cmd.targetUserId());
        List<CandidateOption> candidates = listInviteableCandidates(cmd.jobName());
        if (candidates.isEmpty()) {
            return SuggestReply.text("暂无可邀约且已上传简历的候选人"
                    + (StringUtils.hasText(cmd.jobName()) ? "（岗位「" + cmd.jobName() + "」）" : "")
                    + "。请先在招聘里维护候选人简历与阶段，或到工作台日程助手选人发起。");
        }
        StringBuilder body = new StringBuilder();
        body.append("将为「").append(who).append("」创建面试钉钉日程（简历上传钉盘并写入描述）。\n")
                .append("时间：").append(cmd.start().format(CARD_TIME))
                .append("（").append(cmd.durationMin()).append(" 分）\n\n")
                .append("请选择候选人（将打开系统页面并免登）：");
        List<CardButton> buttons = new ArrayList<>();
        for (CandidateOption c : candidates) {
            Integer round = nextInviteRound(c.stage());
            if (round == null) {
                continue;
            }
            String label = c.name() + " · " + (StringUtils.hasText(c.jobName()) ? c.jobName() : "未定岗");
            if (label.length() > 20) {
                label = label.substring(0, 18) + "…";
            }
            buttons.add(new CardButton(label, h5ActionUrl(KIND_INVITE, "interview", cmd.targetUserId(),
                    cmd.start(), cmd.durationMin(), cmd.jobName(), c.applicationId(), round, senderUserId)));
            if (buttons.size() >= MAX_CANDIDATE_BUTTONS) {
                break;
            }
        }
        if (buttons.isEmpty()) {
            return SuggestReply.text("候选人阶段暂不可邀约，请到招聘面板核对后再试。");
        }
        if (candidates.size() > buttons.size()) {
            body.append("\n（仅展示前 ").append(buttons.size()).append(" 位，可到工作台查看全部）");
        }
        return new SuggestReply(body.toString(), buttons);
    }

    private List<CandidateOption> listInviteableCandidates(String jobName) {
        String sql = """
                SELECT a.id, c.display_name, COALESCE(r.job_name, '') job_name, a.current_stage
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id AND c.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.is_active = 1
                  AND a.current_stage IN ('SCREEN_PASS','FIRST_ROUND','SECOND_ROUND','R3_PASS','R4_PASS')
                  AND EXISTS (
                      SELECT 1 FROM hr_resume_file f
                      WHERE f.application_id = a.id AND f.is_active = 1
                  )
                """;
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(jobName)) {
            sql += " AND r.job_name LIKE ? ";
            args.add("%" + jobName.trim() + "%");
        }
        sql += " ORDER BY a.update_time DESC LIMIT 20 ";
        List<CandidateOption> rows = jdbc.query(sql, (rs, i) -> new CandidateOption(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("job_name"),
                rs.getString("current_stage")), args.toArray());
        List<CandidateOption> filtered = new ArrayList<>();
        for (CandidateOption row : rows) {
            if (INVITEABLE_STAGES.contains(row.stage()) && nextInviteRound(row.stage()) != null) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private static Integer nextInviteRound(String stage) {
        if (!StringUtils.hasText(stage)) {
            return null;
        }
        return switch (stage.trim()) {
            case "SCREEN_PASS" -> 1;
            case "FIRST_ROUND" -> 2;
            case "SECOND_ROUND" -> 3;
            case "R3_PASS" -> 4;
            case "R4_PASS" -> 5;
            default -> null;
        };
    }

    private String loadApplicationStage(Long applicationId) {
        if (applicationId == null) {
            return null;
        }
        return jdbc.query("SELECT current_stage FROM hr_application WHERE id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, applicationId);
    }

    private String loadCandidateName(Long applicationId) {
        if (applicationId == null) {
            return "候选人";
        }
        String name = jdbc.query("""
                SELECT c.display_name FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                WHERE a.id = ? AND a.is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : null, applicationId);
        return StringUtils.hasText(name) ? name : "候选人";
    }

    private SuggestReply buildSuggestReply(DingTalkAssistantSuggestVO vo, Long senderUserId) {
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
        body.append("\n点下方按钮将打开系统页面（钉钉内免登），自动创建钉钉日程；面试邀约会先选候选人，并把简历钉盘链接写入日程描述。");

        LocalDateTime firstStart = starts.getFirst();
        int firstDuration = durations.getFirst();
        List<CardButton> buttons = new ArrayList<>();
        if ("interview".equals(action)) {
            buttons.add(new CardButton("发起面试邀约",
                    h5ActionUrl(KIND_PREP, "interview", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName(), null, null, senderUserId)));
        } else if ("report".equals(action)) {
            buttons.add(new CardButton("安排工作汇报",
                    h5ActionUrl(KIND_PREP, "report", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName(), null, null, senderUserId)));
        } else if ("meeting".equals(action)) {
            buttons.add(new CardButton("邀请开会",
                    h5ActionUrl(KIND_PREP, "meeting", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName(), null, null, senderUserId)));
        } else {
            buttons.add(new CardButton("发起面试邀约",
                    h5ActionUrl(KIND_PREP, "interview", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName(), null, null, senderUserId)));
            buttons.add(new CardButton("邀请开会",
                    h5ActionUrl(KIND_PREP, "meeting", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName(), null, null, senderUserId)));
            buttons.add(new CardButton("安排工作汇报",
                    h5ActionUrl(KIND_PREP, "report", vo.getTargetUserId(),
                            firstStart, firstDuration, vo.getJobName(), null, null, senderUserId)));
        }
        return new SuggestReply(body.toString().trim(), buttons);
    }

    private String issueIntentTicket(String kind, String action, Long targetUserId, LocalDateTime start,
                                     int durationMin, String jobName, Long applicationId, Integer roundNo,
                                     Long senderUserId) {
        return intentTicketStore.issue(new DingTalkIntentTicketStore.Intent(
                kind, action, targetUserId, start, durationMin, jobName, applicationId, roundNo, senderUserId, 0L));
    }

    private String h5ActionUrl(String kind, String action, Long targetUserId, LocalDateTime start,
                               int durationMin, String jobName, Long applicationId, Integer roundNo,
                               Long senderUserId) {
        String base = resolveH5BaseUrl();
        if (!StringUtils.hasText(base)) {
            log.warn("dingtalk.h5-base-url 未配置，ActionCard 回退为会话指令");
            if (KIND_INVITE.equals(kind)) {
                return dtmdSendMessage(encodeBookCommand(INVITE_PREFIX, action, targetUserId, start,
                        durationMin, jobName, applicationId, roundNo));
            }
            return dtmdSendMessage(encodeBookCommand(PREP_PREFIX, action, targetUserId, start,
                    durationMin, jobName, applicationId, roundNo));
        }
        String ticket = issueIntentTicket(kind, action, targetUserId, start, durationMin, jobName,
                applicationId, roundNo, senderUserId);
        String page = base.replaceAll("/+$", "") + "/dingtalk/bridge?ticket="
                + URLEncoder.encode(ticket, StandardCharsets.UTF_8);
        return "dingtalk://dingtalkclient/page/link?url="
                + URLEncoder.encode(page, StandardCharsets.UTF_8)
                + "&pc_slide=true";
    }

    private String resolveH5BaseUrl() {
        if (StringUtils.hasText(dingTalkProperties.getH5BaseUrl())) {
            return dingTalkProperties.getH5BaseUrl().trim().replaceAll("/+$", "");
        }
        if (!StringUtils.hasText(uploadPublicBase)) {
            return "";
        }
        String base = uploadPublicBase.trim().replaceAll("/+$", "");
        if (base.endsWith("/api")) {
            return base.substring(0, base.length() - 4);
        }
        return base;
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
        return encodeBookCommand(prefix, action, targetUserId, start, durationMin, jobName, null, null);
    }

    private String encodeBookCommand(String prefix, String action, Long targetUserId, LocalDateTime start,
                                     int durationMin, String jobName, Long applicationId, Integer roundNo) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("a", action);
            node.put("t", targetUserId);
            node.put("s", start.format(API_TIME));
            node.put("d", durationMin);
            if (StringUtils.hasText(jobName)) {
                node.put("j", jobName.trim());
            }
            if (applicationId != null) {
                node.put("app", applicationId);
            }
            if (roundNo != null) {
                node.put("r", roundNo);
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
            Long applicationId = null;
            if (node.get("app") != null && StringUtils.hasText(String.valueOf(node.get("app")))) {
                applicationId = Long.valueOf(String.valueOf(node.get("app")));
            }
            Integer roundNo = null;
            if (node.get("r") != null && StringUtils.hasText(String.valueOf(node.get("r")))) {
                roundNo = Integer.valueOf(String.valueOf(node.get("r")));
            }
            return new BookCommand(normalizeAction(action), target, start, duration, job, applicationId, roundNo);
        } catch (Exception ex) {
            log.warn("parse book command failed: {}", ex.getMessage());
            return null;
        }
    }

    private static boolean isPrepCommand(String text) {
        return text != null && PREP_CMD.matcher(text).find();
    }

    private static boolean isInviteCommand(String text) {
        return text != null && INVITE_CMD.matcher(text).find();
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

    record BookCommand(String action, Long targetUserId, LocalDateTime start, int durationMin, String jobName,
                       Long applicationId, Integer roundNo) {
    }

    record CandidateOption(Long applicationId, String name, String jobName, String stage) {
    }

    record CardButton(String title, String actionUrl) {
    }

    record SuggestReply(String markdown, List<CardButton> buttons) {
        static SuggestReply text(String markdown) {
            return new SuggestReply(markdown, List.of());
        }
    }
}
