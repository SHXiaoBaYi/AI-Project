package com.base.admin.service;

import com.base.admin.domain.dto.HrPipelineBoardQueryDTO;
import com.base.admin.domain.dto.HrPipelineOnboardDTO;
import com.base.admin.domain.dto.HrPipelinePhoneDTO;
import com.base.admin.domain.dto.HrPipelineScreenDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.vo.HrPipelineBoardVO;
import com.base.admin.domain.vo.HrPipelineDetailVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 单岗位简历全流程：初筛 → 电话 → 面试 → 待入职。
 */
@Service
@RequiredArgsConstructor
public class HrPipelineService {

    private static final Set<String> ONBOARD_STAGES = Set.of(
            "SALARY", "BG_COLLECT", "BG_CHECK", "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD",
            "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");

    private static final List<PhaseMeta> PHASES = List.of(
            new PhaseMeta("SCREEN", "简历筛选"),
            new PhaseMeta("PHONE", "电话沟通"),
            new PhaseMeta("INTERVIEW", "面试沟通"),
            new PhaseMeta("ONBOARD", "待入职")
    );

    private final JdbcTemplate jdbc;
    private final SysTaskService taskService;
    private final HrDataScope dataScope;

    public HrPipelineBoardVO board(HrPipelineBoardQueryDTO query) {
        if (query == null || query.getRequisitionId() == null) {
            throw new BusinessException("请选择岗位");
        }
        Long requisitionId = query.getRequisitionId();
        Map<String, Object> job = loadJob(requisitionId);

        StringBuilder boardSql = new StringBuilder("""
                SELECT a.id, c.display_name, c.phone, c.email, a.current_stage, s.stage_name, a.screen_result,
                       a.submitted_at, f.file_name,
                       (SELECT COUNT(1) FROM hr_candidate_portfolio p
                        WHERE p.candidate_id = a.candidate_id AND p.is_active = 1) portfolio_count,
                       ph.result phone_result, ph.called_at, ph.interview_at phone_interview_at, ph.reject_reason phone_reject_reason,
                       off.salary_amount, ob.onboard_date,
                       (SELECT rec.conclusion FROM hr_interview_record rec
                        WHERE rec.application_id = a.id AND rec.is_active = 1
                        ORDER BY rec.round_no DESC, rec.id DESC LIMIT 1) latest_conclusion,
                       (SELECT u.nickname FROM hr_interview_record rec
                        LEFT JOIN sys_user u ON u.user_id = rec.interviewer_user_id
                        WHERE rec.application_id = a.id AND rec.is_active = 1
                        ORDER BY rec.round_no DESC, rec.id DESC LIMIT 1) latest_interviewer,
                       (SELECT rec.interviewed_at FROM hr_interview_record rec
                        WHERE rec.application_id = a.id AND rec.is_active = 1
                        ORDER BY rec.round_no DESC, rec.id DESC LIMIT 1) latest_interview_at
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                LEFT JOIN hr_phone_screen ph ON ph.application_id = a.id AND ph.is_active = 1
                LEFT JOIN hr_offer off ON off.application_id = a.id AND off.is_active = 1
                LEFT JOIN hr_onboard ob ON ob.application_id = a.id AND ob.is_active = 1
                WHERE a.is_active = 1 AND a.requisition_id = ?
                """);
        List<Object> boardArgs = new ArrayList<>();
        boardArgs.add(requisitionId);
        dataScope.apply(boardSql, boardArgs, "r", null);
        boardSql.append(" ORDER BY a.submitted_at DESC, a.id DESC");

        List<Map<String, Object>> rows = jdbc.queryForList(boardSql.toString(), boardArgs.toArray());
        Map<String, HrPipelineBoardVO.PhaseColumn> columns = new LinkedHashMap<>();
        for (PhaseMeta meta : PHASES) {
            HrPipelineBoardVO.PhaseColumn col = new HrPipelineBoardVO.PhaseColumn();
            col.setPhase(meta.code());
            col.setPhaseLabel(meta.label());
            columns.put(meta.code(), col);
        }
        for (Map<String, Object> row : rows) {
            HrPipelineBoardVO.CandidateCard card = toCard(row);
            HrPipelineBoardVO.PhaseColumn col = columns.get(card.getPhase());
            if (col != null) {
                col.getCandidates().add(card);
            }
        }
        HrPipelineBoardVO vo = new HrPipelineBoardVO();
        vo.setRequisitionId(requisitionId);
        vo.setJobName(str(job.get("job_name")));
        vo.setDeptName(str(job.get("dept_name")));
        for (HrPipelineBoardVO.PhaseColumn col : columns.values()) {
            col.setCount(col.getCandidates().size());
            vo.getPhases().add(col);
        }
        return vo;
    }

