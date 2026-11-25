package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.HrTalentMatchQueryDTO;
import com.base.admin.domain.dto.HrTalentNoteDTO;
import com.base.admin.domain.dto.HrTalentQueryDTO;
import com.base.admin.domain.dto.HrTalentRecommendDTO;
import com.base.admin.domain.dto.HrTalentSaveDTO;
import com.base.admin.domain.vo.HrCandidatePortfolioVO;
import com.base.admin.domain.vo.HrTalentMatchVO;
import com.base.admin.domain.vo.HrTalentProfileVO;
import com.base.admin.domain.vo.HrTalentSaveResultVO;
import com.base.admin.domain.vo.HrTalentVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HrTalentService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> POOL_STATUSES = Set.of("POOL", "IN_PROCESS", "ONBOARDED", "REACTIVATABLE");

    private static final String POOL_STATUS_SQL = """
            CASE
              WHEN EXISTS (
                SELECT 1 FROM hr_application oa
                WHERE oa.candidate_id = c.id AND oa.is_active = 1 AND oa.current_stage = 'ONBOARDED'
              ) THEN 'ONBOARDED'
              WHEN EXISTS (
                SELECT 1 FROM hr_application pa
                LEFT JOIN hr_stage_def ps ON ps.stage_code = pa.current_stage
                WHERE pa.candidate_id = c.id AND pa.is_active = 1
                  AND pa.requisition_id IS NOT NULL AND IFNULL(ps.terminal, 0) = 0
              ) THEN 'IN_PROCESS'
              WHEN EXISTS (
                SELECT 1 FROM hr_application ta
                JOIN hr_stage_def ts ON ts.stage_code = ta.current_stage AND ts.terminal = 1
                WHERE ta.candidate_id = c.id AND ta.is_active = 1 AND ta.current_stage <> 'ONBOARDED'
              ) THEN 'REACTIVATABLE'
              ELSE 'POOL'
            END""";

    private final JdbcTemplate jdbc;
    private final HrDataScope dataScope;
    private final HrTalentBlacklistService talentBlacklistService;

    public PageResult<HrTalentVO> page(HrTalentQueryDTO query) {
        HrTalentQueryDTO q = query == null ? new HrTalentQueryDTO() : query;
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), 100);
        StringBuilder where = new StringBuilder(" WHERE c.is_active = 1 ");
        List<Object> args = new ArrayList<>();
        where.append(HrTalentBlacklistMatch.notActiveSql("c"));
        appendScope(where, args);
        appendFilters(where, args, q);
        List<String> poolFilters = resolvePoolFilters(q);
        String from = """
                FROM (
                  SELECT c.id candidate_id, c.display_name, c.phone, c.email, c.gender, c.age, c.education,
                         c.candidate_no, c.current_title, c.current_company, c.city, c.work_years, c.expected_salary,
                         c.skill_tags, c.school_name, c.major, c.school_tags,
                         %s pool_status,
                         (SELECT COUNT(1) FROM hr_application a WHERE a.candidate_id = c.id AND a.is_active = 1) application_count,
                         (SELECT GROUP_CONCAT(a.id ORDER BY a.id) FROM hr_application a WHERE a.candidate_id = c.id AND a.is_active = 1) application_ids,
                         (SELECT a.id FROM hr_application a
                            LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                            WHERE a.candidate_id = c.id AND a.is_active = 1
                            ORDER BY CASE
                              WHEN a.requisition_id IS NOT NULL AND IFNULL(s.terminal, 0) = 0 THEN 0
                              WHEN IFNULL(s.terminal, 0) = 0 THEN 1
                              ELSE 2 END, a.id DESC
                            LIMIT 1) active_application_id,
                         NULLIF(GREATEST(
                           IFNULL((SELECT MAX(n.create_time) FROM hr_talent_note n
                                   WHERE n.candidate_id = c.id AND n.is_active = 1), '1000-01-01'),
                           IFNULL((SELECT MAX(p.called_at) FROM hr_phone_screen p
                                   JOIN hr_application a2 ON a2.id = p.application_id AND a2.is_active = 1
                                   WHERE a2.candidate_id = c.id AND p.is_active = 1), '1000-01-01')
                         ), '1000-01-01') last_contact_at,
                         (SELECT n.next_follow_at FROM hr_talent_note n
                            WHERE n.candidate_id = c.id AND n.is_active = 1 AND n.next_follow_at IS NOT NULL
                            ORDER BY n.id DESC LIMIT 1) next_follow_at
                  FROM hr_candidate c
                  %s
                ) t
                """.formatted(POOL_STATUS_SQL, where);
        String poolSql = "";
        if (!poolFilters.isEmpty()) {
            poolSql = " WHERE t.pool_status IN (" + String.join(",", poolFilters.stream().map(code -> "?").toList()) + ") ";
        }
        List<Object> countArgs = new ArrayList<>(args);
        countArgs.addAll(poolFilters);
        String countSql = "SELECT COUNT(1) " + from + poolSql;
        Long total = jdbc.queryForObject(countSql, Long.class, countArgs.toArray());
        long size = total == null ? 0 : total;
        if (size == 0) {
            return new PageResult<>(0, List.of());
        }
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.addAll(poolFilters);
        String pageSql = "SELECT * " + from + poolSql + " ORDER BY t.candidate_id DESC LIMIT ? OFFSET ? ";
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<HrTalentVO> rows = new ArrayList<>();
        List<Long> candidateIds = new ArrayList<>();
        List<Long> activeIds = new ArrayList<>();
        jdbc.query(pageSql, rs -> {
            HrTalentVO vo = new HrTalentVO();
            vo.setCandidateId(rs.getLong("candidate_id"));
            vo.setDisplayName(rs.getString("display_name"));
            vo.setPhone(blankToNull(rs.getString("phone")));
            vo.setEmail(blankToNull(rs.getString("email")));
            vo.setGender(blankToNull(rs.getString("gender")));
            int age = rs.getInt("age");
            vo.setAge(rs.wasNull() ? null : age);
            vo.setEducation(blankToNull(rs.getString("education")));
            vo.setCandidateNo(blankToNull(rs.getString("candidate_no")));
            vo.setCurrentTitle(blankToNull(rs.getString("current_title")));
            vo.setCurrentCompany(blankToNull(rs.getString("current_company")));
            vo.setCity(blankToNull(rs.getString("city")));
            vo.setWorkYears(blankToNull(rs.getString("work_years")));
            vo.setExpectedSalary(blankToNull(rs.getString("expected_salary")));
            vo.setSkillTags(blankToNull(rs.getString("skill_tags")));
            vo.setSchoolName(blankToNull(rs.getString("school_name")));
            vo.setMajor(blankToNull(rs.getString("major")));
            vo.setSchoolTags(blankToNull(rs.getString("school_tags")));
            vo.setBasicInfo(joinBasic(vo));
            String status = rs.getString("pool_status");
            vo.setPoolStatus(status);
            vo.setPoolStatusName(poolStatusName(status));
            vo.setApplicationCount(rs.getInt("application_count"));
            long activeId = rs.getLong("active_application_id");
            if (!rs.wasNull()) {
                vo.setActiveApplicationId(activeId);
                activeIds.add(activeId);
            }
            vo.setApplicationIds(parseIds(rs.getString("application_ids")));
            vo.setLastContactAt(fmt(rs.getTimestamp("last_contact_at")));
            vo.setNextFollowAt(fmtDate(rs.getTimestamp("next_follow_at")));
            rows.add(vo);
            candidateIds.add(vo.getCandidateId());
        }, pageArgs.toArray());
        attachActiveApplications(rows, activeIds);
        attachPortfolios(rows, candidateIds);
        return new PageResult<>(size, rows);
    }

    public HrTalentProfileVO profile(Long candidateId) {
        requireVisible(candidateId);
        HrTalentQueryDTO query = new HrTalentQueryDTO();
        query.setCandidateId(candidateId);
        query.setPageNum(1);
        query.setPageSize(1);
        List<HrTalentVO> rows = page(query).getRows();
        if (rows.isEmpty()) {
            throw new BusinessException("人才不存在");
        }
        HrTalentProfileVO profile = new HrTalentProfileVO();
        profile.setTalent(rows.get(0));
        profile.setApplications(loadApplications(candidateId));
        profile.setNotes(loadNotes(candidateId));
        profile.setPhones(loadPhones(candidateId));
        profile.setInterviews(loadInterviews(candidateId));
        profile.setOffers(loadOffers(candidateId));
        return profile;
    }

    public List<HrTalentMatchVO> match(HrTalentMatchQueryDTO query) {
        HrTalentMatchQueryDTO q = query == null ? new HrTalentMatchQueryDTO() : query;
        String phoneKey = HrTalentBlacklistMatch.phoneKey(q.getPhone());
        String emailKey = HrTalentBlacklistMatch.emailKey(q.getEmail());
        if (phoneKey.isEmpty() && emailKey.isEmpty()) {
            return List.of();
        }
        String phoneExpr = HrTalentBlacklistMatch.phoneKeySql("c");
        String sql = """
                SELECT c.id, c.display_name, c.candidate_no, c.phone, c.email, c.current_company, c.current_title,
                       %s pool_status
                FROM hr_candidate c
                WHERE c.is_active = 1
                  AND (
                    (? <> '' AND %s = ?)
                    OR (? <> '' AND LOWER(TRIM(IFNULL(c.email, ''))) = ?)
                  )
                """.formatted(POOL_STATUS_SQL, phoneExpr)
                + HrTalentBlacklistMatch.notActiveSql("c");
        List<Object> args = new ArrayList<>();
        args.add(phoneKey);
        args.add(phoneKey);
        args.add(emailKey);
        args.add(emailKey);
        if (q.getExcludeCandidateId() != null) {
            sql += " AND c.id <> ? ";
            args.add(q.getExcludeCandidateId());
        }
        sql += " ORDER BY CASE WHEN ? <> '' AND " + phoneExpr + " = ? THEN 0 ELSE 1 END, c.id DESC LIMIT 8 ";
        args.add(phoneKey);
        args.add(phoneKey);
        List<HrTalentMatchVO> list = new ArrayList<>();
        jdbc.query(sql, rs -> {
            HrTalentMatchVO vo = new HrTalentMatchVO();
            vo.setCandidateId(rs.getLong("id"));
            vo.setDisplayName(rs.getString("display_name"));
            vo.setCandidateNo(blankToNull(rs.getString("candidate_no")));
            vo.setPhone(blankToNull(rs.getString("phone")));
            vo.setEmail(blankToNull(rs.getString("email")));
            vo.setCurrentCompany(blankToNull(rs.getString("current_company")));
            vo.setCurrentTitle(blankToNull(rs.getString("current_title")));
            String status = rs.getString("pool_status");
            vo.setPoolStatus(status);
            vo.setPoolStatusName(poolStatusName(status));
            list.add(vo);
        }, args.toArray());
        return list;
    }

    /** 手机号优先，其次邮箱。没有联系方式时不合并，避免同名不同人被绑到一起。 */
    public Long findReusableCandidateId(String phone, String email) {
        HrTalentMatchQueryDTO query = new HrTalentMatchQueryDTO();
        query.setPhone(phone);
        query.setEmail(email);
        List<HrTalentMatchVO> hits = match(query);
        return hits.isEmpty() ? null : hits.get(0).getCandidateId();
    }

    public boolean hasOpenApplication(Long candidateId, Long requisitionId) {
        if (candidateId == null || requisitionId == null) {
            return false;
        }
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_application a
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                WHERE a.candidate_id = ? AND a.requisition_id = ? AND a.is_active = 1
                  AND IFNULL(s.terminal, 0) = 0
                """, Integer.class, candidateId, requisitionId);
        return n != null && n > 0;
    }

    @Transactional
    public HrTalentSaveResultVO save(HrTalentSaveDTO dto) {
        String name = dto.getDisplayName().trim();
        boolean editing = Boolean.TRUE.equals(dto.getEditing());
        Long matched = findReusableCandidateId(dto.getPhone(), dto.getEmail());
        boolean merged = false;
        Long candidateId;
        if (!editing) {
            if (dto.getCandidateId() != null) {
                if (matched != null && !matched.equals(dto.getCandidateId())) {
                    throw new BusinessException("手机号或邮箱对应的是另一位人才");
                }
                if (matched == null && !visible(dto.getCandidateId())) {
                    throw new BusinessException("人才不存在");
                }
                candidateId = dto.getCandidateId();
                merged = true;
            } else if (matched != null && !Boolean.TRUE.equals(dto.getForceNew())) {
                candidateId = matched;
                merged = true;
            } else {
                candidateId = null;
            }
            if (candidateId == null) {
                talentBlacklistService.assertCanCreate(name, dto.getPhone(), dto.getEmail());
                candidateId = insertCandidate(dto, name);
            } else {
                talentBlacklistService.assertCanUpdate(candidateId, name, dto.getPhone(), dto.getEmail());
                updateCandidate(candidateId, dto, name, false);
            }
        } else {
            if (dto.getCandidateId() == null) {
                throw new BusinessException("人才不存在");
            }
            requireVisible(dto.getCandidateId());
            if (matched != null && !matched.equals(dto.getCandidateId())) {
                throw new BusinessException("手机号或邮箱已被其他人才使用");
            }
            talentBlacklistService.assertCanUpdate(dto.getCandidateId(), name, dto.getPhone(), dto.getEmail());
            updateCandidate(dto.getCandidateId(), dto, name, true);
            candidateId = dto.getCandidateId();
        }
        ensureCandidateNo(candidateId, dto.getCandidateNo());
        Long applicationId = resolveApplication(candidateId, dto, !editing);
        HrTalentSaveResultVO result = new HrTalentSaveResultVO();
        result.setCandidateId(candidateId);
        result.setApplicationId(applicationId);
        result.setMerged(merged);
        return result;
    }

    @Transactional
    public Long recommend(HrTalentRecommendDTO dto) {
        requireVisible(dto.getCandidateId());
        Map<String, Object> person = requireCandidate(dto.getCandidateId());
        talentBlacklistService.assertCanUpdate(dto.getCandidateId(),
                String.valueOf(person.get("display_name")),
                stringOf(person.get("phone")), stringOf(person.get("email")));
        requireRequisition(dto.getRequisitionId());
        if (hasOpenApplication(dto.getCandidateId(), dto.getRequisitionId())) {
            throw new BusinessException("该人才已在此岗位流程中");
        }
        LocalDate submittedAt = dto.getSubmittedAt() == null ? LocalDate.now() : dto.getSubmittedAt();
        Submitter submitter = resolveSubmitter(dto.getSubmitterUserId());
        Long unbound = unboundApplicationId(dto.getCandidateId());
        if (unbound != null) {
            jdbc.update("""
                    UPDATE hr_application
                    SET requisition_id = ?,
                        channel_code = COALESCE(?, channel_code),
                        submitter_user_id = COALESCE(?, submitter_user_id),
                        submitter_name = CASE WHEN ? = '' THEN submitter_name ELSE ? END,
                        submitted_at = ?
                    WHERE id = ? AND is_active = 1 AND requisition_id IS NULL
                    """, dto.getRequisitionId(), emptyToNull(dto.getChannelCode()), submitter.userId(),
                    submitter.name(), submitter.name(), submittedAt, unbound);
            return unbound;
        }
        return insertApplication(dto.getCandidateId(), dto.getRequisitionId(), dto.getChannelCode(),
                submitter, submittedAt, true);
    }

    @Transactional
    public void addNote(HrTalentNoteDTO dto) {
        requireVisible(dto.getCandidateId());
        String content = dto.getContent().trim();
        Operator operator = operator();
        jdbc.update("""
                INSERT INTO hr_talent_note (candidate_id, content, next_follow_at, operator_id, operator_name, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, dto.getCandidateId(), content,
                dto.getNextFollowAt() == null ? null : dto.getNextFollowAt().atStartOfDay(),
                operator.userId(), operator.name(), operator.username());
    }

    @Transactional
    public void delete(Long candidateId) {
        requireVisible(candidateId);
        jdbc.update("UPDATE hr_application SET is_active = 0 WHERE candidate_id = ? AND is_active = 1", candidateId);
        int updated = jdbc.update("UPDATE hr_candidate SET is_active = 0 WHERE id = ? AND is_active = 1", candidateId);
        if (updated == 0) {
            throw new BusinessException("人才不存在");
        }
    }

    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的人才");
        }
        for (Long id : ids) {
            if (id != null) {
                delete(id);
            }
        }
    }

    private Long resolveApplication(Long candidateId, HrTalentSaveDTO dto, boolean mayOpen) {
        if (dto.getRequisitionId() != null) {
            requireRequisition(dto.getRequisitionId());
            if (hasOpenApplication(candidateId, dto.getRequisitionId())) {
                if (!mayOpen) {
                    return activeOrLatest(candidateId);
                }
                throw new BusinessException("该人才已在此岗位流程中");
            }
            if (!mayOpen) {
                return activeOrLatest(candidateId);
            }
            Long unbound = unboundApplicationId(candidateId);
            LocalDate submittedAt = dto.getSubmittedAt() == null ? LocalDate.now() : dto.getSubmittedAt();
            Submitter submitter = resolveSubmitter(dto.getSubmitterUserId());
            if (unbound != null) {
                jdbc.update("""
                        UPDATE hr_application
                        SET requisition_id = ?,
                            channel_code = COALESCE(?, channel_code),
                            submitter_user_id = COALESCE(?, submitter_user_id),
                            submitter_name = CASE WHEN ? = '' THEN submitter_name ELSE ? END,
                            submitted_at = ?
                        WHERE id = ? AND is_active = 1 AND requisition_id IS NULL
                        """, dto.getRequisitionId(), emptyToNull(dto.getChannelCode()), submitter.userId(),
                        submitter.name(), submitter.name(), submittedAt, unbound);
                return unbound;
            }
            return insertApplication(candidateId, dto.getRequisitionId(), dto.getChannelCode(), submitter, submittedAt, true);
        }
        Long unbound = unboundApplicationId(candidateId);
        if (unbound != null && (mayOpen || Boolean.TRUE.equals(dto.getEnsureApplication()))) {
            return unbound;
        }
        if (mayOpen || Boolean.TRUE.equals(dto.getEnsureApplication())) {
            LocalDate submittedAt = dto.getSubmittedAt() == null ? LocalDate.now() : dto.getSubmittedAt();
            Submitter submitter = resolveSubmitter(dto.getSubmitterUserId());
            return insertApplication(candidateId, null, dto.getChannelCode(), submitter, submittedAt, false);
        }
        return activeOrLatest(candidateId);
    }

    private Long insertApplication(Long candidateId, Long requisitionId, String channelCode, Submitter submitter,
                                   LocalDate submittedAt, boolean copyResume) {
        String user = SecurityUtils.getCurrentUsername();
        jdbc.update("""
                INSERT INTO hr_application (requisition_id, candidate_id, channel_code, resume_status,
                    submitter_user_id, submitter_name, submitted_at, current_stage, create_by, is_active)
                VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, 'ENTERED', ?, 1)
                """, requisitionId, candidateId, emptyToNull(channelCode), submitter.userId(), submitter.name(),
                submittedAt, user);
        Long applicationId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("""
                INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                VALUES (?, 'ENTERED', ?, 'RESUME', ?, 1)
                ON DUPLICATE KEY UPDATE is_active = 1
                """, applicationId, submittedAt.atStartOfDay(), user);
        if (copyResume) {
            jdbc.update("""
                    INSERT INTO hr_resume_file (application_id, file_name, storage_path, file_ext, create_by, is_active)
                    SELECT ?, f.file_name, f.storage_path, f.file_ext, ?, 1
                    FROM hr_resume_file f
                    JOIN hr_application a ON a.id = f.application_id AND a.is_active = 1
                    WHERE a.candidate_id = ? AND f.is_active = 1 AND a.id <> ?
                    ORDER BY f.id DESC
                    LIMIT 1
                    """, applicationId, user, candidateId, applicationId);
        }
        return applicationId;
    }

    private Long insertCandidate(HrTalentSaveDTO dto, String name) {
        jdbc.update("""
                INSERT INTO hr_candidate (display_name, phone, email, gender, age, education, candidate_no,
                    current_title, current_company, city, work_years, expected_salary, skill_tags,
                    school_name, major, school_tags, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, name, emptyToNull(dto.getPhone()), emptyToNull(dto.getEmail()), emptyToNull(dto.getGender()),
                dto.getAge(), emptyToNull(dto.getEducation()), emptyToNull(dto.getCandidateNo()),
                emptyToNull(dto.getCurrentTitle()), emptyToNull(dto.getCurrentCompany()),
                emptyToNull(dto.getCity()), emptyToNull(dto.getWorkYears()), emptyToNull(dto.getExpectedSalary()),
                emptyToNull(dto.getSkillTags()), emptyToNull(dto.getSchoolName()), emptyToNull(dto.getMajor()),
                emptyToNull(dto.getSchoolTags()), SecurityUtils.getCurrentUsername());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void updateCandidate(Long candidateId, HrTalentSaveDTO dto, String name, boolean replaceAll) {
        int updated;
        if (replaceAll) {
            updated = jdbc.update("""
                    UPDATE hr_candidate
                    SET display_name = ?, phone = ?, email = ?, gender = ?, age = ?, education = ?, candidate_no = ?,
                        current_title = ?, current_company = ?, city = ?, work_years = ?, expected_salary = ?,
                        skill_tags = ?, school_name = ?, major = ?, school_tags = ?
                    WHERE id = ? AND is_active = 1
                    """, name, emptyToNull(dto.getPhone()), emptyToNull(dto.getEmail()), emptyToNull(dto.getGender()),
                    dto.getAge(), emptyToNull(dto.getEducation()), emptyToNull(dto.getCandidateNo()),
                    emptyToNull(dto.getCurrentTitle()), emptyToNull(dto.getCurrentCompany()),
                    emptyToNull(dto.getCity()), emptyToNull(dto.getWorkYears()), emptyToNull(dto.getExpectedSalary()),
                    emptyToNull(dto.getSkillTags()), emptyToNull(dto.getSchoolName()), emptyToNull(dto.getMajor()),
                    emptyToNull(dto.getSchoolTags()), candidateId);
        } else {
            updated = jdbc.update("""
                    UPDATE hr_candidate
                    SET display_name = ?,
                        phone = COALESCE(?, phone), email = COALESCE(?, email), gender = COALESCE(?, gender),
                        age = COALESCE(?, age), education = COALESCE(?, education),
                        candidate_no = COALESCE(?, candidate_no),
                        current_title = COALESCE(?, current_title), current_company = COALESCE(?, current_company),
                        city = COALESCE(?, city), work_years = COALESCE(?, work_years),
                        expected_salary = COALESCE(?, expected_salary), skill_tags = COALESCE(?, skill_tags),
                        school_name = COALESCE(?, school_name), major = COALESCE(?, major),
                        school_tags = COALESCE(?, school_tags)
                    WHERE id = ? AND is_active = 1
                    """, name, emptyToNull(dto.getPhone()), emptyToNull(dto.getEmail()), emptyToNull(dto.getGender()),
                    dto.getAge(), emptyToNull(dto.getEducation()), emptyToNull(dto.getCandidateNo()),
                    emptyToNull(dto.getCurrentTitle()), emptyToNull(dto.getCurrentCompany()),
                    emptyToNull(dto.getCity()), emptyToNull(dto.getWorkYears()), emptyToNull(dto.getExpectedSalary()),
                    emptyToNull(dto.getSkillTags()), emptyToNull(dto.getSchoolName()), emptyToNull(dto.getMajor()),
                    emptyToNull(dto.getSchoolTags()), candidateId);
        }
        if (updated == 0) {
            throw new BusinessException("人才不存在");
        }
    }

    private void ensureCandidateNo(Long candidateId, String candidateNo) {
        if (candidateId == null || emptyToNull(candidateNo) != null) {
            return;
        }
        jdbc.update("""
                UPDATE hr_candidate
                SET candidate_no = CAST(14000000 + id AS CHAR)
                WHERE id = ? AND is_active = 1 AND (candidate_no IS NULL OR TRIM(candidate_no) = '')
                """, candidateId);
    }

    private Long unboundApplicationId(Long candidateId) {
        return jdbc.query("""
                SELECT a.id FROM hr_application a
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                WHERE a.candidate_id = ? AND a.is_active = 1 AND a.requisition_id IS NULL
                  AND IFNULL(s.terminal, 0) = 0
                ORDER BY a.id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, candidateId);
    }

    private Long activeOrLatest(Long candidateId) {
        return jdbc.query("""
                SELECT a.id FROM hr_application a
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                WHERE a.candidate_id = ? AND a.is_active = 1
                ORDER BY CASE
                  WHEN a.requisition_id IS NOT NULL AND IFNULL(s.terminal, 0) = 0 THEN 0
                  WHEN IFNULL(s.terminal, 0) = 0 THEN 1 ELSE 2 END, a.id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, candidateId);
    }

    private void requireVisible(Long candidateId) {
        if (candidateId == null || !visible(candidateId)) {
            throw new BusinessException("人才不存在");
        }
    }

    private boolean visible(Long candidateId) {
        HrTalentQueryDTO query = new HrTalentQueryDTO();
        query.setCandidateId(candidateId);
        query.setPageNum(1);
        query.setPageSize(1);
        return page(query).getTotal() > 0;
    }

    private Map<String, Object> requireCandidate(Long candidateId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, display_name, phone, email FROM hr_candidate WHERE id = ? AND is_active = 1
                """, candidateId);
        if (rows.isEmpty()) {
            throw new BusinessException("人才不存在");
        }
        return rows.get(0);
    }

    private void requireRequisition(Long requisitionId) {
        Integer ok = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_requisition WHERE id = ? AND is_active = 1", Integer.class, requisitionId);
        if (ok == null || ok == 0) {
            throw new BusinessException("招聘需求不存在");
        }
    }

    private void appendScope(StringBuilder sql, List<Object> args) {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            sql.append(" AND 1 = 0 ");
            return;
        }
        sql.append("""
                 AND (c.create_by = ? OR EXISTS (
                   SELECT 1 FROM hr_application a
                   LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                   WHERE a.candidate_id = c.id AND a.is_active = 1
                """);
        args.add(user.getUsername() == null ? "" : user.getUsername());
        dataScope.apply(sql, args, "r", "a");
        sql.append(")) ");
    }

    private void appendFilters(StringBuilder sql, List<Object> args, HrTalentQueryDTO q) {
        if (q.getCandidateId() != null) {
            sql.append(" AND c.id = ? ");
            args.add(q.getCandidateId());
        }
        if (StringUtils.hasText(q.getCandidateName())) {
            sql.append(" AND c.display_name LIKE ? ");
            args.add("%" + q.getCandidateName().trim() + "%");
        }
        if (StringUtils.hasText(q.getPhone())) {
            String like = "%" + q.getPhone().trim() + "%";
            sql.append(" AND (c.phone LIKE ? OR REPLACE(REPLACE(IFNULL(c.phone, ''), '-', ''), ' ', '') LIKE ?) ");
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(q.getCompany())) {
            sql.append(" AND c.current_company LIKE ? ");
            args.add("%" + q.getCompany().trim() + "%");
        }
        if (StringUtils.hasText(q.getTitle())) {
            sql.append(" AND c.current_title LIKE ? ");
            args.add("%" + q.getTitle().trim() + "%");
        }
        if (StringUtils.hasText(q.getSchool())) {
            sql.append(" AND (c.school_name LIKE ? OR c.major LIKE ?) ");
            String like = "%" + q.getSchool().trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(q.getTags())) {
            sql.append(" AND (c.skill_tags LIKE ? OR c.school_tags LIKE ?) ");
            String like = "%" + q.getTags().trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String like = "%" + q.getKeyword().trim() + "%";
            sql.append("""
                     AND (c.display_name LIKE ? OR c.candidate_no LIKE ? OR c.phone LIKE ? OR c.email LIKE ?
                       OR c.current_company LIKE ? OR c.current_title LIKE ? OR c.school_name LIKE ? OR c.major LIKE ?
                       OR c.skill_tags LIKE ? OR c.city LIKE ? OR c.education LIKE ? OR c.school_tags LIKE ?)
                    """);
            for (int i = 0; i < 12; i++) {
                args.add(like);
            }
        }
        if (q.getRequisitionId() != null || StringUtils.hasText(q.getChannelCode()) || StringUtils.hasText(q.getStageCode())
                || q.getSubmitterUserId() != null || q.getStartDate() != null || q.getEndDate() != null
                || (q.getOwnerUserIds() != null && !q.getOwnerUserIds().isEmpty())) {
            sql.append("""
                     AND EXISTS (
                       SELECT 1 FROM hr_application fa
                       LEFT JOIN hr_requisition fr ON fr.id = fa.requisition_id
                       WHERE fa.candidate_id = c.id AND fa.is_active = 1
                    """);
            if (q.getRequisitionId() != null) {
                sql.append(" AND fa.requisition_id = ? ");
                args.add(q.getRequisitionId());
            }
            if (StringUtils.hasText(q.getChannelCode())) {
                sql.append(" AND fa.channel_code = ? ");
                args.add(q.getChannelCode().trim());
            }
            if (StringUtils.hasText(q.getStageCode())) {
                sql.append(" AND fa.current_stage = ? ");
                args.add(q.getStageCode().trim());
            }
            if (q.getSubmitterUserId() != null) {
                sql.append(" AND fa.submitter_user_id = ? ");
                args.add(q.getSubmitterUserId());
            }
            if (q.getStartDate() != null) {
                sql.append(" AND fa.submitted_at >= ? ");
                args.add(q.getStartDate());
            }
            if (q.getEndDate() != null) {
                sql.append(" AND fa.submitted_at <= ? ");
                args.add(q.getEndDate());
            }
            HrOwnerFilters.append(sql, args, "fr", null, q.getOwnerUserIds());
            sql.append(") ");
        }
    }

    private void attachActiveApplications(List<HrTalentVO> rows, List<Long> activeIds) {
        if (activeIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", activeIds.stream().map(id -> "?").toList());
        Map<Long, HrTalentVO> byActive = new HashMap<>();
        for (HrTalentVO row : rows) {
            if (row.getActiveApplicationId() != null) {
                byActive.put(row.getActiveApplicationId(), row);
            }
        }
        jdbc.query("""
                SELECT a.id, a.requisition_id, r.job_name, a.current_stage, s.stage_name, a.create_time, a.submitted_at,
                       f.file_name,
                       (SELECT ai.score FROM hr_application_ai ai
                         WHERE ai.application_id = a.id AND ai.is_active = 1 ORDER BY ai.id DESC LIMIT 1) ai_score,
                       (SELECT COUNT(1) FROM hr_application_ai ai WHERE ai.application_id = a.id AND ai.is_active = 1) ai_count,
                       (CASE
                          WHEN IFNULL(NULLIF(TRIM(a.current_stage), ''), 'ENTERED') <> 'ENTERED' THEN 1
                          WHEN a.screen_result IS NOT NULL AND TRIM(a.screen_result) <> '' THEN 1
                          WHEN EXISTS (SELECT 1 FROM hr_phone_screen p WHERE p.application_id = a.id AND p.is_active = 1) THEN 1
                          WHEN EXISTS (SELECT 1 FROM hr_interview_record rec WHERE rec.application_id = a.id AND rec.is_active = 1) THEN 1
                          WHEN EXISTS (
                            SELECT 1 FROM hr_stage_event e
                            WHERE e.application_id = a.id AND e.is_active = 1
                              AND UPPER(TRIM(IFNULL(e.stage_code, ''))) <> 'ENTERED') THEN 1
                          ELSE 0
                        END) handled
                FROM hr_application a
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE a.id IN (%s)
                """.formatted(placeholders), rs -> {
            HrTalentVO vo = byActive.get(rs.getLong("id"));
            if (vo == null) {
                return;
            }
            long requisitionId = rs.getLong("requisition_id");
            vo.setActiveRequisitionId(rs.wasNull() ? null : requisitionId);
            vo.setActiveJobName(blankToNull(rs.getString("job_name")));
            vo.setActiveStage(blankToNull(rs.getString("current_stage")));
            vo.setActiveStageName(blankToNull(rs.getString("stage_name")));
            vo.setResumeName(blankToNull(rs.getString("file_name")));
            int score = rs.getInt("ai_score");
            vo.setAiScore(rs.wasNull() ? null : score);
            vo.setAiCount(rs.getInt("ai_count"));
            boolean handled = rs.getInt("handled") != 0;
            boolean pending = "IN_PROCESS".equals(vo.getPoolStatus()) && !handled;
            vo.setPending(pending);
            if (pending) {
                vo.setRemainingHours(remainingHours(rs.getTimestamp("create_time"), rs.getDate("submitted_at")));
            }
        }, activeIds.toArray());
    }

    private void attachPortfolios(List<HrTalentVO> rows, List<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return;
        }
        Map<Long, HrTalentVO> byId = new LinkedHashMap<>();
        for (HrTalentVO row : rows) {
            byId.put(row.getCandidateId(), row);
        }
        String placeholders = String.join(",", candidateIds.stream().map(id -> "?").toList());
        jdbc.query("""
                SELECT id, candidate_id, file_name, file_ext, file_size, create_time
                FROM hr_candidate_portfolio
                WHERE is_active = 1 AND candidate_id IN (%s)
                ORDER BY id DESC
                """.formatted(placeholders), rs -> {
            HrTalentVO vo = byId.get(rs.getLong("candidate_id"));
            if (vo == null) {
                return;
            }
            HrCandidatePortfolioVO item = new HrCandidatePortfolioVO();
            item.setId(rs.getLong("id"));
            item.setCandidateId(rs.getLong("candidate_id"));
            item.setFileName(rs.getString("file_name"));
            item.setFileExt(rs.getString("file_ext"));
            item.setFileSize(rs.getLong("file_size"));
            item.setCreateTime(fmt(rs.getTimestamp("create_time")));
            vo.getPortfolios().add(item);
        }, candidateIds.toArray());
    }

    private List<HrTalentProfileVO.Application> loadApplications(Long candidateId) {
        List<HrTalentProfileVO.Application> list = new ArrayList<>();
        jdbc.query("""
                SELECT a.id, a.requisition_id, r.job_name, a.channel_code, ch.channel_name, a.current_stage, s.stage_name,
                       IFNULL(s.terminal, 0) terminal, a.submitted_at, a.submitter_name, f.file_name
                FROM hr_application a
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_channel ch ON ch.channel_code = a.channel_code
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE a.candidate_id = ? AND a.is_active = 1
                ORDER BY a.id DESC
                """, rs -> {
            HrTalentProfileVO.Application item = new HrTalentProfileVO.Application();
            item.setApplicationId(rs.getLong("id"));
            long requisitionId = rs.getLong("requisition_id");
            item.setRequisitionId(rs.wasNull() ? null : requisitionId);
            item.setJobName(blankToNull(rs.getString("job_name")));
            item.setChannelCode(blankToNull(rs.getString("channel_code")));
            item.setChannelName(blankToNull(rs.getString("channel_name")));
            item.setStageCode(blankToNull(rs.getString("current_stage")));
            item.setStageName(blankToNull(rs.getString("stage_name")));
            item.setTerminal(rs.getInt("terminal") == 1);
            item.setSubmittedAt(fmtDate(rs.getDate("submitted_at")));
            item.setSubmitterName(blankToNull(rs.getString("submitter_name")));
            item.setResumeName(blankToNull(rs.getString("file_name")));
            list.add(item);
        }, candidateId);
        return list;
    }

    private List<HrTalentProfileVO.Note> loadNotes(Long candidateId) {
        List<HrTalentProfileVO.Note> list = new ArrayList<>();
        jdbc.query("""
                SELECT id, content, next_follow_at, operator_name, create_time
                FROM hr_talent_note
                WHERE candidate_id = ? AND is_active = 1
                ORDER BY id DESC LIMIT 50
                """, rs -> {
            HrTalentProfileVO.Note note = new HrTalentProfileVO.Note();
            note.setId(rs.getLong("id"));
            note.setContent(rs.getString("content"));
            note.setNextFollowAt(fmtDate(rs.getTimestamp("next_follow_at")));
            note.setOperatorName(blankToNull(rs.getString("operator_name")));
            note.setCreateTime(fmt(rs.getTimestamp("create_time")));
            list.add(note);
        }, candidateId);
        return list;
    }

    private List<HrTalentProfileVO.Phone> loadPhones(Long candidateId) {
        List<HrTalentProfileVO.Phone> list = new ArrayList<>();
        jdbc.query("""
                SELECT p.application_id, r.job_name, p.called_at, p.result, p.reject_reason, p.remark
                FROM hr_phone_screen p
                JOIN hr_application a ON a.id = p.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.candidate_id = ? AND p.is_active = 1
                ORDER BY p.called_at DESC, p.id DESC LIMIT 30
                """, rs -> {
            HrTalentProfileVO.Phone item = new HrTalentProfileVO.Phone();
            item.setApplicationId(rs.getLong("application_id"));
            item.setJobName(blankToNull(rs.getString("job_name")));
            item.setCalledAt(fmt(rs.getTimestamp("called_at")));
            item.setResult(blankToNull(rs.getString("result")));
            item.setRejectReason(blankToNull(rs.getString("reject_reason")));
            item.setRemark(blankToNull(rs.getString("remark")));
            list.add(item);
        }, candidateId);
        return list;
    }

    private List<HrTalentProfileVO.Interview> loadInterviews(Long candidateId) {
        List<HrTalentProfileVO.Interview> list = new ArrayList<>();
        jdbc.query("""
                SELECT rec.application_id, r.job_name, rec.round_no, rec.conclusion, rec.comment, rec.interviewed_at,
                       COALESCE(NULLIF(TRIM(u.nickname), ''), u.username) interviewer_name
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN sys_user u ON u.user_id = rec.interviewer_user_id
                WHERE a.candidate_id = ? AND rec.is_active = 1
                ORDER BY rec.interviewed_at DESC, rec.id DESC LIMIT 30
                """, rs -> {
            HrTalentProfileVO.Interview item = new HrTalentProfileVO.Interview();
            item.setApplicationId(rs.getLong("application_id"));
            item.setJobName(blankToNull(rs.getString("job_name")));
            item.setRoundNo(rs.getInt("round_no"));
            item.setConclusion(blankToNull(rs.getString("conclusion")));
            item.setComment(blankToNull(rs.getString("comment")));
            item.setInterviewedAt(fmt(rs.getTimestamp("interviewed_at")));
            item.setInterviewerName(blankToNull(rs.getString("interviewer_name")));
            list.add(item);
        }, candidateId);
        return list;
    }

    private List<HrTalentProfileVO.Offer> loadOffers(Long candidateId) {
        List<HrTalentProfileVO.Offer> list = new ArrayList<>();
        jdbc.query("""
                SELECT o.application_id, r.job_name, o.status, o.offered_at, o.salary_amount, o.reject_reason
                FROM hr_offer o
                JOIN hr_application a ON a.id = o.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.candidate_id = ? AND o.is_active = 1
                ORDER BY o.id DESC
                """, rs -> {
            HrTalentProfileVO.Offer item = new HrTalentProfileVO.Offer();
            item.setApplicationId(rs.getLong("application_id"));
            item.setJobName(blankToNull(rs.getString("job_name")));
            item.setStatus(blankToNull(rs.getString("status")));
            item.setOfferedAt(fmt(rs.getTimestamp("offered_at")));
            item.setSalaryAmount(rs.getBigDecimal("salary_amount") == null ? null : rs.getBigDecimal("salary_amount").toPlainString());
            item.setRejectReason(blankToNull(rs.getString("reject_reason")));
            list.add(item);
        }, candidateId);
        return list;
    }

    private Submitter resolveSubmitter(Long userId) {
        if (userId == null) {
            return new Submitter(null, "");
        }
        String nickname = jdbc.query("""
                SELECT COALESCE(NULLIF(TRIM(nickname), ''), username) FROM sys_user
                WHERE user_id = ? AND is_active = 1 AND status = 0
                """, rs -> rs.next() ? rs.getString(1) : null, userId);
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException("提交人不是系统用户");
        }
        return new Submitter(userId, nickname);
    }

    private Operator operator() {
        LoginUser user = SecurityUtils.getCurrentUser();
        Long userId = user == null ? null : user.getUserId();
        String username = SecurityUtils.getCurrentUsername();
        String nickname = username;
        if (userId != null) {
            String nick = jdbc.query("""
                    SELECT COALESCE(NULLIF(TRIM(nickname), ''), username) FROM sys_user WHERE user_id = ?
                    """, rs -> rs.next() ? rs.getString(1) : null, userId);
            if (StringUtils.hasText(nick)) {
                nickname = nick;
            }
        }
        return new Operator(userId, username, nickname);
    }

    private static List<String> resolvePoolFilters(HrTalentQueryDTO query) {
        List<String> codes = new ArrayList<>();
        if (query.getPoolStatuses() != null) {
            for (String raw : query.getPoolStatuses()) {
                String code = poolStatusOf(raw);
                if (code != null && !codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        if (!codes.isEmpty()) {
            return codes;
        }
        String one = poolStatusOf(query.getPoolStatus());
        return one == null ? List.of() : List.of(one);
    }

    private static String poolStatusOf(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String code = raw.trim().toUpperCase();
        if (!POOL_STATUSES.contains(code)) {
            throw new BusinessException("在库状态不正确");
        }
        return code;
    }

    static String poolStatusName(String code) {
        if (code == null) {
            return "在库可推荐";
        }
        return switch (code) {
            case "IN_PROCESS" -> "流程中";
            case "ONBOARDED" -> "已入职";
            case "REACTIVATABLE" -> "淘汰可激活";
            default -> "在库可推荐";
        };
    }

    private static String joinBasic(HrTalentVO vo) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(vo.getGender())) {
            parts.add(vo.getGender().trim());
        }
        if (vo.getAge() != null) {
            parts.add(vo.getAge() + "岁");
        }
        if (StringUtils.hasText(vo.getEducation())) {
            parts.add(vo.getEducation().trim());
        }
        if (StringUtils.hasText(vo.getCity())) {
            parts.add(vo.getCity().trim());
        }
        if (StringUtils.hasText(vo.getWorkYears())) {
            parts.add(vo.getWorkYears().trim());
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private static List<Long> parseIds(String text) {
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : text.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }

    private static Double remainingHours(Timestamp createTime, java.sql.Date submittedAt) {
        LocalDateTime start = createTime == null ? null : createTime.toLocalDateTime();
        if (start == null && submittedAt != null) {
            start = submittedAt.toLocalDate().atStartOfDay();
        }
        if (start == null) {
            return null;
        }
        Duration left = Duration.between(LocalDateTime.now(), start.plusHours(48));
        return Math.round((left.toMillis() / 3600000.0) * 100.0) / 100.0;
    }

    private static String fmt(Timestamp value) {
        if (value == null) {
            return null;
        }
        return value.toLocalDateTime().format(TS);
    }

    private static String fmtDate(java.util.Date value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate().toString();
        }
        return new Timestamp(value.getTime()).toLocalDateTime().toLocalDate().toString();
    }

    private static String blankToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private static String emptyToNull(String text) {
        return blankToNull(text);
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record Submitter(Long userId, String name) {}

    private record Operator(Long userId, String username, String name) {}
}
