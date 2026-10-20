package com.base.admin.controller;

import com.base.admin.common.Result;
import com.base.admin.exception.BusinessException;
import com.base.admin.robot.LinkMonitorService;
import com.base.admin.robot.RobotLinkFillTicketStore;
import com.base.admin.robot.RobotProductRules;
import com.base.admin.robot.RobotSessionService;
import com.base.admin.service.DingTalkCalendarClient;
import com.base.admin.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 群多轮填链：卡片打开 H5，免在群里二次 @ 发链接。
 */
@Slf4j
@Tag(name = "机器人链接填报")
@RestController
@RequestMapping("/robot/link-monitor/fill")
@RequiredArgsConstructor
public class RobotLinkFillController {

    private final RobotLinkFillTicketStore ticketStore;
    private final RobotSessionService sessionService;
    private final LinkMonitorService linkMonitorService;
    private final DingTalkCalendarClient dingTalkCalendarClient;

    @Operation(summary = "填链页上下文")
    @GetMapping("/context")
    public Result<Map<String, Object>> context(@RequestParam String ticket) {
        RobotLinkFillTicketStore.Ticket t = requireTicket(ticket);
        RobotSessionService.Session session = sessionService.get(t.sessionKey());
        List<String> draft = draftUrls(session);
        Map<String, Object> vo = new HashMap<>();
        vo.put("sessionKey", t.sessionKey());
        vo.put("state", session == null ? RobotProductRules.STATE_IDLE : session.state());
        vo.put("draftUrls", draft);
        vo.put("draftCount", draft.size());
        return Result.ok(vo);
    }

    @Operation(summary = "提交链接 / 完成 / 取消")
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody FillSubmitDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTicket())) {
            throw new BusinessException("缺少票据");
        }
        RobotLinkFillTicketStore.Ticket t = requireTicket(dto.getTicket());
        // 登录用户与票据绑定人一致（或票据无 userId 时放行 staff）
        Long loginUid = SecurityUtils.getCurrentUserId();
        if (t.userId() != null && loginUid != null && !t.userId().equals(loginUid)) {
            throw new BusinessException("票据与当前登录用户不匹配，请重新从机器人卡片进入");
        }
        String action = StringUtils.hasText(dto.getAction()) ? dto.getAction().trim().toLowerCase() : "append";
        RobotSessionService.Session session = sessionService.getOrCreate(
                t.conversationType(), t.openConversationId(), t.staffId());
        Map<String, Object> slots = session.slots() == null ? new HashMap<>() : new HashMap<>(session.slots());
        @SuppressWarnings("unchecked")
        List<String> draft = slots.get("urls") instanceof List<?> list
                ? new ArrayList<>(list.stream().map(String::valueOf).toList())
                : new ArrayList<>();

        if ("cancel".equals(action)) {
            sessionService.clear(session.sessionKey());
            return Result.ok(Map.of("message", "已取消添加监控", "committed", 0));
        }

        if ("append".equals(action) || "commit".equals(action)) {
            List<String> extracted = linkMonitorService.extractUrls(dto.getUrlsText() == null ? "" : dto.getUrlsText());
            for (String u : extracted) {
                if (!draft.contains(u)) {
                    draft.add(u);
                }
            }
            if ("append".equals(action)) {
                if (draft.isEmpty()) {
                    throw new BusinessException("请至少填写一条 http/https 链接");
                }
                slots.put("urls", draft);
                sessionService.save(session.sessionKey(), RobotProductRules.STATE_WAIT_MORE,
                        RobotProductRules.INTENT_LINK_ADD, slots);
                return Result.ok(Map.of(
                        "message", "已记下 " + draft.size() + " 条，可继续追加或点「完成提交」",
                        "draftCount", draft.size(),
                        "draftUrls", draft,
                        "committed", 0));
            }
        }

        // commit
        if (draft.isEmpty()) {
            List<String> extracted = linkMonitorService.extractUrls(dto.getUrlsText() == null ? "" : dto.getUrlsText());
            draft.addAll(extracted);
        }
        if (draft.isEmpty()) {
            throw new BusinessException("没有可提交的链接");
        }
        List<String> ok = new ArrayList<>();
        List<String> fail = new ArrayList<>();
        for (String u : draft) {
            try {
                linkMonitorService.addMonitor(u, t.openConversationId(), t.conversationType(),
                        t.staffId(), t.userId() != null ? t.userId() : loginUid, "robot",
                        StringUtils.hasText(t.staffId()) ? t.staffId() : "h5");
                ok.add(u);
            } catch (BusinessException ex) {
                fail.add(u + "（" + ex.getMessage() + "）");
            }
        }
        sessionService.clear(session.sessionKey());
        String alertGroupHint = "";
        if ("2".equals(t.conversationType()) && StringUtils.hasText(t.openConversationId())) {
            String title = linkMonitorService.resolveGroupTitle(t.openConversationId());
            alertGroupHint = StringUtils.hasText(title)
                    ? ("告警将推送到群「" + title + "」。")
                    : "告警将推送到本群。";
            if (!ok.isEmpty()) {
                try {
                    StringBuilder md = new StringBuilder();
                    md.append("已通过填链表单加入监控 ").append(ok.size()).append(" 条。  \n");
                    for (String u : ok) {
                        md.append("- ").append(u).append("  \n");
                    }
                    md.append(alertGroupHint).append("每天 09:30 探测。");
                    dingTalkCalendarClient.sendRobotGroupMarkdown(
                            t.openConversationId(), "链接监控", md.toString());
                } catch (Exception ex) {
                    log.warn("fill commit notify group failed: {}", ex.getMessage());
                }
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("committed", ok.size());
        res.put("okUrls", ok);
        res.put("fail", fail);
        res.put("alertGroupHint", alertGroupHint);
        res.put("message", ok.isEmpty()
                ? ("未加入：" + String.join("；", fail))
                : ("已加入监控 " + ok.size() + " 条。" + alertGroupHint
                + (fail.isEmpty() ? "" : " 部分失败见详情")));
        return Result.ok(res);
    }

    private RobotLinkFillTicketStore.Ticket requireTicket(String ticket) {
        RobotLinkFillTicketStore.Ticket t = ticketStore.parse(ticket);
        if (t == null || !StringUtils.hasText(t.sessionKey())) {
            throw new BusinessException("填链票据无效或已过期，请重新 @ 机器人发起");
        }
        return t;
    }

    private static List<String> draftUrls(RobotSessionService.Session session) {
        if (session == null || session.slots() == null) {
            return List.of();
        }
        Object raw = session.slots().get("urls");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    @Data
    @Schema(description = "H5 填链提交")
    public static class FillSubmitDTO {
        @Schema(description = "票据")
        private String ticket;
        @Schema(description = "链接文本，可多行")
        private String urlsText;
        @Schema(description = "append=追加草稿；commit=提交入库；cancel=取消")
        private String action;
    }
}
