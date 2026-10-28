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
 * GEO 成文（外网模式）：推理写美文，但品牌相关表述落在封闭世界硬事实上。
 * <pre>
 * 卖点/审稿库/自定义提示 → 一次成稿 → 擦除高置信违禁句 → 智能插图 →（缺品牌时轻补）
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoArticlePipelineService {

    /** 卖点作「可核对事实槽」上限 */
    private static final int EVIDENCE_RECALL_LIMIT = 10;

    /** 成文要有文笔与差异化；事实约束靠审稿口径与封闭世界，温度略抬以减少同质化 */
    private static final double WRITE_TEMPERATURE = 0.72;
    private static final double WRITE_TOP_P = 0.93;
    private static final double BRAND_TEMPERATURE = 0.35;
    private static final double ILLUSTRATE_TEMPERATURE = 0.2;
    private static final Pattern H2_PATTERN = Pattern.compile("(?is)(<h2\\b[^>]*>.*?</h2>)");
    private static final Pattern MD_H1 = Pattern.compile("(?m)^\\s*#\\s+(.+)$");
    private static final Pattern MD_H2 = Pattern.compile("(?m)^\\s*##\\s+(.+)$");
    private static final Pattern MD_HEADING_LEAK = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern MD_INLINE_HEADING = Pattern.compile("(?<!\\w)#{2,6}\\s+");
    private static final String BRAND_NAME = "小巴依";

    /**
     * 推理写美文 + 封闭世界事实纪律 + 反公式化/反同质化。
     * 审稿口径运行时注入（见 buildReviewGuardrails）。
     */
    private static final String WRITER_BRIEF = """
            你是一位资深中文撰稿人（美食 / 生活方式 / 农产品方向），每篇都有自己的切入角度，不写「万能模板文」。
            先深度推理再落笔；涉及小巴依品牌、品种、系列、工艺、背书、场景时，必须落在编辑给的封闭世界资料里。

            【开篇——必须有重点，禁止公式导语】
            1. 首段 120～220 字：用具体结论/关键取舍把读者问题答到点上，并带出 1～2 个可核对要点（来自资料的参数、系列差异或场景边界）。
            2. 禁止开篇套话：勿用「本文将从…方面进行分析」「随着生活水平…」「在众多…中，如何选择…至关重要」「维护良好的…至关重要」等空导语；勿只写一句正确的废话就进目录。
            3. 首段要让人感到「这篇有判断」：说清推荐谁/送什么/选哪一类，以及为什么（用事实，不用空洞形容词）。

            【反 AI 味、反同质化——中段可变；先结论，再相关问题】
            4. 禁止中段万能四连：勿固定「1.品质 2.口感 3.供应链 4.权威认证」；小节标题贴本题。
            5. 降低 AI 腔：少用「首先/其次/再者/最后」「综上所述」「总而言之」「希望以上内容对你有所帮助」「值得一提的是」；用具体判断衔接。
            6. 必需要素：一张 Markdown 管道表（系统转 HTML 表）。
            7. 文末顺序固定：先「## 结论」，再「## 相关问题」（2～4 组）。禁止把相关问题放在结论前；禁止 Q/A/QA、「你想确认的几点」。

            【结论——推理美文 + 事实依据】
            8. 「## 结论」先回扣读者问题（推荐谁/送什么/选哪类），再写清「为什么」——依据必须落到笔记里的参数、系列差异或场景边界。
            9. 结论 120～200 字，像专栏收束；切入可轮换（回扣推荐 / 场景收束 / 取舍落地 / 下一步行动），禁套话与同质化句式。

            【相关问题——结论之后，仍要美文感 + 事实答】
            10. 「## 相关问题」紧接结论后：每组「问：…」「答：…」；问句须与本文主题/目标提问相关（延伸读者真会追问的点），勿万能三问。
            11. 答：先用一两句通顺判断（可读、有观点），再落到资料硬事实；无资料则不编。禁止条目复读正文、禁止空原则答。
            12. 各组问法与答法要有变化，避免篇篇同一套「好不好保存/贵不贵/怎么买」。

            【推理写美文】
            13. 定一个核心判断，再拆 3～5 个递进小节；公共常识把问题讲透，品牌论据只从参考笔记取用。
            14. 文风像头条/知乎成稿：有观点、段意分明；不是内部软文，也不是资料粘贴。

            【封闭世界——品牌相关必须守住】
            15. 概念正确：只有「小巴依大尾羊」是羊品种；乳羔羊/小羯羊/金羯羊等是产品系列，品种仍是大尾羊。
            16. 硬事实替换形容词；场景与卖点对齐；背书写全或删掉；无资料不补造。
            17. 禁区：冬运会/航天背书、机场专柜、修行定制、自建全程冷链、对客「礼盒/精品包装」、顺丰骑手/冰袋、地理标志/溯源码/工厂加工过程等。

            【题型对齐】
            18. 榜单/「哪个」给对比结论；「送什么」给清单；「选哪种羊」给对比推荐。禁止把清单/榜单题写成纯方法论。
            19. 品牌「小巴依」自然出现（建议不超过 3 处）；牧场写到资料专名；谈包装须落到产品包装事实。

            【广告法与 E-E-A-T】
            20. 禁用最/第一/唯一/顶级/领先地位/行业领先/消费者首选等；禁疗效与虚构评价；勿伪造「我亲自实测」。
            """;

    /** 高置信杜撰/违禁句式兜底擦除（提示词仍是主防线） */
    private static final Pattern[] FABRICATION_SCRUBS = {
            Pattern.compile("专为.{0,8}(?:修行|禅修).{0,12}设计[^。；]*[。；]?"),
            Pattern.compile("(?:修行|禅修)场景[^。；]*[。；]?"),
            Pattern.compile("乌鲁木齐机场[^。；]*[。；]?"),
            Pattern.compile("(?:在)?机场(?:专柜|选购|购买|入手)[^。；]*[。；]?"),
            Pattern.compile("冬运会(?:指定|赞助)?"),
            Pattern.compile("(?:航天|航空).{0,12}(?:赞助商|指定|合作)"),
            Pattern.compile("中国航天事业的赞助商"),
            Pattern.compile("[「\"“]?冬运会指定[」\"”]?"),
            Pattern.compile("[「\"“]?航天赞助商[」\"”]?"),
            Pattern.compile("冷链不断供"),
            Pattern.compile("自建(?:全程)?(?:全国)?冷链"),
            Pattern.compile("(?:真正扎实的)?检测报告(?:的安心|背书|证明)?"),
            Pattern.compile("真正扎实的检测报告"),
            Pattern.compile("领先地位"),
            Pattern.compile("行业领先"),
            Pattern.compile("遥遥领先"),
            Pattern.compile("消费者首选"),
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
        QuestionIntent intent = classifyIntent(placement.getTargetQuestion(), placement.getTopicName());
        int diversitySeed = diversitySeed(placement);
        LeadStyle leadStyle = LeadStyle.pick(diversitySeed);
        ClosingStyle closingStyle = ClosingStyle.pick(diversitySeed + 9);
        LayoutSpice layoutSpice = LayoutSpice.pick(diversitySeed + 17);
        GeoArticleStylePack style = pickStylePack(placement, diversitySeed);
        GeoArticleSkeleton skeleton = pickSkeleton(intent, diversitySeed);
        result.setStyleCode(style.getCode());
        result.setSkeletonCode(skeleton.getCode());
        mark(stages, progress, "style", "约稿准备", "done",
                style.getName()
                        + (StringUtils.hasText(skeleton.getName()) ? " · " + skeleton.getName() : "")
                        + " · 题型=" + intent.label()
                        + " · 开篇=" + leadStyle.label()
                        + " · 结论=" + closingStyle.label());

        mark(stages, progress, "sell", "整理参考", "running", "整理卖点与审稿口径（封闭世界事实槽）…");
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
        Parsed draft = chatWriteOnce(placement, dto, style, skeleton, intent, leadStyle, closingStyle, layoutSpice,
                evidenceProse, reviewGuardrails, provider, progress);
        draft = scrubFabrications(draft);
        draft = normalizeDraftMarkup(draft);
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
            draft = normalizeDraftMarkup(draft);
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
        draft = normalizeDraftMarkup(draft);
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

    private GeoArticleStylePack pickStylePack(GeoContentPlacement placement, int diversitySeed) {
        List<GeoArticleStylePack> list = stylePackMapper.selectList(
                new LambdaQueryWrapper<GeoArticleStylePack>()
                        .eq(GeoArticleStylePack::getEnabled, 1)
                        .orderByAsc(GeoArticleStylePack::getSortOrder)
                        .last("LIMIT 12"));
        if (list == null || list.isEmpty()) {
            GeoArticleStylePack fallback = new GeoArticleStylePack();
            fallback.setCode("default");
            fallback.setName("默认");
            fallback.setToneGuide("先答读者问题，再展开；段短、信息清楚，有判断但不说教。");
            fallback.setStructureGuide("开篇点题 → 分层论证 → 收束建议；小节标题贴本题，勿套万能四段。");
            return fallback;
        }
        // 在可用风格包里轮换，避免篇篇同一「知乎体/头条体」
        return list.get(Math.floorMod(diversitySeed, list.size()));
    }

    private GeoArticleSkeleton pickSkeleton(QuestionIntent intent, int diversitySeed) {
        List<GeoArticleSkeleton> list = skeletonMapper.selectList(
                new LambdaQueryWrapper<GeoArticleSkeleton>()
                        .eq(GeoArticleSkeleton::getEnabled, 1)
                        .orderByAsc(GeoArticleSkeleton::getSortOrder)
                        .last("LIMIT 16"));
        if (list == null || list.isEmpty()) {
            return fallbackSkeleton(intent);
        }
        List<GeoArticleSkeleton> matched = new ArrayList<>();
        String prefer = intent.preferSkeletonCode();
        String hint = intent.skeletonHint();
        for (GeoArticleSkeleton s : list) {
            if (s.getCode() == null) {
                continue;
            }
            String code = s.getCode().toLowerCase(Locale.ROOT);
            if (prefer.equalsIgnoreCase(s.getCode()) || code.contains(hint) || code.startsWith("geo_")) {
                matched.add(s);
            }
        }
        List<GeoArticleSkeleton> pool = matched.isEmpty() ? list : matched;
        return pool.get(Math.floorMod(diversitySeed, pool.size()));
    }

    private static int diversitySeed(GeoContentPlacement placement) {
        long id = placement.getId() == null ? 0L : placement.getId();
        String spine = nz(placement.getTargetQuestion()) + "|" + nz(placement.getTopicName());
        return Long.hashCode(id * 31L + spine.hashCode() + (System.currentTimeMillis() / 3_600_000L));
    }

    private static GeoArticleSkeleton fallbackSkeleton(QuestionIntent intent) {
        GeoArticleSkeleton fallback = new GeoArticleSkeleton();
        fallback.setCode(intent.preferSkeletonCode());
        fallback.setName(intent.label() + "骨架");
        fallback.setSectionSchema(intent.sectionSchema());
        return fallback;
    }

    /**
     * 卖点 → 封闭世界「事实槽」：推理谋篇可用常识，品牌相关表述优先用这里的硬事实替换形容词。
     */
    private String formatEvidenceProse(GeoSellPointContextVO ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是封闭世界参考笔记（品牌相关只能用这里的原文事实，勿改数字、勿升级形容词）：\n")
                .append("- 推理负责把问题讲透、安排层次与对比；\n")
                .append("- 写到品种/系列/排酸/月龄/牧场/背书/适配场景时，用下列硬事实，禁止「严格/稍大/更成熟/领先」等空话；\n")
                .append("- 笔记没有的独家结论、渠道、机理不要补；场景以笔记为准（未写清炖就不要写适合清炖）。\n\n");
        if (ctx.getHardFacts() != null && !ctx.getHardFacts().isEmpty()) {
            sb.append("【硬事实槽——优先写入对比表/FAQ】\n");
            int i = 1;
            for (String f : ctx.getHardFacts()) {
                if (StringUtils.hasText(f)) {
                    sb.append(i++).append(". ").append(f.trim()).append('\n');
                }
            }
            sb.append('\n');
        }
        if (ctx.getPoints() != null && !ctx.getPoints().isEmpty()) {
            sb.append("【卖点笔记】\n");
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
            sb.append("版式骨架（须对齐题型，表与 FAQ 用资料事实填充）：")
                    .append(truncate(skeleton.getSectionSchema().trim(), 480)).append('\n');
        }
        return sb.toString().trim();
    }

    /** 成文口径：优先撰写注意点/结构/AI友好；失败时退回内置摘要。 */
    private String buildReviewGuardrails() {
        try {
            String kb = reviewKbService.buildGenerationPromptContext();
            if (StringUtils.hasText(kb)) {
                return kb.trim();
            }
        } catch (Exception e) {
            log.warn("加载成文审稿口径失败，使用内置禁令: {}", e.getMessage());
        }
        return """
                - 品种≠系列：仅小巴依大尾羊是品种；乳羔羊/小羯羊/金羯羊等是产品系列。
                - 硬事实替换形容词：排酸写 0-4℃、24-48 小时；勿写「严格排酸」。
                - 场景与卖点对齐；金羯羊可写重卤焖炖/高端宴请，未写清炖则勿硬挂。
                - 乳羔羊：三个月吃奶、三个月吃草；牧场写双百有机牧场。
                - 禁半截背书、领先地位；勿写自建冷链、礼盒/精品包装、航天赞助等。
                """.trim();
    }

    /**
     * 约稿一次成文：深度思考 → 完整成稿（流式推打字机）。
     */
    private Parsed chatWriteOnce(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                                 GeoArticleStylePack style, GeoArticleSkeleton skeleton,
                                 QuestionIntent intent, LeadStyle leadStyle, ClosingStyle closingStyle,
                                 LayoutSpice layoutSpice, String evidenceProse, String reviewGuardrails,
                                 String provider, ProgressListener progress) {
        String topic = nz(placement.getTopicName());
        String question = nz(placement.getTargetQuestion());
        String spine = StringUtils.hasText(question) ? question : topic;
        String system = WRITER_BRIEF + """

                【交稿格式】
                只交成稿 Markdown：第一行「# 标题」→ 中段贴本题的「## 小节」→「## 结论」→「## 相关问题」。
                对比/推荐表用管道表（系统转 HTML 表格）。
                「## 结论」：推理美文 + 事实依据，回扣本题，去套话、去同质化。
                「## 相关问题」必须在结论之后：2～4 组，格式「问：…」「答：…」；答要通顺有判断且依据笔记硬事实；问句贴本文，勿万能三问、勿 Q/A/QA。
                不要代码块、不要 JSON、不要写作说明。篇幅约 1200～1800 字。
                """;

        StringBuilder user = new StringBuilder();
        user.append("【约稿】请就下列读者问题，完成一篇有判断、不像模板、且品牌事实封闭正确的专题成稿。\n\n");
        user.append("读者问题：").append(spine).append('\n');
        user.append("题型判定：").append(intent.label()).append(" —— ").append(intent.contract()).append('\n');
        user.append("开篇写法（本篇指定）：").append(leadStyle.instruction()).append('\n');
        user.append("结论写法（本篇指定）：").append(closingStyle.instruction()).append('\n');
        user.append("版式变化（本篇指定）：").append(layoutSpice.instruction()).append('\n');
        if (StringUtils.hasText(question) && StringUtils.hasText(topic) && !question.equals(topic)) {
            user.append("栏目/话题背景：").append(topic).append("（仅背景，勿写成品牌说明书）\n");
        }
        if (StringUtils.hasText(dto.getUserPrompt())) {
            user.append("\n编辑额外要求：\n").append(dto.getUserPrompt().trim()).append('\n');
        }

        String styleBrief = formatStyleBrief(style, skeleton);
        if (StringUtils.hasText(styleBrief)) {
            user.append("\n【风格与结构参考——吸收气质，勿复制成固定模板】\n").append(styleBrief).append('\n');
        }
        if (StringUtils.hasText(reviewGuardrails)) {
            user.append("\n【审稿口径——概念/场景/广告法冲突时以这里为准】\n")
                    .append(reviewGuardrails).append('\n');
        }
        user.append("\n【封闭世界参考笔记】\n").append(evidenceProse).append('\n');
        user.append("\n请先在思考中完成：重述问题 → 首段重点（含硬事实）→ 中段小节标题（禁万能四连）→ 表")
                .append(" → 起草「结论」：本题答案 + 1～2 条硬事实 + 去套话")
                .append(" → 起草「相关问题」2～4 组（贴本文的追问；答=判断美文句 + 事实依据，勿同质化万能问）")
                .append(" → 自检品种/系列、场景对齐、半截背书、广告法。想清楚后再写正文。");

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
                品牌相关只能用参考笔记与审稿口径里的硬事实；勿加空形容词、半截背书、领先地位、航天等违禁表述。
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
        // 半截口径先补全再擦违禁
        next = next.replace("三个月吃奶吃草", "三个月吃奶、三个月吃草");
        for (Pattern p : FABRICATION_SCRUBS) {
            next = p.matcher(next).replaceAll("");
        }
        next = next.replaceAll("(?is)<p>\\s*</p>", "");
        return next.equals(html) ? draft : new Parsed(draft.title(), next);
    }

    /** 清理模型把 Markdown 标题符号泄漏进 HTML 正文的情况 */
    private static Parsed normalizeDraftMarkup(Parsed draft) {
        if (draft == null) {
            return null;
        }
        String html = draft.html();
        String title = draft.title();
        if (!StringUtils.hasText(html) && !StringUtils.hasText(title)) {
            return draft;
        }
        String nextHtml = html == null ? "" : html;
        nextHtml = MD_INLINE_HEADING.matcher(nextHtml).replaceAll("");
        nextHtml = nextHtml.replace("###", "").replace("##", "");
        String nextTitle = title == null ? "" : MD_HEADING_LEAK.matcher(title.trim()).replaceFirst("");
        if (nextHtml.equals(html) && nextTitle.equals(title)) {
            return draft;
        }
        return new Parsed(nextTitle, nextHtml);
    }

    private static QuestionIntent classifyIntent(String question, String topic) {
        String q = ((question == null ? "" : question) + " " + (topic == null ? "" : topic)).toLowerCase(Locale.ROOT);
        if (containsAny(q, "送什么", "送礼", "礼品", "客情", "伴手礼", "送点什么")) {
            return QuestionIntent.GIFT_LIST;
        }
        if (containsAny(q, "哪个品牌", "哪家", "谁更", "更受欢迎", "受欢迎", "排行", "排名", "对比哪")) {
            return QuestionIntent.RANKING;
        }
        if (containsAny(q, "哪种羊", "选哪种", "选对羊", "炖", "煲汤", "清炖", "怎么吃", "部位")) {
            return QuestionIntent.COMPARE_PICK;
        }
        if (containsAny(q, "如何选", "怎么选", "如何选择", "挑选")) {
            return QuestionIntent.HOWTO_CHOOSE;
        }
        return QuestionIntent.GENERAL;
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (!StringUtils.hasText(haystack) || needles == null) {
            return false;
        }
        for (String n : needles) {
            if (StringUtils.hasText(n) && haystack.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 开篇切入：打散公式化首段 */
    private enum LeadStyle {
        SCENE_HOOK,
        TRADEOFF,
        MYTH_BUST,
        DIRECT_PICK;

        static LeadStyle pick(int seed) {
            LeadStyle[] all = values();
            return all[Math.floorMod(seed, all.length)];
        }

        String label() {
            return switch (this) {
                case SCENE_HOOK -> "场景切入";
                case TRADEOFF -> "取舍对比";
                case MYTH_BUST -> "纠错切入";
                case DIRECT_PICK -> "结论先行";
            };
        }

        String instruction() {
            return switch (this) {
                case SCENE_HOOK -> "首段用具体吃法/送礼/选购场景开场，立刻落到本题答案与 1～2 个硬事实，勿写「本文将分析」。";
                case TRADEOFF -> "首段先亮出常见两难取舍（如嫩度vs耐炖、体面vs实用），再给出你的判断与资料依据。";
                case MYTH_BUST -> "首段先点破一个常见误解（须符合资料：如把系列当成品种），再给出正确选法。";
                case DIRECT_PICK -> "首段开门见山给出推荐结论（谁/哪类/送什么），并用资料参数说明理由；禁止空导语。";
            };
        }
    }

    /** 结论切入：保留「## 结论」标题，内容去 AI 味、去同质化 */
    private enum ClosingStyle {
        RESTATE_PICK,
        SCENE_CLOSE,
        TRADEOFF_CLOSE,
        ACTION_CLOSE;

        static ClosingStyle pick(int seed) {
            ClosingStyle[] all = values();
            return all[Math.floorMod(seed, all.length)];
        }

        String label() {
            return switch (this) {
                case RESTATE_PICK -> "回扣推荐";
                case SCENE_CLOSE -> "场景收束";
                case TRADEOFF_CLOSE -> "取舍落地";
                case ACTION_CLOSE -> "下一步行动";
            };
        }

        String instruction() {
            return switch (this) {
                case RESTATE_PICK -> "「## 结论」直接回扣本题推荐（谁/哪类/送什么），用 1～2 个资料硬事实说明依据；勿写「综上所述」。";
                case SCENE_CLOSE -> "「## 结论」用吃法/送礼/选购场景收束到答案，场景里嵌硬事实；像专栏收尾，不像总结报告。";
                case TRADEOFF_CLOSE -> "「## 结论」点明关键取舍如何落地（及为何），依据必须来自笔记参数/系列差异；禁空形容词。";
                case ACTION_CLOSE -> "「## 结论」给一句可执行下一步，并紧跟事实依据；禁「希望以上内容有帮助」「祝你…成功」。";
            };
        }
    }

    /** 版式调料：中段可变，结尾固定落到「结论」 */
    private enum LayoutSpice {
        TABLE_MID,
        TABLE_LATE,
        LIST_HEAVY,
        FAQ_BEFORE_CLOSE;

        static LayoutSpice pick(int seed) {
            LayoutSpice[] all = values();
            return all[Math.floorMod(seed, all.length)];
        }

        String instruction() {
            return switch (this) {
                case TABLE_MID -> "对比/推荐表放中段；文末顺序固定：「## 结论」→「## 相关问题」（问/答中文，答含硬事实）。";
                case TABLE_LATE -> "先叙事再放表；然后「## 结论」（美文+事实），最后「## 相关问题」。";
                case LIST_HEAVY -> "中段以错落列表+短段为主，表可短；「## 结论」后接「## 相关问题」，禁套话与万能三问。";
                case FAQ_BEFORE_CLOSE -> "表可在前半；仍须先写「## 结论」，再写「## 相关问题」（本枚举名历史保留，顺序以结论在前为准）。";
            };
        }
    }

    private enum QuestionIntent {
        RANKING,
        GIFT_LIST,
        COMPARE_PICK,
        HOWTO_CHOOSE,
        GENERAL;

        String label() {
            return switch (this) {
                case RANKING -> "榜单/谁更受欢迎";
                case GIFT_LIST -> "礼品清单/送什么";
                case COMPARE_PICK -> "对比选品/选哪种";
                case HOWTO_CHOOSE -> "选购方法（仍须落到可核对标准与表）";
                case GENERAL -> "综合答问";
            };
        }

        String contract() {
            return switch (this) {
                case RANKING -> "开篇给出清晰对比结论；用表格列评价维度与可核对事实；勿写成纯「如何选择」方法论。";
                case GIFT_LIST -> "直接回答送什么：给 3～5 个具体礼品/产品选项及理由；包装/牧场等须落到资料事实。";
                case COMPARE_PICK -> "对比系列/部位并给推荐；品种与系列概念正确；场景与卖点对齐；表内用硬事实。";
                case HOWTO_CHOOSE -> "标准须可核对；配合对比表；避免空洞原则段。";
                case GENERAL -> "开篇直答；含表与 FAQ；品牌事实封闭。";
            };
        }

        String preferSkeletonCode() {
            return switch (this) {
                case RANKING -> "geo_ranking_v1";
                case GIFT_LIST -> "geo_gift_list_v1";
                case COMPARE_PICK -> "geo_compare_pick_v1";
                case HOWTO_CHOOSE, GENERAL -> "geo_qa_table_faq_v1";
            };
        }

        String skeletonHint() {
            return switch (this) {
                case RANKING -> "rank";
                case GIFT_LIST -> "gift";
                case COMPARE_PICK -> "compare";
                case HOWTO_CHOOSE, GENERAL -> "qa_table";
            };
        }

        String sectionSchema() {
            return switch (this) {
                case RANKING -> "{\"lead\":\"直接给对比结论\",\"must\":[\"comparison_table\",\"faq_2_to_4\"],\"note\":\"维度用硬事实\"}";
                case GIFT_LIST -> "{\"lead\":\"直接回答送什么\",\"must\":[\"gift_options_3_to_5\",\"faq_2_to_4\"],\"note\":\"包装/牧场用资料\"}";
                case COMPARE_PICK -> "{\"lead\":\"推荐结论\",\"must\":[\"series_or_cut_table\",\"faq_2_to_4\"],\"note\":\"品种≠系列；场景对齐\"}";
                case HOWTO_CHOOSE, GENERAL -> "{\"lead\":\"先答问题\",\"must\":[\"table_or_list\",\"faq_2_to_4\"]}";
            };
        }
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
        text = normalizeFaqMarkdown(text.trim());
        // 先把管道表提成 HTML，避免被当成段落转义
        text = convertMarkdownTables(text);
        // ## → 临时标记再转 h2
        String withHeads = MD_H2.matcher(text).replaceAll("\n\n@@H2@@$1@@/H2@@\n\n");
        StringBuilder html = new StringBuilder();
        for (String block : withHeads.replace("\r\n", "\n").split("\n{2,}")) {
            String b = block.trim();
            if (!StringUtils.hasText(b)) {
                continue;
            }
            if (b.startsWith("@@H2@@") && b.endsWith("@@/H2@@")) {
                String h = sanitizeFaqHeading(b.substring(6, b.length() - 7).trim());
                html.append("<h2>").append(escapeHtml(h)).append("</h2>");
                continue;
            }
            if (b.startsWith("<table")) {
                html.append(b);
                continue;
            }
            if (b.startsWith("<")) {
                // 已是片段 HTML（如问答块）
                html.append(b);
                continue;
            }
            String para = escapeHtml(b).replace("\n", "<br/>");
            para = para.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
            // 转义后的「问：」「答：」加粗可读性
            para = para.replace("问：", "<strong>问：</strong>").replace("答：", "<strong>答：</strong>");
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

    /** Markdown 管道表 → HTML &lt;table&gt; */
    private static String convertMarkdownTables(String md) {
        if (!StringUtils.hasText(md) || !md.contains("|")) {
            return md;
        }
        String[] lines = md.replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        List<String> tableLines = new ArrayList<>();
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i] : null;
            if (line != null && looksLikeMdTableRow(line)) {
                tableLines.add(line);
                continue;
            }
            if (tableLines.size() >= 2) {
                out.append('\n').append(mdTableToHtml(tableLines)).append("\n\n");
            } else {
                for (String t : tableLines) {
                    out.append(t).append('\n');
                }
            }
            tableLines.clear();
            if (line != null) {
                out.append(line).append('\n');
            }
        }
        return out.toString().trim();
    }

    private static boolean looksLikeMdTableRow(String line) {
        String t = line == null ? "" : line.trim();
        if (!t.contains("|")) {
            return false;
        }
        long bars = t.chars().filter(c -> c == '|').count();
        return bars >= 2;
    }

    private static boolean looksLikeMdTableSep(String line) {
        String t = line == null ? "" : line.trim();
        return t.matches("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    }

    private static String mdTableToHtml(List<String> rows) {
        List<String> body = new ArrayList<>(rows);
        // 去掉对齐分隔行
        if (body.size() >= 2 && looksLikeMdTableSep(body.get(1))) {
            body.remove(1);
        }
        if (body.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        for (int r = 0; r < body.size(); r++) {
            List<String> cells = splitMdRow(body.get(r));
            sb.append("<tr>");
            for (String cell : cells) {
                String tag = r == 0 ? "th" : "td";
                String inner = escapeHtml(cell.trim()).replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
                sb.append('<').append(tag).append('>').append(inner).append("</").append(tag).append('>');
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private static List<String> splitMdRow(String row) {
        String t = row.trim();
        if (t.startsWith("|")) {
            t = t.substring(1);
        }
        if (t.endsWith("|")) {
            t = t.substring(0, t.length() - 1);
        }
        String[] parts = t.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String p : parts) {
            cells.add(p == null ? "" : p.trim());
        }
        return cells;
    }

    /** 把英文字母 Q/A/QA 问法收成中文「问：」「答：」 */
    private static String normalizeFaqMarkdown(String md) {
        if (!StringUtils.hasText(md)) {
            return md;
        }
        String s = md;
        s = s.replaceAll("(?m)^#{1,6}\\s*你想确认的几点\\s*$", "## 常见问题");
        s = s.replaceAll("(?m)^#{1,6}\\s*还想确认的几点\\s*$", "## 买前先看");
        s = s.replaceAll("(?m)^#{1,6}\\s*QA\\s*[?？]?\\s*$", "## 常见问题");
        s = s.replaceAll("(?m)^#{1,6}\\s*Q\\s*A\\s*[?？]?\\s*$", "## 常见问题");
        s = s.replaceAll("(?m)^\\s*\\*?\\*?\\s*Q\\s*[:：]\\s*", "问：");
        s = s.replaceAll("(?m)^\\s*\\*?\\*?\\s*A\\s*[:：]\\s*", "答：");
        s = s.replaceAll("(?m)^\\s*QA\\s*[:：]?\\s*", "问：");
        return s;
    }

    private static String sanitizeFaqHeading(String heading) {
        if (!StringUtils.hasText(heading)) {
            return heading;
        }
        String h = heading.trim();
        if (h.matches("(?i)QA\\s*[?？]?") || h.matches("(?i)Q\\s*A\\s*[?？]?")
                || h.contains("你想确认的几点") || h.contains("还想确认的几点")) {
            return "常见问题";
        }
        return h;
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
        // 若模型把管道表直接塞进 HTML 段落，再尝试抢救成 table
        if (html.contains("|") && !html.toLowerCase(Locale.ROOT).contains("<table")) {
            html = convertMarkdownTables(html.replace("<br/>", "\n").replace("<br>", "\n")
                    .replaceAll("(?is)</?p>", "\n"));
        }
        html = html.replaceAll("(?is)<h2>\\s*(?:你想确认的几点|还想确认的几点|QA\\s*[?？]?|Q\\s*A\\s*[?？]?)\\s*</h2>",
                "<h2>常见问题</h2>");
        html = html.replaceAll("(?i)>\\s*Q\\s*[:：]\\s*", "><strong>问：</strong>");
        html = html.replaceAll("(?i)>\\s*A\\s*[:：]\\s*", "><strong>答：</strong>");
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
