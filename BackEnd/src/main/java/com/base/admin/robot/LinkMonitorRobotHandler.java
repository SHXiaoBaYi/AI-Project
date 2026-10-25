package com.base.admin.robot;

import com.base.admin.exception.BusinessException;
import com.base.admin.service.DingTalkRobotAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LinkMonitorRobotHandler {

    private final LinkMonitorService linkMonitorService;
    private final RobotSessionService sessionService;
    private final RobotLinkFillTicketStore fillTicketStore;
    private final ObjectProvider<DingTalkRobotAssistantService> dingTalkRobotAssistantService;

    public RobotReply handleAdd(RobotSessionService.Session session, String text, String utteranceType,
                                String staffId, Long userId) {
        String key = session.sessionKey();
        Map<String, Object> slots = session.slots() == null ? new HashMap<>() : new HashMap<>(session.slots());
        @SuppressWarnings("unchecked")
        List<String> draft = slots.get("urls") instanceof List<?> list
                ? new ArrayList<>(list.stream().map(String::valueOf).toList())
                : new ArrayList<>();
        boolean group = "2".equals(session.conversationType());

        if ("cancel".equals(utteranceType)) {
            sessionService.clear(key);
            return RobotReply.text("已取消添加监控。");
        }
        if ("start".equals(utteranceType) || RobotProductRules.STATE_IDLE.equals(session.state())) {
            List<String> urls = linkMonitorService.extractUrls(text);
            if (!urls.isEmpty()) {
                draft.addAll(urls);
                slots.put("urls", draft);
                sessionService.save(key, RobotProductRules.STATE_WAIT_MORE, RobotProductRules.INTENT_LINK_ADD, slots);
                return confirmMoreReply(session, staffId, userId, draft.size(), group);
            }
            sessionService.save(key, RobotProductRules.STATE_WAIT_URL, RobotProductRules.INTENT_LINK_ADD, slots);
            return waitUrlReply(session, staffId, userId, group);
        }
        if ("provide_url".equals(utteranceType) || RobotProductRules.STATE_WAIT_URL.equals(session.state())) {
            List<String> urls = linkMonitorService.extractUrls(text);
            if (urls.isEmpty()) {
                if (group) {
                    return waitUrlReply(session, staffId, userId, true);
                }
                return RobotReply.text("没有识别到有效链接，请发送以 http:// 或 https:// 开头的地址。");
            }
            for (String u : urls) {
                if (!draft.contains(u)) {
                    draft.add(u);
                }
            }
            slots.put("urls", draft);
            sessionService.save(key, RobotProductRules.STATE_WAIT_MORE, RobotProductRules.INTENT_LINK_ADD, slots);
            return confirmMoreReply(session, staffId, userId, draft.size(), group);
        }
        if ("confirm_more_yes".equals(utteranceType)) {
            sessionService.save(key, RobotProductRules.STATE_WAIT_URL, RobotProductRules.INTENT_LINK_ADD, slots);
            return waitUrlReply(session, staffId, userId, group);
        }
        if ("confirm_more_unclear".equals(utteranceType)) {
            if (group) {
                return confirmMoreReply(session, staffId, userId, draft.size(), true);
            }
            return RobotReply.text("请回复「需要」继续追加，或「不需要」完成提交。");
        }
        if ("confirm_more_no".equals(utteranceType)) {
            return commitDraft(session, staffId, userId, draft);
        }
        return group ? waitUrlReply(session, staffId, userId, true)
                : RobotReply.text("请发送链接，或回复「取消」。");
    }

    private RobotReply waitUrlReply(RobotSessionService.Session session, String staffId, Long userId, boolean group) {
        if (!group) {
            return RobotReply.text("请发送要监控的完整链接（http/https）。也可一次发多条。回复「取消」可退出。");
        }
        String alertHint = "告警将推送到本群，并通知添加人。";
        String md = "请点击下方按钮填写要监控的链接（钉钉群不会推送未 @ 的消息，所以改用卡片填写）。\n"
                + alertHint + "\n也可在此条里 @ 我并附上链接。回复「取消」退出。";
        RobotReply card = fillCard(session, staffId, userId, md, "点击填写监控链接");
        return card != null ? card : RobotReply.text(md + "\n（H5 地址未配置，请直接 @ 我发送链接）");
    }

    private RobotReply confirmMoreReply(RobotSessionService.Session session, String staffId, Long userId,
                                        int draftCount, boolean group) {
        if (!group) {
            return RobotReply.text("已记下 " + draftCount + " 条链接。还需要追加吗？回复「需要」或「不需要」。");
        }
        String md = "已记下 " + draftCount + " 条。告警将推送到本群。\n"
                + "请用下方按钮追加或完成提交（无需再 @）。也可回复「需要」/「不需要」（需 @ 我）。";
        RobotReply card = fillCard(session, staffId, userId, md, "追加/完成提交");
        return card != null ? card : RobotReply.text(md);
    }

    private RobotReply fillCard(RobotSessionService.Session session, String staffId, Long userId,
                                String markdown, String buttonTitle) {
        DingTalkRobotAssistantService asst = dingTalkRobotAssistantService.getIfAvailable();
        if (asst == null) {
            return null;
        }
        String ticket = fillTicketStore.issue(new RobotLinkFillTicketStore.Ticket(
                session.sessionKey(),
                staffId,
                userId,
                session.openConversationId(),
                session.conversationType(),
                0L));
        String actionUrl = asst.buildLinkMonitorFillActionUrl(userId, ticket);
        if (!StringUtils.hasText(actionUrl)) {
            return null;
        }
        return RobotReply.card(markdown, List.of(new RobotReply.CardButton(buttonTitle, actionUrl)));
    }

    private RobotReply commitDraft(RobotSessionService.Session session, String staffId, Long userId,
                                   List<String> draft) {
        String key = session.sessionKey();
        if (draft.isEmpty()) {
            sessionService.clear(key);
            return RobotReply.text("没有可提交的链接。");
        }
        List<String> ok = new ArrayList<>();
        List<String> fail = new ArrayList<>();
        for (String u : draft) {
            try {
                linkMonitorService.addMonitor(u, session.openConversationId(), session.conversationType(),
                        staffId, userId, "robot", staffId);
                ok.add(u);
            } catch (BusinessException ex) {
                fail.add(u + "（" + ex.getMessage() + "）");
            }
        }
        sessionService.clear(key);
        StringBuilder sb = new StringBuilder();
        if (!ok.isEmpty()) {
            sb.append("已加入监控（共 ").append(ok.size()).append(" 条）。")
                    .append("按自然日每天 09:30 探测；若无法打开或页面出现 404/无权限/已删除等提示，将告警。\n");
            for (String u : ok) {
                sb.append("- ").append(u).append("\n");
            }
            if ("2".equals(session.conversationType()) && StringUtils.hasText(session.openConversationId())) {
                sb.append("告警会推送到本群，并通知添加人。");
            } else {
                sb.append("告警会通知添加人。");
            }
        }
        if (!fail.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("未加入：\n");
            for (String f : fail) {
                sb.append("- ").append(f).append("\n");
            }
        }
        return RobotReply.text(sb.isEmpty() ? "没有变更。" : sb.toString().trim());
    }

    public RobotReply handleList() {
        List<Map<String, Object>> list = linkMonitorService.listActive(50);
        if (list.isEmpty()) {
            return RobotReply.text("当前没有正在监控的链接。");
        }
        StringBuilder md = new StringBuilder("当前共有 ").append(list.size()).append(" 条正在监控的链接：\n");
        int i = 1;
        for (Map<String, Object> row : list) {
            String url = String.valueOf(row.get("url"));
            String cid = row.get("open_conversation_id") == null ? "" : String.valueOf(row.get("open_conversation_id"));
            String ctype = row.get("conversation_type") == null ? "" : String.valueOf(row.get("conversation_type"));
            md.append(i++).append(". ").append(url);
            if ("2".equals(ctype) && StringUtils.hasText(cid)) {
                md.append("（告警群已绑定）");
            }
            md.append("\n");
        }
        md.append("\n如需停止某条，可说「删除监控」。查异常可说「监控哪些有异常」。");
        return RobotReply.text(md.toString().trim());
    }

    public RobotReply handleAbnormal() {
        // 先对全部在监控链接即时探测，再按本轮结果回复（不读过期缓存）
        LinkMonitorService.ProbeBatchSummary batch = linkMonitorService.probeAllActiveNow(false);
        if (batch.total() == 0) {
            return RobotReply.text("当前没有正在监控的链接。");
        }
        StringBuilder md = new StringBuilder();
        md.append("已即时探测 ").append(batch.total()).append(" 条监控链接：")
                .append("正常 ").append(batch.okCount())
                .append("，异常 ").append(batch.failCount()).append("。\n");
        if (batch.failCount() == 0) {
            md.append("目前没有异常。");
            return RobotReply.text(md.toString());
        }
        md.append("\n异常明细：\n");
        int i = 1;
        for (LinkMonitorService.ProbeFailItem item : batch.failures()) {
            md.append(i++).append(". ").append(item.url());
            if (StringUtils.hasText(item.error())) {
                md.append(" — ").append(item.error());
            }
            md.append("\n");
        }
        return RobotReply.text(md.toString().trim());
    }

    public RobotReply handleDelete(String staffId) {
        List<Map<String, Object>> list = linkMonitorService.listActive(20);
        if (list.isEmpty()) {
            return RobotReply.text("当前没有监控中的链接。");
        }
        StringBuilder md = new StringBuilder("当前监控链接（全局可见，群内任何人可删）：\n");
        List<RobotReply.CardButton> buttons = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> row : list) {
            long id = ((Number) row.get("id")).longValue();
            String url = String.valueOf(row.get("url"));
            String cid = row.get("open_conversation_id") == null ? "" : String.valueOf(row.get("open_conversation_id"));
            String ctype = row.get("conversation_type") == null ? "" : String.valueOf(row.get("conversation_type"));
            int no = i++;
            md.append(no).append(". ").append(url);
            if ("2".equals(ctype) && StringUtils.hasText(cid)) {
                md.append("（告警群已绑定）");
            }
            md.append("\n");
            // 按钮序号与上方列表一致；指令仍带数据库 id，避免删错行
            String title = "删除#" + no;
            String cmd = RobotProductRules.CMD_LM_DEL_PREFIX + id;
            String urlEnc = "dtmd://dingtalkclient/sendMessage?content="
                    + URLEncoder.encode(cmd, StandardCharsets.UTF_8);
            buttons.add(new RobotReply.CardButton(title, urlEnc));
            if (buttons.size() >= 8) {
                break;
            }
        }
        if (!StringUtils.hasText(staffId)) {
            return RobotReply.text(md.toString());
        }
        return RobotReply.card(md.toString(), buttons);
    }

    public RobotReply handleDeleteCommand(String text, String staffId) {
        String raw = text.trim();
        if (!raw.startsWith(RobotProductRules.CMD_LM_DEL_PREFIX)) {
            return null;
        }
        String idPart = raw.substring(RobotProductRules.CMD_LM_DEL_PREFIX.length()).trim();
        long id;
        try {
            id = Long.parseLong(idPart);
        } catch (NumberFormatException ex) {
            return RobotReply.text("删除指令无效。");
        }
        try {
            String url = linkMonitorService.softDelete(id, staffId, true);
            String shown = url == null ? "" : url.trim();
            if (shown.length() > 80) {
                shown = shown.substring(0, 80) + "…";
            }
            return RobotReply.text(shown.isEmpty() ? "已停止监控这条链接。" : "已停止监控：\n" + shown);
        } catch (BusinessException ex) {
            return RobotReply.text(ex.getMessage());
        }
    }
}
