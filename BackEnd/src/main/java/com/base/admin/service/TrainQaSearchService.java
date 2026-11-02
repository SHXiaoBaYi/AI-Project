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
 * 扫码提问：先理解用户意图，再在题录里选同一操作的题目。
 * <ul>
 *   <li>AI 先抽出简短意图（在问什么操作）</li>
 *   <li>题量不大时直接全量题录交给 AI 点选；否则向量粗召回后再点选</li>
 *   <li>AI 必须选出「同一操作」的题，或明确 index=0（题录没有）</li>
 *   <li>≥0.9 且硬闸门通过才 HIT；否则建议/未命中，绝不硬凑答案</li>
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
    /** 全库粗召回条数（仅题太多时） */
    private static final int RECALL_TOP = 100;
    /** 向量粗召回后交给 AI 点选的条数 */
    private static final int RERANK_POOL = 40;
    /** 题量不超过此数时，跳过向量，直接全量题录给 AI */
    private static final int FULL_CATALOG_LIMIT = 120;
    private static final int SUGGEST_LIMIT = 3;
    /** 关键词重合对召回分的加成上限 */
    private static final float KEYWORD_BONUS = 0.18f;
    private static final float WORDING_BONUS = 0.03f;
    /** 弱向量建议地板：过低不推；且须过硬闸门或有关键词重合 */
    private static final float WEAK_VECTOR_FLOOR = 0.42f;

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

        List<Scored> pool;
        if (aiChatService.isEnabled() && entries.size() <= FULL_CATALOG_LIMIT) {
            pool = catalogPool(entries);
        } else {
            List<Scored> ranked = rank(question, entries);
            if (ranked.isEmpty()) {
                return java.util.Optional.of(miss(cat, oldVersions));
            }
            pool = new ArrayList<>(ranked.subList(0, Math.min(RERANK_POOL, ranked.size())));
        }

        String intent = null;
        if (aiChatService.isEnabled()) {
            intent = extractIntent(question);
            applyIntentPick(question, intent, pool);
            pool.sort(Comparator.comparingDouble((Scored s) -> s.intent).reversed());
        } else {
            for (Scored s : pool) {
                s.intent = clamp01(Math.max(s.raw, s.keyword));
            }
            pool.sort(Comparator.comparingDouble((Scored s) -> s.intent).reversed());
        }

        // HIT：AI 确认同一操作且硬闸门通过；高分但闸门失败则降级
        for (Scored s : pool) {
            if (s.intent < HIT) {
                break;
            }
            if (passesEntryGate(question, s.entry)) {
                log.info("答疑HIT question={} intent={} score={} title={}",
                        question, intent, s.intent, s.entry.question);
                return java.util.Optional.of(hit(s.entry, MSG_HIT));
            }
            log.info("答疑HIT被硬闸门拦截 question={} score={} title={}", question, s.intent, s.entry.question);
            s.intent = Math.min(s.intent, NEAR - 0.01f);
        }
        pool.sort(Comparator.comparingDouble((Scored s) -> s.intent).reversed());

        List<Scored> gatedPool = filterSuggestable(question, pool);
        float best = gatedPool.isEmpty() ? 0f : gatedPool.get(0).intent;
        if (best >= NEAR) {
            return java.util.Optional.of(suggestByIntent(gatedPool, MSG_NEAR, NEAR));
        }
        if (best >= SUGGEST) {
            return java.util.Optional.of(suggestByIntent(gatedPool, MSG_SUGGEST, SUGGEST));
        }
        if (!aiChatService.isEnabled() || entries.size() > FULL_CATALOG_LIMIT) {
            List<Scored> ranked = rank(question, entries);
            TrainAssistantAskVO related = suggestByVector(question, ranked, MSG_NEAR);
            if (related.getItems() != null && !related.getItems().isEmpty()) {
                return java.util.Optional.of(related);
            }
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

    private List<Scored> catalogPool(List<TrainQaVectorIndex.Entry> entries) {
        List<Scored> pool = new ArrayList<>(entries.size());
        for (TrainQaVectorIndex.Entry e : entries) {
            Scored s = new Scored();
            s.entry = e;
            s.raw = 0f;
            s.keyword = TrainQaIntentMatcher.keywordOverlap("", e.question);
            s.rank = 0f;
            s.intent = 0f;
            pool.add(s);
        }
        return pool;
    }

    /** 全库打分，取 Top {@link #RECALL_TOP}：向量 + 关键词重合 + 措辞命中。 */
    private List<Scored> rank(String question, List<TrainQaVectorIndex.Entry> entries) {
        float[] query = embedQuery(question);
        List<Scored> ranked = new ArrayList<>();
        for (TrainQaVectorIndex.Entry e : entries) {
            float keyword = TrainQaIntentMatcher.keywordOverlap(question, e.question, e.aliases);
            float raw = 0f;
            if (query != null && e.vector != null && e.vector.length == query.length) {
                raw = TrainQaVectors.dot(query, e.vector);
            } else if (keyword <= 0f && !matchesWording(question, e)) {
                continue;
            }
            float bonus = matchesWording(question, e) ? WORDING_BONUS : 0f;
            Scored s = new Scored();
            s.entry = e;
            s.raw = raw;
            s.keyword = keyword;
            s.rank = raw + KEYWORD_BONUS * keyword + bonus;
            s.intent = 0f;
            ranked.add(s);
        }
        ranked.sort(Comparator.comparingDouble((Scored s) -> s.rank).reversed());
        if (ranked.size() > RECALL_TOP) {
            return new ArrayList<>(ranked.subList(0, RECALL_TOP));
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
     * 第一步：把口语问法收敛成「在问什么操作」的短意图，供后续点选题录。
     */
    private String extractIntent(String question) {
        String system = """
                你是收银门店操作培训的意图理解助手。
                根据用户原话，用一句短中文说明「用户想完成的操作」，不要回答操作步骤。
                规则：
                - 只描述用户意图，15~40 字
                - 区分易混操作：退款/退货/退单 ≠ 寄存；会员登记/开卡 ≠ 积分兑换；肉票/储值卡结算 ≠ 长短款对账；出票 ≠ 查流水
                - 若原话在抱怨界面/功能缺失（如不能复制粘贴），意图里要保留该具体诉求
                只输出 JSON：{"intent":"..."}
                """;
        String user = "用户原话：\n" + question;
        try {
            String raw = aiChatService.chat(system, user, null, 0.1);
            String intent = parseIntentJson(raw);
            if (StringUtils.hasText(intent)) {
                log.info("答疑意图抽取 question={} intent={}", question, intent);
                return intent.trim();
            }
        } catch (Exception e) {
            log.warn("答疑意图抽取失败: {}", e.getMessage());
        }
        return question;
    }

    private String parseIntentJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            JsonNode intent = root.get("intent");
            if (intent != null && intent.isTextual() && StringUtils.hasText(intent.asText())) {
                return intent.asText().trim();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    /**
     * 第二步：按「已理解的意图」在候选题录里点选同一操作的题目。
     * 题录没有对应操作时必须 index=0，禁止用沾边题凑答案。
     */
    private void applyIntentPick(String question, String intent, List<Scored> pool) {
        if (pool == null || pool.isEmpty()) {
            return;
        }
        for (Scored s : pool) {
            s.intent = 0f;
        }
        StringBuilder catalog = new StringBuilder();
        for (int i = 0; i < pool.size(); i++) {
            catalog.append(i + 1).append(". ").append(pool.get(i).entry.question).append('\n');
        }
        String intentLine = StringUtils.hasText(intent) ? intent : question;
        String system = """
                你是收银操作培训答疑的题录点选助手。
                已知用户意图，请在「候选题干」中选出与该意图为「同一操作」的题目。
                硬性规则：
                1. 只有操作目标一致才可选（允许口语/同义/倒装）
                2. 仅主题沾边、场景相关、同属收银但操作不同 → 必须选 0
                3. 题录里没有该操作 → 必须选 0（宁可未命中，禁止答非所问）
                4. 易混对照（不同操作，禁止互配）：
                   - 退款/退货/退单 ≠ 寄存/暂存
                   - 会员登记/开卡/录入手机号/能否复制粘贴 ≠ 积分兑换/取消兑换
                   - 肉票卡/储值卡结算/出票 ≠ 长短款/收银差/对账
                   - 查流水/查销售 ≠ 寄存
                   - 一般退单步骤 ≠ 「退单找不到/超期无法退」这类异常说明（用户未提到找不到/超期时不要选异常题）
                5. score：同一操作 ≥0.9；相关但不确定 0.75~0.89；否则 <0.75 且 index 必须为 0
                只输出 JSON，不要其它文字：
                {"index":3,"score":0.95,"reason":"同一操作：退货退款"}
                或 {"index":0,"score":0,"reason":"题录无会员登记"}
                index 从 1 起；没有合适题时 index=0。
                """;
        String user = "用户原话：\n" + question
                + "\n\n用户意图：\n" + intentLine
                + "\n\n候选题干：\n" + catalog;
        try {
            String raw = aiChatService.chat(system, user, null, 0.1);
            if (applyPickJson(raw, pool)) {
                return;
            }
            if (!applyScoreJson(raw, pool)) {
                int pick = parseIndexOnly(raw);
                if (pick > 0 && pick <= pool.size()) {
                    Scored s = pool.get(pick - 1);
                    s.intent = Math.min(NEAR - 0.01f, Math.max(s.intent, 0.82f));
                    log.info("答疑点选仅返回index，不抬到HIT index={} title={}", pick, s.entry.question);
                } else if (pick == 0) {
                    log.info("答疑点选明确无匹配 question={} intent={}", question, intentLine);
                }
            }
        } catch (Exception e) {
            log.warn("答疑题录点选失败，退回未命中分: {}", e.getMessage());
        }
    }

    /** 解析点选结果：只抬高被选中的那一道；其余保持 0。index=0 表示无匹配。 */
    private boolean applyPickJson(String raw, List<Scored> pool) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            if (!root.has("index")) {
                return false;
            }
            int index = root.get("index").asInt(-1);
            float score = root.has("score") ? (float) root.get("score").asDouble(0) : 0f;
            String reason = root.has("reason") && root.get("reason").isTextual()
                    ? root.get("reason").asText() : "";
            if (index == 0) {
                log.info("答疑点选无匹配 reason={}", reason);
                return true;
            }
            if (index < 1 || index > pool.size()) {
                return false;
            }
            pool.get(index - 1).intent = clamp01(score);
            log.info("答疑点选 index={} score={} reason={} title={}",
                    index, score, reason, pool.get(index - 1).entry.question);
            return true;
        } catch (Exception e) {
            return false;
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
        vo.setHasOlderVersions(hasOlderWithContent(e.category));
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

    private TrainAssistantAskVO suggestByIntent(List<Scored> ranked, String message, float minScore) {
        List<Scored> filtered = new ArrayList<>();
        for (Scored s : ranked) {
            if (s.intent >= minScore) {
                filtered.add(s);
            }
        }
        return buildSuggest(filtered, message);
    }

    /** 意图未达门槛：仅推有词面/族重合或硬闸门通过的向量近邻，避免无关噪声。 */
    private TrainAssistantAskVO suggestByVector(String question, List<Scored> vectorRanked, String message) {
        List<Scored> ordered = new ArrayList<>(vectorRanked);
        ordered.sort(Comparator.comparingDouble((Scored s) -> s.rank).reversed());
        List<Scored> top = new ArrayList<>();
        for (Scored s : ordered) {
            if (top.size() >= SUGGEST_LIMIT) {
                break;
            }
            if (s.raw < WEAK_VECTOR_FLOOR && s.keyword < 0.2f) {
                continue;
            }
            if (!isSuggestable(question, s)) {
                continue;
            }
            top.add(s);
        }
        return buildSuggest(top, message);
    }

    private List<Scored> filterSuggestable(String question, List<Scored> pool) {
        List<Scored> out = new ArrayList<>();
        for (Scored s : pool) {
            if (isSuggestable(question, s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean isSuggestable(String question, Scored s) {
        if (s.keyword >= 0.34f || matchesWording(question, s.entry)) {
            return true;
        }
        return passesEntryGate(question, s.entry);
    }

    private static boolean passesEntryGate(String question, TrainQaVectorIndex.Entry e) {
        return TrainQaIntentMatcher.passesHardGate(question, e.question, e.aliases);
    }

    private TrainAssistantAskVO buildSuggest(List<Scored> ranked, String message) {
        TrainAssistantAskVO vo = new TrainAssistantAskVO();
        vo.setStatus("SUGGEST");
        vo.setMessage(message);
        List<TrainAssistantAskVO.QaItem> items = new ArrayList<>();
        for (Scored s : ranked) {
            if (items.size() >= SUGGEST_LIMIT) {
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
            vo.setHasOlderVersions(hasOlderWithContent(top.entry.category));
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
        boolean older = !oldVersions && hasOlderWithContent(category);
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

    private boolean hasOlderWithContent(String category) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM train_doc d
                JOIN train_doc_version v ON v.doc_id = d.id AND v.is_active = 1
                  AND v.id <> IFNULL(d.latest_version_id, 0)
                  AND v.content_text IS NOT NULL AND TRIM(v.content_text) <> ''
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
        float keyword;
        float rank;
        /** 用户意图与题干吻合度（优先 AI 点选，否则为向量/关键词分） */
        float intent;
    }
}
