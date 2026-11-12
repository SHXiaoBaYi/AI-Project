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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 收银答疑检索（精确率优先）：
 * <ol>
 *   <li>AI 抽取用户意图（在问什么操作）</li>
 *   <li>AI 做单向 NLI：题干意思包含用户意图才算蕴含（输出 item_id；否则 0）</li>
 *   <li>仅当 label=entailment 且 confidence≥阈值且硬闸门通过 → HIT，答案只读题库原文</li>
 *   <li>否则 NOT_FOUND / NEED_OLD，绝不拿沾边题凑答案</li>
 * </ol>
 * 题量不大时全量题录点选；过大时先向量粗召回再点选。
 */
@Slf4j
@Service
public class TrainQaSearchService {

    /** 点选置信度达到才 HIT（宁可漏答，禁止错答） */
    private static final float HIT = 0.90f;
    /** 全库粗召回条数（仅题太多时） */
    private static final int RECALL_TOP = 80;
    /** 向量粗召回后交给 AI 点选的条数 */
    private static final int RERANK_POOL = 40;
    /** 不超过此数直接全量题录点选 */
    private static final int FULL_CATALOG_LIMIT = 150;
    private static final float KEYWORD_BONUS = 0.18f;
    private static final float WORDING_BONUS = 0.03f;

    private static final String MSG_HIT = "已根据培训文档找到相关问答";
    private static final String MSG_MISS = "没有找到与该问题匹配的培训说明，请换个问法后再试。";

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

