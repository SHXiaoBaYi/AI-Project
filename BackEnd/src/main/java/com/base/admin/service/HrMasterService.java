package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrBoardViewDTO;
import com.base.admin.domain.dto.HrDepartmentDTO;
import com.base.admin.domain.dto.HrApplicationDTO;
import com.base.admin.domain.dto.HrDingTalkBindDTO;
import com.base.admin.domain.dto.HrJobCardQueryDTO;
import com.base.admin.domain.dto.HrJobCandidateQueryDTO;
import com.base.admin.domain.dto.HrJobCommStatsQueryDTO;
import com.base.admin.domain.dto.HrRequisitionDTO;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.HrBaseDictDTO;
import com.base.admin.domain.dto.HrFailReasonDTO;
import com.base.admin.domain.dto.HrTargetOptionDTO;
import com.base.admin.domain.dto.HrSchoolQueryDTO;
import com.base.admin.domain.vo.HrBaseDictVO;
import com.base.admin.domain.vo.HrDingTalkIdentityVO;
import com.base.admin.domain.vo.HrJobCardVO;
import com.base.admin.domain.vo.HrJobCandidateVO;
import com.base.admin.domain.vo.HrJobCommStatsVO;
import com.base.admin.domain.vo.HrSchoolVO;
import com.base.admin.domain.vo.HrStageCatalogVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrMasterService {

    private static final Set<String> JOB_FUNNEL_INTERVIEWING = Set.of(
            "INVITED", "SHOW_UP", "PHONE_PASS",
            "FIRST_PENDING", "FIRST_ROUND", "R1_DISPUTE",
            "SECOND_ROUND", "R2_PENDING", "R2_DISPUTE",
            "R3_PASS", "R3_PENDING", "R3_DISPUTE",
            "R4_PASS", "R4_PENDING", "R4_DISPUTE",
            "R5_PASS", "R5_PENDING", "R5_DISPUTE");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final HrDataScope dataScope;
    private final DataScopeFilter dataScopeFilter;
    private final DingTalkCalendarClient dingTalk;
    private final FileStorageService fileStorage;
    private final HrStageCatalog stageCatalog;
    private final HrKpiTaskService kpiTaskService;

    public List<Map<String, Object>> requisitions(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.job_name, r.job_desc, r.salary_range, r.education_req, r.experience_req, r.skill_req,
                       r.status, r.location_code, r.headcount, r.target_text, r.priority,
                       r.job_priority_code, r.work_address_id,
                       r.importance_level, r.urgency_level, r.difficulty_level,
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
                       (SELECT GROUP_CONCAT(CONCAT(rr.round_no, ':', IFNULL((
                            SELECT GROUP_CONCAT(c.cc_user_id ORDER BY c.id SEPARATOR '|')
                            FROM hr_requisition_round_cc c
                            WHERE c.requisition_id = rr.requisition_id AND c.round_no = rr.round_no AND c.is_active = 1
                        ), '')) ORDER BY rr.round_no SEPARATOR ',')
                        FROM hr_requisition_round rr
                        WHERE rr.requisition_id = r.id AND rr.is_active = 1) interview_round_ccs,
                       (SELECT GROUP_CONCAT(CONCAT(CASE rr.round_no WHEN 1 THEN '一面' WHEN 2 THEN '二面' WHEN 3 THEN '三面' WHEN 4 THEN '四面' ELSE '五面' END, ' ', IFNULL((
                            SELECT GROUP_CONCAT(u.nickname ORDER BY i.id SEPARATOR '、')
                            FROM hr_requisition_round_interviewer i
                            LEFT JOIN sys_user u ON u.user_id = i.interviewer_user_id
                            WHERE i.requisition_id = rr.requisition_id AND i.round_no = rr.round_no AND i.is_active = 1
                        ), ''), IFNULL((
                            SELECT CONCAT('（抄送：', GROUP_CONCAT(u.nickname ORDER BY c.id SEPARATOR '、'), '）')
                            FROM hr_requisition_round_cc c
                            LEFT JOIN sys_user u ON u.user_id = c.cc_user_id
                            WHERE c.requisition_id = rr.requisition_id AND c.round_no = rr.round_no AND c.is_active = 1
                        ), '')) ORDER BY rr.round_no SEPARATOR '；')
                        FROM hr_requisition_round rr
                        WHERE rr.requisition_id = r.id AND rr.is_active = 1) interview_flow
                FROM hr_requisition r
                LEFT JOIN hr_department d ON d.id = r.dept_id
                WHERE r.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (q.getRequisitionId() != null) {
            sql.append(" AND r.id = ? ");
            args.add(q.getRequisitionId());
        }
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            sql.append(" AND r.status = ? ");
            args.add(q.getStatus());
        }
        if (q.getLocationCode() != null && !q.getLocationCode().isBlank()) {
            sql.append(" AND r.location_code = ? ");
            args.add(q.getLocationCode());
        }
        if (q.getWorkAddressId() != null) {
            sql.append(" AND r.work_address_id = ? ");
            args.add(q.getWorkAddressId());
        }
        if (q.getDeptId() != null) {
            sql.append(" AND r.dept_id IN (SELECT id FROM hr_department WHERE is_active = 1 AND (id = ? OR CONCAT(',', ancestors, ',') LIKE CONCAT('%,', ?, ',%'))) ");
            args.add(q.getDeptId());
            args.add(String.valueOf(q.getDeptId()));
        }
        HrOwnerFilters.append(sql, args, "r", q.getOwnerUserId(), q.getOwnerUserIds());
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

    /**
     * 岗位看板第一屏：筛选 + 分页卡片。
     * 卡片状态：OPEN 且无候选人→待招；OPEN 且有候选人→进行中；其余→已关闭。
     */
    public HrJobCardVO jobCards(HrJobCardQueryDTO query) {
        HrJobCardQueryDTO q = query == null ? new HrJobCardQueryDTO() : query;
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), 100);

        String fromWhere = """
                FROM hr_requisition r
                LEFT JOIN hr_department d ON d.id = r.dept_id
                WHERE r.is_active = 1
                """;
        StringBuilder filter = new StringBuilder(fromWhere);
        List<Object> args = new ArrayList<>();
        appendJobCardFilters(filter, args, q);
        dataScope.apply(filter, args, "r", null);

        Long total = jdbc.queryForObject("SELECT COUNT(1) " + filter, Long.class, args.toArray());
        String listSql = """
                SELECT r.id, r.job_name, r.job_desc, r.salary_range, r.education_req, r.experience_req, r.skill_req,
                       r.status, r.location_code, r.headcount, r.target_text, r.priority, r.job_priority_code,
                       r.received_date, r.onboard_date, r.dept_id, d.name dept_name,
                       (SELECT COUNT(1) FROM hr_application a
                        WHERE a.requisition_id = r.id AND a.is_active = 1) candidate_count,
                       (SELECT COUNT(1) FROM hr_requisition_owner o
                        WHERE o.requisition_id = r.id AND o.is_active = 1) owner_count,
                       (SELECT GROUP_CONCAT(COALESCE(u.nickname, o.alias) ORDER BY o.sort_no SEPARATOR '、')
                        FROM hr_requisition_owner o
                        LEFT JOIN sys_user u ON u.user_id = o.user_id
                        WHERE o.requisition_id = r.id AND o.is_active = 1) owner_names,
                       (SELECT GROUP_CONCAT(o.user_id ORDER BY o.sort_no)
                        FROM hr_requisition_owner o
                        WHERE o.requisition_id = r.id AND o.is_active = 1 AND o.user_id IS NOT NULL) owner_user_ids,
                       (SELECT wa.name FROM hr_base_dict wa
                         WHERE wa.id = r.work_address_id AND wa.dict_type = 'WORK_ADDRESS' AND wa.is_active = 1
                         LIMIT 1) work_address_name,
                       (SELECT jp.name FROM hr_base_dict jp
                         WHERE jp.dict_type = 'JOB_PRIORITY' AND jp.is_active = 1
                           AND jp.code = r.job_priority_code LIMIT 1) job_priority_name,
                       (SELECT COUNT(1) FROM hr_application a
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND IFNULL(NULLIF(TRIM(a.current_stage), ''), 'ENTERED') = 'ENTERED'
                          AND (a.screen_result IS NULL OR TRIM(a.screen_result) = '')
                          AND NOT EXISTS (
                              SELECT 1 FROM hr_phone_screen p
                              WHERE p.application_id = a.id AND p.is_active = 1)
                          AND NOT EXISTS (
                              SELECT 1 FROM hr_interview_record rec
                              WHERE rec.application_id = a.id AND rec.is_active = 1)
                          AND NOT EXISTS (
                              SELECT 1 FROM hr_stage_event e
                              WHERE e.application_id = a.id AND e.is_active = 1
                                AND UPPER(TRIM(IFNULL(e.stage_code, ''))) <> 'ENTERED')
                       ) pending_count,
                       (SELECT COUNT(1) FROM hr_application a
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND a.current_stage IN (
                            'INVITED','SHOW_UP','PHONE_PASS',
                            'FIRST_PENDING','FIRST_ROUND','R1_DISPUTE',
                            'SECOND_ROUND','R2_PENDING','R2_DISPUTE',
                            'R3_PASS','R3_PENDING','R3_DISPUTE',
                            'R4_PASS','R4_PENDING','R4_DISPUTE',
                            'R5_PASS','R5_PENDING','R5_DISPUTE'
                          )) interviewing_count,
                       (SELECT COUNT(1) FROM hr_application a
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND a.current_stage = 'ONBOARDED') onboarded_count,
                       (SELECT COUNT(1) FROM hr_application a
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND a.create_time >= CURDATE()
                          AND a.create_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY)) today_screen_count,
                       (SELECT COUNT(DISTINCT a.id) FROM hr_application a
                        JOIN hr_phone_screen p ON p.application_id = a.id AND p.is_active = 1
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND p.called_at >= CURDATE()
                          AND p.called_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)) today_phone_count,
                       (SELECT COUNT(DISTINCT a.id) FROM hr_application a
                        JOIN hr_interview_invite i ON i.application_id = a.id AND i.is_active = 1
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND i.status <> 'CANCELLED'
                          AND i.interview_at >= CURDATE()
                          AND i.interview_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)) today_invite_count,
                       (SELECT COUNT(DISTINCT a.id) FROM hr_application a
                        JOIN hr_interview_record rec ON rec.application_id = a.id AND rec.is_active = 1
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND rec.conclusion IS NOT NULL AND TRIM(rec.conclusion) <> ''
                          AND COALESCE(rec.interviewed_at, rec.create_time) >= CURDATE()
                          AND COALESCE(rec.interviewed_at, rec.create_time) < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                       ) today_interview_count,
                       (SELECT COUNT(DISTINCT a.id) FROM hr_application a
                        JOIN hr_interview_record rec ON rec.application_id = a.id AND rec.is_active = 1
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND rec.conclusion = 'PASS'
                          AND COALESCE(rec.interviewed_at, rec.create_time) >= CURDATE()
                          AND COALESCE(rec.interviewed_at, rec.create_time) < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                       ) today_interview_pass_count,
                       (SELECT COUNT(1) FROM hr_application a
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND a.current_stage IN ('BG_COLLECT', 'BG_CHECK')) bg_check_count,
                       (SELECT COUNT(1) FROM hr_application a
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND a.current_stage = 'PENDING_ONBOARD') pending_onboard_count
                """ + filter + """
                 ORDER BY FIELD(r.priority,1,2,3), r.received_date DESC, r.id DESC
                 LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add((long) (pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(listSql, listArgs.toArray());

        HrJobCardVO vo = new HrJobCardVO();
        vo.setTotal(total == null ? 0L : total);
        for (Map<String, Object> row : rows) {
            vo.getRows().add(toJobCard(row));
        }
        return vo;
    }

    private static void appendJobCardFilters(StringBuilder sql, List<Object> args, HrJobCardQueryDTO q) {
        if (q.getDeptId() != null) {
            sql.append("""
                     AND r.dept_id IN (
                       SELECT id FROM hr_department
                       WHERE is_active = 1 AND (id = ? OR CONCAT(',', ancestors, ',') LIKE CONCAT('%,', ?, ',%'))
                     )
                    """);
            args.add(q.getDeptId());
            args.add(String.valueOf(q.getDeptId()));
        }
        if (q.getJobName() != null && !q.getJobName().isBlank()) {
            sql.append(" AND r.job_name LIKE ? ");
            args.add("%" + q.getJobName().trim() + "%");
        }
        HrOwnerFilters.append(sql, args, "r", q.getOwnerUserId(), q.getOwnerUserIds());
        if (q.getPriority() != null) {
            sql.append(" AND r.priority = ? ");
            args.add(q.getPriority());
        }
        if (q.getJobPriorityCode() != null && !q.getJobPriorityCode().isBlank()) {
            sql.append(" AND r.job_priority_code = ? ");
            args.add(q.getJobPriorityCode().trim());
        }
        if (q.getWorkAddressId() != null) {
            sql.append("""
                     AND (
                       r.work_address_id = ?
                       OR (
                         r.work_address_id IS NULL
                         AND r.location_code = (
                           SELECT CASE
                             WHEN name LIKE '%新疆%' THEN 'XJ'
                             WHEN name LIKE '%上海%' THEN 'SH'
                             ELSE NULL
                           END
                           FROM hr_base_dict
                           WHERE id = ? AND dict_type = 'WORK_ADDRESS' AND is_active = 1
                         )
                       )
                     )
                    """);
            args.add(q.getWorkAddressId());
            args.add(q.getWorkAddressId());
        }
        String cardStatus = q.getCardStatus() == null ? "" : q.getCardStatus().trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(cardStatus)) {
            sql.append(" AND r.status = 'OPEN' AND NOT EXISTS (SELECT 1 FROM hr_application a WHERE a.requisition_id = r.id AND a.is_active = 1) ");
        } else if ("ACTIVE".equals(cardStatus)) {
            sql.append(" AND r.status = 'OPEN' AND EXISTS (SELECT 1 FROM hr_application a WHERE a.requisition_id = r.id AND a.is_active = 1) ");
        } else if ("CLOSED".equals(cardStatus)) {
            sql.append(" AND r.status IN ('DONE','STOPPED','ARCHIVED','PAUSED') ");
        }
    }

    private static HrJobCardVO.JobCard toJobCard(Map<String, Object> row) {
        HrJobCardVO.JobCard card = new HrJobCardVO.JobCard();
        card.setId(((Number) row.get("id")).longValue());
        card.setJobName(stringOf(row.get("job_name")));
        card.setSalaryRange(stringOf(row.get("salary_range")));
        card.setEducationReq(stringOf(row.get("education_req")));
        card.setExperienceReq(stringOf(row.get("experience_req")));
        card.setSkillReq(stringOf(row.get("skill_req")));
        card.setResumeRequirement(joinResumeReq(card.getEducationReq(), card.getExperienceReq(), card.getSkillReq()));
        card.setJobSummary(summarize(stringOf(row.get("job_desc")), 120));
        card.setReceivedDate(dateOf(row.get("received_date")));
        card.setTargetText(stringOf(row.get("target_text")));
        card.setOnboardDate(dateOf(row.get("onboard_date")));
        card.setOwnerNames(stringOf(row.get("owner_names")));
        card.setOwnerUserIds(parseIds(stringOf(row.get("owner_user_ids"))));
        Object deptId = row.get("dept_id");
        card.setDeptId(deptId == null ? null : ((Number) deptId).longValue());
        card.setDeptName(stringOf(row.get("dept_name")));
        String status = stringOf(row.get("status"));
        card.setStatus(status);
        int candidateCount = row.get("candidate_count") == null ? 0 : ((Number) row.get("candidate_count")).intValue();
        card.setCandidateCount(candidateCount);
        String phase = resolveCardStatus(status, candidateCount);
        card.setCardStatus(phase);
        card.setCardStatusLabel(switch (phase) {
            case "PENDING" -> "待招";
            case "ACTIVE" -> "进行中";
            default -> "已关闭";
        });
        Object priority = row.get("priority");
        Integer p = priority == null ? null : ((Number) priority).intValue();
        card.setPriority(p);
        card.setPriorityLabel(p == null ? null : switch (p) {
            case 1 -> "紧急";
            case 2 -> "优先";
            case 3 -> "常规";
            default -> String.valueOf(p);
        });
        Object headcount = row.get("headcount");
        card.setHeadcount(headcount == null ? null : ((Number) headcount).intValue());
        card.setLocationCode(stringOf(row.get("location_code")));
        card.setOwnerCount(intOf(row.get("owner_count")));
        card.setPendingCount(intOf(row.get("pending_count")));
        card.setInterviewingCount(intOf(row.get("interviewing_count")));
        card.setOnboardedCount(intOf(row.get("onboarded_count")));
        card.setTodayScreenCount(intOf(row.get("today_screen_count")));
        card.setTodayPhoneCount(intOf(row.get("today_phone_count")));
        card.setTodayInviteCount(intOf(row.get("today_invite_count")));
        card.setTodayInterviewCount(intOf(row.get("today_interview_count")));
        card.setTodayInterviewPassCount(intOf(row.get("today_interview_pass_count")));
        card.setBgCheckCount(intOf(row.get("bg_check_count")));
        card.setPendingOnboardCount(intOf(row.get("pending_onboard_count")));
        String addr = stringOf(row.get("work_address_name"));
        if (addr == null || addr.isBlank()) {
            String loc = card.getLocationCode();
            addr = "XJ".equals(loc) ? "新疆" : ("SH".equals(loc) ? "上海" : loc);
        }
        card.setWorkLocation(addr);
        String priorityCode = stringOf(row.get("job_priority_code"));
        card.setJobPriorityCode(priorityCode);
        String priorityName = stringOf(row.get("job_priority_name"));
        if (priorityCode != null && !priorityCode.isBlank() && priorityName != null && !priorityName.isBlank()) {
            card.setJobPriorityLabel(priorityCode + " " + priorityName);
        } else if (priorityCode != null && !priorityCode.isBlank()) {
            card.setJobPriorityLabel(priorityCode);
        }
        return card;
    }

    private static Integer intOf(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static String resolveCardStatus(String status, int candidateCount) {
        if ("OPEN".equalsIgnoreCase(status)) {
            return candidateCount > 0 ? "ACTIVE" : "PENDING";
        }
        return "CLOSED";
    }

    private static String joinResumeReq(String education, String experience, String skill) {
        return Arrays.asList(education, experience, skill).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" / "));
    }

    private static String summarize(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, max) + "…";
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDate dateOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        if (value instanceof java.util.Date d) {
            return new Date(d.getTime()).toLocalDate();
        }
        String text = String.valueOf(value).trim();
        if (text.length() >= 10) {
            return LocalDate.parse(text.substring(0, 10));
        }
        return null;
    }

    private static List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return ids;
    }

    @Transactional
    public Long saveRequisition(HrRequisitionDTO dto) {
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
        String jobPriorityCode = resolveJobPriorityCode(dto.getJobPriorityCode());
        Long workAddressId = resolveWorkAddressId(dto.getWorkAddressId(), dto.getLocationCode());
        Long requisitionId = dto.getId();
        String locationCode = resolveLocationCode(requisitionId, workAddressId, dto.getLocationCode());
        if (requisitionId == null) {
            // urgency_level 与 priority 同源：紧急=1，其余按常规=2 冗余同步，展示以 priority 为准
            Integer urgency = dto.getPriority() != null && dto.getPriority() == 1 ? 1 : 2;
            jdbc.update("""
                    INSERT INTO hr_requisition (job_name, job_desc, salary_range, education_req, experience_req, skill_req,
                                               status, location_code, dept_id, headcount, target_text, priority,
                                               job_priority_code, work_address_id,
                                               importance_level, urgency_level, difficulty_level,
                                               received_date, onboard_date, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getJobName().trim(), emptyToNull(dto.getJobDesc()), emptyToNull(dto.getSalaryRange()),
                    emptyToNull(dto.getEducationReq()), emptyToNull(dto.getExperienceReq()), emptyToNull(dto.getSkillReq()),
                    locationCode, dto.getDeptId(), headcount, target, dto.getPriority(),
                    jobPriorityCode, workAddressId,
                    dto.getImportanceLevel(), urgency, dto.getDifficultyLevel(),
                    dto.getReceivedDate(), dto.getOnboardDate(), SecurityUtils.getCurrentUsername());
            requisitionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            dto.setId(requisitionId);
        } else {
            Integer urgency = dto.getPriority() != null && dto.getPriority() == 1 ? 1 : 2;
            int updated = jdbc.update("""
                    UPDATE hr_requisition
                    SET job_name = ?, job_desc = ?, salary_range = ?, education_req = ?, experience_req = ?, skill_req = ?,
                        location_code = ?, dept_id = ?, headcount = ?, target_text = ?,
                        priority = ?, job_priority_code = ?, work_address_id = ?,
                        importance_level = ?, urgency_level = ?, difficulty_level = ?,
                        received_date = ?, onboard_date = ?
                    WHERE id = ? AND is_active = 1
                    """, dto.getJobName().trim(), emptyToNull(dto.getJobDesc()), emptyToNull(dto.getSalaryRange()),
                    emptyToNull(dto.getEducationReq()), emptyToNull(dto.getExperienceReq()), emptyToNull(dto.getSkillReq()),
                    locationCode, dto.getDeptId(), headcount, target, dto.getPriority(),
                    jobPriorityCode, workAddressId,
                    dto.getImportanceLevel(), urgency, dto.getDifficultyLevel(),
                    dto.getReceivedDate(), dto.getOnboardDate(), requisitionId);
            if (updated == 0) {
                throw new BusinessException("需求不存在");
            }
        }
        replaceOwners(requisitionId, dto.getOwnerUserIds());
        replaceRounds(requisitionId, dto.getInterviewRounds());
        kpiTaskService.syncForRequisition(requisitionId);
        return requisitionId;
    }

    public void changeRequisitionStatus(Long id, String status) {
        if (!java.util.Set.of("OPEN", "PAUSED", "ARCHIVED", "DONE", "STOPPED").contains(status)) {
            throw new BusinessException("不支持的需求状态");
        }
        int updated = jdbc.update("UPDATE hr_requisition SET status = ? WHERE id = ? AND is_active = 1", status, id);
        if (updated == 0) {
            throw new BusinessException("需求不存在");
        }
        kpiTaskService.syncForRequisition(id);
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
        // 全量软删，避免 Excel 灌数留下的 alias-only（user_id 为空）脏行在改负责人后仍被 GROUP_CONCAT 拼进 owner_names
        jdbc.update("UPDATE hr_requisition_owner SET is_active = 0 WHERE requisition_id = ?", requisitionId);
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
        jdbc.update("UPDATE hr_requisition_round_cc SET is_active = 0 WHERE requisition_id = ?", requisitionId);
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
            if (item.getCcUserIds() != null) {
                for (Long userId : item.getCcUserIds()) {
                    if (userId == null) {
                        continue;
                    }
                    jdbc.update("""
                            INSERT INTO hr_requisition_round_cc (requisition_id, round_no, cc_user_id, create_by, is_active)
                            VALUES (?, ?, ?, ?, 1)
                            ON DUPLICATE KEY UPDATE is_active = 1
                            """, requisitionId, item.getRoundNo(), userId, SecurityUtils.getCurrentUsername());
                }
            }
        }
    }

    private String resolveJobPriorityCode(String raw) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            return null;
        }
        Integer ok = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_base_dict
                WHERE dict_type = ? AND code = ? AND is_active = 1
                """, Integer.class, Constants.HR_DICT_JOB_PRIORITY, code);
        if (ok == null || ok == 0) {
            throw new BusinessException("岗位优先级不在基础配置中，请先在「岗位优先级」里维护");
        }
        return code;
    }

    private Long resolveWorkAddressId(Long id, String locationCode) {
        if (id != null) {
            Integer ok = jdbc.queryForObject("""
                    SELECT COUNT(1) FROM hr_base_dict
                    WHERE id = ? AND dict_type = ? AND is_active = 1
                    """, Integer.class, id, Constants.HR_DICT_WORK_ADDRESS);
            if (ok == null || ok == 0) {
                throw new BusinessException("工作地址不在基础配置中，请先在「工作地址」里维护");
            }
            return id;
        }
        Long mapped = findWorkAddressIdByLocation(locationCode);
        if (mapped == null) {
            throw new BusinessException("请选择工作地址");
        }
        return mapped;
    }

    private Long findWorkAddressIdByLocation(String locationCode) {
        boolean xinjiang = "XJ".equalsIgnoreCase(locationCode == null ? "" : locationCode.trim());
        boolean shanghai = "SH".equalsIgnoreCase(locationCode == null ? "" : locationCode.trim());
        if (!xinjiang && !shanghai) {
            return null;
        }
        String exact = xinjiang ? "新疆" : "上海";
        String like = "%" + exact + "%";
        List<Long> ids = jdbc.query("""
                SELECT id FROM hr_base_dict
                WHERE dict_type = ? AND is_active = 1 AND (name = ? OR name LIKE ?)
                ORDER BY CASE WHEN name = ? THEN 0 ELSE 1 END, id
                LIMIT 1
                """, (rs, i) -> rs.getLong(1), Constants.HR_DICT_WORK_ADDRESS, exact, like, exact);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String resolveLocationCode(Long requisitionId, Long workAddressId, String fallback) {
        if (workAddressId != null) {
            String name = jdbc.query("""
                    SELECT name FROM hr_base_dict
                    WHERE id = ? AND dict_type = ? AND is_active = 1
                    """, rs -> rs.next() ? rs.getString(1) : null, workAddressId, Constants.HR_DICT_WORK_ADDRESS);
            if (name != null) {
                if (name.contains("新疆")) {
                    return "XJ";
                }
                if (name.contains("上海")) {
                    return "SH";
                }
            }
        }
        String loc = fallback == null ? "" : fallback.trim().toUpperCase(Locale.ROOT);
        if ("XJ".equals(loc) || "SH".equals(loc)) {
            return loc;
        }
        if (requisitionId != null) {
            String existing = jdbc.query("SELECT location_code FROM hr_requisition WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, requisitionId);
            if ("XJ".equals(existing) || "SH".equals(existing)) {
                return existing;
            }
        }
        return "SH";
    }

    private static String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private void ensureCandidateNo(Long candidateId, String candidateNo) {
        if (candidateId == null) {
            return;
        }
        if (emptyToNull(candidateNo) != null) {
            return;
        }
        jdbc.update("""
                UPDATE hr_candidate
                SET candidate_no = CAST(14000000 + id AS CHAR)
                WHERE id = ? AND is_active = 1 AND (candidate_no IS NULL OR TRIM(candidate_no) = '')
                """, candidateId);
    }

    private static boolean matchJobFunnel(Map<String, Object> row, String funnelStatus) {
        String bucket = funnelStatus == null ? "" : funnelStatus.trim().toUpperCase(Locale.ROOT);
        if (bucket.isEmpty()) {
            return true;
        }
        String stage = stringOf(row.get("current_stage"));
        if (stage == null || stage.isBlank()) {
            stage = "ENTERED";
        }
        return switch (bucket) {
            case "PENDING" -> Boolean.TRUE.equals(row.get("pending"));
            case "INTERVIEWING" -> JOB_FUNNEL_INTERVIEWING.contains(stage.toUpperCase(Locale.ROOT));
            case "ONBOARDED" -> "ONBOARDED".equalsIgnoreCase(stage);
            case "BG_CHECK" -> "BG_CHECK".equalsIgnoreCase(stage) || "BG_COLLECT".equalsIgnoreCase(stage);
            case "PENDING_ONBOARD" -> "PENDING_ONBOARD".equalsIgnoreCase(stage);
            default -> true;
        };
    }

    /** 按日/沟通类漏斗查出 application_id；非此类漏斗返回 null（走阶段匹配）。 */
    private Set<Long> resolveTodayFunnelIds(Long requisitionId, String funnelStatus) {
        String bucket = funnelStatus == null ? "" : funnelStatus.trim().toUpperCase(Locale.ROOT);
        String sql = switch (bucket) {
            case "TODAY_SCREEN" -> """
                    SELECT a.id FROM hr_application a
                    WHERE a.is_active = 1 AND a.requisition_id = ?
                      AND a.create_time >= CURDATE()
                      AND a.create_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                    """;
            case "TODAY_PHONE" -> """
                    SELECT DISTINCT a.id FROM hr_application a
                    JOIN hr_phone_screen p ON p.application_id = a.id AND p.is_active = 1
                    WHERE a.is_active = 1 AND a.requisition_id = ?
                      AND p.called_at >= CURDATE()
                      AND p.called_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                    """;
            case "TODAY_INVITE" -> """
                    SELECT DISTINCT a.id FROM hr_application a
                    JOIN hr_interview_invite i ON i.application_id = a.id AND i.is_active = 1
                    WHERE a.is_active = 1 AND a.requisition_id = ?
                      AND i.status <> 'CANCELLED'
                      AND i.interview_at >= CURDATE()
                      AND i.interview_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                    """;
            case "TODAY_INTERVIEW" -> """
                    SELECT DISTINCT a.id FROM hr_application a
                    JOIN hr_interview_record rec ON rec.application_id = a.id AND rec.is_active = 1
                    WHERE a.is_active = 1 AND a.requisition_id = ?
                      AND rec.conclusion IS NOT NULL AND TRIM(rec.conclusion) <> ''
                      AND COALESCE(rec.interviewed_at, rec.create_time) >= CURDATE()
                      AND COALESCE(rec.interviewed_at, rec.create_time) < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                    """;
            case "TODAY_INTERVIEW_PASS" -> """
                    SELECT DISTINCT a.id FROM hr_application a
                    JOIN hr_interview_record rec ON rec.application_id = a.id AND rec.is_active = 1
                    WHERE a.is_active = 1 AND a.requisition_id = ?
                      AND rec.conclusion = 'PASS'
                      AND COALESCE(rec.interviewed_at, rec.create_time) >= CURDATE()
                      AND COALESCE(rec.interviewed_at, rec.create_time) < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                    """;
            default -> null;
        };
        if (sql == null) {
            return null;
        }
        return new LinkedHashSet<>(jdbc.query(sql, (rs, i) -> rs.getLong(1), requisitionId));
    }

    private static void attachCandidateDisplay(Map<String, Object> row) {
        row.put("basic_info", buildBasicInfo(row));
        boolean pending = !isHandled(row);
        row.put("pending", pending);
        // 仅未产生任何处理记录时才计 48h；已沟通/改阶段/评价等显示 —
        row.put("remaining_hours", pending
                ? remainingHours(row.get("create_time"), row.get("submitted_at"))
                : null);
    }

    /**
     * 已处理：改过阶段、有初筛结果、电话沟通、面试评价，或阶段事件（入库 ENTERED 除外）。
     */
    private static boolean isHandled(Map<String, Object> row) {
        Object flag = row.get("handled");
        if (flag instanceof Number n) {
            return n.intValue() != 0;
        }
        if (flag instanceof Boolean b) {
            return b;
        }
        String stage = stringOf(row.get("current_stage"));
        if (stage != null && !stage.isBlank() && !"ENTERED".equalsIgnoreCase(stage)) {
            return true;
        }
        String screen = stringOf(row.get("screen_result"));
        return screen != null && !screen.isBlank();
    }

    private static HrJobCandidateVO toJobCandidate(Map<String, Object> row) {
        HrJobCandidateVO vo = new HrJobCandidateVO();
        Object appId = row.get("id");
        vo.setApplicationId(appId == null ? null : ((Number) appId).longValue());
        Object cid = row.get("candidate_id");
        vo.setCandidateId(cid == null ? null : ((Number) cid).longValue());
        vo.setDisplayName(stringOf(row.get("display_name")));
        vo.setPending(Boolean.TRUE.equals(row.get("pending")));
        vo.setGender(stringOf(row.get("gender")));
        Object age = row.get("age");
        vo.setAge(age == null ? null : ((Number) age).intValue());
        vo.setEducation(stringOf(row.get("education")));
        vo.setBasicInfo(stringOf(row.get("basic_info")));
        vo.setCandidateNo(stringOf(row.get("candidate_no")));
        vo.setCurrentTitle(stringOf(row.get("current_title")));
        vo.setCurrentCompany(stringOf(row.get("current_company")));
        LocalDate recommended = dateOf(row.get("submitted_at"));
        vo.setRecommendedAt(recommended == null ? null : recommended.toString());
        Object hours = row.get("remaining_hours");
        vo.setRemainingHours(hours == null ? null : ((Number) hours).doubleValue());
        vo.setReferrerName(stringOf(row.get("submitter_name")));
        vo.setCurrentStage(stringOf(row.get("current_stage")));
        vo.setStageName(stringOf(row.get("stage_name")));
        vo.setResumeName(stringOf(row.get("file_name")));
        return vo;
    }

    private static String buildBasicInfo(Map<String, Object> row) {
        List<String> parts = new ArrayList<>();
        String gender = stringOf(row.get("gender"));
        if (gender != null && !gender.isBlank()) {
            parts.add(gender.trim());
        }
        Object age = row.get("age");
        if (age instanceof Number n) {
            parts.add(String.valueOf(n.intValue()));
        }
        String education = stringOf(row.get("education"));
        if (education != null && !education.isBlank()) {
            parts.add(education.trim());
        }
        return String.join(" | ", parts);
    }

    private static Double remainingHours(Object createTime, Object submittedAt) {
        LocalDateTime start = dateTimeOf(createTime);
        if (start == null) {
            LocalDate day = dateOf(submittedAt);
            if (day == null) {
                return null;
            }
            start = day.atStartOfDay();
        }
        Duration left = Duration.between(LocalDateTime.now(), start.plusHours(48));
        return Math.round((left.toMillis() / 3600000.0) * 100.0) / 100.0;
    }

    private static LocalDateTime dateTimeOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime t) {
            return t;
        }
        if (value instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (value instanceof java.util.Date d) {
            return new Timestamp(d.getTime()).toLocalDateTime();
        }
        return null;
    }

    public List<Map<String, Object>> applications(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, c.id candidate_id, c.display_name, c.phone, c.email, c.gender, c.age, c.education, c.candidate_no,
                       c.current_title, c.current_company, a.requisition_id, r.job_name, a.channel_code, ch.channel_name,
                       a.resume_status, a.screen_result, a.current_stage, s.stage_name, a.submitter_user_id, a.submitter_name, a.submitted_at, a.create_time,
                       f.file_name,
                       (SELECT ai.score FROM hr_application_ai ai WHERE ai.application_id = a.id AND ai.is_active = 1 ORDER BY ai.id DESC LIMIT 1) ai_score,
                       (SELECT COUNT(1) FROM hr_application_ai ai WHERE ai.application_id = a.id AND ai.is_active = 1) ai_count,
                       (SELECT COUNT(1) FROM hr_candidate_portfolio p WHERE p.candidate_id = c.id AND p.is_active = 1) portfolio_count,
                       (SELECT GROUP_CONCAT(CONCAT(p.id, ':', p.file_name) ORDER BY p.id DESC SEPARATOR '||')
                          FROM hr_candidate_portfolio p WHERE p.candidate_id = c.id AND p.is_active = 1) portfolio_files,
                       (CASE
                          WHEN IFNULL(NULLIF(TRIM(a.current_stage), ''), 'ENTERED') <> 'ENTERED' THEN 1
                          WHEN a.screen_result IS NOT NULL AND TRIM(a.screen_result) <> '' THEN 1
                          WHEN EXISTS (
                              SELECT 1 FROM hr_phone_screen p
                              WHERE p.application_id = a.id AND p.is_active = 1) THEN 1
                          WHEN EXISTS (
                              SELECT 1 FROM hr_interview_record rec
                              WHERE rec.application_id = a.id AND rec.is_active = 1) THEN 1
                          WHEN EXISTS (
                              SELECT 1 FROM hr_stage_event e
                              WHERE e.application_id = a.id AND e.is_active = 1
                                AND UPPER(TRIM(IFNULL(e.stage_code, ''))) <> 'ENTERED') THEN 1
                          ELSE 0
                        END) handled
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
        HrOwnerFilters.append(sql, args, "r", q.getOwnerUserId(), q.getOwnerUserIds());
        dataScope.apply(sql, args, "r", "a");
        sql.append(" ORDER BY a.submitted_at DESC, a.id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            attachCandidateDisplay(row);
        }
        return rows;
    }

    public PageResult<HrJobCandidateVO> jobCandidates(HrJobCandidateQueryDTO query) {
        HrJobCandidateQueryDTO q = query == null ? new HrJobCandidateQueryDTO() : query;
        if (q.getRequisitionId() == null) {
            throw new BusinessException("请选择岗位");
        }
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), 100);
        HrBoardQueryDTO filter = new HrBoardQueryDTO();
        filter.setRequisitionId(q.getRequisitionId());
        Set<Long> todayIds = resolveTodayFunnelIds(q.getRequisitionId(), q.getFunnelStatus());
        List<Map<String, Object>> all = applications(filter).stream()
                .filter(row -> {
                    if (todayIds != null) {
                        Object id = row.get("id");
                        return id != null && todayIds.contains(((Number) id).longValue());
                    }
                    return matchJobFunnel(row, q.getFunnelStatus());
                })
                .toList();
        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        List<HrJobCandidateVO> page = new ArrayList<>();
        for (Map<String, Object> row : all.subList(from, to)) {
            page.add(toJobCandidate(row));
        }
        return new PageResult<>((long) all.size(), page);
    }

    /** 岗位候选人抽屉：该岗位下电话不通过 / 标记不合适原因饼图与明细。 */
    public HrJobCommStatsVO jobCommStats(HrJobCommStatsQueryDTO query) {
        if (query == null || query.getRequisitionId() == null) {
            throw new BusinessException("请选择岗位");
        }
        Long requisitionId = query.getRequisitionId();
        Integer ok = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_requisition WHERE id = ? AND is_active = 1", Integer.class, requisitionId);
        if (ok == null || ok == 0) {
            throw new BusinessException("岗位不存在");
        }
        HrJobCommStatsVO vo = new HrJobCommStatsVO();
        vo.setPhoneFailReasons(buildUnsuitableReasonSlices("""
                SELECT COALESCE(NULLIF(TRIM(p.reject_reason), ''), '未填写') reason_name, COUNT(1) cnt
                FROM hr_phone_screen p
                JOIN hr_application a ON a.id = p.application_id AND a.is_active = 1
                WHERE p.is_active = 1 AND UPPER(TRIM(p.result)) = 'FAIL' AND a.requisition_id = ?
                GROUP BY COALESCE(NULLIF(TRIM(p.reject_reason), ''), '未填写')
                """, requisitionId));
        vo.setScreenFailReasons(buildUnsuitableReasonSlices("""
                SELECT COALESCE(NULLIF(TRIM(a.screen_reject_reason), ''), '未填写') reason_name, COUNT(1) cnt
                FROM hr_application a
                WHERE a.is_active = 1 AND UPPER(TRIM(COALESCE(a.screen_result, ''))) = 'FAIL'
                  AND a.requisition_id = ?
                GROUP BY COALESCE(NULLIF(TRIM(a.screen_reject_reason), ''), '未填写')
                """, requisitionId));
        vo.setDetails(loadJobCommDetails(requisitionId));
        return vo;
    }

    private List<HrJobCommStatsVO.PieSlice> buildUnsuitableReasonSlices(String groupSql, Long requisitionId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(groupSql, rs -> {
            counts.put(rs.getString("reason_name"), rs.getLong("cnt"));
        }, requisitionId);
        List<String> ordered = jdbc.query("""
                SELECT name FROM hr_base_dict
                WHERE dict_type = ? AND is_active = 1
                ORDER BY sort_no, id
                """, (rs, row) -> rs.getString(1), Constants.HR_DICT_UNSUITABLE);
        List<HrJobCommStatsVO.PieSlice> slices = new ArrayList<>();
        for (String name : ordered) {
            long value = counts.getOrDefault(name, 0L);
            if (value <= 0) {
                continue;
            }
            HrJobCommStatsVO.PieSlice slice = new HrJobCommStatsVO.PieSlice();
            slice.setName(name);
            slice.setKey(name);
            slice.setValue(value);
            slices.add(slice);
            counts.remove(name);
        }
        List<String> extras = new ArrayList<>(counts.keySet());
        extras.sort(Comparator.naturalOrder());
        for (String name : extras) {
            long value = counts.getOrDefault(name, 0L);
            if (value <= 0) {
                continue;
            }
            HrJobCommStatsVO.PieSlice slice = new HrJobCommStatsVO.PieSlice();
            slice.setName(name);
            slice.setKey(name);
            slice.setValue(value);
            slices.add(slice);
        }
        return slices;
    }

    private List<HrJobCommStatsVO.Detail> loadJobCommDetails(Long requisitionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id application_id, c.display_name, 'PHONE_FAIL' record_type, '电话沟通不通过' record_type_label,
                       p.result, p.reject_reason reason, p.remark, p.called_at occurred_at,
                       a.current_stage, s.stage_name, f.file_name resume_name
                FROM hr_phone_screen p
                JOIN hr_application a ON a.id = p.application_id AND a.is_active = 1
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE p.is_active = 1 AND UPPER(TRIM(p.result)) = 'FAIL' AND a.requisition_id = ?
                UNION ALL
                SELECT a.id application_id, c.display_name, 'SCREEN_FAIL' record_type, '标记不合适' record_type_label,
                       a.screen_result result, a.screen_reject_reason reason, NULL remark,
                       COALESCE(
                         (SELECT MAX(t.event_at) FROM hr_stage_event t
                          WHERE t.application_id = a.id AND t.stage_code = 'SCREEN_FAIL' AND t.is_active = 1),
                         a.update_time, a.create_time
                       ) occurred_at,
                       a.current_stage, s.stage_name, f.file_name resume_name
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE a.is_active = 1 AND UPPER(TRIM(COALESCE(a.screen_result, ''))) = 'FAIL'
                  AND a.requisition_id = ?
                ORDER BY occurred_at DESC, application_id DESC
                """, requisitionId, requisitionId);
        List<HrJobCommStatsVO.Detail> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            HrJobCommStatsVO.Detail d = new HrJobCommStatsVO.Detail();
            Object appId = row.get("application_id");
            d.setApplicationId(appId == null ? null : ((Number) appId).longValue());
            d.setDisplayName(Objects.toString(row.get("display_name"), ""));
            d.setRecordType(Objects.toString(row.get("record_type"), ""));
            d.setRecordTypeLabel(Objects.toString(row.get("record_type_label"), ""));
            Object result = row.get("result");
            d.setResult(result == null ? null : String.valueOf(result));
            Object reason = row.get("reason");
            d.setReason(reason == null || String.valueOf(reason).isBlank() ? null : String.valueOf(reason).trim());
            Object remark = row.get("remark");
            d.setRemark(remark == null || String.valueOf(remark).isBlank() ? null : String.valueOf(remark).trim());
            d.setOccurredAt(fmtDateTime(dateTimeOf(row.get("occurred_at"))));
            Object stage = row.get("current_stage");
            d.setCurrentStage(stage == null ? null : String.valueOf(stage));
            Object stageName = row.get("stage_name");
            d.setStageName(stageName == null ? null : String.valueOf(stageName));
            Object resume = row.get("resume_name");
            d.setResumeName(resume == null ? null : String.valueOf(resume));
            list.add(d);
        }
        return list;
    }

    private static String fmtDateTime(LocalDateTime at) {
        if (at == null) {
            return null;
        }
        String text = at.toString().replace('T', ' ');
        return text.length() > 19 ? text.substring(0, 19) : text;
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
                    INSERT INTO hr_candidate (display_name, phone, email, gender, age, education, candidate_no,
                                              current_title, current_company, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getDisplayName().trim(), emptyToNull(dto.getPhone()), emptyToNull(dto.getEmail()),
                    emptyToNull(dto.getGender()), dto.getAge(), emptyToNull(dto.getEducation()),
                    emptyToNull(dto.getCandidateNo()), emptyToNull(dto.getCurrentTitle()),
                    emptyToNull(dto.getCurrentCompany()), user);
            Long candidateId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            ensureCandidateNo(candidateId, dto.getCandidateNo());
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
                UPDATE hr_candidate
                SET display_name = ?, phone = ?, email = ?, gender = ?, age = ?, education = ?, candidate_no = ?,
                    current_title = ?, current_company = ?
                WHERE id = ? AND is_active = 1
                """, dto.getDisplayName().trim(), emptyToNull(dto.getPhone()), emptyToNull(dto.getEmail()),
                emptyToNull(dto.getGender()), dto.getAge(), emptyToNull(dto.getEducation()),
                emptyToNull(dto.getCandidateNo()), emptyToNull(dto.getCurrentTitle()),
                emptyToNull(dto.getCurrentCompany()), candidateId);
        ensureCandidateNo(candidateId, dto.getCandidateNo());
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
        String storagePath = fileStorage.toRelativeUploadPath(stored);
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
                    """, original, storagePath, ext, user, applicationId);
        } else {
            jdbc.update("""
                    INSERT INTO hr_resume_file (application_id, file_name, storage_path, file_ext, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, 1)
                    """, applicationId, original, storagePath, ext, user);
        }
    }

    private Long requireCandidateIdByApplication(Long applicationId) {
        Long candidateId = jdbc.query("""
                SELECT candidate_id FROM hr_application WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getLong(1) : null, applicationId);
        if (candidateId == null) {
            throw new BusinessException("候选人不存在");
        }
        return candidateId;
    }

    public List<com.base.admin.domain.vo.HrCandidatePortfolioVO> listPortfolios(Long applicationId) {
        Long candidateId = requireCandidateIdByApplication(applicationId);
        return jdbc.query("""
                SELECT id, candidate_id, file_name, file_ext, file_size, create_time
                FROM hr_candidate_portfolio
                WHERE candidate_id = ? AND is_active = 1
                ORDER BY id DESC
                """, (rs, rowNum) -> {
            com.base.admin.domain.vo.HrCandidatePortfolioVO vo = new com.base.admin.domain.vo.HrCandidatePortfolioVO();
            vo.setId(rs.getLong("id"));
            vo.setCandidateId(rs.getLong("candidate_id"));
            vo.setFileName(rs.getString("file_name"));
            vo.setFileExt(rs.getString("file_ext"));
            vo.setFileSize(rs.getLong("file_size"));
            java.sql.Timestamp ts = rs.getTimestamp("create_time");
            vo.setCreateTime(ts == null ? null : ts.toLocalDateTime().toString().replace('T', ' '));
            return vo;
        }, candidateId);
    }

    @Transactional
    public Long uploadPortfolio(Long applicationId, org.springframework.web.multipart.MultipartFile file) {
        Long candidateId = requireCandidateIdByApplication(applicationId);
        Path stored = fileStorage.saveHrPortfolio(file);
        String original = file.getOriginalFilename() == null ? stored.getFileName().toString() : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : null;
        if (ext != null && ext.length() > 16) {
            ext = ext.substring(0, 16);
        }
        String user = SecurityUtils.getCurrentUsername();
        jdbc.update("""
                INSERT INTO hr_candidate_portfolio (candidate_id, file_name, storage_path, file_ext, file_size, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, candidateId, original, stored.toString(), ext, file.getSize(), user);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional
    public void deletePortfolio(Long portfolioId) {
        int updated = jdbc.update(
                "UPDATE hr_candidate_portfolio SET is_active = 0 WHERE id = ? AND is_active = 1", portfolioId);
        if (updated == 0) {
            throw new BusinessException("作品集附件不存在");
        }
    }

    public Path portfolioFile(Long portfolioId) {
        return jdbc.query("""
                SELECT file_name, storage_path FROM hr_candidate_portfolio WHERE id = ? AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                throw new BusinessException("作品集附件不存在");
            }
            Path path = Path.of(rs.getString("storage_path"));
            if (!java.nio.file.Files.isRegularFile(path)) {
                throw new BusinessException("作品集文件不在服务器上");
            }
            return path;
        }, portfolioId);
    }

    public String portfolioDisplayName(Long portfolioId) {
        return jdbc.query("""
                SELECT file_name FROM hr_candidate_portfolio WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : "portfolio", portfolioId);
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

    public List<HrBaseDictVO> dictOptions(String type) {
        DictKind kind = DictKind.of(type);
        return jdbc.query("""
                SELECT id, dict_type, code, category, name, sort_no
                FROM hr_base_dict
                WHERE dict_type = ? AND is_active = 1
                ORDER BY sort_no, id
                """, (rs, row) -> {
            HrBaseDictVO vo = new HrBaseDictVO();
            vo.setId(rs.getLong("id"));
            vo.setDictType(rs.getString("dict_type"));
            vo.setCode(rs.getString("code"));
            vo.setCategory(rs.getString("category"));
            vo.setName(rs.getString("name"));
            vo.setSortNo(rs.getInt("sort_no"));
            return vo;
        }, kind.type);
    }

    @Transactional
    public void saveDict(String type, HrBaseDictDTO dto) {
        DictKind kind = DictKind.of(type);
        assertDictEdit(kind);
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException(kind.label + "不能为空");
        }
        String code = dto.getCode() == null ? "" : dto.getCode().trim();
        if (kind.requireCode && code.isEmpty()) {
            throw new BusinessException("请填写编码，例如 P0");
        }
        if (!kind.requireCode) {
            code = "";
        }
        String category = dto.getCategory() == null ? "" : dto.getCategory().trim();
        if (kind.requireCategory && category.isEmpty()) {
            throw new BusinessException("请填写地址类别");
        }
        if (!kind.requireCategory) {
            category = "";
        }
        int sort = dto.getSortNo() == null ? 0 : dto.getSortNo();
        long excludeId = dto.getId() == null ? 0L : dto.getId();
        Integer nameDup = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_base_dict
                WHERE dict_type = ? AND name = ? AND is_active = 1 AND id <> ?
                """, Integer.class, kind.type, name, excludeId);
        if (nameDup != null && nameDup > 0) {
            throw new BusinessException(kind.label + "已存在");
        }
        if (!code.isEmpty()) {
            Integer codeDup = jdbc.queryForObject("""
                    SELECT COUNT(1) FROM hr_base_dict
                    WHERE dict_type = ? AND code = ? AND is_active = 1 AND id <> ?
                    """, Integer.class, kind.type, code, excludeId);
            if (codeDup != null && codeDup > 0) {
                throw new BusinessException("编码「" + code + "」已存在");
            }
        }
        if (dto.getId() == null) {
            Long inactiveId = jdbc.query("""
                    SELECT id FROM hr_base_dict WHERE dict_type = ? AND name = ? AND is_active = 0 ORDER BY id DESC LIMIT 1
                    """, rs -> rs.next() ? rs.getLong(1) : null, kind.type, name);
            if (inactiveId != null) {
                jdbc.update("""
                        UPDATE hr_base_dict SET code = ?, category = ?, sort_no = ?, is_active = 1, update_by = ? WHERE id = ?
                        """, code, category, sort, SecurityUtils.getCurrentUsername(), inactiveId);
                return;
            }
            jdbc.update("""
                    INSERT INTO hr_base_dict (dict_type, code, category, name, sort_no, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, 1)
                    """, kind.type, code, category, name, sort, SecurityUtils.getCurrentUsername());
            return;
        }
        int updated = jdbc.update("""
                UPDATE hr_base_dict SET code = ?, category = ?, name = ?, sort_no = ?, update_by = ?
                WHERE id = ? AND dict_type = ? AND is_active = 1
                """, code, category, name, sort, SecurityUtils.getCurrentUsername(), dto.getId(), kind.type);
        if (updated == 0) {
            throw new BusinessException(kind.label + "不存在");
        }
    }

    @Transactional
    public void deleteDict(String type, Long id) {
        DictKind kind = DictKind.of(type);
        assertDictEdit(kind);
        String name = jdbc.query("""
                SELECT name FROM hr_base_dict WHERE id = ? AND dict_type = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : null, id, kind.type);
        if (name == null) {
            throw new BusinessException(kind.label + "不存在");
        }
        if (kind == DictKind.UNSUITABLE) {
            Integer used = jdbc.queryForObject("""
                    SELECT COUNT(1) FROM hr_phone_screen
                    WHERE is_active = 1 AND TRIM(reject_reason) = ?
                    """, Integer.class, name);
            if (used != null && used > 0) {
                throw new BusinessException("已有电话沟通使用该理由，不能删除");
            }
        }
        jdbc.update("UPDATE hr_base_dict SET is_active = 0, update_by = ? WHERE id = ?",
                SecurityUtils.getCurrentUsername(), id);
    }

    private void assertDictEdit(DictKind kind) {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未授权");
        }
        Set<String> perms = user.getPermissions();
        if (perms != null && perms.contains(Constants.ADMIN_PERM)) {
            return;
        }
        if (perms != null && perms.contains(kind.editPerm)) {
            return;
        }
        throw new BusinessException(403, "权限不足，无法访问");
    }

    private enum DictKind {
        WORK_ADDRESS(Constants.HR_DICT_WORK_ADDRESS, "工作地址", "hr:work-address:edit", false, true),
        BG_FAIL(Constants.HR_DICT_BG_FAIL, "背调不通过原因", "hr:bg-fail:edit", false, false),
        OFFER_UNSENT(Constants.HR_DICT_OFFER_UNSENT, "offer未发原因", "hr:offer-unsent:edit", false, false),
        UNSUITABLE(Constants.HR_DICT_UNSUITABLE, "电话&面试不合适理由", "hr:unsuitable:edit", false, false),
        JOB_PRIORITY(Constants.HR_DICT_JOB_PRIORITY, "岗位优先级", "hr:job-priority:edit", true, false);

        private final String type;
        private final String label;
        private final String editPerm;
        private final boolean requireCode;
        private final boolean requireCategory;

        DictKind(String type, String label, String editPerm, boolean requireCode, boolean requireCategory) {
            this.type = type;
            this.label = label;
            this.editPerm = editPerm;
            this.requireCode = requireCode;
            this.requireCategory = requireCategory;
        }

        static DictKind of(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new BusinessException("字典类型不能为空");
            }
            String key = raw.trim().toUpperCase(Locale.ROOT);
            for (DictKind kind : values()) {
                if (kind.type.equals(key)) {
                    return kind;
                }
            }
            throw new BusinessException("不支持的基础数据类型");
        }
    }

    public List<Map<String, Object>> stages() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT stage_code, stage_name, sort_no, terminal
                FROM hr_stage_def WHERE is_active = 1 ORDER BY sort_no
                """);
        for (Map<String, Object> row : rows) {
            String code = row.get("stage_code") == null ? null : String.valueOf(row.get("stage_code"));
            row.put("phase", stageCatalog.phaseOf(code));
            row.put("funnelBucket", stageCatalog.funnelBucketOf(code));
            row.put("stageName", row.get("stage_name"));
            row.put("stageCode", code);
        }
        return rows;
    }

    public HrStageCatalogVO stageCatalog() {
        return stageCatalog.catalog();
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
        Path local = findLocalResumeFile(applicationId);
        if (local != null) {
            return local;
        }
        throw new BusinessException("简历文件不在服务器上");
    }

    /** 本地磁盘上的简历；本机连远程库时通常为空（文件在公网机器上） */
    public Path findLocalResumeFile(Long applicationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT file_name, storage_path FROM hr_resume_file WHERE application_id = ? AND is_active = 1", applicationId);
        if (rows.isEmpty()) {
            return null;
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
            return null;
        }
        try (var walk = Files.walk(root)) {
            Path found = walk.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals(fileName))
                    .findFirst().orElse(null);
            if (found == null) {
                return null;
            }
            jdbc.update("UPDATE hr_resume_file SET storage_path = ? WHERE application_id = ? AND is_active = 1",
                    fileStorage.toRelativeUploadPath(found), applicationId);
            return found;
        } catch (Exception ex) {
            return null;
        }
    }

    /** 公网可打开的简历地址（本地无文件时回退用） */
    public String resumePublicUrl(Long applicationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT storage_path FROM hr_resume_file WHERE application_id = ? AND is_active = 1", applicationId);
        if (rows.isEmpty() || rows.get(0).get("storage_path") == null) {
            return null;
        }
        String url = fileStorage.toPublicUrl(String.valueOf(rows.get(0).get("storage_path")));
        if (url == null || url.isBlank() || !url.contains("/uploads/")) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return null;
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
        return buildTree(dataScopeFilter.filterHrDepartmentsFlat(flat), 0L);
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
        List<Map<String, Object>> list;
        if ("owner".equals(scope)) {
            list = jdbc.queryForList("""
                    SELECT DISTINCT u.user_id, u.username, u.nickname, u.phone,
                           CASE WHEN d.user_id IS NULL THEN 0 ELSE 1 END dingtalk_bound
                    FROM sys_user u
                    JOIN sys_user_role ur ON ur.user_id = u.user_id AND ur.is_active = 1
                    JOIN sys_role r ON r.role_id = ur.role_id AND r.role_key IN ('hr_admin', 'hr_owner') AND r.is_active = 1
                    LEFT JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                    WHERE u.is_active = 1 AND u.status = 0
                    ORDER BY u.user_id
                    """);
        } else if ("interviewer".equals(scope)) {
            list = jdbc.queryForList("""
                    SELECT DISTINCT u.user_id, u.username, u.nickname, u.phone,
                           CASE WHEN d.user_id IS NULL THEN 0 ELSE 1 END dingtalk_bound
                    FROM sys_user u
                    JOIN sys_user_role ur ON ur.user_id = u.user_id AND ur.is_active = 1
                    JOIN sys_role r ON r.role_id = ur.role_id AND r.role_key IN ('hr_admin', 'hr_owner', 'hr_interviewer') AND r.is_active = 1
                    LEFT JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                    WHERE u.is_active = 1 AND u.status = 0
                    ORDER BY u.user_id
                    """);
        } else {
            list = jdbc.queryForList("""
                    SELECT u.user_id, u.username, u.nickname, u.phone,
                           CASE WHEN d.user_id IS NULL THEN 0 ELSE 1 END dingtalk_bound
                    FROM sys_user u
                    LEFT JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                    WHERE u.is_active = 1 AND u.status = 0
                    ORDER BY u.user_id
                    """);
        }
        return dataScopeFilter.filterHrUsers(list);
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
                ON DUPLICATE KEY UPDATE dingtalk_user_id = VALUES(dingtalk_user_id), dingtalk_union_id = VALUES(dingtalk_union_id),
                  is_active = 1, update_by = VALUES(create_by)
                """, dto.getUserId(), identity.getDingtalkUserId(), identity.getUnionId(), SecurityUtils.getCurrentUsername());
    }

    /**
     * 按手机号静默刷新企业钉钉绑定（优先专属账号）。失败不抛错，避免打断建日程主流程。
     */
    public void refreshDingTalkBindingQuietly(Long userId) {
        if (userId == null || !dingTalk.configured()) {
            return;
        }
        try {
            String phone = jdbc.query("SELECT phone FROM sys_user WHERE user_id = ? AND is_active = 1",
                    rs -> rs.next() ? rs.getString(1) : null, userId);
            if (phone == null || phone.isBlank()) {
                return;
            }
            DingTalkCalendarClient.DingIdentity identity = resolveDingIdentity(userId, phone.trim());
            if (identity == null || identity.unionId() == null || identity.unionId().isBlank()) {
                return;
            }
            jdbc.update("""
                    INSERT INTO hr_user_dingtalk (user_id, dingtalk_user_id, dingtalk_union_id, create_by, is_active)
                    VALUES (?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE dingtalk_user_id = VALUES(dingtalk_user_id),
                      dingtalk_union_id = VALUES(dingtalk_union_id), is_active = 1,
                      update_by = VALUES(create_by)
                    """, userId, identity.userId(), identity.unionId(), SecurityUtils.getCurrentUsername());
        } catch (Exception ignored) {
            // 静默：通讯录暂时不可达时仍用库里已有绑定
        }
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
