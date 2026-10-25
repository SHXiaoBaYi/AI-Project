package com.base.admin.service;

import com.base.admin.domain.vo.TrainAssistantAskVO;
import com.base.admin.util.TrainQaIntentMatcher;
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
 * 扫码提问：内存里对全部已发布题干做精确向量检索。
 * 高置信直接返回原文；几道题太接近时才让模型选题干；都不像则给出可点的原题或未命中。
 */
@Slf4j
@Service
public class TrainQaSearchService {

    private static final float DIRECT = 0.82f;
    private static final float DIRECT_GAP = 0.04f;
    private static final float KEYWORD_DIRECT = 0.74f;
    private static final float RERANK = 0.62f;
    private static final float SUGGEST = 0.55f;
    private static final Pattern INDEX_JSON = Pattern.compile("\"index\"\\s*:\\s*(-?\\d+)");

    private final TrainQaVectorIndex vectorIndex;
    private final TrainQaEmbeddingClient embeddingClient;
    private final AiChatService aiChatService;
    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, float[]> queryCache = new ConcurrentHashMap<>();

    public TrainQaSearchService(TrainQaVectorIndex vectorIndex,
                                TrainQaEmbeddingClient embeddingClient,
                                AiChatService aiChatService,
                                JdbcTemplate jdbc) {
        this.vectorIndex = vectorIndex;
        this.embeddingClient = embeddingClient;
        this.aiChatService = aiChatService;
        this.jdbc = jdbc;
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
        Scored best = ranked.get(0);
        Scored second = ranked.size() > 1 ? ranked.get(1) : null;
        float gap = second == null ? 1f : best.raw - second.raw;
        if (best.raw >= DIRECT && gap >= DIRECT_GAP) {
            return java.util.Optional.of(hit(best.entry, "已根据培训文档找到相关问答"));
        }
        if (best.raw >= KEYWORD_DIRECT && gap >= 0.05f && matchesWording(question, best.entry)) {
            return java.util.Optional.of(hit(best.entry, "已根据培训文档找到相关问答"));
        }
        if (best.raw >= RERANK && aiChatService.isEnabled()) {
            int pick = pickWithModel(question, ranked.subList(0, Math.min(12, ranked.size())));
            if (pick > 0 && pick <= Math.min(12, ranked.size())) {
                return java.util.Optional.of(hit(ranked.get(pick - 1).entry, "已根据培训文档找到相关问答"));
            }
        }
        if (best.raw >= SUGGEST) {
            return java.util.Optional.of(suggest(ranked));
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
            float bonus = matchesWording(question, e) ? 0.05f : 0f;
            Scored s = new Scored();
            s.entry = e;
            s.raw = raw;
            s.rank = raw + bonus;
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

    private int pickWithModel(String question, List<Scored> pool) {
        StringBuilder catalog = new StringBuilder();
        for (int i = 0; i < pool.size(); i++) {
            catalog.append(i + 1).append(". ").append(pool.get(i).entry.question).append('\n');
        }
        String system = """
                你是收银操作帮助的选题助手。
                根据用户原话，从候选题干中选出最贴近的一条。
                按语义判断，近义、口语、倒装都算同一件事。
                只能从候选里选，禁止编造。
                只输出 JSON：{"index":1}
                index 从 1 开始；若都不相关则 {"index":0}。
                """;
        String user = "用户原话：\n" + question + "\n\n候选题干：\n" + catalog;
        try {
            String raw = aiChatService.chat(system, user, null, 0.1);
            Matcher m = INDEX_JSON.matcher(raw == null ? "" : raw);
            if (!m.find()) {
                return -1;
            }
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            log.warn("答疑模型选题失败，改给出相近原题: {}", e.getMessage());
            return -1;
        }
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

    private TrainAssistantAskVO suggest(List<Scored> ranked) {
        TrainAssistantAskVO vo = new TrainAssistantAskVO();
        vo.setStatus("SUGGEST");
        vo.setMessage("没有直接对上，可以点下面的原题查看。");
        List<TrainAssistantAskVO.QaItem> items = new ArrayList<>();
        for (Scored s : ranked) {
            if (s.raw < 0.50f || items.size() >= 3) {
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
            vo.setMessage("没有找到答案");
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
            vo.setMessage("没有找到答案");
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
    }
}
