package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.HrTalentBlacklistLogQueryDTO;
import com.base.admin.domain.dto.HrTalentBlacklistMoveDTO;
import com.base.admin.domain.dto.HrTalentBlacklistRestoreDTO;
import com.base.admin.domain.vo.HrTalentBlacklistLogVO;
import com.base.admin.domain.vo.HrTalentBlacklistVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HrTalentBlacklistService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final HrDataScope dataScope;

    /** 新增候选人、导入候选人之前调用。命中则不能入库。 */
    public void assertCanCreate(String name, String phone, String email) {
        String message = createBlockMessage(name, phone, email);
        if (message != null) {
            throw new BusinessException(message);
        }
    }

    /** 修改已有候选人：本人已在黑名单，或改成黑名单里的手机号/邮箱，都不能保存。 */
    public void assertCanUpdate(Long candidateId, String name, String phone, String email) {
        if (candidateId != null && isCandidateBlacklisted(candidateId)) {
            throw new BusinessException("该人才在黑名单中，不能修改");
        }
        String message = createBlockMessage(name, phone, email);
        if (message != null) {
            throw new BusinessException(message.replace("不能新增", "不能保存"));
        }
    }

    public void assertCanInvite(Long applicationId) {
        if (applicationId == null) {
            return;
        }
        Boolean hit = jdbc.query("""
                SELECT %s
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                WHERE a.id = ? AND a.is_active = 1
                """.formatted(HrTalentBlacklistMatch.activeExistsSql("c")),
                rs -> rs.next() ? rs.getBoolean(1) : null, applicationId);
        if (Boolean.TRUE.equals(hit)) {
            throw new BusinessException("该人才已在黑名单中，不能创建面试邀约");
        }
    }

    /**
     * @return 不能新增时的说明；可以新增时返回 null
     */
    public String createBlockMessage(String name, String phone, String email) {
        String phoneKey = HrTalentBlacklistMatch.phoneKey(phone);
        String emailKey = HrTalentBlacklistMatch.emailKey(email);
        String nameKey = HrTalentBlacklistMatch.nameKey(name, phoneKey, emailKey);
        String blockedName = jdbc.query("""
                SELECT display_name FROM hr_talent_blacklist
                WHERE is_active = 1 AND status = 'IN'
                  AND (
                    (? <> '' AND phone_key = ?)
                    OR (? <> '' AND email_key = ?)
                    OR (? <> '' AND phone_key = '' AND email_key = '' AND name_key = ?)
                  )
                LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null,
                phoneKey, phoneKey, emailKey, emailKey, nameKey, nameKey);
        if (!StringUtils.hasText(blockedName)) {
            return null;
        }
        String who = name == null ? "" : name.trim();
        if (!phoneKey.isEmpty()) {
            return "「" + who + "」的手机号与黑名单人才「" + blockedName + "」相同，不能新增";
        }
        if (!emailKey.isEmpty()) {
            return "「" + who + "」的邮箱与黑名单人才「" + blockedName + "」相同，不能新增";
        }
        return "「" + who + "」与黑名单人才「" + blockedName + "」同名，且都没有手机号和邮箱，不能新增";
    }

    public List<HrTalentBlacklistVO> list(String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, display_name, phone, email, reason, remark, operator_name, blacklisted_at, application_ids
                FROM hr_talent_blacklist
                WHERE is_active = 1 AND status = 'IN'
                """);
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (display_name LIKE ? OR phone LIKE ? OR IFNULL(email, '') LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY blacklisted_at DESC, id DESC");
        List<HrTalentBlacklistVO> rows = jdbc.query(sql.toString(), (rs, i) -> {
            HrTalentBlacklistVO vo = new HrTalentBlacklistVO();
            vo.setId(rs.getLong("id"));
            vo.setDisplayName(rs.getString("display_name"));
            vo.setPhone(rs.getString("phone"));
            vo.setEmail(rs.getString("email"));
            vo.setReason(rs.getString("reason"));
            vo.setRemark(rs.getString("remark"));
            vo.setOperatorName(rs.getString("operator_name"));
            vo.setBlacklistedAt(rs.getTimestamp("blacklisted_at") == null ? null
                    : rs.getTimestamp("blacklisted_at").toLocalDateTime().format(TS));
            vo.setJobSummary(rs.getString("application_ids"));
            return vo;
        }, args.toArray());
        fillJobs(rows);
        return rows;
    }

    public PageResult<HrTalentBlacklistLogVO> logs(HrTalentBlacklistLogQueryDTO query) {
        HrTalentBlacklistLogQueryDTO q = query == null ? new HrTalentBlacklistLogQueryDTO() : query;
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), 100);
        StringBuilder where = new StringBuilder(" WHERE is_active = 1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getAction())) {
            String action = q.getAction().trim().toUpperCase();
            if (!"IN".equals(action) && !"OUT".equals(action)) {
                throw new BusinessException("记录类型不正确");
            }
            where.append(" AND action = ? ");
            args.add(action);
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND (display_name LIKE ? OR IFNULL(phone, '') LIKE ? OR IFNULL(operator_name, '') LIKE ?) ");
            String like = "%" + q.getKeyword().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM hr_talent_blacklist_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<HrTalentBlacklistLogVO> rows = jdbc.query("""
                SELECT id, action, display_name, phone, email, reason, remark, operator_name, operated_at
                FROM hr_talent_blacklist_log
                """ + where + " ORDER BY operated_at DESC, id DESC LIMIT ? OFFSET ?", (rs, i) -> {
            HrTalentBlacklistLogVO vo = new HrTalentBlacklistLogVO();
            vo.setId(rs.getLong("id"));
            String action = rs.getString("action");
            vo.setAction(action);
            vo.setActionName("OUT".equals(action) ? "移出" : "移入");
            vo.setDisplayName(rs.getString("display_name"));
            vo.setPhone(rs.getString("phone"));
            vo.setEmail(rs.getString("email"));
            vo.setReason(rs.getString("reason"));
            vo.setRemark(rs.getString("remark"));
            vo.setOperatorName(rs.getString("operator_name"));
            vo.setOperatedAt(rs.getTimestamp("operated_at") == null ? null
                    : rs.getTimestamp("operated_at").toLocalDateTime().format(TS));
            return vo;
        }, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    @Transactional
    public void moveIn(HrTalentBlacklistMoveDTO dto) {
        String reason = required(dto.getReason(), "请填写移入原因", 255);
        String remark = required(dto.getRemark(), "请填写备注", 500);
        List<Long> applicationIds = distinctIds(dto.getApplicationIds());
        if (applicationIds.isEmpty()) {
            throw new BusinessException("请选择候选人");
        }
        for (Long applicationId : applicationIds) {
            assertVisible(applicationId);
        }
        List<Person> selected = loadByApplicationIds(applicationIds);
        if (selected.size() != applicationIds.size()) {
            throw new BusinessException("候选人不存在");
        }
        Map<String, List<Person>> groups = new LinkedHashMap<>();
        for (Person person : selected) {
            groups.computeIfAbsent(groupKey(person), key -> new ArrayList<>()).add(person);
        }
        Operator operator = operator();
        for (List<Person> group : groups.values()) {
            Person sample = group.get(0);
            if (alreadyActive(sample)) {
                throw new BusinessException("「" + sample.name + "」已在黑名单中");
            }
            List<Person> members = expand(sample, group);
            if (members.isEmpty()) {
                members = group;
            }
            insertActive(sample, members, reason, remark, operator);
        }
    }

    @Transactional
    public void moveOut(HrTalentBlacklistRestoreDTO dto) {
        String reason = required(dto.getReason(), "请填写移出原因", 255);
        String remark = required(dto.getRemark(), "请填写备注", 500);
        List<Long> ids = distinctIds(dto.getIds());
        if (ids.isEmpty()) {
            throw new BusinessException("请选择黑名单人才");
        }
        Operator operator = operator();
        for (Long id : ids) {
            ActiveRow row = jdbc.query("""
                    SELECT id, display_name, phone, email, candidate_ids, application_ids
                    FROM hr_talent_blacklist
                    WHERE id = ? AND is_active = 1 AND status = 'IN'
                    """, rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new ActiveRow(rs.getLong("id"), rs.getString("display_name"), rs.getString("phone"),
                        rs.getString("email"), rs.getString("candidate_ids"), rs.getString("application_ids"));
            }, id);
            if (row == null) {
                throw new BusinessException("黑名单记录不存在");
            }
            int updated = jdbc.update("""
                    UPDATE hr_talent_blacklist
                    SET status = 'OUT', released_at = NOW(), update_by = ?
                    WHERE id = ? AND is_active = 1 AND status = 'IN'
                    """, operator.username, id);
            if (updated == 0) {
                throw new BusinessException("黑名单记录不存在");
            }
            insertLog(id, "OUT", row.name, row.phone, row.email, reason, remark,
                    row.candidateIds, row.applicationIds, operator);
        }
    }

    private void insertActive(Person sample, List<Person> members, String reason, String remark, Operator operator) {
        Set<Long> candidateIds = new LinkedHashSet<>();
        Set<Long> applicationIds = new LinkedHashSet<>();
        for (Person member : members) {
            candidateIds.add(member.candidateId);
            applicationIds.add(member.applicationId);
        }
        String candidateText = joinIds(candidateIds);
        String applicationText = joinIds(applicationIds);
        jdbc.update("""
                INSERT INTO hr_talent_blacklist (
                  display_name, phone, email, phone_key, email_key, name_key, status,
                  reason, remark, candidate_ids, application_ids,
                  operator_id, operator_name, blacklisted_at, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 'IN', ?, ?, ?, ?, ?, ?, NOW(), ?, 1)
                """, sample.name, emptyToNull(sample.phone), emptyToNull(sample.email),
                sample.phoneKey, sample.emailKey, sample.nameKey,
                reason, remark, candidateText, applicationText,
                operator.userId, operator.nickname, operator.username);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        insertLog(id, "IN", sample.name, sample.phone, sample.email, reason, remark,
                candidateText, applicationText, operator);
    }

    private void insertLog(Long blacklistId, String action, String name, String phone, String email,
                           String reason, String remark, String candidateIds, String applicationIds, Operator operator) {
        jdbc.update("""
                INSERT INTO hr_talent_blacklist_log (
                  blacklist_id, action, display_name, phone, email, reason, remark,
                  candidate_ids, application_ids, operator_id, operator_name, operated_at, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, 1)
                """, blacklistId, action, name, emptyToNull(phone), emptyToNull(email), reason, remark,
                candidateIds, applicationIds, operator.userId, operator.nickname, operator.username);
    }

    private boolean alreadyActive(Person person) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_talent_blacklist
                WHERE is_active = 1 AND status = 'IN'
                  AND (
                    (? <> '' AND phone_key = ?)
                    OR (? <> '' AND email_key = ?)
                    OR FIND_IN_SET(?, IFNULL(candidate_ids, ''))
                  )
                """, Integer.class,
                person.phoneKey, person.phoneKey, person.emailKey, person.emailKey, String.valueOf(person.candidateId));
        return n != null && n > 0;
    }

    private boolean isCandidateBlacklisted(Long candidateId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1)
                FROM hr_candidate c
                WHERE c.id = ? AND c.is_active = 1 AND %s
                """.formatted(HrTalentBlacklistMatch.activeExistsSql("c")), Integer.class, candidateId);
        return n != null && n > 0;
    }

    private List<Person> expand(Person sample, List<Person> selected) {
        if (!sample.phoneKey.isEmpty()) {
            return loadMatching(" AND " + HrTalentBlacklistMatch.phoneKeySql("c") + " = ? ", sample.phoneKey);
        }
        if (!sample.emailKey.isEmpty()) {
            return loadMatching(" AND LOWER(TRIM(IFNULL(c.email, ''))) = ? ", sample.emailKey);
        }
        List<Long> candidateIds = selected.stream().map(person -> person.candidateId).distinct().toList();
        String marks = String.join(",", candidateIds.stream().map(id -> "?").toList());
        return loadMatching(" AND c.id IN (" + marks + ") ", candidateIds.toArray());
    }

    private List<Person> loadMatching(String extraSql, Object... args) {
        return jdbc.query("""
                SELECT a.id application_id, c.id candidate_id, c.display_name, c.phone, c.email
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id AND c.is_active = 1
                WHERE a.is_active = 1
                """ + extraSql, (rs, i) -> toPerson(rs), args);
    }

    private List<Person> loadByApplicationIds(List<Long> applicationIds) {
        String marks = String.join(",", applicationIds.stream().map(id -> "?").toList());
        return jdbc.query("""
                SELECT a.id application_id, c.id candidate_id, c.display_name, c.phone, c.email
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                WHERE a.is_active = 1 AND a.id IN (""" + marks + ") ", (rs, i) -> toPerson(rs), applicationIds.toArray());
    }

    private Person toPerson(java.sql.ResultSet rs) throws java.sql.SQLException {
        String name = rs.getString("display_name");
        String phone = rs.getString("phone");
        String email = rs.getString("email");
        String phoneKey = HrTalentBlacklistMatch.phoneKey(phone);
        String emailKey = HrTalentBlacklistMatch.emailKey(email);
        return new Person(rs.getLong("application_id"), rs.getLong("candidate_id"),
                name == null ? "" : name.trim(), phone, email, phoneKey, emailKey,
                HrTalentBlacklistMatch.nameKey(name, phoneKey, emailKey));
    }

    private void fillJobs(List<HrTalentBlacklistVO> rows) {
        Map<Long, List<Long>> appsByRow = new LinkedHashMap<>();
        Set<Long> applicationIds = new LinkedHashSet<>();
        for (HrTalentBlacklistVO row : rows) {
            List<Long> ids = parseIds(row.getJobSummary());
            appsByRow.put(row.getId(), ids);
            applicationIds.addAll(ids);
            row.setJobSummary("");
        }
        if (applicationIds.isEmpty()) {
            for (HrTalentBlacklistVO row : rows) {
                row.setJobSummary("—");
            }
            return;
        }
        String marks = String.join(",", applicationIds.stream().map(id -> "?").toList());
        Map<Long, String> labels = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.id, COALESCE(NULLIF(TRIM(r.job_name), ''), '未定岗') job_name,
                       COALESCE(NULLIF(TRIM(s.stage_name), ''), NULLIF(TRIM(a.current_stage), ''), '') stage_name
                FROM hr_application a
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage
                WHERE a.id IN (""" + marks + ") ", rs -> {
            String stage = rs.getString("stage_name");
            String label = rs.getString("job_name");
            if (StringUtils.hasText(stage)) {
                label = label + "（" + stage + "）";
            }
            labels.put(rs.getLong("id"), label);
        }, applicationIds.toArray());
        for (HrTalentBlacklistVO row : rows) {
            List<String> parts = new ArrayList<>();
            for (Long applicationId : appsByRow.getOrDefault(row.getId(), List.of())) {
                String label = labels.get(applicationId);
                if (label != null && !parts.contains(label)) {
                    parts.add(label);
                }
            }
            row.setJobSummary(parts.isEmpty() ? "—" : String.join("、", parts));
        }
    }

    private void assertVisible(Long applicationId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1) FROM hr_application a
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.id = ? AND a.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        args.add(applicationId);
        dataScope.apply(sql, args, "r", "a");
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        if (n == null || n == 0) {
            throw new BusinessException("候选人不存在");
        }
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

    private static String required(String value, String message, int max) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        String text = value.trim();
        if (text.length() > max) {
            throw new BusinessException(message);
        }
        return text;
    }

    private static List<Long> distinctIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        Set<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }

    private static String joinIds(Set<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static List<Long> parseIds(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : text.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略脏数据
            }
        }
        return ids;
    }

    private static String groupKey(Person person) {
        if (!person.phoneKey.isEmpty()) {
            return "p:" + person.phoneKey;
        }
        if (!person.emailKey.isEmpty()) {
            return "e:" + person.emailKey;
        }
        return "c:" + person.candidateId;
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Person(long applicationId, long candidateId, String name, String phone, String email,
                          String phoneKey, String emailKey, String nameKey) {
    }

    private record ActiveRow(long id, String name, String phone, String email, String candidateIds, String applicationIds) {
    }

    private record Operator(Long userId, String username, String nickname) {
    }
}
