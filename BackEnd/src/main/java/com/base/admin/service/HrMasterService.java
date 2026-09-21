package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrBoardViewDTO;
import com.base.admin.domain.dto.HrDepartmentDTO;
import com.base.admin.domain.dto.HrApplicationDTO;
import com.base.admin.domain.dto.HrDingTalkBindDTO;
import com.base.admin.domain.dto.HrRequisitionDTO;
import com.base.admin.domain.dto.HrFailReasonDTO;
import com.base.admin.domain.dto.HrTargetOptionDTO;
import com.base.admin.domain.dto.HrSchoolQueryDTO;
import com.base.admin.domain.vo.HrDingTalkIdentityVO;
import com.base.admin.domain.vo.HrSchoolVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HrMasterService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final HrDataScope dataScope;
    private final DingTalkCalendarClient dingTalk;
    private final FileStorageService fileStorage;

    public List<Map<String, Object>> requisitions(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.job_name, r.job_desc, r.status, r.location_code, r.headcount, r.target_text, r.priority,
                       r.received_date, r.onboard_date, r.dept_id, d.name dept_name,
                       (SELECT GROUP_CONCAT(COALESCE(u.nickname, o.alias) ORDER BY o.sort_no SEPARATOR '、')
                        FROM hr_requisition_owner o
                        LEFT JOIN sys_user u ON u.user_id = o.user_id
                        WHERE o.requisition_id = r.id AND o.is_active = 1) owner_names,
                       (SELECT GROUP_CONCAT(o.user_id ORDER BY o.sort_no)
                        FROM hr_requisition_owner o
                        WHERE o.requisition_id = r.id AND o.is_active = 1 AND o.user_id IS NOT NULL) owner_user_ids,
                       (SELECT GROUP_CONCAT(CONCAT(rr.round_no, ':', IFNULL((
                            SELECT GROUP_CONCAT(i.interviewer_user_id ORDER BY i.id SEPARATOR '|')
                            FROM hr_requisition_round_interviewer i
                            WHERE i.requisition_id = rr.requisition_id AND i.round_no = rr.round_no AND i.is_active = 1
                        ), '')) ORDER BY rr.round_no SEPARATOR ',')
                        FROM hr_requisition_round rr
                        WHERE rr.requisition_id = r.id AND rr.is_active = 1) interview_rounds,
                       (SELECT GROUP_CONCAT(CONCAT(CASE rr.round_no WHEN 1 THEN '一面' WHEN 2 THEN '二面' WHEN 3 THEN '三面' WHEN 4 THEN '四面' ELSE '五面' END, ' ', IFNULL((
                            SELECT GROUP_CONCAT(u.nickname ORDER BY i.id SEPARATOR '、')
                            FROM hr_requisition_round_interviewer i
                            LEFT JOIN sys_user u ON u.user_id = i.interviewer_user_id
                            WHERE i.requisition_id = rr.requisition_id AND i.round_no = rr.round_no AND i.is_active = 1
                        ), '')) ORDER BY rr.round_no SEPARATOR '；')
                        FROM hr_requisition_round rr
                        WHERE rr.requisition_id = r.id AND rr.is_active = 1) interview_flow
                FROM hr_requisition r
                LEFT JOIN hr_department d ON d.id = r.dept_id
                WHERE r.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            sql.append(" AND r.status = ? ");
            args.add(q.getStatus());
        }
        if (q.getLocationCode() != null && !q.getLocationCode().isBlank()) {
            sql.append(" AND r.location_code = ? ");
            args.add(q.getLocationCode());
        }
        if (q.getDeptId() != null) {
            sql.append(" AND r.dept_id IN (SELECT id FROM hr_department WHERE is_active = 1 AND (id = ? OR CONCAT(',', ancestors, ',') LIKE CONCAT('%,', ?, ',%'))) ");
            args.add(q.getDeptId());
            args.add(String.valueOf(q.getDeptId()));
        }
        if (q.getOwnerUserId() != null) {
            sql.append(" AND r.id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(q.getOwnerUserId());
        }
        if (q.getPriority() != null) {
            sql.append(" AND r.priority = ? ");
            args.add(q.getPriority());
        }
        if (q.getJobName() != null && !q.getJobName().isBlank()) {
            sql.append(" AND r.job_name LIKE ? ");
            args.add("%" + q.getJobName().trim() + "%");
        }
        if (q.getTargetText() != null && !q.getTargetText().isBlank()) {
            sql.append(" AND TRIM(r.target_text) = ? ");
            args.add(q.getTargetText().trim());
        }
        dataScope.apply(sql, args, "r", null);
        sql.append(" ORDER BY r.received_date DESC, r.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public void saveRequisition(HrRequisitionDTO dto) {
        int headcount = dto.getHeadcount() == null || dto.getHeadcount() < 1 ? 1 : dto.getHeadcount();
        String target = dto.getTargetText() == null ? "" : dto.getTargetText().trim();
        if (target.isEmpty()) {
            throw new BusinessException("请选择目标到岗");
        }
        Integer targetOk = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_target_option WHERE name = ? AND is_active = 1", Integer.class, target);
        if (targetOk == null || targetOk == 0) {
            throw new BusinessException("目标到岗不在基础配置中，请先在「目标到岗」里维护");
        }
        if (dto.getPriority() != null && (dto.getPriority() < 1 || dto.getPriority() > 3)) {
            throw new BusinessException("优先级只能是紧急、优先或常规");
        }
        Long requisitionId = dto.getId();
        if (requisitionId == null) {
            jdbc.update("""
                    INSERT INTO hr_requisition (job_name, job_desc, status, location_code, dept_id, headcount, target_text, priority, received_date, onboard_date, create_by, is_active)
                    VALUES (?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getJobName().trim(), emptyToNull(dto.getJobDesc()), dto.getLocationCode(), dto.getDeptId(),
                    headcount, target, dto.getPriority(), dto.getReceivedDate(), dto.getOnboardDate(),
                    SecurityUtils.getCurrentUsername());
            requisitionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            int updated = jdbc.update("""
                    UPDATE hr_requisition
                    SET job_name = ?, job_desc = ?, location_code = ?, dept_id = ?, headcount = ?, target_text = ?,
                        priority = ?, received_date = ?, onboard_date = ?
                    WHERE id = ? AND is_active = 1
                    """, dto.getJobName().trim(), emptyToNull(dto.getJobDesc()), dto.getLocationCode(), dto.getDeptId(),
                    headcount, target, dto.getPriority(), dto.getReceivedDate(), dto.getOnboardDate(), requisitionId);
            if (updated == 0) {
                throw new BusinessException("需求不存在");
            }
        }
        replaceOwners(requisitionId, dto.getOwnerUserIds());
        replaceRounds(requisitionId, dto.getInterviewRounds());
    }

    public void changeRequisitionStatus(Long id, String status) {
        if (!java.util.Set.of("OPEN", "PAUSED", "ARCHIVED", "DONE", "STOPPED").contains(status)) {
            throw new BusinessException("不支持的需求状态");
        }
        int updated = jdbc.update("UPDATE hr_requisition SET status = ? WHERE id = ? AND is_active = 1", status, id);
        if (updated == 0) {
            throw new BusinessException("需求不存在");
        }
    }

    public void deleteRequisition(Long id) {
        int updated = jdbc.update("UPDATE hr_requisition SET is_active = 0 WHERE id = ? AND is_active = 1", id);
        if (updated == 0) {
            throw new BusinessException("需求不存在");
        }
    }

    public void deleteRequisitionBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的招聘需求");
        }
        for (Long id : ids) {
            if (id != null) {
                deleteRequisition(id);
            }
        }
    }

    private void replaceOwners(Long requisitionId, List<Long> ownerUserIds) {
        jdbc.update("UPDATE hr_requisition_owner SET is_active = 0 WHERE requisition_id = ? AND user_id IS NOT NULL", requisitionId);
        if (ownerUserIds == null) {
            return;
        }
        int sort = 0;
        for (Long userId : ownerUserIds) {
            if (userId == null) {
                continue;
            }
            String nickname = jdbc.query("SELECT nickname FROM sys_user WHERE user_id = ? AND is_active = 1",
                    rs -> rs.next() ? rs.getString(1) : null, userId);
            if (nickname == null) {
                throw new BusinessException("负责人不存在");
            }
            jdbc.update("""
                    INSERT INTO hr_requisition_owner (requisition_id, alias, user_id, sort_no, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), sort_no = VALUES(sort_no), is_active = 1
                    """, requisitionId, "u:" + userId, userId, sort++, SecurityUtils.getCurrentUsername());
        }
    }

    private void replaceRounds(Long requisitionId, List<HrRequisitionDTO.InterviewRound> rounds) {
        List<HrRequisitionDTO.InterviewRound> items = rounds == null ? List.of() : new ArrayList<>(rounds);
        items.sort(Comparator.comparing(item -> item.getRoundNo() == null ? 99 : item.getRoundNo()));
        if (items.isEmpty()) {
            throw new BusinessException("请设置需要几面，并从一面开始指定面试官");
        }
        if (items.size() > 5) {
            throw new BusinessException("面试最多五面");
        }
        int expected = 1;
        for (HrRequisitionDTO.InterviewRound item : items) {
            if (item.getRoundNo() == null || item.getRoundNo() != expected
                    || item.getInterviewerUserIds() == null || item.getInterviewerUserIds().isEmpty()) {
                throw new BusinessException("面试流程要从一面开始连续设置，每一面至少指定一名面试官");
            }
            expected++;
        }
        jdbc.update("UPDATE hr_requisition_round SET is_active = 0 WHERE requisition_id = ?", requisitionId);
        jdbc.update("UPDATE hr_requisition_round_interviewer SET is_active = 0 WHERE requisition_id = ?", requisitionId);
        int sort = 1;
        for (HrRequisitionDTO.InterviewRound item : items) {
            Long first = item.getInterviewerUserIds().get(0);
            jdbc.update("""
                    INSERT INTO hr_requisition_round (requisition_id, round_no, interviewer_user_id, sort_no, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE interviewer_user_id = VALUES(interviewer_user_id), sort_no = VALUES(sort_no), is_active = 1
                    """, requisitionId, item.getRoundNo(), first, sort++, SecurityUtils.getCurrentUsername());
            for (Long userId : item.getInterviewerUserIds()) {
                if (userId == null) {
                    continue;
                }
                jdbc.update("""
                        INSERT INTO hr_requisition_round_interviewer (requisition_id, round_no, interviewer_user_id, create_by, is_active)
                        VALUES (?, ?, ?, ?, 1)
                        ON DUPLICATE KEY UPDATE is_active = 1
                        """, requisitionId, item.getRoundNo(), userId, SecurityUtils.getCurrentUsername());
            }
        }
    }

    private static String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public List<Map<String, Object>> applications(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, c.display_name, c.phone, c.email, a.requisition_id, r.job_name, a.channel_code, ch.channel_name,
                       a.resume_status, a.current_stage, s.stage_name, a.submitter_user_id, a.submitter_name, a.submitted_at, f.file_name,
                       (SELECT ai.score FROM hr_application_ai ai WHERE ai.application_id = a.id AND ai.is_active = 1 ORDER BY ai.id DESC LIMIT 1) ai_score,
                       (SELECT COUNT(1) FROM hr_application_ai ai WHERE ai.application_id = a.id AND ai.is_active = 1) ai_count
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_channel ch ON ch.channel_code = a.channel_code
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE a.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (q.getCandidateName() != null && !q.getCandidateName().isBlank()) {
            sql.append(" AND c.display_name LIKE ? ");
            args.add("%" + q.getCandidateName().trim() + "%");
        }
        if (q.getJobName() != null && !q.getJobName().isBlank()) {
            sql.append(" AND r.job_name LIKE ? ");
            args.add("%" + q.getJobName().trim() + "%");
        }
        if (q.getChannelCode() != null && !q.getChannelCode().isBlank()) {
            sql.append(" AND a.channel_code = ? ");
            args.add(q.getChannelCode());
        }
        if (q.getStageCode() != null && !q.getStageCode().isBlank()) {
            sql.append(" AND a.current_stage = ? ");
            args.add(q.getStageCode());
        }
        if (q.getSubmitterUserId() != null) {
            sql.append(" AND a.submitter_user_id = ? ");
            args.add(q.getSubmitterUserId());
        } else if (q.getSubmitterName() != null && !q.getSubmitterName().isBlank()) {
            sql.append(" AND a.submitter_name LIKE ? ");
            args.add("%" + q.getSubmitterName().trim() + "%");
        }
        if (q.getStartDate() != null) {
            sql.append(" AND a.submitted_at >= ? ");
            args.add(q.getStartDate());
        }
        if (q.getEndDate() != null) {
            sql.append(" AND a.submitted_at <= ? ");
            args.add(q.getEndDate());
        }
        if (q.getRequisitionId() != null) {
            sql.append(" AND a.requisition_id = ? ");
            args.add(q.getRequisitionId());
        }
        dataScope.apply(sql, args, "r", "a");
        sql.append(" ORDER BY a.submitted_at DESC, a.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Long saveApplication(HrApplicationDTO dto) {
        String stage = dto.getCurrentStage().trim();
        Integer stageOk = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_stage_def WHERE stage_code = ? AND is_active = 1", Integer.class, stage);
        if (stageOk == null || stageOk == 0) {
            throw new BusinessException("阶段不存在");
        }
        Integer requisitionOk = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_requisition WHERE id = ? AND is_active = 1", Integer.class, dto.getRequisitionId());
        if (requisitionOk == null || requisitionOk == 0) {
            throw new BusinessException("招聘需求不存在");
        }
        String user = SecurityUtils.getCurrentUsername();
        String resumeStatus = "ENTERED".equals(stage) ? "DRAFT" : "PASS";
        Submitter submitter = resolveSubmitter(dto.getSubmitterUserId(), dto.getSubmitterName());
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO hr_candidate (display_name, phone, email, create_by, is_active)
                    VALUES (?, ?, ?, ?, 1)
                    """, dto.getDisplayName().trim(), emptyToNull(dto.getPhone()), emptyToNull(dto.getEmail()), user);
            Long candidateId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("""
                    INSERT INTO hr_application (requisition_id, candidate_id, channel_code, resume_status, submitter_user_id, submitter_name, submitted_at, current_stage, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getRequisitionId(), candidateId, emptyToNull(dto.getChannelCode()), resumeStatus, submitter.userId(),
                    submitter.name(), dto.getSubmittedAt(), stage, user);
            Long applicationId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            touchStage(applicationId, "ENTERED", dto.getSubmittedAt(), user);
            if (!"ENTERED".equals(stage)) {
                touchStage(applicationId, stage, dto.getSubmittedAt(), user);
            }
            return applicationId;
        }
        Long candidateId = jdbc.query("""
                SELECT candidate_id FROM hr_application WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getLong(1) : null, dto.getId());
        if (candidateId == null) {
            throw new BusinessException("候选人不存在");
        }
        jdbc.update("""
                UPDATE hr_candidate SET display_name = ?, phone = ?, email = ? WHERE id = ? AND is_active = 1
                """, dto.getDisplayName().trim(), emptyToNull(dto.getPhone()), emptyToNull(dto.getEmail()), candidateId);
        int updated = jdbc.update("""
                UPDATE hr_application
                SET requisition_id = ?, channel_code = ?, resume_status = ?, submitter_user_id = ?, submitter_name = ?, submitted_at = ?, current_stage = ?
                WHERE id = ? AND is_active = 1
                """, dto.getRequisitionId(), emptyToNull(dto.getChannelCode()), resumeStatus, submitter.userId(), submitter.name(),
                dto.getSubmittedAt(), stage, dto.getId());
        if (updated == 0) {
            throw new BusinessException("候选人不存在");
        }
        touchStage(dto.getId(), "ENTERED", dto.getSubmittedAt(), user);
        if (!"ENTERED".equals(stage)) {
            touchStage(dto.getId(), stage, dto.getSubmittedAt(), user);
        }
        return dto.getId();
    }

    @Transactional
    public void uploadResume(Long applicationId, org.springframework.web.multipart.MultipartFile file) {
        Integer ok = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_application WHERE id = ? AND is_active = 1", Integer.class, applicationId);
        if (ok == null || ok == 0) {
            throw new BusinessException("候选人不存在");
        }
        Path stored = fileStorage.saveHrResume(file);
        String original = file.getOriginalFilename() == null ? stored.getFileName().toString() : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : null;
        if (ext != null && ext.length() > 8) {
            ext = ext.substring(0, 8);
        }
        String user = SecurityUtils.getCurrentUsername();
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_resume_file WHERE application_id = ?", Integer.class, applicationId);
        if (exists != null && exists > 0) {
            jdbc.update("""
                    UPDATE hr_resume_file
                    SET file_name = ?, storage_path = ?, file_ext = ?, update_by = ?, is_active = 1
                    WHERE application_id = ?
                    """, original, stored.toString(), ext, user, applicationId);
        } else {
            jdbc.update("""
                    INSERT INTO hr_resume_file (application_id, file_name, storage_path, file_ext, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, 1)
                    """, applicationId, original, stored.toString(), ext, user);
        }
    }

    public void deleteApplication(Long id) {
        int updated = jdbc.update("UPDATE hr_application SET is_active = 0 WHERE id = ? AND is_active = 1", id);
        if (updated == 0) {
            throw new BusinessException("候选人不存在");
        }
    }

    public void deleteApplicationBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的候选人");
        }
        for (Long id : ids) {
            if (id != null) {
                deleteApplication(id);
            }
        }
    }

    public List<Map<String, Object>> targetOptions() {
        return jdbc.queryForList("""
                SELECT id, name, sort_no FROM hr_target_option WHERE is_active = 1 ORDER BY sort_no, id
                """);
    }

    @Transactional
    public void saveTarget(HrTargetOptionDTO dto) {
        String name = dto.getName().trim();
        int sort = dto.getSortNo() == null ? 0 : dto.getSortNo();
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_target_option WHERE name = ? AND is_active = 1 AND id <> ?
                """, Integer.class, name, dto.getId() == null ? 0L : dto.getId());
        if (dup != null && dup > 0) {
            throw new BusinessException("目标到岗已存在");
        }
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO hr_target_option (name, sort_no, create_by, is_active) VALUES (?, ?, ?, 1)
                    """, name, sort, SecurityUtils.getCurrentUsername());
            return;
        }
        int updated = jdbc.update("""
                UPDATE hr_target_option SET name = ?, sort_no = ?, update_by = ? WHERE id = ? AND is_active = 1
                """, name, sort, SecurityUtils.getCurrentUsername(), dto.getId());
        if (updated == 0) {
            throw new BusinessException("目标到岗不存在");
        }
    }

    @Transactional
    public void deleteTarget(Long id) {
        String name = jdbc.query("SELECT name FROM hr_target_option WHERE id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (name == null) {
            throw new BusinessException("目标到岗不存在");
        }
        Integer used = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_requisition WHERE is_active = 1 AND TRIM(target_text) = ?", Integer.class, name);
        if (used != null && used > 0) {
            throw new BusinessException("已有招聘需求使用该目标到岗，不能删除");
        }
        jdbc.update("UPDATE hr_target_option SET is_active = 0, update_by = ? WHERE id = ?",
                SecurityUtils.getCurrentUsername(), id);
    }

    public List<Map<String, Object>> failReasonOptions() {
        return jdbc.queryForList("""
                SELECT id, name, sort_no FROM hr_fail_reason WHERE is_active = 1 ORDER BY sort_no, id
                """);
    }

    @Transactional
    public void saveFailReason(HrFailReasonDTO dto) {
        String name = dto.getName().trim();
        int sort = dto.getSortNo() == null ? 0 : dto.getSortNo();
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_fail_reason WHERE name = ? AND is_active = 1 AND id <> ?
                """, Integer.class, name, dto.getId() == null ? 0L : dto.getId());
        if (dup != null && dup > 0) {
            throw new BusinessException("未通过原因已存在");
        }
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO hr_fail_reason (name, sort_no, create_by, is_active) VALUES (?, ?, ?, 1)
                    """, name, sort, SecurityUtils.getCurrentUsername());
            return;
        }
        int updated = jdbc.update("""
                UPDATE hr_fail_reason SET name = ?, sort_no = ?, update_by = ? WHERE id = ? AND is_active = 1
                """, name, sort, SecurityUtils.getCurrentUsername(), dto.getId());
        if (updated == 0) {
            throw new BusinessException("未通过原因不存在");
        }
    }

    @Transactional
    public void deleteFailReason(Long id) {
        String name = jdbc.query("SELECT name FROM hr_fail_reason WHERE id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (name == null) {
            throw new BusinessException("未通过原因不存在");
        }
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(1) FROM (
                  SELECT 1 FROM hr_interview_record WHERE is_active = 1 AND fail_reason = ?
                  UNION ALL
                  SELECT 1 FROM hr_interview_verdict WHERE is_active = 1 AND fail_reason = ?
                ) t
                """, Integer.class, name, name);
        if (used != null && used > 0) {
            throw new BusinessException("已有面试评价使用该原因，不能删除");
        }
        jdbc.update("UPDATE hr_fail_reason SET is_active = 0, update_by = ? WHERE id = ?",
                SecurityUtils.getCurrentUsername(), id);
    }

    public List<Map<String, Object>> stages() {
        return jdbc.queryForList("""
                SELECT stage_code, stage_name FROM hr_stage_def WHERE is_active = 1 ORDER BY sort_no
                """);
    }

    private Submitter resolveSubmitter(Long userId, String rawName) {
        if (userId != null) {
            String nickname = jdbc.query("""
                    SELECT nickname FROM sys_user WHERE user_id = ? AND is_active = 1 AND status = 0
                    """, rs -> rs.next() ? rs.getString(1) : null, userId);
            if (nickname == null || nickname.isBlank()) {
                throw new BusinessException("提交人不是系统用户");
            }
            return new Submitter(userId, nickname);
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            return new Submitter(null, "");
        }
        List<Long> ids = jdbc.query("""
                SELECT user_id FROM sys_user
                WHERE is_active = 1 AND status = 0 AND (nickname = ? OR username = ?)
                """, (rs, row) -> rs.getLong(1), name, name);
        if (ids.isEmpty()) {
            throw new BusinessException("提交人「" + name + "」不是系统用户");
        }
        if (ids.size() > 1) {
            throw new BusinessException("提交人「" + name + "」对应多个用户，请改成登录账号");
        }
        String nickname = jdbc.query("SELECT nickname FROM sys_user WHERE user_id = ?",
                rs -> rs.next() ? rs.getString(1) : name, ids.get(0));
        return new Submitter(ids.get(0), nickname == null || nickname.isBlank() ? name : nickname);
    }

    private record Submitter(Long userId, String name) {}

    private void touchStage(Long applicationId, String stage, java.time.LocalDate submittedAt, String user) {
        jdbc.update("""
                INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                VALUES (?, ?, ?, 'RESUME', ?, 1)
                ON DUPLICATE KEY UPDATE is_active = 1
                """, applicationId, stage, submittedAt.atStartOfDay(), user);
    }

    public Map<String, Object> applicationDetail(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.requisition_id, c.display_name, c.parsed_name, c.phone, c.email, r.job_name, a.channel_code,
                       a.resume_status, a.screen_result, a.current_stage, s.stage_name, a.submitter_name, a.submitted_at,
                       p.major, p.degree, p.school_name_raw, p.school_tags, p.qs_rank, p.last_company, p.ai_score,
                       f.file_name, f.storage_path
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                LEFT JOIN hr_resume_parse p ON p.application_id = a.id
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE a.id = ? AND a.is_active = 1
                """, id);
        if (rows.isEmpty()) {
            throw new BusinessException("投递不存在");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.get(0));
        detail.put("rounds", jdbc.queryForList("""
                SELECT round_no, interviewer_name, interview_at, comment FROM hr_interview_round
                WHERE application_id = ? AND is_active = 1 ORDER BY round_no
                """, id));
        detail.put("invites", jdbc.queryForList("""
                SELECT id, round_no, status, interview_at, fail_reason, dingtalk_event_id, invited_by, cancelled_at
                FROM hr_interview_invite WHERE application_id = ? AND is_active = 1 ORDER BY id DESC
                """, id));
        Object requisitionId = detail.get("requisition_id");
        detail.put("process", requisitionId == null ? List.of() : jdbc.queryForList("""
                SELECT rr.round_no, rr.interviewer_user_id, u.nickname interviewer_name
                FROM hr_requisition_round rr
                LEFT JOIN sys_user u ON u.user_id = rr.interviewer_user_id
                WHERE rr.requisition_id = ? AND rr.is_active = 1
                ORDER BY rr.round_no
                """, requisitionId));
        return detail;
    }

    public String resumeDisplayName(Long applicationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT file_name FROM hr_resume_file WHERE application_id = ? AND is_active = 1", applicationId);
        if (rows.isEmpty() || rows.get(0).get("file_name") == null) {
            return null;
        }
        return String.valueOf(rows.get(0).get("file_name"));
    }

    public Path resumeFile(Long applicationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT file_name, storage_path FROM hr_resume_file WHERE application_id = ? AND is_active = 1", applicationId);
        if (rows.isEmpty()) {
            throw new BusinessException("没有简历文件");
        }
        String fileName = rows.get(0).get("file_name") == null ? null : String.valueOf(rows.get(0).get("file_name"));
        String stored = rows.get(0).get("storage_path") == null ? null : String.valueOf(rows.get(0).get("storage_path"));
        if (stored != null && !stored.isBlank()) {
            Path uploaded = fileStorage.resolveUploadPath(stored);
            if (uploaded != null) {
                return uploaded;
            }
            Path path = Path.of(stored).normalize();
            Path root = hrFilesRoot();
            if (Files.isRegularFile(path) && root != null && path.startsWith(root)) {
                return path;
            }
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        Path root = hrFilesRoot();
        if (fileName == null || fileName.isBlank() || root == null) {
            throw new BusinessException("简历文件不在服务器上");
        }
        try (var walk = Files.walk(root)) {
            Path found = walk.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals(fileName))
                    .findFirst().orElse(null);
            if (found == null) {
                throw new BusinessException("简历文件不在服务器上");
            }
            jdbc.update("UPDATE hr_resume_file SET storage_path = ? WHERE application_id = ? AND is_active = 1",
                    found.toString(), applicationId);
            return found;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("简历文件不在服务器上");
        }
    }

    private static Path hrFilesRoot() {
        for (Path candidate : List.of(Path.of("HR"), Path.of("..", "HR"))) {
            Path root = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(root)) {
                return root;
            }
        }
        return null;
    }

    public List<Map<String, Object>> departmentTree() {
        List<Map<String, Object>> flat = jdbc.queryForList("""
                SELECT id, parent_id, name, leader_user_id, sort_order, status, ancestors
                FROM hr_department WHERE is_active = 1 ORDER BY sort_order, id
                """);
        return buildTree(flat, 0L);
    }

    @Transactional
    public void saveDepartment(HrDepartmentDTO dto) {
        long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        String ancestors = parentId == 0 ? "0" : ancestorsOf(parentId) + "," + parentId;
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO hr_department (parent_id, name, ancestors, leader_user_id, sort_order, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, 1)
                    """, parentId, dto.getName().trim(), ancestors, dto.getLeaderUserId(),
                    dto.getSortOrder() == null ? 0 : dto.getSortOrder(), SecurityUtils.getCurrentUsername());
            return;
        }
        jdbc.update("""
                UPDATE hr_department SET parent_id = ?, name = ?, ancestors = ?, leader_user_id = ?, sort_order = ?
                WHERE id = ? AND is_active = 1
                """, parentId, dto.getName().trim(), ancestors, dto.getLeaderUserId(),
                dto.getSortOrder() == null ? 0 : dto.getSortOrder(), dto.getId());
        refreshDescendants(dto.getId(), ancestors + "," + dto.getId());
    }

    public List<Map<String, Object>> users(String scope) {
        if ("owner".equals(scope)) {
            return jdbc.queryForList("""
                    SELECT DISTINCT u.user_id, u.username, u.nickname, u.phone,
                           CASE WHEN d.user_id IS NULL THEN 0 ELSE 1 END dingtalk_bound
                    FROM sys_user u
                    JOIN sys_user_role ur ON ur.user_id = u.user_id AND ur.is_active = 1
                    JOIN sys_role r ON r.role_id = ur.role_id AND r.role_key IN ('hr_admin', 'hr_owner') AND r.is_active = 1
                    LEFT JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                    WHERE u.is_active = 1 AND u.status = 0
                    ORDER BY u.user_id
                    """);
        }
        if ("interviewer".equals(scope)) {
            return jdbc.queryForList("""
                    SELECT DISTINCT u.user_id, u.username, u.nickname, u.phone,
                           CASE WHEN d.user_id IS NULL THEN 0 ELSE 1 END dingtalk_bound
                    FROM sys_user u
                    JOIN sys_user_role ur ON ur.user_id = u.user_id AND ur.is_active = 1
                    JOIN sys_role r ON r.role_id = ur.role_id AND r.role_key IN ('hr_admin', 'hr_owner', 'hr_interviewer') AND r.is_active = 1
                    LEFT JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                    WHERE u.is_active = 1 AND u.status = 0
                    ORDER BY u.user_id
                    """);
        }
        return jdbc.queryForList("""
                SELECT u.user_id, u.username, u.nickname, u.phone,
                       CASE WHEN d.user_id IS NULL THEN 0 ELSE 1 END dingtalk_bound
                FROM sys_user u
                LEFT JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                WHERE u.is_active = 1 AND u.status = 0
                ORDER BY u.user_id
                """);
    }

    public PageResult<HrSchoolVO> schools(HrSchoolQueryDTO query) {
        HrSchoolQueryDTO q = query == null ? new HrSchoolQueryDTO() : query;
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), 100);
        int offset = (pageNum - 1) * pageSize;
        boolean qs = "QS".equalsIgnoreCase(q.getKind());
        StringBuilder where = new StringBuilder(" WHERE is_active = 1 ");
        List<Object> args = new ArrayList<>();
        if (q.getName() != null && !q.getName().isBlank()) {
            if (qs) {
                where.append(" AND (name_zh LIKE ? OR name_en LIKE ?) ");
                String like = "%" + q.getName().trim() + "%";
                args.add(like);
                args.add(like);
            } else {
                where.append(" AND school_name LIKE ? ");
                args.add("%" + q.getName().trim() + "%");
            }
        }
        if (q.getRegion() != null && !q.getRegion().isBlank()) {
            where.append(qs ? " AND country LIKE ? " : " AND region LIKE ? ");
            args.add("%" + q.getRegion().trim() + "%");
        }
        if (!qs && q.getTags() != null && !q.getTags().isBlank()) {
            where.append(" AND tags LIKE ? ");
            args.add("%" + q.getTags().trim() + "%");
        }
        String table = qs ? "hr_qs_university" : "hr_school";
        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM " + table + where, Long.class, args.toArray());
        String select = qs
                ? "SELECT id, rank_no, name_zh, name_en, abbr, country, score FROM hr_qs_university"
                : "SELECT id, school_name, school_code, school_type, tags, edu_level, region, authority, qs_rank_text, intro FROM hr_school";
        String order = qs ? " ORDER BY rank_no, id " : " ORDER BY school_name, id ";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add(offset);
        List<HrSchoolVO> rows = jdbc.query(select + where + order + " LIMIT ? OFFSET ?", (rs, rowNum) -> {
            HrSchoolVO vo = new HrSchoolVO();
            vo.setId(rs.getLong("id"));
            if (qs) {
                vo.setRankNo(rs.getInt("rank_no"));
                vo.setName(rs.getString("name_zh"));
                vo.setNameEn(rs.getString("name_en"));
                vo.setAbbr(rs.getString("abbr"));
                vo.setRegion(rs.getString("country"));
                vo.setScore(rs.getBigDecimal("score"));
            } else {
                vo.setName(rs.getString("school_name"));
                vo.setCode(rs.getString("school_code"));
                vo.setSchoolType(rs.getString("school_type"));
                vo.setTags(rs.getString("tags"));
                vo.setEduLevel(rs.getString("edu_level"));
                vo.setRegion(rs.getString("region"));
                vo.setAuthority(rs.getString("authority"));
                vo.setQsRank(rs.getString("qs_rank_text"));
                vo.setIntro(rs.getString("intro"));
            }
            return vo;
        }, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    public HrDingTalkIdentityVO previewDingTalk(Long userId) {
        String phone = requireUserPhone(userId);
        DingTalkCalendarClient.DingIdentity identity = resolveDingIdentity(userId, phone);
        HrDingTalkIdentityVO vo = new HrDingTalkIdentityVO();
        vo.setPhone(phone);
        vo.setDingtalkUserId(identity.userId());
        vo.setUnionId(identity.unionId());
        return vo;
    }

    @Transactional
    public void bindDingTalk(HrDingTalkBindDTO dto) {
        HrDingTalkIdentityVO identity = previewDingTalk(dto.getUserId());
        jdbc.update("""
                INSERT INTO hr_user_dingtalk (user_id, dingtalk_user_id, dingtalk_union_id, create_by, is_active)
                VALUES (?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE dingtalk_user_id = VALUES(dingtalk_user_id), dingtalk_union_id = VALUES(dingtalk_union_id), is_active = 1
                """, dto.getUserId(), identity.getDingtalkUserId(), identity.getUnionId(), SecurityUtils.getCurrentUsername());
    }

    public void unbindDingTalk(Long userId) {
        jdbc.update("UPDATE hr_user_dingtalk SET is_active = 0 WHERE user_id = ?", userId);
    }

    private String requireUserPhone(Long userId) {
        String phone = jdbc.query("SELECT phone FROM sys_user WHERE user_id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("请先在用户资料里填写手机号，再绑定钉钉");
        }
        return phone.trim();
    }

    private String nicknameOf(Long userId) {
        String nickname = jdbc.query("SELECT nickname FROM sys_user WHERE user_id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        return nickname == null ? "" : nickname.trim();
    }

    private DingTalkCalendarClient.DingIdentity resolveDingIdentity(Long userId, String phone) {
        if (!dingTalk.configured()) {
            throw new BusinessException("钉钉应用还没配置，不能按手机号获取钉钉身份");
        }
        DingTalkCalendarClient.DingIdentity identity = dingTalk.resolveByMobile(phone, nicknameOf(userId));
        if (identity == null) {
            throw new BusinessException("没有按手机号「" + phone + "」匹配到企业钉钉身份，请确认与钉钉通讯录一致");
        }
        return identity;
    }

    public List<Map<String, Object>> channels() {
        return jdbc.queryForList("SELECT channel_code, channel_name FROM hr_channel WHERE is_active = 1");
    }

    public void saveView(HrBoardViewDTO dto) {
        try {
            String json = objectMapper.writeValueAsString(dto.getFilter());
            jdbc.update("""
                    INSERT INTO hr_board_view (user_id, view_name, filter_json, is_default, create_by, is_active)
                    VALUES (?, ?, CAST(? AS JSON), ?, ?, 1)
                    ON DUPLICATE KEY UPDATE filter_json = VALUES(filter_json), is_default = VALUES(is_default), is_active = 1
                    """, SecurityUtils.getCurrentUserId(), dto.getViewName().trim(), json,
                    Boolean.TRUE.equals(dto.getIsDefault()) ? 1 : 0, SecurityUtils.getCurrentUsername());
        } catch (Exception ex) {
            throw new BusinessException("保存视图失败");
        }
    }

    public List<Map<String, Object>> views() {
        return jdbc.queryForList("""
                SELECT id, view_name, filter_json, is_default FROM hr_board_view
                WHERE user_id = ? AND is_active = 1 ORDER BY id DESC
                """, SecurityUtils.getCurrentUserId());
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> flat, long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> row : flat) {
            if (((Number) row.get("parent_id")).longValue() == parentId) {
                Map<String, Object> node = new LinkedHashMap<>(row);
                node.put("children", buildTree(flat, ((Number) row.get("id")).longValue()));
                nodes.add(node);
            }
        }
        return nodes;
    }

    private String ancestorsOf(long id) {
        return jdbc.queryForObject("SELECT ancestors FROM hr_department WHERE id = ? AND is_active = 1", String.class, id);
    }

    private void refreshDescendants(long id, String childAncestors) {
        List<Long> children = jdbc.query("SELECT id FROM hr_department WHERE parent_id = ? AND is_active = 1",
                (rs, row) -> rs.getLong(1), id);
        for (Long child : children) {
            jdbc.update("UPDATE hr_department SET ancestors = ? WHERE id = ?", childAncestors, child);
            refreshDescendants(child, childAncestors + "," + child);
        }
    }
}
