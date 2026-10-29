package com.base.admin.service;

import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 把邀约、到面这类动作写回候选人当前阶段。二面及以后的邀约不退回「已邀约」。
 */
@Service
@RequiredArgsConstructor
public class HrApplicationStageSync {

    private final JdbcTemplate jdbc;

    /** 一面邀约已建成（含未建钉钉日程）时，把还停在邀约之前的候选人推进到已邀约。 */
    public void markRoundOneInvited(Long applicationId) {
        if (applicationId == null) {
            return;
        }
        String current = currentStage(applicationId);
        if (current == null || atOrAfterInvited(current)) {
            return;
        }
        jdbc.update("UPDATE hr_application SET current_stage = 'INVITED' WHERE id = ? AND is_active = 1", applicationId);
    }

    /** 一面已没有有效邀约，且阶段还停在已邀约时，退回电话通过。 */
    public void revertRoundOneInvite(Long applicationId) {
        if (applicationId == null) {
            return;
        }
        Integer left = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_invite
                WHERE application_id = ? AND round_no = 1 AND is_active = 1
                  AND status IN ('SUCCESS', 'NO_CALENDAR')
                """, Integer.class, applicationId);
        if (left != null && left > 0) {
            return;
        }
        jdbc.update("""
                UPDATE hr_stage_event SET is_active = 0
                WHERE application_id = ? AND stage_code = 'INVITED' AND source_sheet = 'INVITE'
                """, applicationId);
        String current = currentStage(applicationId);
        if (!"INVITED".equals(current)) {
            return;
        }
        jdbc.update("UPDATE hr_application SET current_stage = 'PHONE_PASS' WHERE id = ? AND is_active = 1", applicationId);
    }

    /** 一面邀约时间已到、还没有评价时，从已邀约改为已到面。 */
    public int promoteShowUpDue() {
        List<Long> ids = jdbc.query("""
                SELECT a.id
                FROM hr_application a
                WHERE a.is_active = 1 AND a.current_stage = 'INVITED'
                  AND EXISTS (
                    SELECT 1 FROM hr_interview_invite i
                    WHERE i.application_id = a.id AND i.is_active = 1 AND i.round_no = 1
                      AND i.status IN ('SUCCESS', 'NO_CALENDAR')
                      AND i.interview_at <= NOW()
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM hr_interview_record rec
                    WHERE rec.application_id = a.id AND rec.round_no = 1 AND rec.is_active = 1
                      AND rec.conclusion IS NOT NULL AND TRIM(rec.conclusion) <> ''
                  )
                """, (rs, rowNum) -> rs.getLong(1));
        String user = SecurityUtils.getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            jdbc.update("UPDATE hr_application SET current_stage = 'SHOW_UP' WHERE id = ? AND is_active = 1 AND current_stage = 'INVITED'", id);
            jdbc.update("""
                    INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                    VALUES (?, 'SHOW_UP', ?, 'SHOW', ?, 1)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """, id, now, user);
        }
        return ids.size();
    }

    private String currentStage(Long applicationId) {
        return jdbc.query("SELECT current_stage FROM hr_application WHERE id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, applicationId);
    }

    static boolean atOrAfterInvited(String stage) {
        if (!StringUtils.hasText(stage)) {
            return false;
        }
        String code = stage.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "INVITED", "SHOW_UP",
                    "FIRST_PENDING", "FIRST_ROUND", "FIRST_FAIL", "R1_DISPUTE",
                    "R2_PENDING", "SECOND_ROUND", "R2_FAIL", "R2_DISPUTE",
                    "FINAL", "SALARY", "BG_COLLECT", "BG_CHECK", "MEDICAL",
                    "OFFER_PENDING", "PENDING_ONBOARD", "OFFER_SENT", "OFFER_ACCEPTED",
                    "ACCEPTED", "CANDIDATE_REJECT", "ONBOARDED" -> true;
            default -> code.startsWith("R") && (code.endsWith("_PASS") || code.endsWith("_PENDING")
                    || code.endsWith("_DISPUTE") || code.endsWith("_FAIL"));
        };
    }
}
