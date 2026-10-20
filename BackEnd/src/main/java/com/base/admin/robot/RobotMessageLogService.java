package com.base.admin.robot;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RobotMessageLogService {

    private final JdbcTemplate jdbc;

    public void logIn(String staffId, String conversationId, String conversationType,
                      String intentCode, String content) {
        insert("IN", staffId, conversationId, conversationType, intentCode, content, true, "");
    }

    public void logOut(String staffId, String conversationId, String conversationType,
                       String intentCode, String content, boolean success, String error) {
        insert("OUT", staffId, conversationId, conversationType, intentCode, content, success, error);
    }

    private void insert(String direction, String staffId, String conversationId, String conversationType,
                        String intentCode, String content, boolean success, String error) {
        String c = content == null ? "" : content;
        if (c.length() > 1900) {
            c = c.substring(0, 1900);
        }
        String e = error == null ? "" : error;
        if (e.length() > 480) {
            e = e.substring(0, 480);
        }
        jdbc.update("""
                INSERT INTO robot_message_log (direction, staff_id, open_conversation_id, conversation_type,
                  intent_code, content, success, error_msg)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, direction,
                staffId == null ? "" : staffId,
                conversationId == null ? "" : conversationId,
                conversationType == null ? "1" : conversationType,
                intentCode == null ? "" : intentCode,
                c, success ? 1 : 0, e);
    }
}
