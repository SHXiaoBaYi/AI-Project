package com.base.admin.service;

import com.base.admin.domain.vo.TrainAssistantAskVO;
import com.base.admin.util.TrainQaIntentMatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫码提问：向量只负责召回；意图吻合度决定是否直出答案。
 * <ul>
 *   <li>≥0.9：命中，直接返回答案</li>
 *   <li>[0.8, 0.9)：未确定命中，给出相近原题供点选</li>
 *   <li>[0.75, 0.8)：建议原题，请用户点选</li>
 *   <li>&lt;0.75：未命中（宁缺毋滥，不出错答）</li>
 * </ul>
 */
@Slf4j
@Service
public class TrainQaSearchService {

    /** 意图吻合度：达到才算命中并直接返回答案 */
    private static final float HIT = 0.90f;
    /** [NEAR, HIT)：未命中文案 + 相近原题 */
    private static final float NEAR = 0.80f;
    /** [SUGGEST, NEAR)：不确定建议 */
    private static final float SUGGEST = 0.75f;
    private static final int RERANK_POOL = 12;
    private static final int SUGGEST_LIMIT = 3;

    private static final String MSG_HIT = "已根据培训文档找到相关问答";
    private static final String MSG_NEAR =
            "没有找到与该问题匹配的培训说明，请换个问法，或从下面相近原题里选一条。";
    private static final String MSG_SUGGEST = "没有十分确定的答案，这些原题可能相关，请点选查看。";
    private static final String MSG_MISS = "没有找到与该问题匹配的培训说明，请换个问法后再试。";

    private static final Pattern INDEX_JSON = Pattern.compile("\"index\"\\s*:\\s*(-?\\d+)");
    private static final Pattern SCORE_PAIR =
            Pattern.compile("\"index\"\\s*:\\s*(\\d+)\\s*,\\s*\"score\"\\s*:\\s*([0-9]*\\.?[0-9]+)");

    private final TrainQaVectorIndex vectorIndex;
    private final TrainQaEmbeddingClient embeddingClient;
    private final AiChatService aiChatService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, float[]> queryCache = new ConcurrentHashMap<>();