    public HrPipelineDetailVO detail(Long applicationId) {
        if (applicationId == null) {
            throw new BusinessException("请选择候选人");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.requisition_id, c.display_name, c.phone, c.email, r.job_name,
                       a.current_stage, s.stage_name, a.screen_result, a.submitted_at, f.file_name,
                       ph.called_at, ph.result phone_result, ph.interview_at, ph.reject_reason, ph.remark phone_remark,
                       off.salary_amount, off.status offer_status, off.offered_at,
                       ob.onboard_date, ob.probation_status
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                LEFT JOIN hr_phone_screen ph ON ph.application_id = a.id AND ph.is_active = 1
                LEFT JOIN hr_offer off ON off.application_id = a.id AND off.is_active = 1
                LEFT JOIN hr_onboard ob ON ob.application_id = a.id AND ob.is_active = 1
                WHERE a.id = ? AND a.is_active = 1
                """, applicationId);
        if (rows.isEmpty()) {
            throw new BusinessException("候选人不存在");
        }
        Map<String, Object> row = rows.get(0);
        HrPipelineDetailVO vo = new HrPipelineDetailVO();
        vo.setApplicationId(applicationId);
        Object reqId = row.get("requisition_id");
        vo.setRequisitionId(reqId == null ? null : ((Number) reqId).longValue());
        vo.setDisplayName(str(row.get("display_name")));
        vo.setPhone(str(row.get("phone")));
        vo.setEmail(str(row.get("email")));
        vo.setJobName(str(row.get("job_name")));
        vo.setCurrentStage(str(row.get("current_stage")));
        vo.setStageName(str(row.get("stage_name")));
        String phase = resolvePhase(vo.getCurrentStage(), str(row.get("screen_result")), str(row.get("phone_result")));
        vo.setPhase(phase);
        vo.setPhaseLabel(phaseLabel(phase));
        vo.setScreenResult(str(row.get("screen_result")));
        vo.setResumeName(str(row.get("file_name")));
        vo.setSubmittedAt(toDate(row.get("submitted_at")));

        if (row.get("called_at") != null || row.get("phone_result") != null) {
            HrPipelineDetailVO.PhoneScreen phone = new HrPipelineDetailVO.PhoneScreen();
            phone.setCalledAt(toDateTime(row.get("called_at")));
            phone.setResult(str(row.get("phone_result")));
            phone.setInterviewAt(toDateTime(row.get("interview_at")));
            phone.setRejectReason(str(row.get("reject_reason")));
            phone.setRemark(str(row.get("phone_remark")));
            vo.setPhoneScreen(phone);
        }
        if (row.get("salary_amount") != null || row.get("offer_status") != null) {
            HrPipelineDetailVO.OfferInfo offer = new HrPipelineDetailVO.OfferInfo();
            offer.setSalaryAmount(toDecimal(row.get("salary_amount")));
            offer.setStatus(str(row.get("offer_status")));
            offer.setOfferedAt(toDateTime(row.get("offered_at")));
            vo.setOffer(offer);
        }
        if (row.get("onboard_date") != null || row.get("probation_status") != null) {
            HrPipelineDetailVO.OnboardInfo onboard = new HrPipelineDetailVO.OnboardInfo();
            onboard.setOnboardDate(toDate(row.get("onboard_date")));
            onboard.setProbationStatus(str(row.get("probation_status")));
            vo.setOnboard(onboard);
        }

        vo.setTimeline(loadTimeline(applicationId));
        vo.setInterviews(loadInterviews(applicationId));
        vo.setDocuments(loadDocs(applicationId));
        return vo;
    }

    @Transactional
    public void screen(HrPipelineScreenDTO dto) {
        String result = normalizePassFail(dto.getResult(), "初筛结果");
        ensureApplication(dto.getApplicationId());
        String stage = "PASS".equals(result) ? "SCREEN_PASS" : "SCREEN_FAIL";
        jdbc.update("""
                UPDATE hr_application
                SET screen_result = ?, resume_status = ?, current_stage = ?
                WHERE id = ? AND is_active = 1
                """, result, result, stage, dto.getApplicationId());
        touchStage(dto.getApplicationId(), stage, "SCREEN",
                "PASS".equals(result) ? "初筛合适" : "初筛不合适"
                        + (StringUtils.hasText(dto.getRemark()) ? "：" + dto.getRemark().trim() : ""));
    }

    @Transactional
    public void phone(HrPipelinePhoneDTO dto) {
        String result = normalizePassFail(dto.getResult(), "沟通结果");
        ensureApplication(dto.getApplicationId());
        if ("PASS".equals(result) && dto.getInterviewAt() == null) {
            throw new BusinessException("合适时请填写约面时间");
        }
        if ("FAIL".equals(result) && !StringUtils.hasText(dto.getRejectReason())) {
            throw new BusinessException("不合适时请填写原因");
        }
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_phone_screen WHERE application_id = ?", Integer.class, dto.getApplicationId());
        String user = SecurityUtils.getCurrentUsername();
        if (exists != null && exists > 0) {
            jdbc.update("""
                    UPDATE hr_phone_screen
                    SET called_at = ?, result = ?, interview_at = ?, reject_reason = ?, remark = ?,
                        update_by = ?, is_active = 1
                    WHERE application_id = ?
                    """, dto.getCalledAt(), result, dto.getInterviewAt(),
                    emptyToNull(dto.getRejectReason()), emptyToNull(dto.getRemark()), user, dto.getApplicationId());
        } else {
            jdbc.update("""
                    INSERT INTO hr_phone_screen
                      (application_id, called_at, result, interview_at, reject_reason, remark, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getApplicationId(), dto.getCalledAt(), result, dto.getInterviewAt(),
                    emptyToNull(dto.getRejectReason()), emptyToNull(dto.getRemark()), user);
        }
        String stage = "PASS".equals(result) ? "PHONE_PASS" : "PHONE_FAIL";
        jdbc.update("UPDATE hr_application SET current_stage = ? WHERE id = ? AND is_active = 1",
                stage, dto.getApplicationId());
        touchStage(dto.getApplicationId(), stage, "PHONE",
                "PASS".equals(result) ? "电话沟通合适，约面 "
                        + dto.getInterviewAt() : "电话沟通不合适：" + dto.getRejectReason().trim());
        if ("PASS".equals(result)) {
            createInterviewReminder(dto.getApplicationId(), dto.getInterviewAt());
        }
    }

