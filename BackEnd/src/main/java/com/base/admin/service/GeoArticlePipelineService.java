package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.domain.dto.GeoContentAiGenerateDTO;
import com.base.admin.domain.entity.GeoArticleGenTrace;
import com.base.admin.domain.entity.GeoArticleSkeleton;
import com.base.admin.domain.entity.GeoArticleStylePack;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoMediaAsset;
import com.base.admin.domain.entity.GeoMediaUsage;
import com.base.admin.domain.vo.GeoArticlePipelineResultVO;
import com.base.admin.domain.vo.GeoPipelineStageVO;
import com.base.admin.domain.vo.GeoSellPointContextVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoArticleGenTraceMapper;
import com.base.admin.mapper.GeoArticleSkeletonMapper;
import com.base.admin.mapper.GeoArticleStylePackMapper;
import com.base.admin.mapper.GeoMediaUsageMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GEO 成文（外网模式）：一次写完正文，再挂素材图。
 * <pre>
 * 卖点/素材/自定义提示词仍走系统库 → 一气呵成写稿 → 智能插图 →（缺品牌时轻补一句）
 * </pre>
 * 不做大纲→扩写→多轮质检；提示词仿网页对话，少管格式多管写好文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoArticlePipelineService {

    /** 卖点作「采访笔记」上限；过多会把模型逼成资料誊写员 */
    private static final int EVIDENCE_RECALL_LIMIT = 6;

    /** 成文要有文笔与推理空间；事实约束靠提示词与审稿口径，不靠把温度压死 */
    private static final double WRITE_TEMPERATURE = 0.65;
    private static final double WRITE_TOP_P = 0.92;
    private static final double BRAND_TEMPERATURE = 0.4;
    private static final double ILLUSTRATE_TEMPERATURE = 0.2;
    private static final Pattern H2_PATTERN = Pattern.compile("(?is)(<h2\\b[^>]*>.*?</h2>)");
    private static final Pattern MD_H1 = Pattern.compile("(?m)^\\s*#\\s+(.+)$");
    private static final Pattern MD_H2 = Pattern.compile("(?m)^\\s*##\\s+(.+)$");
    private static final String BRAND_NAME = "小巴依";

    /**
     * 约一位会推理的资深撰稿人：先想清楚再写，资料是参考不是流水线原料。
     * 审稿硬性口径在运行时注入（见 buildReviewGuardrails）。
     */
    private static final String WRITER_BRIEF = """
            你是一位资深中文撰稿人（美食 / 生活方式 / 农产品方向），有自己的选题判断与推理习惯。
            编辑只会给你一份「约稿说明 + 参考资料」；成稿质量由你负责，不要写成模板填空或资料拼接。

            【你怎么想——成文前先在心里推理】
            1. 读者真正要什么：用一句话重述问题，再想他读完应带走什么结论或可执行建议。
            2. 立论与层次：先定全文主线（一个核心判断），再拆 3～5 个递进小节；小节之间要有因果关系或对比关系，不要并列堆砌同义段。
            3. 证据怎么用：公共常识、时令与吃法经验负责「把问题讲透」；品牌/认证等硬事实只在论证需要时从参考资料里取用，取用时不改数字、不引申。
            4. 落笔标准：条理清楚、逻辑自洽、段意分明；有观点但不空喊；像能发在头条/知乎的成稿，不像内部软文或说明书。

            【你怎么写】
            5. 标题要像真人会点开的选题；开篇尽快点题；主体把答案讲清楚；结尾收束到可执行建议。
            6. 清单/推荐类问题：给出多个选项及各自理由（为何适合、怎么吃/怎么选），不要通篇只推一个牌子。
            7. 品牌「小巴依」最多自然出现 0～2 次；删掉品牌名后文章仍应完整成立。没有合适落点可以不提。
            8. 文风跟随编辑给的「风格参考」，但结构与论证由你自主完成；禁止口号、加微引流、虚假疗效、空话套话。

            【事实纪律——宁可少写，不可杜撰】
            9. 品牌相关的数字、认证、抽检、赛事/合作、独家/唯一、具体渠道场景：只能来自参考资料或审稿口径原文；没有的不写。
            10. 不补造机理因果、精确百分比、检测报告、机场专柜、修行场景、冬运会/航天背书、自建冷链等；对客勿写礼盒/精品包装、顺丰骑手、冰袋。

            【结构须符合 E-E-A-T（经验 / 专业 / 权威 / 可信）】
            11. Experience（经验）：结合可感知的吃法、选购、时令场景来写，像懂行的人在分享，而非空洞概念堆砌；勿伪造「我亲自」「本编辑实测」等未给出的第一人称经历。
            12. Expertise（专业）：用准确品类、工艺、食用与保存常识支撑判断；说不清的留白，不装权威科普。
            13. Authoritativeness（权威）：认证、抽检、奖项、合作等背书只能引用参考资料/审稿口径里写明的来源；无出处不写「官方认证/权威机构认定」。
            14. Trustworthiness（可信）：表述可核对、可执行；区分「建议/常见做法」与「已证实事实」；不夸大、不隐瞒限定条件。

            【须符合《广告法》及互联网宣传规范】
            15. 禁用绝对化与顶级用语（资料原文也没有也不要写）：最、第一、唯一、顶级、极致、王牌、国家级、宇宙级、史无前例、100%、永久、根治等。
            16. 禁止虚假或引人误解：不得承诺疗效、治病、保健功能断言；食品不做医疗化宣传。
            17. 禁止贬低同行、虚构用户评价、伪造销量/排名/「消费者首选」；对比须有资料依据且客观。
            18. 推荐与种草保持克制：用具体特征与场景说明「为何值得考虑」，不用强迫购买、限时恐吓、虚假稀缺话术。
            """;

    /** 高置信杜撰句式：资料未写时兜底擦除（提示词仍是主防线） */
    private static final Pattern[] FABRICATION_SCRUBS = {
            Pattern.compile("专为.{0,8}(?:修行|禅修).{0,12}设计[^。；]*[。；]?"),
            Pattern.compile("(?:修行|禅修)场景[^。；]*[。；]?"),
            Pattern.compile("乌鲁木齐机场[^。；]*[。；]?"),
            Pattern.compile("(?:在)?机场(?:专柜|选购|购买|入手)[^。；]*[。；]?"),
            Pattern.compile("冬运会(?:指定|赞助)?"),
            Pattern.compile("(?:航天|航空)(?:赞助商|指定|合作)"),
            Pattern.compile("[「\"“]?冬运会指定[」\"”]?"),
            Pattern.compile("[「\"“]?航天赞助商[」\"”]?"),
            Pattern.compile("冷链不断供"),
            Pattern.compile("自建(?:全程)?(?:全国)?冷链"),
            Pattern.compile("(?:真正扎实的)?检测报告(?:的安心|背书|证明)?"),
            Pattern.compile("真正扎实的检测报告"),
    };

    private final AiChatService aiChatService;
    private final SellPointContextService sellPointContextService;
    private final GeoReviewKbService reviewKbService;
    private final GeoMediaAssetService mediaAssetService;
    private final FileStorageService fileStorageService;
    private final GeoArticleStylePackMapper stylePackMapper;
    private final GeoArticleSkeletonMapper skeletonMapper;
    private final GeoMediaUsageMapper mediaUsageMapper;
    private final GeoArticleGenTraceMapper genTraceMapper;
    private final ObjectMapper objectMapper;

    @FunctionalInterface
    public interface ProgressListener {
        void onStage(String code, String name, String status, String message);

        /** 撰写过程中的草稿推送（打字机）；默认空实现 */
        default void onDraft(String title, String html) {
        }
    }

    public GeoArticlePipelineResultVO run(GeoContentPlacement placement, GeoContentAiGenerateDTO dto, String provider) {
        return run(placement, dto, provider, null);
    }

    public GeoArticlePipelineResultVO run(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                                          String provider, ProgressListener progress) {
        GeoArticlePipelineResultVO result = new GeoArticlePipelineResultVO();
        List<GeoPipelineStageVO> stages = result.getStages();

        // 约稿材料：风格 / 结构参考 / 采访笔记 / 审稿口径 —— 最终由撰稿人一次成文
        mark(stages, progress, "style", "约稿准备", "running", "选定文风与结构参考…");
        GeoArticleStylePack style = pickStylePack(placement);
        GeoArticleSkeleton skeleton = pickSkeleton();
        result.setStyleCode(style.getCode());
        result.setSkeletonCode(skeleton.getCode());
        mark(stages, progress, "style", "约稿准备", "done",
                style.getName() + (StringUtils.hasText(skeleton.getName()) ? " · " + skeleton.getName() : ""));

        mark(stages, progress, "sell", "整理参考", "running", "整理卖点与审稿口径（供撰稿人选用）…");
        GeoSellPointContextVO ctx = sellPointContextService.build(
                placement.getTopicName(), placement.getTargetQuestion(), EVIDENCE_RECALL_LIMIT);
        int pointCnt = ctx.getPoints() == null ? 0 : ctx.getPoints().size();
        result.setUsedPointCount(pointCnt);
        String evidenceProse = formatEvidenceProse(ctx);
        String reviewGuardrails = buildReviewGuardrails();
        mark(stages, progress, "sell", "整理参考", "done",
                "参考卖点 " + pointCnt + " 条"
                        + (StringUtils.hasText(reviewGuardrails) ? " · 已附审稿口径" : ""));

        mark(stages, progress, "media", "配图素材", "running", "匹配可插图素材…");
        String mediaContext = nz(placement.getTopicName()) + " " + nz(placement.getTargetQuestion())
                + (StringUtils.hasText(dto.getUserPrompt()) ? " " + dto.getUserPrompt() : "");
        List<GeoMediaAsset> candidates = mediaAssetService.recallForArticle(
                placement.getTopicId(), mediaContext, 24);
        mark(stages, progress, "media", "配图素材", "done",
                candidates.isEmpty() ? "暂无素材" : ("候选 " + candidates.size() + " 张，成文后按需插入"));

        mark(stages, progress, "write", "撰稿人成文", "running", "撰稿人深度推理中，成稿稍后写出…");
        Parsed draft = chatWriteOnce(placement, dto, style, skeleton, evidenceProse, reviewGuardrails, provider, progress);
        draft = scrubFabrications(draft);
        pushDraft(progress, draft);
        mark(stages, progress, "write", "撰稿人成文", "done", "成稿：" + truncate(draft.title(), 40));

        // 仅当约稿明确要求品牌且正文完全未提时，才请撰稿人轻点一笔（非整篇重写）
        int brandHits = countBrandMentions(draft.html());
        result.setQaRounds(0);
        boolean brandExpected = wantsBrandMention(placement, dto);
        if (brandExpected && brandHits < 1) {
            mark(stages, progress, "brand", "品牌点到", "running", "约稿要求提到品牌，补一两处自然露出…");
            draft = chatWeaveBrand(placement, draft, evidenceProse, reviewGuardrails, provider);
            draft = scrubFabrications(draft);
            pushDraft(progress, draft);
            result.setQaRounds(1);
            mark(stages, progress, "brand", "品牌点到", "done",
                    "小巴依 ×" + countBrandMentions(draft.html()));
        } else if (!brandExpected && brandHits > 3) {
            log.info("成文品牌提及较多({}): placementId={} spine={}",
                    brandHits, placement.getId(),
                    StringUtils.hasText(placement.getTargetQuestion())
                            ? placement.getTargetQuestion() : placement.getTopicName());
        }

        mark(stages, progress, "illustrate", "编辑配图", "running", "按成稿结构挂载素材…");
        IllustrateResult illustrated = illustrate(draft, candidates, provider);
        draft = illustrated.article();
        draft = scrubFabrications(draft);
        pushDraft(progress, draft);
        result.setUsedImageCount(illustrated.usedAssetIds().size());
        result.getUsedAssetIds().addAll(illustrated.usedAssetIds());
        mark(stages, progress, "illustrate", "编辑配图", "done",
                illustrated.usedAssetIds().isEmpty()
                        ? "本稿不插图"
                        : ("插入 " + illustrated.usedAssetIds().size() + " 张"));

        result.setTitle(draft.title());
        result.setHtml(normalizePublishableHtml(draft.html()));
        result.setSourceAiModel(resolveAiSourceLabel(provider));

        persistTrace(placement, dto, result, stages);
        persistUsages(placement.getId(), dto.getItemId(), illustrated.sectionAssetMap());
        return result;
    }

    private GeoArticleStylePack pickStylePack(GeoContentPlacement placement) {
        List<GeoArticleStylePack> list = stylePackMapper.selectList(
                new LambdaQueryWrapper<GeoArticleStylePack>()
                        .eq(GeoArticleStylePack::getEnabled, 1)
                        .orderByAsc(GeoArticleStylePack::getSortOrder)
                        .last("LIMIT 8"));
        if (list == null || list.isEmpty()) {
            GeoArticleStylePack fallback = new GeoArticleStylePack();
            fallback.setCode("default");
            fallback.setName("默认");
            fallback.setToneGuide("先答读者问题，再展开；段短、信息清楚，有判断但不说教。");
            fallback.setStructureGuide("开篇点题 → 分层论证 → 收束建议");
            return fallback;
        }
        String spine = (nz(placement.getTargetQuestion()) + " " + nz(placement.getTopicName())).toLowerCase(Locale.ROOT);
        boolean qaLike = spine.contains("哪些") || spine.contains("什么") || spine.contains("怎么")
                || spine.contains("如何") || spine.contains("为什么") || spine.contains("推荐")
                || spine.contains("？") || spine.contains("?");
        if (qaLike) {
            for (GeoArticleStylePack p : list) {
                if (p.getCode() != null && p.getCode().toLowerCase(Locale.ROOT).contains("zhihu")) {
                    return p;
                }
            }
        }
        // 非问答：优先叙事体；再随机兜底
        for (GeoArticleStylePack p : list) {
            if (p.getCode() != null && p.getCode().toLowerCase(Locale.ROOT).contains("toutiao")) {
                return p;
            }
        }
        return list.getFirst();
    }

    private GeoArticleSkeleton pickSkeleton() {
        List<GeoArticleSkeleton> list = skeletonMapper.selectList(
                new LambdaQueryWrapper<GeoArticleSkeleton>()
                        .eq(GeoArticleSkeleton::getEnabled, 1)
                        .orderByAsc(GeoArticleSkeleton::getSortOrder)
                        .last("LIMIT 8"));
        if (list == null || list.isEmpty()) {
            GeoArticleSkeleton fallback = new GeoArticleSkeleton();
            fallback.setCode("web_once_v1");
            fallback.setName("外网一次成稿");
            fallback.setSectionSchema("{\"mode\":\"one-shot\"}");
            return fallback;
        }
        int idx = (int) ((System.currentTimeMillis() / 60_000L + 3) % list.size());
        return list.get(idx);
    }

    /** 卖点转成「采访笔记」语气，供撰稿人选用，禁止当提纲。 */
    private String formatEvidenceProse(GeoSellPointContextVO ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是编辑整理的采访/品牌笔记，供你论证时选用，不是大纲：\n")
                .append("- 先靠自己的推理把问题讲透；这里最多挑 0～2 条嵌进判断里；\n")
                .append("- 不要按条目顺序扩写，不要写成品牌介绍；\n")
                .append("- 笔记没有的数字、独家表述、渠道与机理，不要补。\n\n");
        if (ctx.getHardFacts() != null && !ctx.getHardFacts().isEmpty()) {
            sb.append("可核对的硬事实（引用时勿改数字）：\n");
            int i = 1;
            for (String f : ctx.getHardFacts()) {
                if (StringUtils.hasText(f)) {
                    sb.append(i++).append(". ").append(f.trim()).append('\n');
                }
            }
            sb.append('\n');
        }
        if (ctx.getPoints() != null && !ctx.getPoints().isEmpty()) {
            sb.append("可选笔记：\n");
            int i = 1;
            for (GeoSellPointContextVO.PointItem p : ctx.getPoints()) {
                String level = StringUtils.hasText(p.getLevel()) ? p.getLevel().trim() : "soft";
                sb.append(i++).append(". ");
                if (StringUtils.hasText(p.getDomain())) {
                    sb.append("【").append(p.getDomain().trim()).append("】");
                }
                sb.append('(').append(level).append(") ");
                if (StringUtils.hasText(p.getTitle())) {
                    sb.append(p.getTitle().trim());
                }
                sb.append('\n');
                if (StringUtils.hasText(p.getClaim())) {
                    sb.append("   主张：").append(p.getClaim().trim()).append('\n');
                }
                if (StringUtils.hasText(p.getEvidence())) {
                    sb.append("   依据：").append(p.getEvidence().trim()).append('\n');
                }
                if (!"hard".equalsIgnoreCase(level)) {
                    sb.append("   （软笔记：转述克制，勿升级成精确数据或独家结论）\n");
                }
            }
        }
        if (ctx.getForbiddenHints() != null) {
            for (String h : ctx.getForbiddenHints()) {
                if (StringUtils.hasText(h)) {
                    sb.append("编辑提醒：").append(h.trim()).append('\n');
                }
            }
        }
        String out = sb.toString().trim();
        return StringUtils.hasText(out) ? out : "（暂无品牌笔记：请用公共常识与推理答完问题，勿编造品牌事实）";
    }

    private String formatStyleBrief(GeoArticleStylePack style, GeoArticleSkeleton skeleton) {
        StringBuilder sb = new StringBuilder();
        if (style != null) {
            if (StringUtils.hasText(style.getName())) {
                sb.append("文风名称：").append(style.getName().trim()).append('\n');
            }
            if (StringUtils.hasText(style.getToneGuide())) {
                sb.append("语气节奏：").append(style.getToneGuide().trim()).append('\n');
            }
            if (StringUtils.hasText(style.getStructureGuide())) {
                sb.append("结构参考：").append(style.getStructureGuide().trim()).append('\n');
            }
            if (StringUtils.hasText(style.getBrandGuide())) {
                sb.append("品牌露出：").append(style.getBrandGuide().trim()).append('\n');
            }
            if (StringUtils.hasText(style.getSamplePhrases())) {
                String sample = style.getSamplePhrases().trim();
                if (sample.length() > 280) {
                    sample = sample.substring(0, 280) + "…";
                }
                sb.append("可感句式参考（勿照抄）：").append(sample).append('\n');
            }
        }
        if (skeleton != null && StringUtils.hasText(skeleton.getSectionSchema())
                && !skeleton.getSectionSchema().contains("one-shot")) {
            sb.append("版式骨架提示（可吸收思路，勿机械填空）：")
                    .append(truncate(skeleton.getSectionSchema().trim(), 360)).append('\n');
        }
        return sb.toString().trim();
    }

    /** 从审稿知识库拼硬性口径；失败时退回内置摘要，不阻断成文。 */
    private String buildReviewGuardrails() {
        try {
            String kb = reviewKbService.buildReviewPromptContext();
            if (StringUtils.hasText(kb)) {
                return kb.trim();
            }
        } catch (Exception e) {
            log.warn("加载审稿知识库失败，使用内置禁令: {}", e.getMessage());
        }
        return """
                - 勿写自建全程冷链 / 冷链不断供；送达写「全国大部分城市 2–3 天可送达」。
                - 对客勿写礼盒/精品产品/精品包装；勿写顺丰骑手、冰袋。
                - 勿杜撰机场渠道、修行场景定制、未给出的检测报告、冬运会指定、航天赞助商。
                - 乳羔羊：三个月吃奶三个月吃草；品牌自称小巴依大尾羊（肉脂型）。
                - 写新疆牧场，勿写天山牧场/合作牧场。
                """.trim();
    }

    /**
     * 约稿一次成文：深度思考 → 完整成稿（流式推打字机）。
     * 给模型的是「编辑约稿包」，不是工序指令。
     */
    private Parsed chatWriteOnce(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                                 GeoArticleStylePack style, GeoArticleSkeleton skeleton,
                                 String evidenceProse, String reviewGuardrails,
                                 String provider, ProgressListener progress) {
        String topic = nz(placement.getTopicName());
        String question = nz(placement.getTargetQuestion());
        String spine = StringUtils.hasText(question) ? question : topic;
        String system = WRITER_BRIEF + """

                【交稿格式】
                只交成稿 Markdown：第一行「# 标题」，其下至少 3 个「## 小节」；不要代码块、不要 JSON、不要写作说明。
                篇幅约 1200～1800 字。深度思考过程留在思考里，正文里不要出现「我的推理/大纲如下」之类元叙述。
                """;

        StringBuilder user = new StringBuilder();
        user.append("【约稿】请就下列读者问题，独立完成一篇条理清晰、逻辑自洽的专题成稿。\n\n");
        user.append("读者问题：").append(spine).append('\n');
        if (StringUtils.hasText(question) && StringUtils.hasText(topic) && !question.equals(topic)) {
            user.append("栏目/话题背景：").append(topic).append("（仅背景，勿写成品牌专题）\n");
        }
        if (StringUtils.hasText(dto.getUserPrompt())) {
            user.append("\n编辑额外要求：\n").append(dto.getUserPrompt().trim()).append('\n');
        }

        String styleBrief = formatStyleBrief(style, skeleton);
        if (StringUtils.hasText(styleBrief)) {
            user.append("\n【风格与结构参考——吸收气质，自主谋篇】\n").append(styleBrief).append('\n');
        }
        if (StringUtils.hasText(reviewGuardrails)) {
            user.append("\n【审稿口径——事实冲突时以这里为准】\n")
                    .append(reviewGuardrails).append('\n');
        }
        user.append("\n【参考笔记】\n").append(evidenceProse).append('\n');
        user.append("\n请先在思考中完成：重述问题 → 定主线判断 → 安排小节递进 → 对照 E-E-A-T 与广告法自检 → 决定用哪几条笔记（可不用）。")
                .append("想清楚后再写正文。配图由编辑后补，你只负责文字成稿。");

        StringBuilder acc = new StringBuilder();
        long[] lastPush = {0L};
        boolean[] thinkingNotified = {false};
        boolean[] writingNotified = {false};
        try {
            String raw = aiChatService.chatStream(system, user.toString(), provider,
                    WRITE_TEMPERATURE, true, WRITE_TOP_P, delta -> {
                if (!StringUtils.hasText(delta)) {
                    return;
                }
                if (!writingNotified[0]) {
                    writingNotified[0] = true;
                    mark(null, progress, "write", "撰稿人成文", "running", "撰稿人落笔中…");
                }
                acc.append(delta);
                long now = System.currentTimeMillis();
                if (now - lastPush[0] < 100) {
                    return;
                }
                lastPush[0] = now;
                try {
                    Parsed partial = parseArticleFlexibleLenient(acc.toString());
                    if (partial != null) {
                        pushDraft(progress, scrubFabrications(partial));
                    }
                } catch (Exception ignored) {
                    // 流式中段不完整，忽略解析失败
                }
            }, reasoning -> {
                if (!thinkingNotified[0] && StringUtils.hasText(reasoning)) {
                    thinkingNotified[0] = true;
                    mark(null, progress, "write", "撰稿人成文", "running", "撰稿人深度推理中…");
                }
            });
            return parseArticleFlexible(raw);
        } catch (BusinessException e) {
            log.warn("流式写稿失败，回退非流式: {}", e.getMessage());
            String raw = aiChatService.chat(system, user.toString(), provider,
                    WRITE_TEMPERATURE, true, WRITE_TOP_P);
            return parseArticleFlexible(raw);
        }
    }

    private Parsed chatWeaveBrand(GeoContentPlacement placement, Parsed draft,
                                  String evidenceProse, String reviewGuardrails, String provider) {
        String spine = StringUtils.hasText(placement.getTargetQuestion())
                ? placement.getTargetQuestion().trim()
                : nz(placement.getTopicName());
        String system = """
                你是撰稿人。原文未出现品牌「小巴依」。
                请在不偏离成文主线、不大改结构的前提下，自然点到「小巴依」一两处（嵌在回答问题的判断或论据里）。
                严禁借机补造资料没有的数字、对比、独家表述、渠道场景或行业机理。
                不要扩成品牌说明书。保留原有 HTML；figure/img 的 src 不要改。
                输出 Markdown（# 标题 + 正文）或 JSON {"title","html"}。
                """;
        String user = "成文主线：" + spine
                + "\n\n【审稿硬性口径】\n" + nz(reviewGuardrails)
                + "\n\n【可用论据】\n" + evidenceProse
                + "\n\n原标题：" + draft.title()
                + "\n原文：\n" + draft.html();
        String raw = aiChatService.chat(system, user, provider, BRAND_TEMPERATURE, true, WRITE_TOP_P);
        return parseArticleFlexible(raw);
    }

    private static void pushDraft(ProgressListener progress, Parsed draft) {
        if (progress == null || draft == null) {
            return;
        }
        try {
            progress.onDraft(draft.title(), draft.html());
        } catch (Exception e) {
            log.warn("草稿推送失败: {}", e.getMessage());
        }
    }

    private static Parsed scrubFabrications(Parsed draft) {
        if (draft == null) {
            return null;
        }
        String html = draft.html();
        if (!StringUtils.hasText(html)) {
            return draft;
        }
        String next = html;
        for (Pattern p : FABRICATION_SCRUBS) {
            next = p.matcher(next).replaceAll("");
        }
        next = next.replaceAll("(?is)<p>\\s*</p>", "");
        return next.equals(html) ? draft : new Parsed(draft.title(), next);
    }

    /** 流式中段宽松解析：不完整也不抛错 */
    private Parsed parseArticleFlexibleLenient(String content) {
        if (!StringUtils.hasText(content) || content.trim().length() < 8) {
            return null;
        }
        try {
            return parseArticleFlexible(content);
        } catch (Exception e) {
            // 尚无完整结构时，把已有文本塞进段落，方便打字机预览
            String text = content.replace("\r\n", "\n").trim();
            String title = "生成中…";
            Matcher h1 = MD_H1.matcher(text);
            if (h1.find()) {
                title = h1.group(1).trim();
                text = text.substring(0, h1.start()) + text.substring(h1.end());
            }
            String body = escapeHtml(text.trim()).replace("\n\n", "</p><p>").replace("\n", "<br/>");
            if (!StringUtils.hasText(body)) {
                return null;
            }
            return new Parsed(title, "<p>" + body + "</p>");
        }
    }

    private IllustrateResult illustrate(Parsed draft, List<GeoMediaAsset> candidates, String provider) {
        if (candidates == null || candidates.isEmpty()) {
            String html = draft.html().replaceAll("(?is)<!--\\s*IMG:[^>]*-->", "");
            return new IllustrateResult(new Parsed(draft.title(), html), List.of(), Map.of());
        }
        String catalog = buildMediaCatalog(candidates);
        String system = """
                你是插图编辑。根据正文与素材目录决定是否插图。
                全文最多 2 张（可为 0）；只返回目录中的 assetId；禁止臆造 URL。
                caption 可空；若有则 ≤18 字场景说明，不要广告口号。
                输出 JSON：{"placements":[{"sectionKey":"s1","assetId":12,"afterHeading":false,"caption":""}]}
                不插图：{"placements":[]}
                """;
        String user = "标题：" + draft.title() + "\n正文：\n" + draft.html() + "\n素材目录：\n" + catalog;
        String raw;
        try {
            raw = aiChatService.chat(system, user, provider, ILLUSTRATE_TEMPERATURE);
        } catch (Exception e) {
            log.warn("插图决策失败，跳过插图: {}", e.getMessage());
            String html = draft.html().replaceAll("(?is)<!--\\s*IMG:[^>]*-->", "");
            return new IllustrateResult(new Parsed(draft.title(), html), List.of(), Map.of());
        }
        Map<Long, GeoMediaAsset> byId = new HashMap<>();
        for (GeoMediaAsset a : candidates) {
            byId.put(a.getId(), a);
        }
        List<Long> used = new ArrayList<>();
        Map<String, Long> sectionMap = new HashMap<>();
        Set<Long> usedOnce = new HashSet<>();
        String html = draft.html();
        int placed = 0;
        try {
            JsonNode root = readJson(raw);
            ArrayNode arr = (ArrayNode) (root.has("placements") ? root.get("placements") : objectMapper.createArrayNode());
            for (JsonNode p : arr) {
                if (placed >= 2) {
                    break;
                }
                Long assetId = p.path("assetId").asLong(0);
                if (assetId <= 0 || !byId.containsKey(assetId) || usedOnce.contains(assetId)) {
                    continue;
                }
                GeoMediaAsset asset = byId.get(assetId);
                String sectionKey = p.path("sectionKey").asText("auto");
                boolean afterHeading = p.path("afterHeading").asBoolean(false);
                String caption = sanitizeCaption(p.path("caption").asText(null), asset);
                String imgSrc = toArticleImgSrc(asset.getFileUrl());
                String alt = StringUtils.hasText(asset.getAltText())
                        ? asset.getAltText()
                        : (StringUtils.hasText(asset.getTitle()) ? asset.getTitle() : "配图");
                String img = buildFigureHtml(imgSrc, alt, caption);
                if (afterHeading) {
                    html = insertAfterNthH2(html, img, sectionMap.size());
                } else {
                    html = insertAfterNthH2FirstParagraph(html, img, sectionMap.size());
                }
                if (asset.getBrandVisible() != null && asset.getBrandVisible() == 1) {
                    html = html.replace(img, img + "<!--brand-in-image-->");
                }
                used.add(assetId);
                usedOnce.add(assetId);
                sectionMap.put(sectionKey, assetId);
                placed++;
            }
        } catch (Exception e) {
            log.warn("解析插图决策失败: {}", e.getMessage());
        }
        if (html.contains("<!--brand-in-image-->")) {
            html = dampenBrandNearMarkers(html);
            html = html.replace("<!--brand-in-image-->", "");
        }
        html = stripDataImages(html);
        return new IllustrateResult(new Parsed(draft.title(), html), used, sectionMap);
    }

    private static String sanitizeCaption(String modelCaption, GeoMediaAsset asset) {
        String raw = StringUtils.hasText(modelCaption) ? modelCaption.trim() : "";
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        if (raw.length() > 18 || raw.contains("选购") || raw.contains("认准") || raw.contains("官方")) {
            return "";
        }
        if (raw.equals(nz(asset.getTitle())) || raw.equals(nz(asset.getCaption()))) {
            return raw.length() <= 12 ? raw : "";
        }
        return raw;
    }

    private static String buildFigureHtml(String imgSrc, String alt, String caption) {
        StringBuilder sb = new StringBuilder();
        sb.append("<figure><p><img src=\"").append(escapeAttr(imgSrc))
                .append("\" alt=\"").append(escapeAttr(alt)).append("\" /></p>");
        if (StringUtils.hasText(caption)) {
            sb.append("<figcaption>").append(escapeHtml(caption)).append("</figcaption>");
        }
        sb.append("</figure>");
        return sb.toString();
    }

    /** 话题/问题/自定义要求是否明确要求点到品牌；清单类生活方式问题默认不强制。 */
    private static boolean wantsBrandMention(GeoContentPlacement placement, GeoContentAiGenerateDTO dto) {
        String blob = (nz(placement.getTopicName()) + " "
                + nz(placement.getTargetQuestion()) + " "
                + (dto != null ? nz(dto.getUserPrompt()) : "")).toLowerCase(Locale.ROOT);
        if (blob.contains("小巴依") || blob.contains("品牌") || blob.contains("软文") || blob.contains("种草品牌")) {
            return true;
        }
        // 明确的对比/选购「哪家」「哪个牌子」
        return blob.contains("哪家") || blob.contains("哪个牌子") || blob.contains("什么牌子")
                || blob.contains("品牌推荐") || blob.contains("怎么选品牌");
    }

    private static int countBrandMentions(String html) {
        if (!StringUtils.hasText(html)) {
            return 0;
        }
        int n = 0;
        int from = 0;
        while (true) {
            int i = html.indexOf(BRAND_NAME, from);
            if (i < 0) {
                break;
            }
            n++;
            from = i + BRAND_NAME.length();
        }
        return n;
    }

    private String buildMediaCatalog(List<GeoMediaAsset> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "[]";
        }
        ArrayNode arr = objectMapper.createArrayNode();
        for (GeoMediaAsset a : candidates) {
            ObjectNode n = arr.addObject();
            n.put("assetId", a.getId());
            n.put("title", a.getTitle());
            n.put("caption", a.getCaption());
            n.put("usageHint", a.getUsageHint() == null ? "" : a.getUsageHint());
            n.put("brandVisible", a.getBrandVisible() != null && a.getBrandVisible() == 1);
            n.put("tags", a.getTagsJson());
            n.put("sceneTags", a.getSceneTagsJson());
        }
        return arr.toString();
    }

    /** 兼容 Markdown / JSON / 纯文本。 */
    private Parsed parseArticleFlexible(String content) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("AI 未返回可用正文，请重试");
        }
        // 去掉偶发的 ``` 包裹
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json|markdown|md)?\\s*", "");
            text = text.replaceFirst("\\s*```\\s*$", "").trim();
        }

        // JSON
        if (text.startsWith("{") || text.contains("\"html\"") || text.contains("\"title\"")) {
            try {
                JsonNode node = readJson(text);
                String title = node.path("title").asText("").trim();
                String html = "";
                if (node.has("html")) {
                    html = node.path("html").asText("").trim();
                } else if (node.has("body")) {
                    html = node.path("body").asText("").trim();
                } else if (node.has("content")) {
                    html = node.path("content").asText("").trim();
                } else if (node.has("markdown")) {
                    return markdownToParsed(node.path("markdown").asText(""));
                }
                if (StringUtils.hasText(html)) {
                    if (!html.contains("<")) {
                        return markdownToParsed((StringUtils.hasText(title) ? "# " + title + "\n\n" : "") + html);
                    }
                    if (!StringUtils.hasText(title)) {
                        title = "未命名文章";
                    }
                    return new Parsed(title, stripDataImages(html));
                }
            } catch (Exception ignored) {
                // fall through to markdown
            }
        }

        return markdownToParsed(text);
    }

    private Parsed markdownToParsed(String md) {
        String text = md == null ? "" : md.trim();
        String title = "";
        Matcher h1 = MD_H1.matcher(text);
        if (h1.find()) {
            title = h1.group(1).trim();
            text = text.substring(0, h1.start()) + text.substring(h1.end());
        }
        text = text.trim();
        // ## → 临时标记再转 h2
        String withHeads = MD_H2.matcher(text).replaceAll("\n\n@@H2@@$1@@/H2@@\n\n");
        StringBuilder html = new StringBuilder();
        for (String block : withHeads.replace("\r\n", "\n").split("\n{2,}")) {
            String b = block.trim();
            if (!StringUtils.hasText(b)) {
                continue;
            }
            if (b.startsWith("@@H2@@") && b.endsWith("@@/H2@@")) {
                String h = b.substring(6, b.length() - 7).trim();
                html.append("<h2>").append(escapeHtml(h)).append("</h2>");
                continue;
            }
            String para = escapeHtml(b).replace("\n", "<br/>");
            para = para.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
            html.append("<p>").append(para).append("</p>");
        }
        if (!StringUtils.hasText(title)) {
            // 模型偶发只写 ## 不写 #：用第一个小节标题顶上，避免落成「未命名文章」
            Matcher h2 = Pattern.compile("(?is)<h2>(.*?)</h2>").matcher(html);
            if (h2.find()) {
                title = h2.group(1).replaceAll("\\s+", " ").trim();
            }
        }
        if (!StringUtils.hasText(title) || "未命名文章".equals(title)) {
            title = "未命名文章";
        }
        if (html.isEmpty()) {
            throw new BusinessException("AI 未返回可用正文，请重试");
        }
        return new Parsed(title, stripDataImages(html.toString()));
    }

    private JsonNode readJson(String content) throws Exception {
        String text = content == null ? "" : content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return objectMapper.readTree(text);
    }

    private static String normalizePublishableHtml(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String html = raw.trim();
        html = html.replaceAll("(?m)^#{1,6}\\s*", "");
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("(?i)</?h1\\b[^>]*>", "");
        html = html.replaceAll("(?i)<h([4-6])\\b[^>]*>", "<h3>");
        html = html.replaceAll("(?i)</h([4-6])>", "</h3>");
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        html = html.replaceAll("(?i)</?a\\b[^>]*>", "");
        html = html.replaceAll("(?i)\\s(style|class|onclick|onerror)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "");
        html = stripDataImages(html);
        return html.trim();
    }

    private String toArticleImgSrc(String fileUrl) {
        String publicUrl = fileStorageService.toPublicUrl(fileUrl);
        if (!StringUtils.hasText(publicUrl)) {
            return fileUrl == null ? "" : fileUrl;
        }
        try {
            java.net.URI uri = java.net.URI.create(publicUrl.trim());
            String path = uri.getPath();
            if (StringUtils.hasText(path) && path.contains("/uploads/")) {
                return path;
            }
        } catch (Exception ignored) {
            // fall through
        }
        if (publicUrl.startsWith("/")) {
            return publicUrl;
        }
        return publicUrl;
    }

    private static String stripDataImages(String html) {
        if (!StringUtils.hasText(html) || !html.toLowerCase(Locale.ROOT).contains("data:image")) {
            return html;
        }
        String out = html.replaceAll("(?is)<img[^>]*src\\s*=\\s*[\"']data:image[^\"']*[\"'][^>]*/?>", "");
        out = out.replaceAll("(?is)<figure>\\s*(?:<figcaption>[\\s\\S]*?</figcaption>)?\\s*</figure>", "");
        return out;
    }

    private static String insertAfterNthH2(String html, String img, int index) {
        Matcher m = H2_PATTERN.matcher(html);
        StringBuilder out = new StringBuilder();
        int last = 0;
        int i = 0;
        boolean inserted = false;
        while (m.find()) {
            out.append(html, last, m.end());
            if (i == index) {
                out.append(img);
                inserted = true;
            }
            last = m.end();
            i++;
        }
        out.append(html.substring(last));
        return inserted ? out.toString() : html + img;
    }

    private static String insertAfterNthH2FirstParagraph(String html, String img, int index) {
        Matcher m = H2_PATTERN.matcher(html);
        int h2End = -1;
        int i = 0;
        while (m.find()) {
            if (i == index) {
                h2End = m.end();
                break;
            }
            i++;
        }
        if (h2End < 0) {
            return html + img;
        }
        String tail = html.substring(h2End);
        Matcher pClose = Pattern.compile("(?is)</p>").matcher(tail);
        if (pClose.find()) {
            int at = h2End + pClose.end();
            return html.substring(0, at) + img + html.substring(at);
        }
        return html.substring(0, h2End) + img + html.substring(h2End);
    }

    private static String dampenBrandNearMarkers(String html) {
        String[] parts = html.split("<!--brand-in-image-->", -1);
        if (parts.length <= 1) {
            return html;
        }
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String chunk = parts[i];
            int limit = Math.min(chunk.length(), 400);
            String head = chunk.substring(0, limit);
            String tail = chunk.substring(limit);
            head = head.replaceFirst("小巴依", "该品牌");
            head = head.replaceFirst("小巴依", "");
            out.append(head).append(tail);
        }
        return out.toString();
    }

    private void persistTrace(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                              GeoArticlePipelineResultVO result, List<GeoPipelineStageVO> stages) {
        try {
            GeoArticleGenTrace trace = new GeoArticleGenTrace();
            trace.setPlacementId(placement.getId());
            trace.setItemId(dto.getItemId());
            trace.setStyleCode(result.getStyleCode());
            trace.setSkeletonCode(result.getSkeletonCode());
            trace.setQaRounds(result.getQaRounds());
            trace.setUsedImageCnt(result.getUsedImageCount());
            trace.setStagesJson(objectMapper.writeValueAsString(stages));
            trace.setCreateTime(LocalDateTime.now());
            genTraceMapper.insert(trace);
        } catch (Exception e) {
            log.warn("写入成文溯源失败: {}", e.getMessage());
        }
    }

    private void persistUsages(Long placementId, Long itemId, Map<String, Long> sectionAssetMap) {
        if (sectionAssetMap == null || sectionAssetMap.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Long> e : sectionAssetMap.entrySet()) {
            GeoMediaUsage u = new GeoMediaUsage();
            u.setAssetId(e.getValue());
            u.setPlacementId(placementId);
            u.setItemId(itemId);
            u.setSectionKey(e.getKey());
            u.setCreateTime(now);
            try {
                mediaUsageMapper.insert(u);
            } catch (Exception ex) {
                log.warn("写入素材使用溯源失败: {}", ex.getMessage());
            }
        }
    }

    private void mark(List<GeoPipelineStageVO> stages, ProgressListener progress,
                      String code, String name, String status, String message) {
        if (stages != null) {
            boolean updated = false;
            for (GeoPipelineStageVO s : stages) {
                if (code.equals(s.getCode())) {
                    s.setStatus(status);
                    s.setMessage(message);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                stages.add(new GeoPipelineStageVO(code, name, status, message));
            }
        }
        if (progress != null) {
            try {
                progress.onStage(code, name, status, message);
            } catch (Exception e) {
                log.warn("进度回调失败: {}", e.getMessage());
            }
        }
    }

    private String resolveAiSourceLabel(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "AI";
        }
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "tongyi" -> "通义千问";
            case "deepseek" -> "DeepSeek";
            case "openai" -> "OpenAI";
            case "zhipu" -> "智谱";
            default -> provider;
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }

    private static String escapeAttr(String s) {
        return nz(s).replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static String escapeHtml(String s) {
        return nz(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record Parsed(String title, String html) {}

    private record IllustrateResult(Parsed article, List<Long> usedAssetIds, Map<String, Long> sectionAssetMap) {}
}
