package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.TrainQaCalibrateQueryDTO;
import com.base.admin.domain.dto.TrainQaCalibrateSaveDTO;
import com.base.admin.domain.dto.TrainQaFeedbackDTO;
import com.base.admin.domain.dto.TrainQaHandleDTO;
import com.base.admin.domain.vo.TrainAssistantAskVO;
import com.base.admin.domain.vo.TrainQaCalibrateVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import com.base.admin.util.TrainQaAnswerSanitizer;
import com.base.admin.util.TrainQaHandleStatuses;
import com.base.admin.util.TrainQaIntentMatcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 培训答疑人工校准库：准的答案绑定文档版本。
 * 换版后旧「准」标为 STALE，答疑仍可作兜底（需标注参考版本）；最新版「准」优先直接返回。
 */
@Slf4j
@Service
public class TrainQaCalibrateService {

    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_STALE = "STALE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TrainDocService trainDocService;
    private final TrainQaItemService trainQaItemService;

    public TrainQaCalibrateService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                   @Lazy TrainDocService trainDocService,
                                   @Lazy TrainQaItemService trainQaItemService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.trainDocService = trainDocService;
        this.trainQaItemService = trainQaItemService;
    }

    /**
     * 答疑校准命中：本培训文档下「准」(APPROVED) 或换版后遗留的准 (STALE)，同意图。
     * 优先最新版校准；无则返回历史版本准确答案（由调用方决定是否先搜最新文档）。
     */
    public AccurateHit findAccurateHit(String userQuestion, long docId, long latestVersionId) {
        String norm = normalizeIntent(userQuestion);
        if (!StringUtils.hasText(norm)) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, c.version_id, c.user_question, c.topic_title, c.answer_text, c.answer_html,
                       c.images_json, c.aliases_json, c.status,
                       IFNULL(v.version_label, CONCAT('v', IFNULL(v.version_no, c.version_id))) version_label
                FROM train_qa_calibrate c
                LEFT JOIN train_doc_version v ON v.id = c.version_id
                WHERE c.is_active = 1 AND c.doc_id = ? AND c.status IN (?, ?)
                ORDER BY CASE WHEN c.version_id = ? THEN 0 ELSE 1 END,
                         CASE WHEN c.status = ? THEN 0 ELSE 1 END,
                         c.update_time DESC
                LIMIT 300
                """, docId, STATUS_APPROVED, STATUS_STALE, latestVersionId, STATUS_APPROVED);
        Map<String, Object> best = null;
        for (Map<String, Object> row : rows) {
            if (!matchesIntent(norm, row)) {
                continue;
            }
            if (TrainQaAnswerSanitizer.looksLikeSourceCode(str(row.get("answer_text")), str(row.get("answer_html")))) {
                log.warn("跳过疑似代码的校准命中 id={}", row.get("id"));
                continue;
            }
            best = row;
            break;
        }
        if (best == null) {
            return null;
        }
        Long id = ((Number) best.get("id")).longValue();
        long hitVersionId = ((Number) best.get("version_id")).longValue();
        jdbc.update("UPDATE train_qa_calibrate SET hit_count = IFNULL(hit_count,0) + 1 WHERE id = ?", id);
        TrainAssistantAskVO.QaItem item = new TrainAssistantAskVO.QaItem();
        item.setCalibrateId(id);
        item.setFromCalibrate(true);
        String topic = str(best.get("topic_title"));
        item.setQuestion(TrainQaAnswerSanitizer.stripOfficeMarkup(
                StringUtils.hasText(topic) ? topic : str(best.get("user_question"))));
        item.setAnswer(TrainQaAnswerSanitizer.stripOfficeMarkup(str(best.get("answer_text"))));
        item.setAnswerHtml(TrainQaAnswerSanitizer.stripOfficeMarkup(str(best.get("answer_html"))));
        item.setImages(parseImages(str(best.get("images_json"))));
        String versionLabel = str(best.get("version_label"));
        item.setVersionLabel(versionLabel);
        boolean onLatest = hitVersionId == latestVersionId;
        return new AccurateHit(item, hitVersionId, versionLabel, onLatest);
    }

    /** 校准准确命中（可能绑定最新版或历史版） */
    public record AccurateHit(
            TrainAssistantAskVO.QaItem item,
            long versionId,
            String versionLabel,
            boolean onLatest
    ) {
    }

    @Transactional
    public Long feedback(TrainQaFeedbackDTO dto) {
        if (dto.getAccurate() == null) {
            throw new BusinessException("请标记准或不准");
        }
        String category = normalizeCategory(dto.getCategory());
        DocRef doc = resolveDoc(dto.getDocId(), dto.getVersionId(), category);
        String status = Boolean.TRUE.equals(dto.getAccurate()) ? STATUS_APPROVED : STATUS_REJECTED;
        String userQ = nz(dto.getUserQuestion());
        String norm = normalizeIntent(userQ);
        if (!StringUtils.hasText(norm)) {
            throw new BusinessException("问题不能为空");
        }
        Long id = dto.getCalibrateId();
        if (id == null) {
            id = findIdByNorm(doc.docId, doc.versionId, norm);
        }
        String operator = SecurityUtils.getCurrentUsername();
        String imagesJson = toImagesJson(dto.getImages());
        rejectIfCodeAnswer(status, dto.getAnswer(), dto.getAnswerHtml());
        if (id != null) {
            jdbc.update("""
                    UPDATE train_qa_calibrate SET
                      user_question = ?, intent_norm = ?, topic_title = ?, answer_text = ?, answer_html = ?,
                      images_json = ?, status = ?, source = 'ASSISTANT_MARK', update_by = ?, is_active = 1
                    WHERE id = ? AND is_active = 1
                    """,
                    userQ, norm, nz(dto.getTopicTitle()), nz(dto.getAnswer()), nz(dto.getAnswerHtml()),
                    imagesJson, status, operator, id);
            syncAccurateWording(dto);
            return id;
        }
        KeyHolder kh = new GeneratedKeyHolder();
        String finalStatus = status;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO train_qa_calibrate
                      (doc_id, version_id, category, user_question, intent_norm, aliases_json, topic_title,
                       answer_text, answer_html, images_json, status, hit_count, source, remark, create_by, update_by, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,0,'ASSISTANT_MARK',NULL,?,?,1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, doc.docId);
            ps.setLong(2, doc.versionId);
            ps.setString(3, category);
            ps.setString(4, userQ);
            ps.setString(5, norm);
            ps.setString(6, "[]");
            ps.setString(7, nz(dto.getTopicTitle()));
            ps.setString(8, nz(dto.getAnswer()));
            ps.setString(9, nz(dto.getAnswerHtml()));
            ps.setString(10, imagesJson);
            ps.setString(11, finalStatus);
            ps.setString(12, operator);
            ps.setString(13, operator);
            return ps;
        }, kh);
        Number key = kh.getKey();
        syncAccurateWording(dto);
        return key == null ? null : key.longValue();
    }

    private void syncAccurateWording(TrainQaFeedbackDTO dto) {
        if (!Boolean.TRUE.equals(dto.getAccurate()) || dto.getQaItemId() == null) {
            return;
        }
        try {
            trainQaItemService.rememberUserWording(dto.getQaItemId(), dto.getUserQuestion());
        } catch (Exception e) {
            log.warn("记入同义问法失败 itemId={}: {}", dto.getQaItemId(), e.getMessage());
        }
    }

    public PageResult<TrainQaCalibrateVO> page(TrainQaCalibrateQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        String category = normalizeCategory(query.getCategory());
        StringBuilder where = new StringBuilder(" WHERE c.is_active = 1 ");
        List<Object> args = new ArrayList<>();
        where.append(" AND c.category = ? ");
        args.add(category);
        if (query.getDocId() != null) {
            where.append(" AND c.doc_id = ? ");
            args.add(query.getDocId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            where.append(" AND c.status = ? ");
            args.add(query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            where.append(" AND (c.user_question LIKE ? OR c.topic_title LIKE ? OR c.answer_text LIKE ?) ");
            String like = "%" + query.getKeyword().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (Boolean.TRUE.equals(query.getOnlyLatest())) {
            where.append(" AND c.version_id = d.latest_version_id ");
        }
        if (StringUtils.hasText(query.getHandleStatus())) {
            where.append(" AND c.handle_status = ? ");
            args.add(query.getHandleStatus().trim().toUpperCase(Locale.ROOT));
        } else if (query.getHandled() != null) {
            if (Boolean.TRUE.equals(query.getHandled())) {
                where.append(" AND c.handle_status <> ? ");
            } else {
                where.append(" AND c.handle_status = ? ");
            }
            args.add(TrainQaHandleStatuses.OPEN);
        }
        String countSql = "SELECT COUNT(1) FROM train_qa_calibrate c JOIN train_doc d ON d.id = c.doc_id " + where;
        Integer total = jdbc.queryForObject(countSql, Integer.class, args.toArray());
        int t = total == null ? 0 : total;
        int offset = (pageNum - 1) * pageSize;
        String listSql = """
                SELECT c.id, c.doc_id, d.title doc_title, c.version_id, v.version_label,
                       d.latest_version_id, c.category, c.user_question, c.aliases_json, c.topic_title,
                       c.answer_text, c.answer_html, c.images_json, c.status, c.hit_count, c.source,
                       c.remark, c.handle_status, c.handle_by, c.handle_time, c.update_by, c.update_time
                FROM train_qa_calibrate c
                JOIN train_doc d ON d.id = c.doc_id
                LEFT JOIN train_doc_version v ON v.id = c.version_id
                """ + where + " ORDER BY c.update_time DESC LIMIT ? OFFSET ?";
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset);
        List<TrainQaCalibrateVO> rows = jdbc.query(listSql, (rs, i) -> {
            TrainQaCalibrateVO vo = new TrainQaCalibrateVO();
            vo.setId(rs.getLong("id"));
            vo.setDocId(rs.getLong("doc_id"));
            vo.setDocTitle(rs.getString("doc_title"));
            vo.setVersionId(rs.getLong("version_id"));
            vo.setVersionLabel(rs.getString("version_label"));
            long latest = rs.getLong("latest_version_id");
            vo.setLatestVersion(latest > 0 && latest == vo.getVersionId());
            vo.setCategory(rs.getString("category"));
            vo.setUserQuestion(rs.getString("user_question"));
            vo.setAliases(aliasesToText(rs.getString("aliases_json")));
            vo.setTopicTitle(rs.getString("topic_title"));
            vo.setAnswer(rs.getString("answer_text"));
            vo.setAnswerHtml(rs.getString("answer_html"));
            vo.setImages(parseImages(rs.getString("images_json")));
            vo.setStatus(rs.getString("status"));
            vo.setHitCount(rs.getInt("hit_count"));
            vo.setSource(rs.getString("source"));
            vo.setRemark(rs.getString("remark"));
            String handleStatus = rs.getString("handle_status");
            if (!StringUtils.hasText(handleStatus)) {
                handleStatus = TrainQaHandleStatuses.OPEN;
            }
            vo.setHandleStatus(handleStatus);
            vo.setHandled(TrainQaHandleStatuses.isHandled(handleStatus));
            vo.setHandleBy(rs.getString("handle_by"));
            vo.setHandleTime(rs.getString("handle_time"));
            vo.setUpdateBy(rs.getString("update_by"));
            vo.setUpdateTime(rs.getString("update_time"));
            return vo;
        }, listArgs.toArray());
        return new PageResult<>(t, rows);
    }

    @Transactional
    public void markHandled(TrainQaHandleDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException("记录ID不能为空");
        }
        String handleStatus = TrainQaHandleStatuses.normalize(dto.getHandleStatus());
        String operator = SecurityUtils.getCurrentUsername();
        int n;
        if (TrainQaHandleStatuses.OPEN.equals(handleStatus)) {
            n = jdbc.update("""
                    UPDATE train_qa_calibrate
                    SET handle_status = ?, handle_by = NULL, handle_time = NULL, update_by = ?
                    WHERE id = ? AND is_active = 1
                    """, handleStatus, operator, dto.getId());
        } else {
            n = jdbc.update("""
                    UPDATE train_qa_calibrate
                    SET handle_status = ?, handle_by = ?, handle_time = NOW(), update_by = ?
                    WHERE id = ? AND is_active = 1
                    """, handleStatus, operator, operator, dto.getId());
        }
        if (n <= 0) {
            throw new BusinessException("记录不存在");
        }
    }

    @Transactional
    public Long save(TrainQaCalibrateSaveDTO dto) {
        String category = normalizeCategory(dto.getCategory());
        DocRef doc = resolveDoc(dto.getDocId(), dto.getVersionId(), category);
        String userQ = nz(dto.getUserQuestion());
        String norm = normalizeIntent(userQ);
        if (!StringUtils.hasText(norm)) {
            throw new BusinessException("用户问法不能为空");
        }
        String status = StringUtils.hasText(dto.getStatus())
                ? dto.getStatus().trim().toUpperCase(Locale.ROOT)
                : STATUS_APPROVED;
        if (!List.of(STATUS_APPROVED, STATUS_REJECTED, STATUS_PENDING, STATUS_STALE).contains(status)) {
            throw new BusinessException("状态不合法");
        }
        rejectIfCodeAnswer(status, dto.getAnswer(), dto.getAnswerHtml());
        String operator = SecurityUtils.getCurrentUsername();
        String aliasesJson = aliasesToJson(dto.getAliases());
        String imagesJson = toImagesJson(dto.getImages());
        if (dto.getId() != null) {
            int n = jdbc.update("""
                    UPDATE train_qa_calibrate SET
                      doc_id=?, version_id=?, category=?, user_question=?, intent_norm=?, aliases_json=?,
                      topic_title=?, answer_text=?, answer_html=?, images_json=?, status=?, remark=?,
                      source='ADMIN_EDIT', update_by=?, is_active=1
                    WHERE id=? AND is_active=1
                    """,
                    doc.docId, doc.versionId, category, userQ, norm, aliasesJson,
                    nz(dto.getTopicTitle()), nz(dto.getAnswer()), nz(dto.getAnswerHtml()), imagesJson,
                    status, nz(dto.getRemark()), operator, dto.getId());
            if (n <= 0) {
                throw new BusinessException("记录不存在");
            }
            return dto.getId();
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO train_qa_calibrate
                      (doc_id, version_id, category, user_question, intent_norm, aliases_json, topic_title,
                       answer_text, answer_html, images_json, status, hit_count, source, remark, create_by, update_by, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,0,'ADMIN_EDIT',?,?,?,1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, doc.docId);
            ps.setLong(2, doc.versionId);
            ps.setString(3, category);
            ps.setString(4, userQ);
            ps.setString(5, norm);
            ps.setString(6, aliasesJson);
            ps.setString(7, nz(dto.getTopicTitle()));
            ps.setString(8, nz(dto.getAnswer()));
            ps.setString(9, nz(dto.getAnswerHtml()));
            ps.setString(10, imagesJson);
            ps.setString(11, status);
            ps.setString(12, nz(dto.getRemark()));
            ps.setString(13, operator);
            ps.setString(14, operator);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? null : key.longValue();
    }

    @Transactional
    public void delete(Long id) {
        int n = jdbc.update("UPDATE train_qa_calibrate SET is_active = 0 WHERE id = ? AND is_active = 1", id);
        if (n <= 0) {
            throw new BusinessException("记录不存在");
        }
    }

    @Transactional
    public TrainQaCalibrateVO rerecognize(Long id) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                SELECT id, doc_id, version_id, category, user_question, topic_title, status
                FROM train_qa_calibrate WHERE id = ? AND is_active = 1
                """, id);
        if (found.isEmpty()) {
            throw new BusinessException("记录不存在");
        }
        Map<String, Object> row = found.get(0);
        String userQ = str(row.get("user_question"));
        long docId = ((Number) row.get("doc_id")).longValue();
        long versionId = ((Number) row.get("version_id")).longValue();
        List<TrainAssistantAskVO.QaItem> items = trainDocService.extractQaForVersion(userQ, docId, versionId);
        if (items.isEmpty()) {
            throw new BusinessException("重新识别未找到答案，请手动校准");
        }
        TrainAssistantAskVO.QaItem hit = items.get(0);
        if (TrainQaAnswerSanitizer.looksLikeSourceCode(hit.getAnswer(), hit.getAnswerHtml())) {
            throw new BusinessException("重新识别结果疑似程序代码，已拦截，请手动填写收银操作说明");
        }
        String operator = SecurityUtils.getCurrentUsername();
        // 重新识别后进入待审，避免不准内容直接上线
        jdbc.update("""
                UPDATE train_qa_calibrate SET
                  topic_title = ?, answer_text = ?, answer_html = ?, images_json = ?,
                  status = ?, source = 'RE_RECOGNIZE', update_by = ?
                WHERE id = ?
                """,
                nz(hit.getQuestion()), nz(hit.getAnswer()), nz(hit.getAnswerHtml()),
                toImagesJson(hit.getImages()), STATUS_PENDING, operator, id);
        TrainQaCalibrateVO vo = new TrainQaCalibrateVO();
        vo.setId(id);
        vo.setDocId(docId);
        vo.setVersionId(versionId);
        vo.setUserQuestion(userQ);
        vo.setTopicTitle(hit.getQuestion());
        vo.setAnswer(hit.getAnswer());
        vo.setAnswerHtml(hit.getAnswerHtml());
        vo.setImages(hit.getImages() == null ? List.of() : hit.getImages());
        vo.setStatus(STATUS_PENDING);
        vo.setSource("RE_RECOGNIZE");
        return vo;
    }

    /** 文档上传新版本后：旧版「准」标为 STALE（答疑仍可作历史兜底，需标注参考版本）。 */
    public void markStaleForDocExceptVersion(long docId, long keepVersionId) {
        int n = jdbc.update("""
                UPDATE train_qa_calibrate SET status = ?
                WHERE doc_id = ? AND version_id <> ? AND is_active = 1 AND status = ?
                """, STATUS_STALE, docId, keepVersionId, STATUS_APPROVED);
        if (n > 0) {
            log.info("文档换版，已将旧校准标为 STALE docId={} keepVersionId={} count={}", docId, keepVersionId, n);
        }
    }

    /**
     * 删除某份收银文档时：同文档同版本下，问法/话题/意图键落在被删题干上的校准一并软删。
     */
    public int deactivateMatchingQuestions(long docId, long versionId, List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return 0;
        }
        Set<String> exact = new LinkedHashSet<>();
        Set<String> norms = new LinkedHashSet<>();
        for (String q : questions) {
            if (!StringUtils.hasText(q)) {
                continue;
            }
            String trimmed = q.trim();
            exact.add(trimmed);
            String norm = normalizeIntent(trimmed);
            if (StringUtils.hasText(norm)) {
                norms.add(norm);
            }
        }
        if (exact.isEmpty() && norms.isEmpty()) {
            return 0;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, user_question, topic_title, intent_norm
                FROM train_qa_calibrate
                WHERE is_active = 1 AND doc_id = ? AND version_id = ?
                """, docId, versionId);
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String userQ = str(row.get("user_question"));
            String topic = str(row.get("topic_title"));
            String intent = str(row.get("intent_norm"));
            boolean hit = exact.contains(userQ) || exact.contains(topic)
                    || (StringUtils.hasText(intent) && norms.contains(intent))
                    || norms.contains(normalizeIntent(userQ))
                    || norms.contains(normalizeIntent(topic));
            if (hit) {
                Number id = (Number) row.get("id");
                if (id != null) {
                    ids.add(id.longValue());
                }
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Long id : ids) {
            n += jdbc.update("UPDATE train_qa_calibrate SET is_active = 0 WHERE id = ? AND is_active = 1", id);
        }
        if (n > 0) {
            log.info("删除收银文档后级联软删校准 docId={} versionId={} count={}", docId, versionId, n);
        }
        return n;
    }

    private boolean matchesIntent(String norm, Map<String, Object> row) {
        String storedQ = str(row.get("user_question"));
        String intentNorm = normalizeIntent(storedQ);
        if (norm.equals(intentNorm)) {
            return true;
        }
        // aliases_json: ["怎么寄存","寄存操作"]
        for (String alias : parseAliases(str(row.get("aliases_json")))) {
            if (norm.equals(normalizeIntent(alias))) {
                return true;
            }
        }
        String topic = str(row.get("topic_title"));
        String topicNorm = normalizeIntent(topic);
        if (StringUtils.hasText(topicNorm) && norm.equals(topicNorm)) {
            return true;
        }
        // 宽松匹配须过硬闸门，禁止单靠 contains 串题（上千条时易误命中）
        if (TrainQaIntentMatcher.titleMatches(norm, storedQ)
                && TrainQaIntentMatcher.passesHardGate(norm, storedQ)) {
            return true;
        }
        if (StringUtils.hasText(topic)
                && TrainQaIntentMatcher.titleMatches(norm, topic)
                && TrainQaIntentMatcher.passesHardGate(norm, topic)) {
            return true;
        }
        // 核心几乎相同（长度差≤2 且互相包含）才认 contains
        if (StringUtils.hasText(topicNorm) && nearlySameCore(norm, topicNorm)
                && TrainQaIntentMatcher.passesHardGate(norm, topic)) {
            return true;
        }
        return false;
    }

    private static boolean nearlySameCore(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return false;
        }
        if (Math.abs(a.length() - b.length()) > 2) {
            return false;
        }
        return a.contains(b) || b.contains(a);
    }

    private Long findIdByNorm(long docId, long versionId, String norm) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM train_qa_calibrate
                WHERE is_active = 1 AND doc_id = ? AND version_id = ? AND intent_norm = ?
                ORDER BY update_time DESC LIMIT 1
                """, (rs, i) -> rs.getLong(1), docId, versionId, norm);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private DocRef resolveDoc(Long docId, Long versionId, String category) {
        if (docId != null && versionId != null) {
            DocRef r = new DocRef();
            r.docId = docId;
            r.versionId = versionId;
            return r;
        }
        Map<String, Object> latest = jdbc.query("""
                SELECT d.id doc_id, d.latest_version_id version_id
                FROM train_doc d
                WHERE d.is_active = 1 AND d.category = ? AND d.status = 0
                  AND d.latest_version_id IS NOT NULL
                ORDER BY d.update_time DESC LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return Map.<String, Object>of(
                    "doc_id", rs.getLong("doc_id"),
                    "version_id", rs.getLong("version_id"));
        }, category);
        if (latest == null) {
            throw new BusinessException("暂无培训文档");
        }
        DocRef r = new DocRef();
        r.docId = docId != null ? docId : ((Number) latest.get("doc_id")).longValue();
        r.versionId = versionId != null ? versionId : ((Number) latest.get("version_id")).longValue();
        return r;
    }

    public static String normalizeIntent(String q) {
        // 与答疑召回同一套：去停用词 + 同义归一（订货/要货、厂子/厂家 等）
        return TrainQaIntentMatcher.canonicalCore(q);
    }

    private static void rejectIfCodeAnswer(String status, String answer, String answerHtml) {
        if (!STATUS_APPROVED.equals(status) && !STATUS_PENDING.equals(status)) {
            return;
        }
        if (TrainQaAnswerSanitizer.looksLikeSourceCode(answer, answerHtml)) {
            throw new BusinessException("答案疑似程序代码，不能下发给前台收银员，请改为操作说明");
        }
    }

    private static String normalizeCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : "cashier";
    }

    private String toImagesJson(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images == null ? List.of() : images);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseImages(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String aliasesToJson(String aliasesText) {
        List<String> list = new ArrayList<>();
        if (StringUtils.hasText(aliasesText)) {
            for (String line : aliasesText.split("\\R")) {
                String t = line.trim();
                if (StringUtils.hasText(t)) {
                    list.add(t);
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String aliasesToText(String json) {
        List<String> list = parseAliases(json);
        return String.join("\n", list);
    }

    private List<String> parseAliases(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static class DocRef {
        long docId;
        long versionId;
    }
}