    @Transactional
    public void onboard(HrPipelineOnboardDTO dto) {
        String sub = dto.getSubStatus() == null ? "" : dto.getSubStatus().trim().toUpperCase(Locale.ROOT);
        if (!ONBOARD_STAGES.contains(sub) || "ONBOARDED".equals(sub) || "OFFER_SENT".equals(sub) || "OFFER_ACCEPTED".equals(sub)) {
            if (!Set.of("SALARY", "BG_COLLECT", "BG_CHECK", "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD").contains(sub)) {
                throw new BusinessException("待入职子状态不合法");
            }
        }
        ensureApplication(dto.getApplicationId());
        String user = SecurityUtils.getCurrentUsername();
        jdbc.update("UPDATE hr_application SET current_stage = ? WHERE id = ? AND is_active = 1",
                sub, dto.getApplicationId());
        touchStage(dto.getApplicationId(), sub, "ONBOARD",
                StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : "待入职状态更新为 " + sub);

        if (dto.getSalaryAmount() != null) {
            Integer offerExists = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM hr_offer WHERE application_id = ?", Integer.class, dto.getApplicationId());
            if (offerExists != null && offerExists > 0) {
                jdbc.update("""
                        UPDATE hr_offer SET salary_amount = ?, update_by = ?, is_active = 1
                        WHERE application_id = ?
                        """, dto.getSalaryAmount(), user, dto.getApplicationId());
            } else {
                jdbc.update("""
                        INSERT INTO hr_offer (application_id, salary_amount, offered_at, create_by, is_active)
                        VALUES (?, ?, NOW(), ?, 1)
                        """, dto.getApplicationId(), dto.getSalaryAmount(), user);
            }
        }
        if (dto.getOnboardDate() != null) {
            Integer onboardExists = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM hr_onboard WHERE application_id = ?", Integer.class, dto.getApplicationId());
            if (onboardExists != null && onboardExists > 0) {
                jdbc.update("""
                        UPDATE hr_onboard SET onboard_date = ?, update_by = ?, is_active = 1
                        WHERE application_id = ?
                        """, dto.getOnboardDate(), user, dto.getApplicationId());
            } else {
                jdbc.update("""
                        INSERT INTO hr_onboard (application_id, onboard_date, source, create_by, is_active)
                        VALUES (?, ?, 'PIPELINE', ?, 1)
                        """, dto.getApplicationId(), dto.getOnboardDate(), user);
            }
        }
    }

