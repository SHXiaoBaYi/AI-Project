package com.base.admin.robot;

import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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

    public RobotReply handleAdd(RobotSessionService.Session session, String text, String utteranceType,
                                String staffId, Long userId) {
        String key = session.sessionKey();
        Map<String, Object> slots = session.slots() == null ? new HashMap<>() : new HashMap<>(session.slots());
        @SuppressWarnings("unchecked")
        List<String> draft = slots.get("urls") instanceof List<?> list
                ? new ArrayList<>(list.stream().map(String::valueOf).toList())
                : new ArrayList<>();

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
                return RobotReply.text("已记下 " + draft.size() + " 条链接。还需要追加吗？回复「需要」或「不需要」。");
            }
            sessionService.save(key, RobotProductRules.STATE_WAIT_URL, RobotProductRules.INTENT_LINK_ADD, slots);
            return RobotReply.text("请发送要监控的完整链接（http/https）。也可一次发多条。回复「取消」可退出。");
        }
        if ("provide_url".equals(utteranceType) || RobotProductRules.STATE_WAIT_URL.equals(session.state())) {
            List<String> urls = linkMonitorService.extractUrls(text);
            if (urls.isEmpty()) {
                return RobotReply.text("没有识别到有效链接，请发送以 http:// 或 https:// 开头的地址。");
            }
            for (String u : urls) {
                if (!draft.contains(u)) {
                    draft.add(u);
                }
            }
            slots.put("urls", draft);
            sessionService.save(key, RobotProductRules.STATE_WAIT_MORE, RobotProductRules.INTENT_LINK_ADD, slots);
            return RobotReply.text("已记下 " + draft.size() + " 条。还需要追加吗？回复「需要」或「不需要」。");
        }
        if ("confirm_more_yes".equals(utteranceType)) {
            sessionService.save(key, RobotProductRules.STATE_WAIT_URL, RobotProductRules.INTENT_LINK_ADD, slots);
            return RobotReply.text("请继续发送要追加的链接。");
        }
        if ("confirm_more_unclear".equals(utteranceType)) {
            return RobotReply.text("请回复「需要」继续追加，或「不需要」完成提交。");
        }
        if ("confirm_more_no".equals(utteranceType)) {
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
                sb.append("已加入监控（共 ").append(ok.size()).append(" 条），每 24 小时探测一次能否打开。\n");
                for (String u : ok) {
                    sb.append("- ").append(u).append("\n");
                }
                if ("1".equals(session.conversationType())) {
                    sb.append("单聊添加的监控仅后台告警，并通知 ").append(RobotProductRules.DM_ALERT_USERNAME).append("。");
                } else {
                    sb.append("异常时会告警（群推送能力完善前同步通知指定管理员）。");
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
        return RobotReply.text("请发送链接，或回复「取消」。");
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
            md.append(i++).append(". ").append(url).append("\n");
            String title = "删除#" + id;
            if (title.length() > 20) {
                title = "删" + id;
            }
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
            linkMonitorService.softDelete(id, staffId, true);
            return RobotReply.text("已停止监控 #" + id + "。");
        } catch (BusinessException ex) {
            return RobotReply.text(ex.getMessage());
        }
    }
}
