package com.base.admin.robot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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

    public static String sessionKey(String conversationType, String conversationId, String staffId) {
        String staff = staffId == null ? "" : staffId.trim();
        if ("2".equals(conversationType) && StringUtils.hasText(conversationId)) {
            return "g:" + conversationId.trim() + ":" + staff;
        }
        return "dm:" + staff;
    }

    public Session getOrCreate(String conversationType, String conversationId, String staffId) {
        String key = sessionKey(conversationType, conversationId, staffId);
        List<Session> list = jdbc.query("""
                SELECT id, session_key, staff_id, open_conversation_id, conversation_type, state, intent_code,
                       slots_json, expire_at
                FROM robot_session WHERE session_key = ? AND is_active = 1 LIMIT 1
                """, (rs, i) -> mapRow(rs.getLong("id"), rs.getString("session_key"), rs.getString("staff_id"),
                rs.getString("open_conversation_id"), rs.getString("conversation_type"), rs.getString("state"),
                rs.getString("intent_code"), rs.getString("slots_json"),
                rs.getTimestamp("expire_at") == null ? null : rs.getTimestamp("expire_at").toLocalDateTime()), key);
        if (!list.isEmpty()) {
            Session s = list.getFirst();
            if (s.expireAt() != null && s.expireAt().isBefore(LocalDateTime.now())) {
                clear(key);
            } else {
                return s;
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
        String json;
        try {
            json = objectMapper.writeValueAsString(slots == null ? Map.of() : slots);
        } catch (Exception ex) {
            json = "{}";
        }
        jdbc.update("""
                UPDATE robot_session SET state = ?, intent_code = ?, slots_json = ?, expire_at = ?, update_time = NOW()
                WHERE session_key = ? AND is_active = 1
                """, blank(state), blank(intentCode), json, exp, sessionKey);
    }

    public void clear(String sessionKey) {
        jdbc.update("""
                UPDATE robot_session SET state = ?, intent_code = '', slots_json = '{}',
                  expire_at = ?, update_time = NOW()
                WHERE session_key = ? AND is_active = 1
                """, RobotProductRules.STATE_IDLE, LocalDateTime.now().plusHours(TTL_HOURS), sessionKey);
    }

    public void forceEnd(Long id) {
        jdbc.update("""
                UPDATE robot_session SET state = ?, intent_code = '', slots_json = '{}',
                  expire_at = NOW(), update_time = NOW()
                WHERE id = ? AND is_active = 1
                """, RobotProductRules.STATE_IDLE, id);
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
}
