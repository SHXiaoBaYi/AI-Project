package com.base.admin.robot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotSessionService {

    private static final int TTL_HOURS = 2;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public record Session(
            Long id,
            String sessionKey,
            String staffId,
            String openConversationId,
            String conversationType,
            String state,
            String intentCode,
            Map<String, Object> slots,
            LocalDateTime expireAt) {
    }

    public record HistoryTurn(String role, String text, String intent, String at) {
    }

    public static String sessionKey(String conversationType, String conversationId, String staffId) {
        String staff = staffId == null ? "" : staffId.trim();
        if ("2".equals(conversationType) && StringUtils.hasText(conversationId)) {
            return "g:" + conversationId.trim() + ":" + staff;
        }
        return "dm:" + staff;
    }

    /** 是否处于多轮补槽（群免 @ 的依据）。 */
    public boolean hasActiveMultiTurn(String conversationType, String conversationId, String staffId) {
        Session s = peek(conversationType, conversationId, staffId);
        if (s == null || s.expireAt() != null && s.expireAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return StringUtils.hasText(s.state()) && !RobotProductRules.STATE_IDLE.equals(s.state());
    }

    public Session peek(String conversationType, String conversationId, String staffId) {
        return get(sessionKey(conversationType, conversationId, staffId));
    }

    public Session getOrCreate(String conversationType, String conversationId, String staffId) {
        String key = sessionKey(conversationType, conversationId, staffId);
        Session existing = get(key);
        if (existing != null) {
            if (existing.expireAt() != null && existing.expireAt().isBefore(LocalDateTime.now())) {
                // 过期：清空业务态，保留历史摘要到新会话
                List<HistoryTurn> hist = historyOf(existing);
                clearKeepingHistory(key, hist);
            } else {
                touch(key);
                return get(key);
            }
        }
        LocalDateTime exp = LocalDateTime.now().plusHours(TTL_HOURS);
        jdbc.update("""
                INSERT INTO robot_session (session_key, staff_id, open_conversation_id, conversation_type,
                  state, intent_code, slots_json, expire_at, is_active)
                VALUES (?, ?, ?, ?, ?, '', '{}', ?, 1)
                ON DUPLICATE KEY UPDATE is_active = 1, expire_at = VALUES(expire_at), update_time = NOW()
                """, key, blank(staffId), blank(conversationId), blank(conversationType),
                RobotProductRules.STATE_IDLE, exp);
        return get(key);
    }

    public Session get(String sessionKey) {
        if (!StringUtils.hasText(sessionKey)) {
            return null;
        }
        List<Session> list = jdbc.query("""
                SELECT id, session_key, staff_id, open_conversation_id, conversation_type, state, intent_code,
                       slots_json, expire_at
                FROM robot_session WHERE session_key = ? AND is_active = 1 LIMIT 1
                """, (rs, i) -> mapRow(rs.getLong("id"), rs.getString("session_key"), rs.getString("staff_id"),
                rs.getString("open_conversation_id"), rs.getString("conversation_type"), rs.getString("state"),
                rs.getString("intent_code"), rs.getString("slots_json"),
                rs.getTimestamp("expire_at") == null ? null : rs.getTimestamp("expire_at").toLocalDateTime()), sessionKey);
        return list.isEmpty() ? null : list.getFirst();
    }

    public void save(String sessionKey, String state, String intentCode, Map<String, Object> slots) {
        LocalDateTime exp = LocalDateTime.now().plusHours(TTL_HOURS);
        String json = writeSlots(slots);
        jdbc.update("""
                UPDATE robot_session SET state = ?, intent_code = ?, slots_json = ?, expire_at = ?, update_time = NOW()
                WHERE session_key = ? AND is_active = 1
                """, blank(state), blank(intentCode), json, exp, sessionKey);
    }

    public void touch(String sessionKey) {
        jdbc.update("""
                UPDATE robot_session SET expire_at = ?, update_time = NOW()
                WHERE session_key = ? AND is_active = 1
                """, LocalDateTime.now().plusHours(TTL_HOURS), sessionKey);
    }

    /** 结束业务多轮，但保留近期对话历史。 */
    public void clear(String sessionKey) {
        Session s = get(sessionKey);
        Map<String, Object> slots = new HashMap<>();
        if (s != null) {
            List<HistoryTurn> hist = historyOf(s);
            if (!hist.isEmpty()) {
                slots.put(RobotProductRules.SLOT_HISTORY, toHistoryMaps(hist));
            }
        }
        save(sessionKey, RobotProductRules.STATE_IDLE, "", slots);
    }

    private void clearKeepingHistory(String sessionKey, List<HistoryTurn> hist) {
        Map<String, Object> slots = new HashMap<>();
        if (hist != null && !hist.isEmpty()) {
            slots.put(RobotProductRules.SLOT_HISTORY, toHistoryMaps(hist));
        }
        save(sessionKey, RobotProductRules.STATE_IDLE, "", slots);
    }

    public void forceEnd(Long id) {
        jdbc.update("""
                UPDATE robot_session SET state = ?, intent_code = '', slots_json = '{}',
                  expire_at = NOW(), update_time = NOW()
                WHERE id = ? AND is_active = 1
                """, RobotProductRules.STATE_IDLE, id);
    }

    public void appendTurn(String sessionKey, String role, String text, String intent) {
        Session s = get(sessionKey);
        if (s == null) {
            return;
        }
        Map<String, Object> slots = s.slots() == null ? new HashMap<>() : new HashMap<>(s.slots());
        List<HistoryTurn> hist = historyOf(s);
        hist.add(new HistoryTurn(
                role == null ? "user" : role,
                text == null ? "" : cut(text, 500),
                intent == null ? "" : intent,
                LocalDateTime.now().toString()));
        while (hist.size() > RobotProductRules.HISTORY_MAX_TURNS) {
            hist.removeFirst();
        }
        slots.put(RobotProductRules.SLOT_HISTORY, toHistoryMaps(hist));
        save(sessionKey, s.state(), s.intentCode(), slots);
    }

    public List<HistoryTurn> historyOf(Session session) {
        List<HistoryTurn> out = new ArrayList<>();
        if (session == null || session.slots() == null) {
            return out;
        }
        Object raw = session.slots().get(RobotProductRules.SLOT_HISTORY);
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add(new HistoryTurn(
                        str(m.get("role")),
                        str(m.get("text")),
                        str(m.get("intent")),
                        str(m.get("at"))));
            }
        }
        return out;
    }

    /** 供 Router / 追问：最近若干轮纯文本。 */
    public String historyPlain(Session session, int maxTurns) {
        List<HistoryTurn> hist = historyOf(session);
        if (hist.isEmpty()) {
            return "";
        }
        int from = Math.max(0, hist.size() - Math.max(1, maxTurns));
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < hist.size(); i++) {
            HistoryTurn t = hist.get(i);
            sb.append(t.role()).append(": ").append(t.text()).append('\n');
        }
        return sb.toString().trim();
    }

    private List<Map<String, String>> toHistoryMaps(List<HistoryTurn> hist) {
        List<Map<String, String>> list = new ArrayList<>();
        for (HistoryTurn t : hist) {
            Map<String, String> m = new HashMap<>();
            m.put("role", t.role());
            m.put("text", t.text());
            m.put("intent", t.intent());
            m.put("at", t.at());
            list.add(m);
        }
        return list;
    }

    private String writeSlots(Map<String, Object> slots) {
        try {
            return objectMapper.writeValueAsString(slots == null ? Map.of() : slots);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Session mapRow(Long id, String key, String staff, String cid, String type, String state,
                           String intent, String slotsJson, LocalDateTime exp) {
        Map<String, Object> slots = new HashMap<>();
        if (StringUtils.hasText(slotsJson)) {
            try {
                slots = objectMapper.readValue(slotsJson, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                slots = new HashMap<>();
            }
        }
        return new Session(id, key, staff, cid, type, state, intent, slots, exp);
    }

    private static String blank(String s) {
        return s == null ? "" : s.trim();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
