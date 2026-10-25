package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.TrainQaItemQueryDTO;
import com.base.admin.domain.dto.TrainQaItemSaveDTO;
import com.base.admin.domain.vo.TrainQaItemVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一问一答落库。原文件仍在 train_doc_version，这里只是可检索的题目副本。
 * 重新拆题时按题干保留已维护的同义问法。
 */
@Slf4j
@Service
public class TrainQaItemService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TrainDocService trainDocService;
    private final TrainQaEmbeddingClient embeddingClient;
    private final TrainQaVectorIndex vectorIndex;
    private final TransactionTemplate tx;
    private final ConcurrentLinkedQueue<Long> embedQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService embedExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "train-qa-embed");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean embedWarned = new AtomicBoolean();

    public TrainQaItemService(JdbcTemplate jdbc,
                              ObjectMapper objectMapper,
                              @Lazy TrainDocService trainDocService,
                              TrainQaEmbeddingClient embeddingClient,
                              TrainQaVectorIndex vectorIndex,
                              PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.trainDocService = trainDocService;
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public void rebuildVersion(long docId, long versionId) {
        List<Map<String, Object>> files = jdbc.queryForList("""
                SELECT id FROM train_doc_file
                WHERE version_id = ? AND doc_id = ? AND is_active = 1
                ORDER BY sort_no ASC, id ASC
                """, versionId, docId);
        if (files.isEmpty()) {
            rebuildLegacyVersion(docId, versionId);
            return;
        }
        for (Map<String, Object> file : files) {
            Number id = (Number) file.get("id");
            if (id != null) {
                rebuildFile(docId, versionId, id.longValue());
            }
        }
    }

    public void rebuildFile(long docId, long versionId, long fileId) {
        List<Map<String, Object>> files = jdbc.queryForList("""
                SELECT file_path, file_name FROM train_doc_file
                WHERE id = ? AND doc_id = ? AND version_id = ? AND is_active = 1
                """, fileId, docId, versionId);
        if (files.isEmpty()) {
            log.warn("收银文档不存在 fileId={}", fileId);
            return;
        }
        String filePath = str(files.get(0).get("file_path"));
        String fileName = str(files.get(0).get("file_name"));
        List<TrainQaDraft> drafts;
        try {
            drafts = trainDocService.exportStructuredQaFile(docId, versionId, fileId, filePath, fileName);
        } catch (Exception e) {
            log.warn("拆题失败，保留原文件 fileId={}: {}", fileId, e.getMessage());
            return;
        }
        if (drafts == null || drafts.isEmpty()) {
            log.warn("未拆出问答，保留原文件与已有题目 fileId={}", fileId);
            return;
        }
        String versionPath = jdbc.query("""
                SELECT file_path FROM train_doc_version WHERE id = ?
                """, rs -> rs.next() ? rs.getString(1) : "", versionId);
        boolean primary = !StringUtils.hasText(versionPath) || versionPath.equals(filePath);
        replaceRows(docId, versionId, fileId, primary, drafts);
        scheduleEmbed(versionId);
    }

    private void rebuildLegacyVersion(long docId, long versionId) {
        List<TrainQaDraft> drafts;
        try {
            drafts = trainDocService.exportStructuredQa(docId, versionId);
        } catch (Exception e) {
            log.warn("拆题失败，保留原文件 versionId={}: {}", versionId, e.getMessage());
            return;
        }
        if (drafts == null || drafts.isEmpty()) {
            log.warn("未拆出问答，保留原文件与已有题目 versionId={}", versionId);
            return;
        }
        replaceRows(docId, versionId, null, true, drafts);
        scheduleEmbed(versionId);
    }

    public void backfillMissingLatest() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT d.id doc_id, d.latest_version_id version_id
                    FROM train_doc d
                    WHERE d.is_active = 1 AND d.status = 0 AND d.latest_version_id IS NOT NULL
                      AND NOT EXISTS (
                        SELECT 1 FROM train_qa_item i
                        WHERE i.version_id = d.latest_version_id AND i.is_active = 1
                      )
                    """);
        } catch (Exception e) {
            log.warn("检查未拆题文档失败: {}", e.getMessage());
            return;
        }
        if (rows.isEmpty()) {
            vectorIndex.reload();
            return;
        }
        log.info("开始为已有培训文档拆题 count={}", rows.size());
        for (Map<String, Object> row : rows) {
            Number docId = (Number) row.get("doc_id");
            Number versionId = (Number) row.get("version_id");
            if (docId == null || versionId == null) {
                continue;
            }
            rebuildVersion(docId.longValue(), versionId.longValue());
        }
    }

    public void deactivateDoc(long docId) {
        jdbc.update("UPDATE train_qa_item SET is_active = 0 WHERE doc_id = ? AND is_active = 1", docId);
        vectorIndex.reload();
    }

    public List<TrainQaItemVO> listLatest(long docId) {
        return jdbc.query("""
                SELECT i.id, i.doc_id, i.version_id, i.file_id, i.sort_no, i.question, i.aliases_json, i.answer_text,
                       v.version_label, COALESCE(NULLIF(f.title, ''), f.file_name) file_title,
                       CASE WHEN i.embedding IS NULL THEN 0 ELSE 1 END embedded
                FROM train_qa_item i
                JOIN train_doc d ON d.id = i.doc_id AND d.latest_version_id = i.version_id
                LEFT JOIN train_doc_version v ON v.id = i.version_id
                LEFT JOIN train_doc_file f ON f.id = i.file_id
                WHERE i.doc_id = ? AND i.is_active = 1
                ORDER BY i.file_id ASC, i.sort_no ASC, i.id ASC
                """, (rs, n) -> {
            TrainQaItemVO vo = new TrainQaItemVO();
            vo.setId(rs.getLong("id"));
            vo.setDocId(rs.getLong("doc_id"));
            vo.setVersionId(rs.getLong("version_id"));
            if (rs.getObject("file_id") != null) {
                vo.setFileId(rs.getLong("file_id"));
            }
            vo.setFileTitle(rs.getString("file_title"));
            vo.setSortNo(rs.getInt("sort_no"));
            vo.setQuestion(rs.getString("question"));
            vo.setAliases(joinAliases(readStringList(rs.getString("aliases_json"))));
            vo.setAnswer(rs.getString("answer_text"));
            vo.setVersionLabel(rs.getString("version_label"));
            vo.setEmbedded(rs.getInt("embedded") == 1);
            return vo;
        }, docId);
    }

    public PageResult<TrainQaItemVO> page(TrainQaItemQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        String category = StringUtils.hasText(query.getCategory()) ? query.getCategory().trim().toLowerCase(Locale.ROOT) : "cashier";
        StringBuilder where = new StringBuilder("""
                 WHERE i.is_active = 1 AND d.is_active = 1 AND v.is_active = 1 AND i.category = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(category);
        if (query.getDocId() != null) {
            where.append(" AND i.doc_id = ? ");
            args.add(query.getDocId());
        }
        if (query.getVersionId() != null) {
            where.append(" AND i.version_id = ? ");
            args.add(query.getVersionId());
        } else if (!Boolean.FALSE.equals(query.getOnlyLatest())) {
            where.append(" AND i.version_id = d.latest_version_id ");
        }
        if (StringUtils.hasText(query.getKeyword())) {
            where.append(" AND (i.question LIKE ? OR i.aliases_json LIKE ? OR i.answer_text LIKE ?) ");
            String like = "%" + query.getKeyword().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String from = """
                FROM train_qa_item i
                JOIN train_doc d ON d.id = i.doc_id
                JOIN train_doc_version v ON v.id = i.version_id
                LEFT JOIN train_doc_file f ON f.id = i.file_id
                """;
        Integer total = jdbc.queryForObject("SELECT COUNT(1) " + from + where, Integer.class, args.toArray());
        int offset = (pageNum - 1) * pageSize;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset);
        List<TrainQaItemVO> rows = jdbc.query("""
                SELECT i.id, i.doc_id, d.title doc_title, i.version_id, i.file_id, v.version_no, v.version_label,
                       COALESCE(f.file_name, v.file_name) file_name,
                       COALESCE(NULLIF(f.title, ''), f.file_name, v.file_name) file_title,
                       i.sort_no, i.question, i.aliases_json, i.answer_text,
                       CASE WHEN i.embedding IS NULL THEN 0 ELSE 1 END embedded,
                       CASE WHEN i.version_id = d.latest_version_id THEN 1 ELSE 0 END latest
                """ + from + where + """
                 ORDER BY d.title ASC, v.version_no DESC, f.sort_no ASC, i.sort_no ASC, i.id ASC
                 LIMIT ? OFFSET ?
                """, (rs, n) -> mapItem(rs), listArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    private TrainQaItemVO mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        TrainQaItemVO vo = new TrainQaItemVO();
        vo.setId(rs.getLong("id"));
        vo.setDocId(rs.getLong("doc_id"));
        vo.setDocTitle(rs.getString("doc_title"));
        vo.setVersionId(rs.getLong("version_id"));
        vo.setVersionNo(rs.getInt("version_no"));
        vo.setVersionLabel(rs.getString("version_label"));
        vo.setFileName(rs.getString("file_name"));
        if (rs.getObject("file_id") != null) {
            vo.setFileId(rs.getLong("file_id"));
        }
        vo.setFileTitle(rs.getString("file_title"));
        vo.setSortNo(rs.getInt("sort_no"));
        vo.setQuestion(rs.getString("question"));
        vo.setAliases(joinAliases(readStringList(rs.getString("aliases_json"))));
        vo.setAnswer(rs.getString("answer_text"));
        vo.setEmbedded(rs.getInt("embedded") == 1);
        vo.setLatest(rs.getInt("latest") == 1);
        return vo;
    }

    @Transactional
    public void update(long itemId, TrainQaItemSaveDTO dto) {
        String question = trimQuestion(dto.getQuestion());
        if (!StringUtils.hasText(question)) {
            throw new com.base.admin.exception.BusinessException("题干不能为空");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT version_id, answer_text FROM train_qa_item WHERE id = ? AND is_active = 1
                """, itemId);
        if (rows.isEmpty()) {
            throw new com.base.admin.exception.BusinessException("题目不存在");
        }
        long versionId = ((Number) rows.get(0).get("version_id")).longValue();
        String answer = dto.getAnswer();
        boolean changeAnswer = StringUtils.hasText(answer);
        List<String> aliases = parseAliasText(dto.getAliases());
        aliases.removeIf(a -> a.equals(question));
        if (changeAnswer) {
            jdbc.update("""
                    UPDATE train_qa_item
                    SET question = ?, aliases_json = ?, answer_text = ?, answer_html = NULL,
                        content_hash = NULL, embedding = NULL
                    WHERE id = ? AND is_active = 1
                    """, question, writeJson(aliases), answer.trim(), itemId);
        } else {
            jdbc.update("""
                    UPDATE train_qa_item
                    SET question = ?, aliases_json = ?, content_hash = NULL, embedding = NULL
                    WHERE id = ? AND is_active = 1
                    """, question, writeJson(aliases), itemId);
        }
        vectorIndex.reload();
        scheduleEmbed(versionId);
    }

    /** 用户标记「准确」后，把原问法记到这道题上，下次怪问法能对上。 */
    public void rememberUserWording(Long itemId, String userQuestion) {
        if (itemId == null || !StringUtils.hasText(userQuestion)) {
            return;
        }
        String wording = userQuestion.trim();
        if (wording.length() > 500) {
            wording = wording.substring(0, 500);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT version_id, question, aliases_json FROM train_qa_item WHERE id = ? AND is_active = 1
                """, itemId);
        if (rows.isEmpty()) {
            return;
        }
        String question = String.valueOf(rows.get(0).get("question"));
        if (wording.equals(question)) {
            return;
        }
        List<String> aliases = new ArrayList<>(readStringList(str(rows.get(0).get("aliases_json"))));
        if (aliases.contains(wording)) {
            return;
        }
        aliases.add(wording);
        long versionId = ((Number) rows.get(0).get("version_id")).longValue();
        jdbc.update("""
                UPDATE train_qa_item
                SET aliases_json = ?, content_hash = NULL, embedding = NULL
                WHERE id = ? AND is_active = 1
                """, writeJson(aliases), itemId);
        vectorIndex.reload();
        scheduleEmbed(versionId);
    }

    public void scheduleEmbed(long versionId) {
        embedQueue.add(versionId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    embedExecutor.execute(TrainQaItemService.this::drainEmbed);
                }
            });
            return;
        }
        embedExecutor.execute(this::drainEmbed);
    }

    private void drainEmbed() {
        Set<Long> seen = new LinkedHashSet<>();
        Long id;
        while ((id = embedQueue.poll()) != null) {
            seen.add(id);
        }
        if (seen.isEmpty()) {
            return;
        }
        for (Long versionId : seen) {
            try {
                embedVersion(versionId);
            } catch (Exception e) {
                log.warn("题目向量写入失败 versionId={}: {}", versionId, e.getMessage());
            }
        }
        vectorIndex.reload();
    }

    private void embedVersion(long versionId) {
        if (!embeddingClient.available()) {
            if (embedWarned.compareAndSet(false, true)) {
                log.warn("未配置可用的向量模型，题目已入库，提问暂时沿用原检索");
            }
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, question, aliases_json, content_hash, embedding IS NULL AS missing
                FROM train_qa_item
                WHERE version_id = ? AND is_active = 1
                ORDER BY sort_no ASC, id ASC
                """, versionId);
        List<Long> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String text = embedText(str(row.get("question")), readStringList(str(row.get("aliases_json"))));
            String hash = TrainQaVectors.hash(text);
            boolean missing = flag(row.get("missing"));
            String oldHash = str(row.get("content_hash"));
            if (!missing && hash.equals(oldHash)) {
                continue;
            }
            ids.add(((Number) row.get("id")).longValue());
            texts.add(text);
            hashes.add(hash);
        }
        if (ids.isEmpty()) {
            return;
        }
        for (int i = 0; i < ids.size(); i += 6) {
            int end = Math.min(ids.size(), i + 6);
            List<float[]> vecs = embeddingClient.embed(texts.subList(i, end));
            if (vecs == null || vecs.size() != end - i) {
                log.warn("题目向量未完成 versionId={} from={}", versionId, i);
                return;
            }
            for (int j = 0; j < vecs.size(); j++) {
                jdbc.update("""
                        UPDATE train_qa_item
                        SET embedding = ?, embed_model = ?, content_hash = ?
                        WHERE id = ?
                        """, TrainQaVectors.toBytes(vecs.get(j)), TrainQaVectors.EMBED_MODEL, hashes.get(i + j), ids.get(i + j));
            }
        }
        log.info("题目向量已更新 versionId={} count={}", versionId, ids.size());
    }

    private void replaceRows(long docId, long versionId, Long fileId, boolean primary, List<TrainQaDraft> drafts) {
        tx.executeWithoutResult(status -> insertRows(docId, versionId, fileId, primary, drafts));
        vectorIndex.reload();
    }

    private void insertRows(long docId, long versionId, Long fileId, boolean primary, List<TrainQaDraft> drafts) {
        String category = jdbc.query("""
                SELECT category FROM train_doc WHERE id = ?
                """, rs -> rs.next() ? rs.getString(1) : "cashier", docId);
        if (!StringUtils.hasText(category)) {
            category = "cashier";
        }
        Map<String, String> keptAliases = new java.util.HashMap<>();
        List<Map<String, Object>> old;
        if (fileId == null) {
            old = jdbc.queryForList("""
                    SELECT question, aliases_json FROM train_qa_item WHERE version_id = ? AND is_active = 1
                    """, versionId);
        } else if (primary) {
            old = jdbc.queryForList("""
                    SELECT question, aliases_json FROM train_qa_item
                    WHERE version_id = ? AND is_active = 1 AND (file_id = ? OR file_id IS NULL)
                    """, versionId, fileId);
        } else {
            old = jdbc.queryForList("""
                    SELECT question, aliases_json FROM train_qa_item
                    WHERE version_id = ? AND is_active = 1 AND file_id = ?
                    """, versionId, fileId);
        }
        for (Map<String, Object> row : old) {
            String q = str(row.get("question"));
            String aliases = str(row.get("aliases_json"));
            if (StringUtils.hasText(q) && StringUtils.hasText(aliases) && !"[]".equals(aliases.trim())) {
                keptAliases.put(normKey(q), aliases);
            }
        }
        if (fileId == null) {
            jdbc.update("UPDATE train_qa_item SET is_active = 0 WHERE version_id = ? AND is_active = 1", versionId);
        } else if (primary) {
            jdbc.update("""
                    UPDATE train_qa_item SET is_active = 0
                    WHERE version_id = ? AND is_active = 1 AND (file_id = ? OR file_id IS NULL)
                    """, versionId, fileId);
        } else {
            jdbc.update("""
                    UPDATE train_qa_item SET is_active = 0
                    WHERE version_id = ? AND is_active = 1 AND file_id = ?
                    """, versionId, fileId);
        }
        for (TrainQaDraft draft : drafts) {
            String question = trimQuestion(draft.getQuestion());
            if (!StringUtils.hasText(question)) {
                continue;
            }
            String aliases = keptAliases.getOrDefault(normKey(question), "[]");
            Long rowFileId = draft.getFileId() != null ? draft.getFileId() : fileId;
            jdbc.update("""
                    INSERT INTO train_qa_item
                      (doc_id, version_id, file_id, category, sort_no, question, aliases_json, answer_text, answer_html, images_json, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,1)
                    """,
                    docId, versionId, rowFileId, category.trim().toLowerCase(Locale.ROOT), draft.getSortNo(),
                    question, aliases, nullToEmpty(draft.getAnswer()), nullToEmpty(draft.getAnswerHtml()),
                    writeJson(draft.getImages() == null ? List.of() : draft.getImages()));
        }
        log.info("培训文档已拆题入库 docId={} versionId={} fileId={} count={}", docId, versionId, fileId, drafts.size());
    }

    static String embedText(String question, List<String> aliases) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(question)) {
            sb.append(question.trim());
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (!StringUtils.hasText(alias)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(alias.trim());
                if (sb.length() > 2000) {
                    break;
                }
            }
        }
        if (sb.length() > 2000) {
            return sb.substring(0, 2000);
        }
        return sb.toString();
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            List<String> out = new ArrayList<>();
            if (list != null) {
                for (String s : list) {
                    if (StringUtils.hasText(s)) {
                        out.add(s.trim());
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static List<String> parseAliasText(String raw) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        String normalized = raw.replace('，', '\n').replace(',', '\n').replace('；', '\n').replace(';', '\n');
        Set<String> seen = new LinkedHashSet<>();
        for (String line : normalized.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.length() > 500) {
                if (t.length() > 500) {
                    t = t.substring(0, 500);
                } else {
                    continue;
                }
            }
            if (seen.add(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private String joinAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return "";
        }
        return String.join("\n", aliases);
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String trimQuestion(String q) {
        if (q == null) {
            return "";
        }
        String t = q.trim();
        return t.length() <= 500 ? t : t.substring(0, 500);
    }

    private static String normKey(String q) {
        return q == null ? "" : q.replaceAll("\\s+", "").trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static boolean flag(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return "1".equals(String.valueOf(o));
    }
}
