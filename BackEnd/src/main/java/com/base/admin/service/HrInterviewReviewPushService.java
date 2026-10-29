package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 按面试邀约时间，给面试官发钉钉工作通知催填评价；点击通知直达「我的面试」评价。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrInterviewReviewPushService {

    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalk;
    private final DingTalkProperties dingTalkProperties;

    /** 定时扫描：面试结束后约 30 分钟催评；次日补催一次。 */
    public void scanAndPush() {
        LocalDateTime now = LocalDateTime.now();
        pushWindow(now.minusMinutes(90), now.minusMinutes(25), "AFTER");
        pushWindow(now.minusHours(28), now.minusHours(20), "REMIND");
    }

    private void pushWindow(LocalDateTime from, LocalDateTime to, String kind) {
        java.util.List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT i.id invite_id, i.application_id, i.round_no, i.interviewer_user_id, i.interview_at,
                       a.requisition_id, c.display_name, r.job_name, d.dingtalk_user_id
                FROM hr_interview_invite i
                JOIN hr_application a ON a.id = i.application_id AND a.is_active = 1
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                LEFT JOIN hr_user_dingtalk d ON d.user_id = i.interviewer_user_id AND d.is_active = 1
                WHERE i.is_active = 1 AND i.status IN ('SUCCESS','NO_CALENDAR')
                  AND i.interview_at >= ? AND i.interview_at < ?
                """, from, to);
        for (Map<String, Object> row : rows) {
            tryPush(row, kind);
        }
    }

    private void tryPush(Map<String, Object> row, String kind) {
        Long inviteId = ((Number) row.get("invite_id")).longValue();
        Long interviewerUserId = row.get("interviewer_user_id") == null ? null
                : ((Number) row.get("interviewer_user_id")).longValue();
        if (interviewerUserId == null) {
            return;
        }
        if (alreadyPushed(inviteId, interviewerUserId, kind)) {
            return;
        }
        if (hasReview(row)) {
            logPush(inviteId, interviewerUserId, kind, "SKIP", "已填评价");
            return;
        }
        String dingUserId = row.get("dingtalk_user_id") == null ? "" : String.valueOf(row.get("dingtalk_user_id")).trim();
        if (!StringUtils.hasText(dingUserId)) {
            logPush(inviteId, interviewerUserId, kind, "SKIP", "面试官未绑定钉钉");
            return;
        }
        String link = reviewLink(inviteId);
        if (!StringUtils.hasText(link)) {
            logPush(inviteId, interviewerUserId, kind, "SKIP", "未配置 dingtalk.h5-base-url");
            return;
        }
        String name = String.valueOf(row.get("display_name"));
        String job = row.get("job_name") == null ? "" : String.valueOf(row.get("job_name"));
        int roundNo = ((Number) row.get("round_no")).intValue();
        String title = "REMIND".equals(kind) ? "【补催】请填写面试评价" : "请填写面试评价";
        String markdown = "### " + title + "\n\n"
                + "- 候选人：**" + name + "**\n"
                + "- 岗位：" + job + "\n"
                + "- 轮次：第" + roundNo + "面\n\n"
                + "请尽快填写评价（含「简历是否合格」）。点击下方按钮直达评价。";
        DingTalkCalendarClient.NoticeCall call = dingTalk.notifyActionCard(
                dingUserId, title, markdown, "去评价", link);
        if (call.success()) {
            logPush(inviteId, interviewerUserId, kind, "OK", "已发工作通知");
        } else {
            logPush(inviteId, interviewerUserId, kind, "FAIL", call.message());
        }
    }

    private boolean hasReview(Map<String, Object> row) {
        Long applicationId = ((Number) row.get("application_id")).longValue();
        int roundNo = ((Number) row.get("round_no")).intValue();
        Long interviewerUserId = ((Number) row.get("interviewer_user_id")).longValue();
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_record
                WHERE application_id = ? AND round_no = ? AND interviewer_user_id = ?
                  AND is_active = 1 AND conclusion IS NOT NULL AND TRIM(conclusion) <> ''
                """, Integer.class, applicationId, roundNo, interviewerUserId);
        return n != null && n > 0;
    }

    private boolean alreadyPushed(Long inviteId, Long interviewerUserId, String kind) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_review_push_log
                WHERE invite_id = ? AND interviewer_user_id = ? AND push_kind = ?
                """, Integer.class, inviteId, interviewerUserId, kind);
        return n != null && n > 0;
    }

    private void logPush(Long inviteId, Long interviewerUserId, String kind, String status, String msg) {
        jdbc.update("""
                INSERT INTO hr_interview_review_push_log (invite_id, interviewer_user_id, push_kind, push_status, push_msg)
                VALUES (?, ?, ?, ?, ?)
                """, inviteId, interviewerUserId, kind, status, msg == null ? "" : msg.substring(0, Math.min(500, msg.length())));
    }

    /** 直达我的面试并打开该场评价：/hr/mine?inviteId= */
    private String reviewLink(Long inviteId) {
        String base = dingTalkProperties.getH5BaseUrl();
        if (!StringUtils.hasText(base)) {
            return "";
        }
        String root = base.trim().replaceAll("/+$", "");
        return root + "/hr/mine?inviteId=" + inviteId;
    }
}
