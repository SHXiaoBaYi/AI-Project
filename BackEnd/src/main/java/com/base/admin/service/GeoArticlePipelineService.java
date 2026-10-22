package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.GeoContentAiGenerateDTO;
import com.base.admin.domain.entity.GeoArticleGenTrace;
import com.base.admin.domain.entity.GeoArticleSkeleton;
import com.base.admin.domain.entity.GeoArticleStylePack;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoMediaAsset;
import com.base.admin.domain.entity.GeoMediaUsage;
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.domain.vo.GeoArticlePipelineResultVO;
import com.base.admin.domain.vo.GeoPipelineStageVO;
import com.base.admin.domain.vo.GeoSellPointContextVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoArticleGenTraceMapper;
import com.base.admin.mapper.GeoArticleSkeletonMapper;
import com.base.admin.mapper.GeoArticleStylePackMapper;
import com.base.admin.mapper.GeoMediaUsageMapper;
import com.base.admin.mapper.GeoPlatformMapper;
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
 * 后台两阶段成文流水线：大纲 → 扩写+插图 → 质检循环重写；前端仍一键调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoArticlePipelineService {

    private static final int MAX_QA_ROUNDS = 6;
    /** 喂给模型的资料条数：少而精，避免把全文写成卖点清单 */
    private static final int EVIDENCE_RECALL_LIMIT = 6;
    private static final Pattern H2_PATTERN = Pattern.compile("(?is)(<h2\\b[^>]*>.*?</h2>)");
    /** 成文默认品牌；自然引出，禁止零出现与清单式堆砌 */
    private static final String BRAND_NAME = "小巴依";
    private static final String BRAND_QUALITY_RULES = """
            【品牌露出——主题优先，品牌作佐证】
            1. 全文以目标问题/话题为主角；「小巴依」是合理引出的新疆羊肉品牌佐证，不是每段的推销主语；
            2. 品牌名建议出现 1～3 次即可（最多不超过 4 次）；禁止 0 次，也禁止段段点名、口号刷屏；
            3. 开篇用主题场景自然带出品牌（如「秋冬进补吃羊肉」→ 引出小巴依），禁止开篇硬广与卖点口号连发；
            4. 资料事实要揉进叙事与判断，禁止「卖点①②③」式罗列或把 claim 原文堆进段落；
            5. 若某节插图已含品牌露出，该节文字可不再重复点名品牌。
            """;
    private static final String ANTI_HOMOGENEITY_RULES = """
            【反同质化——每次必须换框架】
            1. 不要固定成「先说结论→三条论据→落到选择」的万能模板；小节标题、论证顺序、开头钩子每次换一种；
            2. 可选开头（择一，勿混用）：读者误区纠偏 / 对比选择 / 季节痛点直答 / 选购判断口吻；禁止编造「我家上周…」式假故事开场；
            3. 全文不要反复复述同一认证/牧场/冷链事实；同一事实最多点一次；
            4. 禁止连续多段都以「小巴依……」开头，或把卖点库标题改写成 h2。
            """;
    /**
     * 防「假生活常识」：模型为求生动常编出风干肉煮汤等离谱例子。
     * 质量优先于文采；拿不准的做法宁可不写。
     */
    private static final String FACTUAL_RIGOR_RULES = """
            【事实与生活常识——宁可枯燥，不可胡说】
            1. 产品事实（认证、产地、工艺、规格、价格、门店）只能来自资料卡；资料没有的数字/编号/故事一律不写；
            2. 烹饪与食用方式必须符合品类常识：风干肉/风干羊肉宜撕食、泡发后再烹或按资料卡写法，禁止编造「直接拿风干肉乱炖乱煲当鲜肉汤」等假场景；鲜肉、冷鲜、冷冻、风干用途不可张冠李戴；
            3. 禁止虚构无法核验的个人经历当论据（如「上周我用小巴依风干肉煮了锅汤」「邻居都说…」）；需要举例时，用「常见吃法/选购时注意」的客观表述；
            4. 进补、营养、功效表述克制：不夸大疗效，不恐吓营销；不确定的食疗结论不写；
            5. 落笔前自问：这个例子懂行的读者会不会觉得外行？会，就删掉或改成资料卡里有的说法。
            """;
    private static final String COMPLIANCE_RULES = """
            【合规与平台】符合广告法与常见自媒体审核：不绝对化承诺、不虚假疗效、不恐吓营销；E-E-A-T：有经验视角、可用资料支撑、表述克制可核验。
            """;
    /** 各内容平台通用的垃圾信息/引流红线（与平台管理「审核规范摘要」对齐） */
    private static final String PLATFORM_SPAM_BASELINE = """
            【内容平台垃圾信息与合规红线——必须遵守】
            垃圾信息与违规策略破坏平台生态、损害用户体验，甚至可能造成用户财产或人身损失。严禁任何以规避、引流、营销等为目的的诱导用户或谋取平台利益的行为，包括但不限于：
            1. 在标题、导语、正文头尾、配图说明中添加关注引导、加微、下单、跳转第三方等营销信息；
            2. 引导用户至第三方平台关注、注册或交易；
            3. 发布可能对用户财产或人身安全造成损失的医疗功效承诺、投资理财荐股等垃圾营销信息；
            4. 全文不得写成硬广清单或协同刷屏式推销；
            5. 不得通过导流、炒作、标题党夸张承诺进行营销；
            6. 不得用特殊符号、拆字、隐晦图注等方式规避平台审核进行营销。
            """;

    private final AiChatService aiChatService;
    private final SellPointContextService sellPointContextService;
    private final GeoMediaAssetService mediaAssetService;
    private final FileStorageService fileStorageService;
    private final GeoArticleStylePackMapper stylePackMapper;
    private final GeoArticleSkeletonMapper skeletonMapper;
    private final GeoMediaUsageMapper mediaUsageMapper;
    private final GeoArticleGenTraceMapper genTraceMapper;
    private final GeoPlatformMapper platformMapper;
    private final ObjectMapper objectMapper;

    @FunctionalInterface
    public interface ProgressListener {
        void onStage(String code, String name, String status, String message);
    }

    public GeoArticlePipelineResultVO run(GeoContentPlacement placement, GeoContentAiGenerateDTO dto, String provider) {
        return run(placement, dto, provider, null);
    }

    public GeoArticlePipelineResultVO run(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                                          String provider, ProgressListener progress) {
        GeoArticlePipelineResultVO result = new GeoArticlePipelineResultVO();
        List<GeoPipelineStageVO> stages = result.getStages();

        // —— 阶段1：风格包 + 骨架 ——
        mark(stages, progress, "style", "加载风格与骨架", "running", "正在加载风格包与文章骨架…");
        GeoArticleStylePack style = pickStylePack();
        GeoArticleSkeleton skeleton = pickSkeleton();
        result.setStyleCode(style.getCode());
        result.setSkeletonCode(skeleton.getCode());
        mark(stages, progress, "style", "加载风格与骨架", "done",
                style.getName() + " / " + skeleton.getName());

        // —— 阶段2：资料召回（克制条数，防卖点堆砌）——
        mark(stages, progress, "sell", "召回资料", "running", "正在按话题/目标问题召回可引用资料…");
        GeoSellPointContextVO ctx = sellPointContextService.build(
                placement.getTopicName(), placement.getTargetQuestion(), EVIDENCE_RECALL_LIMIT);
        int pointCnt = ctx.getPoints() == null ? 0 : ctx.getPoints().size();
        result.setUsedPointCount(pointCnt);
        String pointsJson = compactEvidencePack(ctx);
        mark(stages, progress, "sell", "召回资料", "done",
                "候选 " + pointCnt + " 条（成文只选用 2～4 条，禁止清单堆砌）");

        String platformAuditPack = buildPlatformAuditPack();

        // —— 阶段3：大纲 ——
        mark(stages, progress, "outline", "生成大纲", "running", "深度思考中：主题叙事框架与品牌自然引出…");
        String outlineJson = chatOutline(placement, dto, style, skeleton, pointsJson, platformAuditPack, provider);
        mark(stages, progress, "outline", "生成大纲", "done", truncate(outlineJson, 120));

        // —— 阶段4：语义召回素材（用途/标签匹配；话题仅软优先）——
        mark(stages, progress, "media", "语义召回素材", "running", "按用途/标签匹配素材库…");
        String mediaContext = nz(placement.getTopicName()) + " " + nz(placement.getTargetQuestion())
                + (StringUtils.hasText(dto.getUserPrompt()) ? " " + dto.getUserPrompt() : "");
        List<GeoMediaAsset> candidates = mediaAssetService.recallForArticle(
                placement.getTopicId(), mediaContext, 24);
        mark(stages, progress, "media", "语义召回素材", "done",
                candidates.isEmpty()
                        ? "暂无生效素材，各节可为 0 图"
                        : ("候选 " + candidates.size() + " 张（用途/标签匹配，话题软优先）"));

        // —— 阶段5：扩写正文（含风格句式、品牌软约束）——
        mark(stages, progress, "expand", "扩写正文", "running", "深度扩写中（耗时较长，请稍候）…");
        Parsed draft = chatExpand(placement, dto, style, skeleton, pointsJson, outlineJson, candidates,
                platformAuditPack, provider);
        mark(stages, progress, "expand", "扩写正文", "done", "标题：" + truncate(draft.title(), 40));

        // —— 阶段6：智能插图（模型判定每节 0+ 张）——
        mark(stages, progress, "illustrate", "智能插图", "running", "判定各节是否挂载素材库图片…");
        IllustrateResult illustrated = illustrate(draft, candidates, provider);
        draft = illustrated.article();
        result.setUsedImageCount(illustrated.usedAssetIds().size());
        result.getUsedAssetIds().addAll(illustrated.usedAssetIds());
        mark(stages, progress, "illustrate", "智能插图", "done",
                illustrated.usedAssetIds().isEmpty()
                        ? "模型判定本节无需插图"
                        : ("插入 " + illustrated.usedAssetIds().size() + " 张"));

        // —— 阶段7：质检失败自动重写直到合格 ——
        mark(stages, progress, "qa", "质检与重写", "running", "准备质检…");
        int qaRounds = 0;
        for (int i = 0; i < MAX_QA_ROUNDS; i++) {
            int round = i + 1;
            mark(stages, progress, "qa", "质检与重写", "running",
                    "第 " + round + "/" + MAX_QA_ROUNDS + " 轮质检中（品牌露出、结构、事实、文笔）…");
            QaResult qa = chatQa(placement, style, draft, platformAuditPack, provider);
            qaRounds = round;
            if (qa.pass()) {
                mark(stages, progress, "qa", "质检与重写", "done",
                        "第 " + qaRounds + " 轮质检通过");
                break;
            }
            String reason = StringUtils.hasText(qa.feedback()) ? truncate(qa.feedback(), 80) : "未达标";
            if (i == MAX_QA_ROUNDS - 1) {
                mark(stages, progress, "qa", "质检与重写", "running",
                        "第 " + round + " 轮未通过：" + reason + "；按末次意见强制重写中…");
                draft = chatRewrite(placement, style, draft, qa.feedback(), pointsJson, platformAuditPack, provider);
                mark(stages, progress, "qa", "质检与重写", "done",
                        "已达上限 " + MAX_QA_ROUNDS + " 轮，已按末次意见重写");
                break;
            }
            mark(stages, progress, "qa", "质检与重写", "running",
                    "第 " + round + " 轮未通过：" + reason + "；正在重写…");
            draft = chatRewrite(placement, style, draft, qa.feedback(), pointsJson, platformAuditPack, provider);
            mark(stages, progress, "qa", "质检与重写", "running",
                    "第 " + round + " 轮重写完成，继续下一轮质检…");
        }
        result.setQaRounds(qaRounds);

        String modelLabel = resolveAiSourceLabel(provider);
        result.setTitle(draft.title());
        result.setHtml(normalizePublishableHtml(draft.html()));
        result.setSourceAiModel(modelLabel);

        persistTrace(placement, dto, result, stages);
        persistUsages(placement.getId(), dto.getItemId(), illustrated.sectionAssetMap());
        return result;
    }

    private GeoArticleStylePack pickStylePack() {
        List<GeoArticleStylePack> list = stylePackMapper.selectList(
                new LambdaQueryWrapper<GeoArticleStylePack>()
                        .eq(GeoArticleStylePack::getEnabled, 1)
                        .orderByAsc(GeoArticleStylePack::getSortOrder)
                        .last("LIMIT 8"));
        if (list == null || list.isEmpty()) {
            GeoArticleStylePack fallback = new GeoArticleStylePack();
            fallback.setCode("default");
            fallback.setName("默认阐释体");
            fallback.setToneGuide("先答结论再拆论据；段短、信息密度高。");
            fallback.setStructureGuide("导语直答 → 2～4 个 h2 → 收束。");
            fallback.setSamplePhrases("[\"先说结论：\",\"落到选择上，可以看这几条\"]");
            fallback.setBrandGuide("主题优先；「小巴依」自然出现 1～3 次，禁止卖点清单与开篇硬广。");
            return fallback;
        }
        // 按分钟轮换，连续生成也不易撞同一风格包
        int idx = (int) (System.currentTimeMillis() / 60_000L % list.size());
        return list.get(idx);
    }

    private GeoArticleSkeleton pickSkeleton() {
        List<GeoArticleSkeleton> list = skeletonMapper.selectList(
                new LambdaQueryWrapper<GeoArticleSkeleton>()
                        .eq(GeoArticleSkeleton::getEnabled, 1)
                        .orderByAsc(GeoArticleSkeleton::getSortOrder)
                        .last("LIMIT 8"));
        if (list == null || list.isEmpty()) {
            GeoArticleSkeleton fallback = new GeoArticleSkeleton();
            fallback.setCode("topic_brand_v1");
            fallback.setName("主题叙事+品牌佐证");
            fallback.setSectionSchema("""
                    {"lead":"主题场景开篇并合理引出品牌","bodyRoles":["误区或原理","客观介绍品牌资料中的做法","场景建议"],"close":"总结回扣主题","note":"框架可自由改动"}
                    """);
            return fallback;
        }
        int idx = (int) ((System.currentTimeMillis() / 60_000L + 3) % list.size());
        return list.get(idx);
    }

    /**
     * 压缩卖点为「资料卡」：明确选用上限，去掉易诱发清单写作的冗长字段。
     */
    private String compactEvidencePack(GeoSellPointContextVO ctx) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("usage", "以下为可引用资料卡。全文只选 2～4 条事实自然融入叙事；禁止按条罗列、禁止把 claim 原文堆成广告清单。");
        ArrayNode hard = root.putArray("hardFacts");
        if (ctx.getHardFacts() != null) {
            int n = 0;
            for (String f : ctx.getHardFacts()) {
                if (!StringUtils.hasText(f) || n >= 5) {
                    break;
                }
                hard.add(f.trim());
                n++;
            }
        }
        ArrayNode candidates = root.putArray("candidates");
        if (ctx.getPoints() != null) {
            int n = 0;
            for (GeoSellPointContextVO.PointItem p : ctx.getPoints()) {
                if (n >= EVIDENCE_RECALL_LIMIT) {
                    break;
                }
                ObjectNode item = candidates.addObject();
                item.put("domain", nz(p.getDomain()));
                item.put("claim", truncate(nz(p.getClaim()), 80));
                item.put("evidence", truncate(nz(p.getEvidence()), 120));
                item.put("level", nz(p.getLevel()));
                n++;
            }
        }
        ArrayNode forbid = root.putArray("forbidden");
        if (ctx.getForbiddenHints() != null) {
            for (String h : ctx.getForbiddenHints()) {
                if (StringUtils.hasText(h)) {
                    forbid.add(h);
                }
            }
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException("组装资料上下文失败");
        }
    }

    /**
     * 汇总内容发布平台的官方审核规范（链接 + 摘要），供成文/质检约束。
     */
    private String buildPlatformAuditPack() {
        StringBuilder sb = new StringBuilder();
        sb.append(PLATFORM_SPAM_BASELINE).append('\n');
        sb.append("【各内容平台官方审核规范（须遵守，勿生成违规引流/硬广）】\n");
        List<GeoPlatform> platforms;
        try {
            platforms = platformMapper.selectList(new LambdaQueryWrapper<GeoPlatform>()
                    .eq(GeoPlatform::getPlatformType, Constants.PLATFORM_TYPE_CONTENT)
                    .eq(GeoPlatform::getIsActive, 1)
                    .orderByAsc(GeoPlatform::getSortOrder)
                    .orderByAsc(GeoPlatform::getId)
                    .last("LIMIT 40"));
        } catch (Exception e) {
            log.warn("读取内容平台审核规范失败: {}", e.getMessage());
            return sb.toString();
        }
        if (platforms == null || platforms.isEmpty()) {
            sb.append("（暂无平台明细，以上述红线为准）\n");
            return sb.toString();
        }
        int n = 0;
        for (GeoPlatform p : platforms) {
            boolean hasUrls = StringUtils.hasText(p.getContentAuditUrls());
            boolean hasNotes = StringUtils.hasText(p.getContentAuditNotes());
            if (!hasUrls && !hasNotes) {
                continue;
            }
            n++;
            sb.append(n).append(". ").append(nz(p.getPlatformName())).append('\n');
            if (hasUrls) {
                sb.append("   规范链接：\n");
                for (String line : p.getContentAuditUrls().split("\\R")) {
                    String u = line == null ? "" : line.trim();
                    if (StringUtils.hasText(u)) {
                        sb.append("   - ").append(u).append('\n');
                    }
                }
            }
            if (hasNotes) {
                sb.append("   摘要：").append(truncate(p.getContentAuditNotes().replaceAll("\\s+", " "), 280))
                        .append('\n');
            }
        }
        if (n == 0) {
            sb.append("（平台未配置审核字段，以上述红线为准）\n");
        }
        return sb.toString();
    }

    private String chatOutline(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                               GeoArticleStylePack style, GeoArticleSkeleton skeleton,
                               String pointsJson, String platformAuditPack, String provider) {
        String openingHint = pickOpeningAngle();
        String system = """
                你是资深自媒体选题策划（非广告文案）。只输出大纲 JSON，不要正文。
                文章主角是「目标问题/话题」，品牌「小巴依」只在合适处作为新疆羊肉佐证被自然引出。
                质量标准：像懂行的编辑写的答疑稿，不是编故事的营销号。

                【结构建议（可改动，勿写成固定模板）】
                ① 开头用主题判断/误区纠偏合理引出（参考本次开头角度），禁止规划虚构生活故事；
                ② 中间据资料客观介绍，穿插可核验的选购/食用常识，不是卖点巡展；
                ③ 结尾总结回扣主题。
                【资料】大纲 evidencePick 最多标 2～4 条将使用的事实意图；禁止规划「逐条覆盖全部资料」。
                【品牌】brandBeats 标 1～2 处自然点名即可，不要规划段段露出。
                【举例】sections 的 intent 里若写吃法/场景，必须符合品类常识，禁止「风干肉煮汤」一类外行梗。
                """ + ANTI_HOMOGENEITY_RULES + FACTUAL_RIGOR_RULES + COMPLIANCE_RULES + """
                输出 JSON：
                {"title":"...","openingAngle":"...","lead":"导语意图","keywordPlant":"如何自然植入主题关键词","brandBeats":["何处自然点名"],"evidencePick":["选用哪类事实"],"sections":[{"key":"s1","heading":"...","intent":"...","needImage":"auto|yes|no"}]}
                """;
        String user = buildBaseUser(placement, dto)
                + "\n本次开头角度（必须采用或等价改写）：" + openingHint
                + "\n风格包：" + style.getName() + "\n语气：" + nz(style.getToneGuide())
                + "\n结构参考（可偏离）：" + nz(style.getStructureGuide())
                + "\n骨架参考（可偏离）：" + nz(skeleton.getSectionSchema())
                + "\n品牌指引：" + nz(style.getBrandGuide())
                + "\n" + BRAND_QUALITY_RULES
                + "\n" + platformAuditPack
                + "\n可引用资料卡：\n" + pointsJson;
        return aiChatService.chat(system, user, provider, 0.35, true);
    }

    private Parsed chatExpand(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                              GeoArticleStylePack style, GeoArticleSkeleton skeleton,
                              String pointsJson, String outlineJson,
                              List<GeoMediaAsset> candidates, String platformAuditPack, String provider) {
        String mediaHint = buildMediaCatalog(candidates);
        String topicKw = StringUtils.hasText(placement.getTargetQuestion())
                ? placement.getTargetQuestion().trim()
                : nz(placement.getTopicName());
        String system = """
                你是严谨的美食/农产品自媒体作者（有品类常识，不是营销编故事）。按大纲扩写正式中文 HTML；字数约 1200～1600 字。
                文风自然、逻辑通畅；主题优先，品牌作佐证。宁可少写一句生动例子，也不要写一句外行假故事。

                【事实】只用资料卡中的事实，不可杜撰认证编号/数据/门店/捐赠金额；没有的信息宁可不写。
                【植入】正文自然出现主题关键词「%s」，忌生硬堆砌标题党。
                【资料用法】全文只选用 2～4 条资料事实揉进叙事；禁止卖点清单、禁止连续多段推销腔。
                【句式】风格包样本句式最多改写融入 1 句；禁止照搬范文骨架导致篇篇雷同。
                """.formatted(topicKw)
                + BRAND_QUALITY_RULES + ANTI_HOMOGENEITY_RULES + FACTUAL_RIGOR_RULES + COMPLIANCE_RULES + """
                【插图】本阶段不要插入 <img>；全文最多预留 2 个 <!--IMG:sectionKey--> 占位（可 0 个），勿每节都留。
                【排版】title 独立；html=导语段+若干<h2>小节+收束。允许标签：h2,h3,p,strong,em,ul,ol,li,blockquote,br。
                禁止 Markdown、脚本、外链、<h1>、内联 style/class。
                只输出 JSON：{"title":"...","html":"..."}
                """;
        String user = buildBaseUser(placement, dto)
                + "\n大纲：\n" + outlineJson
                + "\n风格：" + style.getName() + " / " + nz(style.getToneGuide())
                + "\n结构参考：" + nz(style.getStructureGuide())
                + "\n句式样本：" + nz(style.getSamplePhrases())
                + "\n品牌指引：" + nz(style.getBrandGuide())
                + "\n骨架参考：" + nz(skeleton.getSectionSchema())
                + "\n" + platformAuditPack
                + "\n可选素材目录（插图阶段再用）：\n" + mediaHint
                + "\n可引用资料卡：\n" + pointsJson
                + "\n自检：①像答疑文而非广告；②「小巴依」约 1～3 次；③未清单堆砌；④未触犯平台审核红线；"
                + "⑤无一处违背品类常识的假吃法/假故事（尤其风干肉≠鲜肉乱炖）。";
        // 略降温度：减少「为求生动而编假例子」
        String raw = aiChatService.chat(system, user, provider, 0.4, true);
        return parseArticle(raw);
    }

    private IllustrateResult illustrate(Parsed draft, List<GeoMediaAsset> candidates, String provider) {
        if (candidates == null || candidates.isEmpty()) {
            String html = draft.html().replaceAll("(?is)<!--\\s*IMG:[^>]*-->", "");
            return new IllustrateResult(new Parsed(draft.title(), html), List.of(), Map.of());
        }
        String catalog = buildMediaCatalog(candidates);
        String system = """
                你是版式克制的插图编辑。根据正文与素材目录决定是否插图。
                规则：
                1. 全文最多 2 张图（可为 0）；只返回目录中的 assetId；禁止臆造 URL / data:image；
                2. 图服务于主题场景，不要为了塞广告图而硬插；
                3. caption 若需要：≤18 字场景说明，禁止广告口号、禁止重复正文卖点；不需要则 caption 置空字符串；
                4. afterHeading=false 时插在该节首段之后（更自然）；仅封面感强的图可用 afterHeading=true。
                只输出 JSON：{"placements":[{"sectionKey":"s1","assetId":12,"afterHeading":false,"caption":""}]}
                若全不插图：{"placements":[]}
                """;
        String user = "标题：" + draft.title() + "\n正文：\n" + draft.html() + "\n素材目录：\n" + catalog;
        String raw;
        try {
            raw = aiChatService.chat(system, user, provider, 0.2);
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
                String placeholder = "<!--IMG:" + sectionKey + "-->";
                if (html.contains(placeholder)) {
                    html = html.replace(placeholder, img);
                } else if (afterHeading) {
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
        html = html.replaceAll("(?is)<!--\\s*IMG:[^>]*-->", "");
        if (html.contains("<!--brand-in-image-->")) {
            html = dampenBrandNearMarkers(html);
            html = html.replace("<!--brand-in-image-->", "");
        }
        html = stripDataImages(html);
        return new IllustrateResult(new Parsed(draft.title(), html), used, sectionMap);
    }

    private QaResult chatQa(GeoContentPlacement placement, GeoArticleStylePack style, Parsed draft,
                            String platformAuditPack, String provider) {
        int brandHits = countBrandMentions(draft.html());
        if (brandHits < 1) {
            return new QaResult(false,
                    "正文未出现「小巴依」。请在主题叙事中自然引出一次即可，禁止开篇硬广与卖点清单。");
        }
        if (brandHits > 4) {
            return new QaResult(false,
                    "「小巴依」出现 " + brandHits + " 次偏多，像广告刷屏。删减到 1～3 次，把笔墨还给主题。");
        }
        String system = """
                你是严苛的美食品类主编兼合规质检（懂羊肉/牛肉常识）。只输出 JSON：
                {"pass":true/false,"feedback":"不合格时给出可执行修改意见"}
                不合格情形（任一即 fail）：
                1. 未紧扣目标问题，或杜撰资料卡没有的事实；
                2. 卖点/资料清单式堆砌、段段推销腔、同质化万能模板（先说结论→三条卖点→号召选择）；
                3. 「小巴依」为 0，或生硬硬广，或出现过多像刷屏；
                4. 读起来不像在回答主题，而像品牌说明书；
                5. 触犯内容平台审核红线：引流加微/第三方、头尾硬广、医疗投资恐吓营销、规避审核符号等；
                6. 生活常识硬伤：品类吃法张冠李戴（如风干肉当鲜肉乱炖乱煲）、虚构「我家/邻居」故事当论据、夸大进补疗效；
                7. 误用 Markdown / 危险标签；图注广告口号喧宾夺主。
                合格标准：懂行读者读着不闹心——主题清楚、例子经得起常识、品牌自然、资料克制、符合平台规范。
                """ + BRAND_QUALITY_RULES + ANTI_HOMOGENEITY_RULES + FACTUAL_RIGOR_RULES;
        String user = "目标问题：" + nz(placement.getTargetQuestion())
                + "\n话题：" + nz(placement.getTopicName())
                + "\n品牌指引：" + nz(style.getBrandGuide())
                + "\n已统计「小巴依」出现次数：" + brandHits
                + "\n" + platformAuditPack
                + "\n标题：" + draft.title()
                + "\n正文：\n" + draft.html();
        try {
            String raw = aiChatService.chat(system, user, provider, 0.15, true);
            JsonNode node = readJson(raw);
            boolean pass = node.path("pass").asBoolean(false);
            String feedback = node.path("feedback").asText("").trim();
            return new QaResult(pass, feedback);
        } catch (Exception e) {
            log.warn("质检调用失败，仅按品牌次数兜底: {}", e.getMessage());
            return brandHits >= 1 && brandHits <= 4
                    ? new QaResult(true, "")
                    : new QaResult(false, "质检异常，请检查品牌露出是否自然（建议 1～3 次）。");
        }
    }

    private Parsed chatRewrite(GeoContentPlacement placement, GeoArticleStylePack style,
                               Parsed draft, String feedback, String pointsJson,
                               String platformAuditPack, String provider) {
        String system = """
                按质检意见改写全文。保留已插入的 <figure>/<img> 的 src（素材库路径），禁止改成 data:image 或臆造外链，除非意见要求删图。
                改写目标：主题叙事优先、去掉卖点堆砌与同质化模板；资料只保留必要的 2～4 处；品牌「小巴依」自然出现 1～3 次。
                必须删掉/改掉一切违背品类常识的假吃法、虚构个人故事；吃法只保留资料卡有的或公认常识。
                必须符合内容平台审核规范：禁引流、禁头尾硬广、禁医疗投资恐吓营销、禁规避审核话术。
                若 figcaption 像广告口号，改为≤18 字场景说明或删除 figcaption。
                """ + BRAND_QUALITY_RULES + ANTI_HOMOGENEITY_RULES + FACTUAL_RIGOR_RULES + """
                只输出 JSON：{"title":"...","html":"..."}
                允许标签：h2,h3,p,strong,em,ul,ol,li,blockquote,br,figure,img,figcaption。
                """;
        String user = "目标问题：" + nz(placement.getTargetQuestion())
                + "\n质检意见：" + nz(feedback)
                + "\n品牌指引：" + nz(style.getBrandGuide())
                + "\n" + platformAuditPack
                + "\n原标题：" + draft.title()
                + "\n原文：\n" + draft.html()
                + "\n可引用资料卡（勿全用）：\n" + pointsJson
                + "\n自检：像答疑文；无外行假吃法；「小巴依」1～3 次；未触犯平台审核红线。";
        String raw = aiChatService.chat(system, user, provider, 0.35, true);
        return parseArticle(raw);
    }

    private static String pickOpeningAngle() {
        String[] angles = {
                "季节痛点直答（不写虚构个人故事），再自然引出羊肉与品牌",
                "先纠一个常见选购/吃法误区，再落到可核验的选择与品牌佐证",
                "对比「只看便宜」与「看产地/形态（鲜/冷鲜/风干）」的差异，再点名品牌资料",
                "用「怎么判断好不好」的选购清单口吻开场，再客观介绍品牌资料",
                "先分清品类（鲜肉/风干等用途不同），再回答主题并自然点到品牌"
        };
        int idx = (int) (System.currentTimeMillis() / 30_000L % angles.length);
        return angles[idx];
    }

    private static String sanitizeCaption(String modelCaption, GeoMediaAsset asset) {
        String raw = StringUtils.hasText(modelCaption) ? modelCaption.trim() : "";
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        // 过长或明显广告腔：宁可不配图注，版式更干净
        if (raw.length() > 18 || raw.contains("选购") || raw.contains("认准") || raw.contains("官方")) {
            return "";
        }
        if (raw.equals(nz(asset.getTitle())) || raw.equals(nz(asset.getCaption()))) {
            // 素材库原 caption/title 常像广告语，默认不展示
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

    private String buildBaseUser(GeoContentPlacement placement, GeoContentAiGenerateDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("话题：").append(nz(placement.getTopicName()));
        sb.append("\n目标问题：").append(nz(placement.getTargetQuestion()));
        if (StringUtils.hasText(dto.getUserPrompt())) {
            sb.append("\n用户提示词：\n").append(dto.getUserPrompt().trim());
        }
        return sb.toString();
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
            n.put("sourceType", a.getSourceType());
            n.put("tags", a.getTagsJson());
            n.put("sceneTags", a.getSceneTagsJson());
        }
        return arr.toString();
    }

    private Parsed parseArticle(String content) {
        String title = "";
        String html = "";
        try {
            JsonNode node = readJson(content);
            title = node.path("title").asText("").trim();
            if (node.has("html")) {
                html = node.path("html").asText("").trim();
            } else if (node.has("body")) {
                html = node.path("body").asText("").trim();
            } else if (node.has("content")) {
                html = node.path("content").asText("").trim();
            }
        } catch (Exception e) {
            html = content == null ? "" : content.trim();
        }
        if (!StringUtils.hasText(html)) {
            throw new BusinessException("AI 未返回可用正文，请重试");
        }
        if (!StringUtils.hasText(title)) {
            title = "未命名文章";
        }
        if (!html.contains("<")) {
            html = plainToHtml(html);
        }
        return new Parsed(title, stripDataImages(html));
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

    /**
     * Lake 正文用同源路径直接引用素材库（如 /shxby/api/uploads/geo/xxx.jpg），
     * 避免绝对外链被编辑器抓取重传成 data:image。
     */
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
        // 去掉 data URI 图及其空 figure，避免编辑器「上传失败」
        String out = html.replaceAll("(?is)<img[^>]*src\\s*=\\s*[\"']data:image[^\"']*[\"'][^>]*/?>", "");
        out = out.replaceAll("(?is)<figure>\\s*(?:<figcaption>[\\s\\S]*?</figcaption>)?\\s*</figure>", "");
        return out;
    }

    private static String plainToHtml(String text) {
        String[] parts = text.replace("\r\n", "\n").split("\n{2,}");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String p = part.trim();
            if (!p.isEmpty()) {
                sb.append("<p>").append(p.replace("\n", "<br/>")).append("</p>");
            }
        }
        return sb.toString();
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

    /** 插在第 n 个 h2 后的第一个 </p> 之后，避免「标题下立刻大图+图注」版式难看 */
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

    /** 图中已有品牌时，减少紧随其后段落中的「小巴依」文字露出（软处理） */
    private static String dampenBrandNearMarkers(String html) {
        String[] parts = html.split("<!--brand-in-image-->", -1);
        if (parts.length <= 1) {
            return html;
        }
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String chunk = parts[i];
            // 仅处理标记后约 400 字内的前两个「小巴依」
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

    private record QaResult(boolean pass, String feedback) {}

    private record IllustrateResult(Parsed article, List<Long> usedAssetIds, Map<String, Long> sectionAssetMap) {}
}
