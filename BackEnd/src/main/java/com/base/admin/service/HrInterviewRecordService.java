package com.base.admin.service;

import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrInterviewRecordDTO;
import com.base.admin.domain.dto.HrInterviewVerdictDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.vo.HrInterviewReviewVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private final HrHirePipelineService hirePipeline;

    public List<Map<String, Object>> list(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        StringBuilder sql = new StringBuilder("""
                SELECT rec.id, rec.application_id, rec.requisition_id, rec.invite_id, rec.round_no,
                       rec.interviewer_user_id, u.nickname interviewer_name, rec.conclusion, rec.comment, rec.interviewed_at,
                       c.display_name, r.job_name, f.file_name, a.current_stage,
                       CASE WHEN v.id IS NULL THEN 0 ELSE 1 END has_verdict
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = rec.requisition_id
                LEFT JOIN sys_user u ON u.user_id = rec.interviewer_user_id
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                LEFT JOIN hr_interview_verdict v ON v.application_id = rec.application_id AND v.round_no = rec.round_no AND v.is_active = 1
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
        java.util.LinkedHashSet<Long> inviteIds = new java.util.LinkedHashSet<>();
        Long applicationId = dto.getApplicationId();
        Integer roundNo = dto.getRoundNo();
        assertRoundOpen(applicationId, roundNo);
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
            saveOne(one);
            if (one.getInviteId() != null) {
                inviteIds.add(one.getInviteId());
            }
        }
        String outcome = "NONE";
        if (inviteIds.isEmpty()) {
            outcome = applyConsensus(applicationId, requisitionIdOf(applicationId), roundNo, dto.getConclusion().trim().toUpperCase(), "");
        } else {
            for (Long inviteId : inviteIds) {
                outcome = stronger(outcome, syncStage(applicationId, roundNo, inviteId));
            }
        }
        String prefix = ids.size() == 1 ? "" : "已为 " + ids.size() + " 名面试官保存面试评价。";
        return outcomeMessage(prefix, outcome, roundNo);
    }

    public List<HrInterviewReviewVO> reviews(Long applicationId) {
        List<HrInterviewReviewVO> rows = new ArrayList<>();
        jdbc.query("""
                SELECT rec.round_no, u.nickname interviewer_name, rec.conclusion, rec.comment, rec.interviewed_at
                FROM hr_interview_record rec
                LEFT JOIN sys_user u ON u.user_id = rec.interviewer_user_id
                WHERE rec.application_id = ? AND rec.is_active = 1
                ORDER BY rec.round_no, rec.id
                """, rs -> {
            HrInterviewReviewVO vo = new HrInterviewReviewVO();
            vo.setKind("INTERVIEW");
            vo.setRoundNo(rs.getInt("round_no"));
            vo.setRoundName(ROUND_NAME.getOrDefault(vo.getRoundNo(), vo.getRoundNo() + "面"));
            vo.setInterviewerName(rs.getString("interviewer_name"));
            vo.setConclusion(rs.getString("conclusion"));
            vo.setComment(rs.getString("comment"));
            vo.setInterviewedAt(rs.getTimestamp("interviewed_at") == null ? null : rs.getTimestamp("interviewed_at").toLocalDateTime());
            rows.add(vo);
        }, applicationId);
        jdbc.query("""
                SELECT v.round_no, u.nickname interviewer_name, v.conclusion, v.comment, v.update_time
                FROM hr_interview_verdict v
                LEFT JOIN sys_user u ON u.user_id = v.decided_by
                WHERE v.application_id = ? AND v.is_active = 1
                ORDER BY v.round_no
                """, rs -> {
            HrInterviewReviewVO vo = new HrInterviewReviewVO();
            vo.setKind("JOINT");
            vo.setRoundNo(rs.getInt("round_no"));
            vo.setRoundName(ROUND_NAME.getOrDefault(vo.getRoundNo(), vo.getRoundNo() + "面"));
            vo.setInterviewerName(rs.getString("interviewer_name"));
            vo.setConclusion(rs.getString("conclusion"));
            vo.setComment(rs.getString("comment"));
            vo.setInterviewedAt(rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime());
            rows.add(vo);
        }, applicationId);
        return rows;
    }

    @Transactional
    public String saveVerdict(HrInterviewVerdictDTO dto) {
        String conclusion = dto.getConclusion() == null ? "" : dto.getConclusion().trim().toUpperCase();
        if (!CONCLUSIONS.contains(conclusion)) {
            throw new BusinessException("面试结论只能是通过、未通过或待定");
        }
        String current = jdbc.query("SELECT current_stage FROM hr_application WHERE id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, dto.getApplicationId());
        if (current == null) {
            throw new BusinessException("候选人不存在");
        }
        String expected = "R" + dto.getRoundNo() + "_DISPUTE";
        boolean dispute = expected.equals(current);
        if (!dispute && !conclusionsDiffer(dto.getApplicationId(), dto.getRoundNo())) {
            throw new BusinessException("只有同一轮次多名面试官结论不一致时才能提交联合评价");
        }
        if (!dispute && verdictExists(dto.getApplicationId(), dto.getRoundNo())) {
            throw new BusinessException("这一轮已经提交过联合评价");
        }
        jdbc.update("""
                INSERT INTO hr_interview_verdict (application_id, round_no, conclusion, comment, decided_by, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE conclusion = VALUES(conclusion), comment = VALUES(comment),
                  decided_by = VALUES(decided_by), is_active = 1
                """, dto.getApplicationId(), dto.getRoundNo(), conclusion, empty(dto.getComment()),
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUsername());
        Long requisitionId = jdbc.query("SELECT requisition_id FROM hr_application WHERE id = ? AND is_active = 1",
                rs -> rs.next() && rs.getObject(1) != null ? rs.getLong(1) : null, dto.getApplicationId());
        return outcomeMessage("", applyConsensus(dto.getApplicationId(), requisitionId, dto.getRoundNo(), conclusion, ""), dto.getRoundNo());
    }

    private boolean conclusionsDiffer(Long applicationId, Integer roundNo) {
        List<String> conclusions = jdbc.query("""
                SELECT conclusion FROM hr_interview_record
                WHERE application_id = ? AND round_no = ? AND is_active = 1
                """, (rs, rowNum) -> rs.getString(1), applicationId, roundNo);
        if (conclusions.size() < 2) {
            return false;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String conclusion : conclusions) {
            if (conclusion == null || conclusion.isBlank()) {
                return false;
            }
            unique.add(conclusion.trim().toUpperCase());
        }
        return unique.size() > 1;
    }

    private boolean verdictExists(Long applicationId, Integer roundNo) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_verdict
                WHERE application_id = ? AND round_no = ? AND is_active = 1
                """, Integer.class, applicationId, roundNo);
        return count != null && count > 0;
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

    private void saveOne(HrInterviewRecordDTO dto) {
        String conclusion = dto.getConclusion() == null ? "" : dto.getConclusion().trim().toUpperCase();
        if (!CONCLUSIONS.contains(conclusion)) {
            throw new BusinessException("面试结论只能是通过、未通过或待定");
        }
        assertInterviewEnded(dto.getInviteId());
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
    }

    public void delete(Long id) {
        int updated = jdbc.update("UPDATE hr_interview_record SET is_active = 0 WHERE id = ? AND is_active = 1", id);
        if (updated == 0) {
            throw new BusinessException("面试记录不存在");
        }
    }

    @Transactional
    public void deleteOwn(Long id) {
        Map<String, Object> record = jdbc.query("""
                SELECT interviewer_user_id, application_id, round_no
                FROM hr_interview_record WHERE id = ? AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("interviewerUserId", rs.getLong("interviewer_user_id"));
            row.put("applicationId", rs.getLong("application_id"));
            row.put("roundNo", rs.getInt("round_no"));
            return row;
        }, id);
        if (record == null) {
            throw new BusinessException("面试记录不存在");
        }
        if (!SecurityUtils.getCurrentUserId().equals(record.get("interviewerUserId"))) {
            throw new BusinessException("只能删除自己的面试评价");
        }
        assertRoundOpen((Long) record.get("applicationId"), (Integer) record.get("roundNo"));
        delete(id);
    }

    public void deleteBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的面试记录");
        }
        for (Long id : ids) {
            if (id != null) {
                delete(id);
            }
        }
    }

    public static boolean roundConcluded(String currentStage, int roundNo) {
        if (currentStage == null || roundNo <= 0) {
            return false;
        }
        int concluded = switch (currentStage) {
            case "FIRST_PENDING", "FIRST_ROUND", "FIRST_FAIL", "R1_DISPUTE" -> 1;
            case "R2_PENDING", "SECOND_ROUND", "R2_FAIL", "R2_DISPUTE" -> 2;
            case "R3_PASS", "R3_PENDING", "R3_DISPUTE", "R3_FAIL" -> 3;
            case "R4_PASS", "R4_PENDING", "R4_DISPUTE", "R4_FAIL" -> 4;
            case "R5_PASS", "R5_PENDING", "R5_DISPUTE", "R5_FAIL" -> 5;
            case "FINAL", "SALARY", "BG_COLLECT", "BG_CHECK", "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD",
                 "OFFER_SENT", "OFFER_ACCEPTED", "CANDIDATE_REJECT", "ONBOARDED" -> 99;
            default -> 0;
        };
        return concluded >= roundNo;
    }

    private void assertRoundOpen(Long applicationId, Integer roundNo) {
        if (applicationId == null || roundNo == null) {
            return;
        }
        String stage = jdbc.query("SELECT current_stage FROM hr_application WHERE id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, applicationId);
        if (roundConcluded(stage, roundNo)) {
            throw new BusinessException("候选人已进入下一阶段，不能再修改或删除评价");
        }
    }

    private String syncStage(Long applicationId, Integer roundNo, Long inviteId) {
        if (applicationId == null || roundNo == null || inviteId == null) {
            return "NONE";
        }
        LocalDateTime interviewAt = jdbc.query("""
                SELECT interview_at FROM hr_interview_invite WHERE id = ? AND is_active = 1
                """, rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toLocalDateTime() : null, inviteId);
        List<Long> peerInviteIds = new ArrayList<>();
        java.util.LinkedHashSet<Long> expectedSet = new java.util.LinkedHashSet<>();
        jdbc.query("""
                SELECT id, interviewer_user_id FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND is_active = 1
                  AND status IN ('SUCCESS', 'NO_CALENDAR', 'FAILED')
                  AND interview_at <=> ?
                """, rs -> {
            peerInviteIds.add(rs.getLong("id"));
            if (rs.getObject("interviewer_user_id") != null) {
                expectedSet.add(rs.getLong("interviewer_user_id"));
            }
        }, applicationId, roundNo, interviewAt);
        List<Long> expected = new ArrayList<>(expectedSet);
        if (expected.isEmpty()) {
            return "NONE";
        }
        Map<Long, String> submitted = new java.util.HashMap<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(peerInviteIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(applicationId);
        args.add(roundNo);
        args.addAll(peerInviteIds);
        jdbc.query("""
                SELECT interviewer_user_id, conclusion FROM hr_interview_record
                WHERE application_id = ? AND round_no = ? AND is_active = 1
                  AND conclusion IS NOT NULL AND invite_id IN (%s)
                """.formatted(placeholders), rs -> {
            submitted.put(rs.getLong(1), rs.getString(2));
        }, args.toArray());
        Set<String> conclusions = new LinkedHashSet<>();
        for (Long interviewerId : expected) {
            String conclusion = submitted.get(interviewerId);
            if (conclusion == null || conclusion.isBlank()) {
                return "WAIT";
            }
            conclusions.add(conclusion);
        }
        Long requisitionId = requisitionIdOf(applicationId);
        if (conclusions.size() > 1) {
            applyStage(applicationId, "R" + roundNo + "_DISPUTE");
            return "DISPUTE";
        }
        return applyConsensus(applicationId, requisitionId, roundNo, conclusions.iterator().next(), "");
    }

    private Long requisitionIdOf(Long applicationId) {
        return jdbc.query("SELECT requisition_id FROM hr_application WHERE id = ? AND is_active = 1",
                rs -> rs.next() && rs.getObject(1) != null ? rs.getLong(1) : null, applicationId);
    }

    private static String stronger(String left, String right) {
        List<String> order = List.of("NONE", "WAIT", "PASS", "PENDING", "FAIL", "DISPUTE", "NEXT", "HIRE");
        return order.indexOf(right) > order.indexOf(left) ? right : left;
    }

    private String applyConsensus(Long applicationId, Long requisitionId, Integer roundNo, String conclusion, String prefix) {
        boolean finalRound = requisitionId != null && hirePipeline.isFinalRound(requisitionId, roundNo);
        String stage = stageCode(roundNo, conclusion, finalRound);
        applyStage(applicationId, stage);
        if (!"PASS".equals(conclusion)) {
            return prefix + ("FAIL".equals(conclusion) ? "FAIL" : "PENDING");
        }
        if (requisitionId != null && openNextRoundTask(requisitionId, applicationId, roundNo)) {
            return prefix + "NEXT";
        }
        if (finalRound) {
            hirePipeline.openAfterFinal(requisitionId, applicationId);
            return prefix + "HIRE";
        }
        return prefix + "PASS";
    }

    private String outcomeMessage(String prefix, String outcome, Integer roundNo) {
        String roundName = ROUND_NAME.getOrDefault(roundNo, (roundNo == null ? "" : roundNo + "面"));
        if ("HIRE".equals(outcome)) {
            return prefix + "终面已通过，已给招聘需求负责人创建薪资沟通、背调资料收集、背调、体检、待发offer、待入职任务。"
                    + "每项都要上传完成资料，可上传多份。全部完成后才会生成「办理候选人入职」任务。";
        }
        if ("NEXT".equals(outcome)) {
            return (prefix.isEmpty() ? "" : prefix) + nextRoundMessage();
        }
        if ("DISPUTE".equals(outcome)) {
            return prefix + "这场邀约的面试官结论不一致，候选人阶段已更新为" + roundName + "待商榷";
        }
        if ("WAIT".equals(outcome)) {
            return prefix + "评价已保存。这场邀约还有其他面试官未填写评价，暂不更新候选人阶段";
        }
        if ("FAIL".equals(outcome) || "PENDING".equals(outcome) || "PASS".equals(outcome)) {
            String code = stageCode(roundNo, "FAIL".equals(outcome) ? "FAIL" : "PENDING".equals(outcome) ? "PENDING" : "PASS", false);
            return prefix + "评价已保存，候选人阶段已更新为" + stageName(code);
        }
        return prefix.isEmpty() ? "已保存" : prefix.substring(0, prefix.length() - 1);
    }

    private void assertInterviewEnded(Long inviteId) {
        if (inviteId == null) {
            return;
        }
        LocalDateTime at = jdbc.query("SELECT interview_at FROM hr_interview_invite WHERE id = ? AND is_active = 1",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toLocalDateTime() : null, inviteId);
        if (at != null && at.isAfter(LocalDateTime.now())) {
            throw new BusinessException("面试尚未结束，不能评价");
        }
    }

    private void applyStage(Long applicationId, String stageCode) {
        jdbc.update("UPDATE hr_application SET current_stage = ? WHERE id = ? AND is_active = 1", stageCode, applicationId);
        jdbc.update("""
                INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                VALUES (?, ?, ?, 'INTERVIEW', ?, 1)
                ON DUPLICATE KEY UPDATE event_at = VALUES(event_at), is_active = 1
                """, applicationId, stageCode, LocalDateTime.now(), SecurityUtils.getCurrentUsername());
    }

    private String stageCode(Integer roundNo, String conclusion, boolean finalRound) {
        int round = roundNo == null ? 1 : roundNo;
        if ("PASS".equals(conclusion) && finalRound) {
            return "FINAL";
        }
        if (round == 1) {
            return switch (conclusion) {
                case "PASS" -> "FIRST_ROUND";
                case "FAIL" -> "FIRST_FAIL";
                case "PENDING" -> "FIRST_PENDING";
                default -> throw new BusinessException("无法识别的面试结论");
            };
        }
        if (round == 2) {
            return switch (conclusion) {
                case "PASS" -> "SECOND_ROUND";
                case "FAIL" -> "R2_FAIL";
                case "PENDING" -> "R2_PENDING";
                default -> throw new BusinessException("无法识别的面试结论");
            };
        }
        return switch (conclusion) {
            case "PASS" -> "R" + round + "_PASS";
            case "FAIL" -> "R" + round + "_FAIL";
            case "PENDING" -> "R" + round + "_PENDING";
            default -> throw new BusinessException("无法识别的面试结论");
        };
    }

    private String stageName(String code) {
        String name = jdbc.query("SELECT stage_name FROM hr_stage_def WHERE stage_code = ?",
                rs -> rs.next() ? rs.getString(1) : null, code);
        return name == null || name.isBlank() ? code : name;
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