    /**
     * 结构化题录检索。无题录时返回 empty，由调用方走文档抽段兜底。
     * 有题录时：HIT 或 NOT_FOUND/NEED_OLD，不再返回 SUGGEST 当答案。
     */
    public java.util.Optional<TrainAssistantAskVO> trySearch(String question, String category, boolean oldVersions) {
        String cat = normalize(category);
        List<TrainQaVectorIndex.Entry> entries = vectorIndex.snapshot(cat, oldVersions);
        if (entries.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<TrainQaVectorIndex.Entry> poolEntries = buildPickPool(question, entries);
        if (poolEntries.isEmpty()) {
            return java.util.Optional.of(miss(cat, oldVersions));
        }

        if (!aiChatService.isEnabled()) {
            // 无 AI 时不做「猜题」直出，避免词面误命中
            log.warn("答疑AI未启用，结构化题录仅记未命中 question={}", question);
            return java.util.Optional.of(miss(cat, oldVersions));
        }

        String intent = extractIntent(question);
        PickResult pick = pickCatalogItem(question, intent, poolEntries);
        if (pick == null || pick.itemId <= 0 || !pick.entails() || pick.score < HIT) {
            log.info("答疑未命中 question={} intent={} pick={}", question, intent, pick);
            return java.util.Optional.of(miss(cat, oldVersions));
        }

        TrainQaVectorIndex.Entry hitEntry = findById(poolEntries, pick.itemId);
        if (hitEntry == null) {
            hitEntry = findById(entries, pick.itemId);
        }
        if (hitEntry == null) {
            log.warn("答疑点选 item_id 不在题录 question={} itemId={}", question, pick.itemId);
            return java.util.Optional.of(miss(cat, oldVersions));
        }
        if (!TrainQaIntentMatcher.passesHardGate(question, hitEntry.question, hitEntry.aliases)) {
            log.info("答疑HIT被硬闸门拦截 question={} itemId={} title={}",
                    question, hitEntry.id, hitEntry.question);
            return java.util.Optional.of(miss(cat, oldVersions));
        }

        log.info("答疑HIT question={} intent={} itemId={} label={} score={} reason={} title={}",
                question, intent, hitEntry.id, pick.label, pick.score, pick.reason, hitEntry.question);
        return java.util.Optional.of(hit(hitEntry, MSG_HIT));
    }

    public boolean hasCatalog(String category, boolean oldVersions) {
        return vectorIndex.hasCatalog(category, oldVersions);
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

    private List<TrainQaVectorIndex.Entry> buildPickPool(String question, List<TrainQaVectorIndex.Entry> entries) {
        if (entries.size() <= FULL_CATALOG_LIMIT) {
            return new ArrayList<>(entries);
        }
        List<Scored> ranked = rank(question, entries);
        List<TrainQaVectorIndex.Entry> pool = new ArrayList<>();
        for (Scored s : ranked) {
            if (pool.size() >= RERANK_POOL) {
                break;
            }
            pool.add(s.entry);
        }
        return pool;
    }

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
            s.rank = raw + KEYWORD_BONUS * keyword + bonus;
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
        try {
            String raw = aiChatService.chat(system, "用户原话：\n" + question, null, 0.1);
            String intent = readTextField(raw, "intent");
            if (StringUtils.hasText(intent)) {
                log.info("答疑意图抽取 question={} intent={}", question, intent);
                return intent.trim();
            }
        } catch (Exception e) {
            log.warn("答疑意图抽取失败: {}", e.getMessage());
        }
        return question;
    }

    /**
     * 单向 NLI：前提是题干（含别名），假设是用户意图。
     * 只有「题目的意思包含用户意图」才返回该题；否则 item_id=0。
     */
    private PickResult pickCatalogItem(String question, String intent, List<TrainQaVectorIndex.Entry> pool) {
        Map<Long, TrainQaVectorIndex.Entry> byId = new HashMap<>();
        StringBuilder catalog = new StringBuilder();
        for (TrainQaVectorIndex.Entry e : pool) {
            byId.put(e.id, e);
            catalog.append("ID=").append(e.id).append(" | ").append(e.question);
            if (e.aliases != null && !e.aliases.isEmpty()) {
                catalog.append(" | 别名: ").append(String.join("；", e.aliases));
            }
            catalog.append('\n');
        }
        String intentLine = StringUtils.hasText(intent) ? intent : question;
        String system = """
                你是收银操作培训答疑的题录判定助手。做单向自然语言推理（NLI），不是相似度排序，也不要在沾边题里挑最像的。
                前提（premise）= 题干及其别名
                假设（hypothesis）= 用户意图
                只判断一个方向：前提是否蕴含假设，即「题目的意思是否包含用户意图」。
                不要反过来要求用户意图覆盖题目的全部说法。
                entailment（才可选该题）：
                - 用户想完成的操作，落在这道题所讲操作之内（可以是整道题，也可以是题目涵盖的一部分）
                - 口语、同义、倒装、别名算同一件事（打折 ⊂ 折扣改价；厂子订货 ⊂ 找厂家要货）
                neutral 或 contradiction（item_id 必须为 0）：
                - 仅用词相近、主题沾边、场景相关、同属收银但操作不同
                - 用户意图比题目更具体，题目没有讲到意图里的那个点（题目盖不住意图）
                - 题录没有能盖住该意图的题（宁可未命中，禁止答非所问）
                - 易混对照（一律 contradiction）：
                  - 退款/退货/退单 ≠ 寄存/暂存
                  - 会员登记/开卡/录入手机号/能否复制粘贴 ≠ 积分兑换/取消兑换
                  - 肉票卡/储值卡结算/出票 ≠ 长短款/收银差/对账
                  - 查流水/查销售 ≠ 寄存
                  - 一般退单/退货步骤 ≠ 「退单找不到/超期无法退」异常说明（用户未提找不到/超期时不要选）
                confidence：蕴含 ≥0.9；不确定则 item_id=0、label=neutral、confidence 低于 0.75。
                只输出 JSON：
                {"item_id":280,"label":"entailment","confidence":0.95,"reason":"题目「如何折扣改价」包含意图「怎么打折」"}
                或 {"item_id":0,"label":"neutral","confidence":0,"reason":"没有任何题目包含该意图"}
                item_id 必须是题录中的 ID 或 0。label 只能是 entailment、neutral、contradiction。
                """;
        String user = "用户原话：\n" + question
                + "\n\n用户意图：\n" + intentLine
                + "\n\n题录：\n" + catalog;
        try {
            String raw = aiChatService.chat(system, user, null, 0.1);
            PickResult pick = parsePick(raw, byId);
            if (pick != null) {
                return pick;
            }
            log.warn("答疑点选结果无法解析 raw={}", abbreviate(raw));
        } catch (Exception e) {
            log.warn("答疑题录点选失败: {}", e.getMessage());
        }
        return new PickResult(0, 0f, "", "parse_failed");
    }

    private PickResult parsePick(String raw, Map<Long, TrainQaVectorIndex.Entry> byId) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            long itemId = 0;
            if (root.has("item_id")) {
                itemId = root.get("item_id").asLong(0);
            } else if (root.has("index")) {
                // 兼容旧格式：index 为列表序号时无法可靠映射，直接视为未命中
                long idx = root.get("index").asLong(0);
                if (idx == 0) {
                    itemId = 0;
                } else {
                    log.info("答疑点选返回旧 index 格式，忽略以免错配 index={}", idx);
                    return new PickResult(0, 0f, "", "legacy_index_ignored");
                }
            } else {
                return null;
            }
            float confidence = 0f;
            if (root.has("confidence")) {
                confidence = (float) root.get("confidence").asDouble(0);
            } else if (root.has("score")) {
                confidence = (float) root.get("score").asDouble(0);
            }
            String label = "";
            if (root.has("label") && root.get("label").isTextual()) {
                label = root.get("label").asText("").trim().toLowerCase(Locale.ROOT);
            }
            String reason = root.has("reason") && root.get("reason").isTextual()
                    ? root.get("reason").asText() : "";
            if (itemId != 0 && !byId.containsKey(itemId)) {
                log.warn("答疑点选 item_id 非法 id={}", itemId);
                return new PickResult(0, 0f, label, "invalid_item_id");
            }
            if (itemId != 0 && !"entailment".equals(label) && !"蕴含".equals(label)) {
                log.info("答疑点选非蕴含，视为未命中 id={} label={}", itemId, label);
                return new PickResult(0, clamp01(confidence), label, reason);
            }
            return new PickResult(itemId, clamp01(confidence), label, reason);
        } catch (Exception e) {
            return null;
        }
    }

    private String readTextField(String raw, String field) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(field)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            JsonNode node = root.get(field);
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText().trim();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
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

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "...";
    }

    private static TrainQaVectorIndex.Entry findById(List<TrainQaVectorIndex.Entry> entries, long id) {
        for (TrainQaVectorIndex.Entry e : entries) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
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
        float rank;
    }

    private record PickResult(long itemId, float score, String label, String reason) {
        boolean entails() {
            return "entailment".equals(label) || "蕴含".equals(label);
        }

        @Override
        public String toString() {
            return "itemId=" + itemId + ",score=" + score + ",label=" + label + ",reason=" + reason;
        }
    }
}