    private void createInterviewReminder(Long applicationId, LocalDateTime interviewAt) {
        Map<String, Object> info = jdbc.queryForMap("""
                SELECT a.requisition_id, c.display_name, COALESCE(r.job_name, '') job_name
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.id = ?
                """, applicationId);
        Long requisitionId = info.get("requisition_id") == null ? null : ((Number) info.get("requisition_id")).longValue();
        if (requisitionId == null) {
            return;
        }
        List<Long> owners = jdbc.query("""
                SELECT user_id FROM hr_requisition_owner
                WHERE requisition_id = ? AND is_active = 1 AND user_id IS NOT NULL
                ORDER BY sort_no, id
                """, (rs, i) -> rs.getLong(1), requisitionId);
        if (owners.isEmpty()) {
            return;
        }
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM sys_task
                WHERE biz_type = 'hr_interview_remind' AND biz_id = ? AND is_active = 1
                """, Integer.class, applicationId);
        if (exists != null && exists > 0) {
            return;
        }
        String name = String.valueOf(info.get("display_name"));
        String job = String.valueOf(info.get("job_name"));
        SysTaskDTO task = new SysTaskDTO();
        task.setTitle("【面试提醒】" + name + " - " + job);
        task.setContent("电话沟通已约面，请按时安排面试邀约。约面时间：" + interviewAt);
        task.setTaskType("面试提醒");
        task.setPriority(2);
        task.setStatus("未开始");
        task.setProgress(0);
        task.setOwnerUserId(owners.get(0));
        task.setAssigneeUserIds(owners);
        task.setPlanStartTime(interviewAt.minusHours(1));
        task.setPlanEndTime(interviewAt);
        task.setBizType("hr_interview_remind");
        task.setBizId(applicationId);
        task.setBizTitle(job);
        task.setRemark("pipelinePhone");
        taskService.create(task);
    }

    private Map<String, Object> loadJob(Long requisitionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.job_name, d.name dept_name
                FROM hr_requisition r
                LEFT JOIN hr_department d ON d.id = r.dept_id
                WHERE r.id = ? AND r.is_active = 1
                """, requisitionId);
        if (rows.isEmpty()) {
            throw new BusinessException("岗位不存在");
        }
        return rows.get(0);
    }

    private void ensureApplication(Long applicationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_application WHERE id = ? AND is_active = 1", Integer.class, applicationId);
        if (count == null || count == 0) {
            throw new BusinessException("候选人不存在");
        }
    }

    private void touchStage(Long applicationId, String stage, String source, String summary) {
        String user = SecurityUtils.getCurrentUsername();
        jdbc.update("""
                INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                VALUES (?, ?, NOW(), ?, ?, 1)
                ON DUPLICATE KEY UPDATE event_at = NOW(), source_sheet = VALUES(source_sheet), is_active = 1, update_by = VALUES(create_by)
                """, applicationId, stage, source, user);
        // optional remark table not available; summary encoded in source for detail query via stage name
    }

    private HrPipelineBoardVO.CandidateCard toCard(Map<String, Object> row) {
        HrPipelineBoardVO.CandidateCard card = new HrPipelineBoardVO.CandidateCard();
        card.setApplicationId(((Number) row.get("id")).longValue());
        card.setDisplayName(str(row.get("display_name")));
        card.setPhone(str(row.get("phone")));
        card.setEmail(str(row.get("email")));
        card.setCurrentStage(str(row.get("current_stage")));
        card.setStageName(str(row.get("stage_name")));
        card.setScreenResult(str(row.get("screen_result")));
        card.setPhoneResult(str(row.get("phone_result")));
        card.setPhoneCalledAt(toDateTime(row.get("called_at")));
        card.setPhoneInterviewAt(toDateTime(row.get("phone_interview_at")));
        card.setPhoneRejectReason(str(row.get("phone_reject_reason")));
        card.setResumeName(str(row.get("file_name")));
        card.setPortfolioCount(row.get("portfolio_count") == null ? 0 : ((Number) row.get("portfolio_count")).intValue());
        card.setSalaryAmount(toDecimal(row.get("salary_amount")));
        card.setOnboardDate(toDate(row.get("onboard_date")));
        card.setSubmittedAt(toDate(row.get("submitted_at")));
        card.setLatestInterviewConclusion(str(row.get("latest_conclusion")));
        card.setLatestInterviewerName(str(row.get("latest_interviewer")));
        card.setLatestInterviewAt(toDateTime(row.get("latest_interview_at")));
        card.setPhase(resolvePhase(card.getCurrentStage(), card.getScreenResult(), card.getPhoneResult()));
        return card;
    }

    static String resolvePhase(String stage, String screenResult, String phoneResult) {
        if (stage == null || stage.isBlank() || "ENTERED".equals(stage) || "SCREEN_FAIL".equals(stage)) {
            return "SCREEN";
        }
        if ("SCREEN_PASS".equals(stage) || "PHONE_FAIL".equals(stage)) {
            return "PHONE";
        }
        if (ONBOARD_STAGES.contains(stage) || "FINAL".equals(stage)) {
            // FINAL 已通过但未开任务时也归入待入职入口；若仍在面试轮次则下面
            if ("FINAL".equals(stage)) {
                return "ONBOARD";
            }
            return "ONBOARD";
        }
        if ("PHONE_PASS".equals(stage) || stage.startsWith("FIRST") || stage.startsWith("SECOND")
                || stage.startsWith("R") || "INVITED".equals(stage) || "SHOW_UP".equals(stage)
                || "CANDIDATE_REJECT".equals(stage)) {
            return "INTERVIEW";
        }
        // fallback by screen/phone
        if ("FAIL".equalsIgnoreCase(screenResult) || screenResult == null || screenResult.isBlank()) {
            if (!"PASS".equalsIgnoreCase(screenResult)) {
                return "SCREEN";
            }
        }
        if (!"PASS".equalsIgnoreCase(phoneResult)) {
            return "PHONE";
        }
        return "INTERVIEW";
    }

    private static String phaseLabel(String phase) {
        return switch (phase) {
            case "SCREEN" -> "简历筛选";
            case "PHONE" -> "电话沟通";
            case "INTERVIEW" -> "面试沟通";
            case "ONBOARD" -> "待入职";
            default -> phase;
        };
    }

    private List<HrPipelineDetailVO.TimelineItem> loadTimeline(Long applicationId) {
        return jdbc.query("""
                SELECT e.stage_code, s.stage_name, e.source_sheet, e.event_at
                FROM hr_stage_event e
                LEFT JOIN hr_stage_def s ON s.stage_code = e.stage_code
                WHERE e.application_id = ? AND e.is_active = 1
                ORDER BY e.event_at ASC, e.id ASC
                """, (rs, i) -> {
            HrPipelineDetailVO.TimelineItem item = new HrPipelineDetailVO.TimelineItem();
            item.setStageCode(rs.getString("stage_code"));
            item.setStageName(rs.getString("stage_name"));
            item.setSource(rs.getString("source_sheet"));
            Timestamp ts = rs.getTimestamp("event_at");
            item.setEventAt(ts == null ? null : ts.toLocalDateTime());
            item.setSummary(item.getStageName() == null ? item.getStageCode() : item.getStageName());
            return item;
        }, applicationId);
    }

    private List<HrPipelineDetailVO.InterviewItem> loadInterviews(Long applicationId) {
        return jdbc.query("""
                SELECT rec.round_no, u.nickname interviewer_name, rec.conclusion, rec.fail_reason,
                       rec.comment, rec.interviewed_at
                FROM hr_interview_record rec
                LEFT JOIN sys_user u ON u.user_id = rec.interviewer_user_id
                WHERE rec.application_id = ? AND rec.is_active = 1
                ORDER BY rec.round_no, rec.id
                """, (rs, i) -> {
            HrPipelineDetailVO.InterviewItem item = new HrPipelineDetailVO.InterviewItem();
            int round = rs.getInt("round_no");
            item.setRoundNo(round);
            item.setRoundName(roundName(round));
            item.setInterviewerName(rs.getString("interviewer_name"));
            item.setConclusion(rs.getString("conclusion"));
            item.setFailReason(rs.getString("fail_reason"));
            item.setComment(rs.getString("comment"));
            Timestamp ts = rs.getTimestamp("interviewed_at");
            item.setInterviewedAt(ts == null ? null : ts.toLocalDateTime());
            return item;
        }, applicationId);
    }

    private List<HrPipelineDetailVO.DocItem> loadDocs(Long applicationId) {
        List<HrPipelineDetailVO.DocItem> docs = new ArrayList<>();
        jdbc.query("""
                SELECT id, file_name, create_time, storage_path FROM hr_resume_file
                WHERE application_id = ? AND is_active = 1
                """, rs -> {
            while (rs.next()) {
                HrPipelineDetailVO.DocItem d = new HrPipelineDetailVO.DocItem();
                d.setId(rs.getLong("id"));
                d.setKind("简历");
                d.setFileName(rs.getString("file_name"));
                Timestamp ts = rs.getTimestamp("create_time");
                d.setCreateTime(ts == null ? null : ts.toLocalDateTime());
                d.setDownloadable(rs.getString("storage_path") != null);
                docs.add(d);
            }
            return null;
        }, applicationId);
        jdbc.query("""
                SELECT p.id, p.file_name, p.create_time, p.storage_path
                FROM hr_candidate_portfolio p
                JOIN hr_application a ON a.candidate_id = p.candidate_id
                WHERE a.id = ? AND p.is_active = 1 AND a.is_active = 1
                ORDER BY p.id DESC
                """, rs -> {
            while (rs.next()) {
                HrPipelineDetailVO.DocItem d = new HrPipelineDetailVO.DocItem();
                d.setId(rs.getLong("id"));
                d.setKind("作品集/背调资料");
                d.setFileName(rs.getString("file_name"));
                Timestamp ts = rs.getTimestamp("create_time");
                d.setCreateTime(ts == null ? null : ts.toLocalDateTime());
                d.setDownloadable(true);
                docs.add(d);
            }
            return null;
        }, applicationId);
        return docs;
    }

    private static String roundName(int round) {
        return switch (round) {
            case 1 -> "一面";
            case 2 -> "二面";
            case 3 -> "三面";
            case 4 -> "四面";
            case 5 -> "五面";
            default -> round + "面";
        };
    }

    private static String normalizePassFail(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(label + "不能为空");
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (!"PASS".equals(v) && !"FAIL".equals(v)) {
            throw new BusinessException(label + "只能是合适或不合适");
        }
        return v;
    }

    private static String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDate toDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        String text = String.valueOf(value);
        return text.length() >= 10 ? LocalDate.parse(text.substring(0, 10)) : null;
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime d) {
            return d;
        }
        if (value instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        String text = String.valueOf(value).replace('T', ' ');
        if (text.length() >= 19) {
            return LocalDateTime.parse(text.substring(0, 19));
        }
        return null;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private record PhaseMeta(String code, String label) {}
}
