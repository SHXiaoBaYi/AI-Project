package com.base.admin.service;

import com.base.admin.config.GeoAutoMonitorProperties;
import com.base.admin.domain.vo.GeoAutoResultDetailVO;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 裁判：根据「问题 + AI回答原文 + 品牌/竞品词」判定品牌表现指标。
 * 裁判模型与被测平台解耦（默认走便宜稳定的厂商），temperature=0 保证口径稳定；
 * 原始输出全量留档（judge_raw）供人工复核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoMonitorJudgeService {

    private static final String RECOMMEND_APPEAR = "出现且推荐";
    private static final String RECOMMEND_MENTION = "出现未推荐";
    private static final String RECOMMEND_ABSENT = "未出现";

    private final GeoAutoMonitorProperties properties;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    /** 判定结果（字段与 geo_monitor_result 对齐） */
    public record JudgeOutcome(
            Integer mentioned,
            Integer rankNo,
            String recommendStatus,
            String sentiment,
            Integer mentionPosition,
            String negativeContent,
            String competitors,
            List<GeoAutoResultDetailVO.Citation> citations,
            String judgeRaw) {
    }

    public JudgeOutcome judge(String question, String answer, String brandWords, String competitorWords) {
        String system = """
                你是GEO（生成式引擎优化）监测数据分析师。你的任务：给定一个用户问题、AI助手的回答原文、品牌词表与竞品词表，判定品牌在该回答中的表现。
                只输出一个JSON对象，不要输出任何解释、markdown代码块或其他文字。字段定义：
                - mentioned: 布尔，品牌词表中任意一个词（或其别名）是否在回答中被提及
                - rank_no: 整数或null。若回答给出推荐列表/榜单/排名且品牌在其中，为品牌的位次（从1开始）；否则null
                - recommend_status: 字符串，三选一："出现且推荐"（品牌出现在明确的推荐列表、榜单或被加粗/重点强调）、"出现未推荐"（仅正文提及但不在推荐列表）、"未出现"
                - sentiment: 字符串，三选一："正面"、"中性"、"负面"（品牌相关表述的整体倾向）
                - mention_position: 整数或null，品牌第一次出现位于回答第几段（按换行分段，从1开始）
                - negative_content: 字符串，回答中涉及品牌的负面、错误或误导性表述原文摘录（60字内），无则空串
                - competitors: 字符串数组，回答中出现的竞品词表中的竞品名称
                - citations: 对象数组 [{title,site,context}]，回答中明确引用的外部信息来源（标题/站点/上下文摘录30字内），没有则空数组
                """;
        String user = """
                【用户问题】
                %s

                【品牌词表】
                %s

                【竞品词表】
                %s

                【AI回答原文】
                %s
                """.formatted(
                nz(question),
                nz(brandWords),
                StringUtils.hasText(competitorWords) ? competitorWords : "（无）",
                truncate(answer, properties.getAnswerMaxChars()));

        String raw = aiChatService.chat(system, user, properties.getJudgeProvider(), 0.0);
        return parse(raw, brandWords);
    }

    /** 健壮解析：剥代码围栏、截取首尾大括号、字段兜底规范化 */
    JudgeOutcome parse(String raw, String brandWords) {
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            throw new BusinessException("裁判输出无法解析为JSON");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException("裁判输出JSON格式异常：" + truncate(raw, 120));
        }

        boolean mentioned = node.path("mentioned").asBoolean(false);
        Integer rankNo = node.hasNonNull("rank_no") ? node.path("rank_no").asInt() : null;
        if (rankNo != null && rankNo <= 0) {
            rankNo = null;
        }
        String recommendStatus = normalizeRecommend(node.path("recommend_status").asText(""), mentioned, rankNo);
        String sentiment = normalizeSentiment(node.path("sentiment").asText(""));
        Integer mentionPosition = node.hasNonNull("mention_position") ? node.path("mention_position").asInt() : null;
        if (mentionPosition != null && mentionPosition <= 0) {
            mentionPosition = null;
        }
        String negative = truncate(node.path("negative_content").asText(""), 480);

        List<String> competitors = new ArrayList<>();
        JsonNode compNode = node.path("competitors");
        if (compNode.isArray()) {
            compNode.forEach(c -> {
                String name = c.asText("").trim();
                if (StringUtils.hasText(name)) {
                    competitors.add(truncate(name, 60));
                }
            });
        }
        // 兜底：裁判漏了竞品但回答明显提及时不清零，保留模型结果即可；此处仅去重
        String competitorText = String.join(",", competitors.stream().distinct().toList());

        List<GeoAutoResultDetailVO.Citation> citations = new ArrayList<>();
        JsonNode citeNode = node.path("citations");
        if (citeNode.isArray()) {
            citeNode.forEach(c -> {
                GeoAutoResultDetailVO.Citation citation = new GeoAutoResultDetailVO.Citation();
                citation.setTitle(truncate(c.path("title").asText(""), 200));
                citation.setSite(truncate(c.path("site").asText(""), 200));
                citation.setContext(truncate(c.path("context").asText(""), 200));
                if (StringUtils.hasText(citation.getTitle()) || StringUtils.hasText(citation.getSite())) {
                    citations.add(citation);
                }
            });
        }

        return new JudgeOutcome(
                mentioned ? 1 : 0,
                rankNo,
                recommendStatus,
                sentiment,
                mentionPosition,
                negative,
                competitorText,
                citations,
                raw);
    }

    private String normalizeRecommend(String value, boolean mentioned, Integer rankNo) {
        if (RECOMMEND_APPEAR.equals(value) || RECOMMEND_MENTION.equals(value) || RECOMMEND_ABSENT.equals(value)) {
            return value;
        }
        if (!mentioned) {
            return RECOMMEND_ABSENT;
        }
        return rankNo != null ? RECOMMEND_APPEAR : RECOMMEND_MENTION;
    }

    private String normalizeSentiment(String value) {
        if ("正面".equals(value) || "负面".equals(value) || "中性".equals(value)) {
            return value;
        }
        return "中性";
    }

    private String extractJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String nz(String text) {
        return text == null ? "" : text.trim();
    }
}
