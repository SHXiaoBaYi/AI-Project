package com.base.admin.service;

import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrInterviewRecordDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HrInterviewRecordService {

    private static final Set<String> CONCLUSIONS = Set.of("PASS", "FAIL", "PENDING");
    private static final Map<Integer, String> ROUND_NAME = Map.of(1, "一面", 2, "二面", 3, "三面", 4, "四面", 5, "五面");

    private final JdbcTemplate jdbc;
    private final SysTaskService taskService;

    public List<Map<String, Object>> list(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        StringBuilder sql = new StringBuilder("""
                SELECT rec.id, rec.application_id, rec.requisition_id, rec.invite_id, rec.round_no,
                       rec.interviewer_user_id, u.nickname interviewer_name, rec.conclusion, rec.comment, rec.interviewed_at,
                       c.display_name, r.job_name, f.file_name
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = rec.requisition_id
                LEFT JOIN sys_user u ON u.user_id = rec.interviewer_user_id
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE rec.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (q.getCandidateName() != null && !q.getCandidateName().isBlank()) {
            sql.append(" AND c.display_name LIKE ? ");
            args.add("%" + q.getCandidateName().trim() + "%");
        }
        if (q.getRequisitionId() != null) {
            sql.append(" AND rec.requisition_id = ? ");
            args.add(q.getRequisitionId());
        }
        if (q.getRoundNo() != null) {
            sql.append(" AND rec.round_no = ? ");
            args.add(q.getRoundNo());
        }
        if (q.getInterviewerUserId() != null) {
            sql.append(" AND rec.interviewer_user_id = ? ");
            args.add(q.getInterviewerUserId());
        }
        if (Boolean.TRUE.equals(q.getMine())) {
            sql.append(" AND rec.interviewer_user_id = ? ");
            args.add(SecurityUtils.getCurrentUserId());
        }
        sql.append(" ORDER BY rec.interviewed_at DESC, rec.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public String save(HrInterviewRecordDTO dto) {
        java.util.List<Long> ids = dto.getInterviewerUserIds() == null ? java.util.List.of()
                : dto.getInterviewerUserIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            if (dto.getInterviewerUserId() == null) {
                throw new BusinessException("请选择面试官");
            }
            ids = java.util.List.of(dto.getInterviewerUserId());
        }
        boolean tasked = false;
        for (int i = 0; i < ids.size(); i++) {
            Integer userOk = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM sys_user WHERE user_id = ? AND is_active = 1 AND status = 0",
                    Integer.class, ids.get(i));
            if (userOk == null || userOk == 0) {
                throw new BusinessException("面试官必须是系统用户");
            }
            HrInterviewRecordDTO one = new HrInterviewRecordDTO();
            if (i == 0) {
                one.setId(dto.getId());
            }
            one.setApplicationId(dto.getApplicationId());
            one.setRequisitionId(dto.getRequisitionId());
            one.setRoundNo(dto.getRoundNo());
            one.setInterviewerUserId(ids.get(i));
            one.setInviteId(matchInvite(dto.getApplicationId(), dto.getRoundNo(), ids.get(i), dto.getInviteId()));
            one.setConclusion(dto.getConclusion());
            one.setComment(dto.getComment());
            one.setInterviewedAt(dto.getInterviewedAt());
            tasked = saveOne(one) || tasked;
        }
        if (ids.size() == 1) {
            return tasked ? nextRoundMessage() : "已保存";
        }
        return tasked
                ? "已为 " + ids.size() + " 名面试官保存面试记录。" + nextRoundMessage()
                : "已为 " + ids.size() + " 名面试官保存面试记录";
    }

    private static String nextRoundMessage() {
        return "还有下一面，已给招聘需求负责人创建「待创建面试日程」任务，需要负责人手动完成";
    }

    private Long matchInvite(Long applicationId, Integer roundNo, Long interviewerUserId, Long preferredInviteId) {
        if (preferredInviteId != null) {
            Long owner = jdbc.query("""
                    SELECT interviewer_user_id FROM hr_interview_invite
                    WHERE id = ? AND application_id = ? AND round_no = ? AND is_active = 1
                    """, rs -> rs.next() ? (rs.getObject(1) == null ? null : rs.getLong(1)) : null,
                    preferredInviteId, applicationId, roundNo);
            if (interviewerUserId.equals(owner)) {
                return preferredInviteId;
            }
        }
        return jdbc.query("""
                SELECT id FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND interviewer_user_id = ? AND is_active = 1
                ORDER BY id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, applicationId, roundNo, interviewerUserId);
    }

    private boolean saveOne(HrInterviewRecordDTO dto) {
        String conclusion = dto.getConclusion() == null ? "" : dto.getConclusion().trim().toUpperCase();
        if (!CONCLUSIONS.contains(conclusion)) {
            throw new BusinessException("面试结论只能是通过、未通过或待定");
        }
        Long requisitionId = dto.getRequisitionId();
        if (requisitionId == null) {
            requisitionId = jdbc.query("SELECT requisition_id FROM hr_application WHERE id = ? AND is_active = 1",
                    rs -> rs.next() ? (rs.getObject(1) == null ? null : rs.getLong(1)) : null, dto.getApplicationId());
        }
        if (requisitionId == null) {
            throw new BusinessException("候选人还没有对应的招聘需求");
        }
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO hr_interview_record (application_id, requisition_id, invite_id, round_no, interviewer_user_id, conclusion, comment, interviewed_at, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE requisition_id = VALUES(requisition_id), invite_id = VALUES(invite_id),
                      conclusion = VALUES(conclusion), comment = VALUES(comment), interviewed_at = VALUES(interviewed_at), is_active = 1
                    """, dto.getApplicationId(), requisitionId, dto.getInviteId(), dto.getRoundNo(), dto.getInterviewerUserId(),
                    conclusion, empty(dto.getComment()), dto.getInterviewedAt(), SecurityUtils.getCurrentUsername());
        } else {
            int updated = jdbc.update("""
                    UPDATE hr_interview_record
                    SET application_id = ?, requisition_id = ?, invite_id = ?, round_no = ?, interviewer_user_id = ?,
                        conclusion = ?, comment = ?, interviewed_at = ?
                    WHERE id = ? AND is_active = 1
                    """, dto.getApplicationId(), requisitionId, dto.getInviteId(), dto.getRoundNo(), dto.getInterviewerUserId(),
                    conclusion, empty(dto.getComment()), dto.getInterviewedAt(), dto.getId());
            if (updated == 0) {
                throw new BusinessException("面试记录不存在");
            }
        }
        return "PASS".equals(conclusion) && openNextRoundTask(requisitionId, dto.getApplicationId(), dto.getRoundNo());
    }

    public void delete(Long id) {
        int updated = jdbc.update("UPDATE hr_interview_record SET is_active = 0 WHERE id = ? AND is_active = 1", id);
        if (updated == 0) {
            throw new BusinessException("面试记录不存在");
        }
    }

    private boolean openNextRoundTask(Long requisitionId, Long applicationId, Integer roundNo) {
        Integer maxRound = jdbc.query("SELECT MAX(round_no) FROM hr_requisition_round WHERE requisition_id = ? AND is_active = 1",
                rs -> rs.next() ? (rs.getObject(1) == null ? null : rs.getInt(1)) : null, requisitionId);
        if (maxRound == null || roundNo == null || roundNo >= maxRound) {
            return false;
        }
        int next = roundNo + 1;
        String marker = "nextRound:" + next;
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM sys_task
                WHERE biz_type = 'hr_next_interview' AND biz_id = ? AND is_active = 1
                  AND status NOT IN ('已完成', '已取消') AND remark LIKE ?
                """, Integer.class, applicationId, marker + "%");
        if (exists != null && exists > 0) {
            return true;
        }
        List<Long> owners = jdbc.query("""
                SELECT user_id FROM hr_requisition_owner
                WHERE requisition_id = ? AND is_active = 1 AND user_id IS NOT NULL
                ORDER BY sort_no, id
                """, (rs, row) -> rs.getLong(1), requisitionId);
        if (owners.isEmpty()) {
            return false;
        }
        Map<String, Object> person = jdbc.queryForMap("""
                SELECT c.display_name, COALESCE(r.job_name, '') job_name
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.id = ?
                """, applicationId);
        String roundName = ROUND_NAME.getOrDefault(next, next + "面");
        SysTaskDTO task = new SysTaskDTO();
        task.setTitle("【待创建面试日程】" + person.get("display_name") + " - " + person.get("job_name") + " " + roundName);
        task.setContent("候选人已通过上一轮。请为「" + roundName + "」创建面试日程。此任务需招聘需求负责人手动完成。");
        task.setTaskType("待创建面试日程");
        task.setPriority(3);
        task.setStatus("未开始");
        task.setProgress(0);
        task.setOwnerUserId(owners.get(0));
        task.setAssigneeUserIds(owners);
        task.setBizType("hr_next_interview");
        task.setBizId(applicationId);
        task.setBizTitle(String.valueOf(person.get("job_name")));
        task.setRemark(marker + " application:" + applicationId);
        taskService.create(task);
        return true;
    }

    private static String empty(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