    public TrainQaSearchService(TrainQaVectorIndex vectorIndex,
                                TrainQaEmbeddingClient embeddingClient,
                                AiChatService aiChatService,
                                JdbcTemplate jdbc,
                                ObjectMapper objectMapper) {
        this.vectorIndex = vectorIndex;
        this.embeddingClient = embeddingClient;
        this.aiChatService = aiChatService;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 没有任何已嵌入题目时返回 empty，调用方走原来的全文检索。 */
    public java.util.Optional<TrainAssistantAskVO> trySearch(String question, String category, boolean oldVersions) {
        String cat = normalize(category);
        List<TrainQaVectorIndex.Entry> entries = vectorIndex.snapshot(cat, oldVersions);
        if (entries.isEmpty()) {
            return java.util.Optional.empty();
        }
        List<Scored> ranked = rank(question, entries);
        if (ranked.isEmpty()) {
            return java.util.Optional.of(miss(cat, oldVersions));
        }

        List<Scored> pool = ranked.subList(0, Math.min(RERANK_POOL, ranked.size()));
        // 有 AI 时用意图吻合度覆盖排序；否则退回向量分（同一套严格门槛，宁缺毋滥）
        if (aiChatService.isEnabled()) {
            applyIntentScores(question, pool);
            pool = new ArrayList<>(pool);
            pool.sort(Comparator.comparingDouble((Scored s) -> s.intent).reversed());
        } else {
            for (Scored s : pool) {
                s.intent = clamp01(s.raw);
            }
        }

        Scored best = pool.get(0);
        float score = best.intent;
        if (score >= HIT) {
            return java.util.Optional.of(hit(best.entry, MSG_HIT));
        }
        if (score >= NEAR) {
            return java.util.Optional.of(suggest(pool, MSG_NEAR, NEAR));
        }
        if (score >= SUGGEST) {
            return java.util.Optional.of(suggest(pool, MSG_SUGGEST, SUGGEST));
        }
        return java.util.Optional.of(miss(cat, oldVersions));
    }

    public boolean hasUnindexedLatest(String category) {
        String cat = normalize(category);
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM train_doc d
                WHERE d.is_active = 1 AND d.status = 0 AND d.category = ?
                  AND d.latest_version_id IS NOT NULL
                  AND (
                    NOT EXISTS (
                      SELECT 1 FROM train_qa_item i
                      WHERE i.version_id = d.latest_version_id AND i.is_active = 1
                    )
                    OR EXISTS (
                      SELECT 1 FROM train_qa_item i
                      WHERE i.version_id = d.latest_version_id AND i.is_active = 1 AND i.embedding IS NULL
                    )
                  )
                """, Integer.class, cat);
        return n != null && n > 0;
    }

    private List<Scored> rank(String question, List<TrainQaVectorIndex.Entry> entries) {
        float[] query = embedQuery(question);
        List<Scored> ranked = new ArrayList<>();
        if (query == null) {
            return ranked;
        }
        for (TrainQaVectorIndex.Entry e : entries) {
            if (e.vector == null || e.vector.length != query.length) {
                continue;
            }
            float raw = TrainQaVectors.dot(query, e.vector);
            float bonus = matchesWording(question, e) ? 0.03f : 0f;
            Scored s = new Scored();
            s.entry = e;
            s.raw = raw;
            s.rank = raw + bonus;
            s.intent = 0f;
            ranked.add(s);
        }
        ranked.sort(Comparator.comparingDouble((Scored s) -> s.rank).reversed());
        if (ranked.size() > 20) {
            return new ArrayList<>(ranked.subList(0, 20));
        }
        return ranked;
    }

    private float[] embedQuery(String question) {
        String key = question.trim();
        float[] cached = queryCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<float[]> vecs = embeddingClient.embed(List.of(key.length() > 500 ? key.substring(0, 500) : key));
        if (vecs == null || vecs.isEmpty() || vecs.get(0) == null) {
            return null;
        }
        float[] vec = vecs.get(0);
        if (queryCache.size() > 500) {
            queryCache.clear();
        }
        queryCache.put(key, vec);
        return vec;
    }

    /**
     * 让模型给每个候选题干打「用户意图吻合度」0～1。
     * 解析失败时保留向量分作为 intent，避免整条链路不可用。
     */
    private void applyIntentScores(String question, List<Scored> pool) {
        if (pool == null || pool.isEmpty()) {
            return;
        }
        for (Scored s : pool) {
            s.intent = clamp01(s.raw);
        }
        StringBuilder catalog = new StringBuilder();
        for (int i = 0; i < pool.size(); i++) {
            catalog.append(i + 1).append(". ").append(pool.get(i).entry.question).append('\n');
        }
        String system = """
                你是收银操作培训答疑的意图匹配助手。
                根据「用户原话」与每条「候选题干」是否在问同一件事，给出吻合度 score（0~1）。
                规则：
                - 0.9 及以上：同一操作问题（允许口语、同义、倒装）
                - 0.8~0.9：相关但不确定是否同一题
                - 0.75~0.8：勉强相关
                - 低于 0.75：不相关或答非所问
                只输出 JSON，不要其它文字：
                {"scores":[{"index":1,"score":0.92},{"index":2,"score":0.41}]}
                index 从 1 开始，必须覆盖全部候选。
                """;
        String user = "用户原话：\n" + question + "\n\n候选题干：\n" + catalog;
        try {
            String raw = aiChatService.chat(system, user, null, 0.1);
            if (!applyScoreJson(raw, pool)) {
                // 兼容旧格式：只回了 index，则仅抬高该条，其余保持向量分
                int pick = parseIndexOnly(raw);
                if (pick > 0 && pick <= pool.size()) {
                    pool.get(pick - 1).intent = Math.max(pool.get(pick - 1).intent, 0.91f);
                }
            }
        } catch (Exception e) {
            log.warn("答疑意图打分失败，退回向量分: {}", e.getMessage());
        }
    }

    private boolean applyScoreJson(String raw, List<Scored> pool) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            String json = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode scores = root.get("scores");
            if (scores == null || !scores.isArray() || scores.isEmpty()) {
                return applyScorePairs(raw, pool);
            }
            boolean any = false;
            for (JsonNode row : scores) {
                if (row == null || !row.has("index") || !row.has("score")) {
                    continue;
                }
                int index = row.get("index").asInt(-1);
                float score = (float) row.get("score").asDouble(-1);
                if (index < 1 || index > pool.size() || score < 0) {
                    continue;
                }
                pool.get(index - 1).intent = clamp01(score);
                any = true;
            }
            return any;
        } catch (Exception e) {
            return applyScorePairs(raw, pool);
        }
    }

    private boolean applyScorePairs(String raw, List<Scored> pool) {
        Matcher m = SCORE_PAIR.matcher(raw == null ? "" : raw);
        boolean any = false;
        while (m.find()) {
            int index = Integer.parseInt(m.group(1));
            float score = Float.parseFloat(m.group(2));
            if (index >= 1 && index <= pool.size() && score >= 0) {
                pool.get(index - 1).intent = clamp01(score);
                any = true;
            }
        }
        return any;
    }

    private int parseIndexOnly(String raw) {
        Matcher m = INDEX_JSON.matcher(raw == null ? "" : raw);
        if (!m.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String extractJsonObject(String raw) {
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }

    private TrainAssistantAskVO hit(TrainQaVectorIndex.Entry e, String message) {
        TrainAssistantAskVO vo = new TrainAssistantAskVO();
        vo.setStatus("HIT");
        vo.setMessage(message);
        vo.setDocId(e.docId);
        vo.setVersionId(e.versionId);
        vo.setVersionLabel(e.versionLabel);
        vo.setHasOlderVersions(hasOlder(e.category));
        TrainAssistantAskVO.QaItem item = new TrainAssistantAskVO.QaItem();
        item.setQaItemId(e.id);
        item.setQuestion(e.question);
        item.setAnswer(e.answer);
        item.setAnswerHtml(e.answerHtml);
        item.setImages(e.images == null ? List.of() : new ArrayList<>(e.images));
        item.setVersionLabel(e.versionLabel);
        item.setFromCalibrate(false);
        vo.setItems(List.of(item));
        return vo;
    }

    private TrainAssistantAskVO suggest(List<Scored> ranked, String message, float minScore) {
        TrainAssistantAskVO vo = new TrainAssistantAskVO();
        vo.setStatus("SUGGEST");
        vo.setMessage(message);
        List<TrainAssistantAskVO.QaItem> items = new ArrayList<>();
        for (Scored s : ranked) {
            if (s.intent < minScore || items.size() >= SUGGEST_LIMIT) {
                break;
            }
            TrainAssistantAskVO.QaItem item = new TrainAssistantAskVO.QaItem();
            item.setQaItemId(s.entry.id);
            item.setQuestion(s.entry.question);
            item.setVersionLabel(s.entry.versionLabel);
            items.add(item);
        }
        if (!items.isEmpty()) {
            Scored top = ranked.get(0);
            vo.setDocId(top.entry.docId);
            vo.setVersionId(top.entry.versionId);
            vo.setVersionLabel(top.entry.versionLabel);
            vo.setHasOlderVersions(hasOlder(top.entry.category));
        }
        vo.setItems(items);
        if (items.isEmpty()) {
            vo.setStatus("NOT_FOUND");
            vo.setMessage(MSG_MISS);
        }
        return vo;
    }

    private TrainAssistantAskVO miss(String category, boolean oldVersions) {
        TrainAssistantAskVO vo = new TrainAssistantAskVO();
        boolean older = !oldVersions && hasOlder(category);
        vo.setHasOlderVersions(older);
        if (older) {
            vo.setStatus("NEED_OLD");
            vo.setMessage("暂时没有找到答案，是否检索旧版？");
        } else {
            vo.setStatus("NOT_FOUND");
            vo.setMessage(MSG_MISS);
        }
        fillLatestDoc(vo, category);
        return vo;
    }

    private void fillLatestDoc(TrainAssistantAskVO vo, String category) {
        jdbc.query("""
                SELECT d.id, d.latest_version_id, v.version_label
                FROM train_doc d
                LEFT JOIN train_doc_version v ON v.id = d.latest_version_id
                WHERE d.is_active = 1 AND d.status = 0 AND d.category = ?
                  AND d.latest_version_id IS NOT NULL
                ORDER BY d.update_time DESC, d.id DESC
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            vo.setDocId(rs.getLong("id"));
            vo.setVersionId(rs.getLong("latest_version_id"));
            vo.setVersionLabel(rs.getString("version_label"));
            return null;
        }, category);
    }

    private boolean hasOlder(String category) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM train_doc d
                JOIN train_doc_version v ON v.doc_id = d.id AND v.is_active = 1
                  AND v.id <> IFNULL(d.latest_version_id, 0)
                WHERE d.is_active = 1 AND d.status = 0 AND d.category = ?
                """, Integer.class, normalize(category));
        return n != null && n > 0;
    }

    private static boolean matchesWording(String question, TrainQaVectorIndex.Entry e) {
        if (TrainQaIntentMatcher.titleMatches(question, e.question)) {
            return true;
        }
        if (e.aliases == null) {
            return false;
        }
        for (String alias : e.aliases) {
            if (TrainQaIntentMatcher.titleMatches(question, alias)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String category) {
        if (!StringUtils.hasText(category)) {
            return TrainDocService.CATEGORY_CASHIER;
        }
        return category.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Scored {
        TrainQaVectorIndex.Entry entry;
        float raw;
        float rank;
        /** 用户意图与题干吻合度（优先 AI，否则为向量分） */
        float intent;
    }
}
