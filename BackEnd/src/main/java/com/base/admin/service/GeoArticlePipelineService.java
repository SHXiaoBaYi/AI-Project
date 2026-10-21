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
 * 后台两阶段成文流水线：大纲 → 扩写+插图 → 质检循环重写；前端仍一键调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoArticlePipelineService {

    private static final int MAX_QA_ROUNDS = 6;
    private static final Pattern H2_PATTERN = Pattern.compile("(?is)(<h2\\b[^>]*>.*?</h2>)");
    /** 成文默认品牌；自然露出，禁止零出现 */
    private static final String BRAND_NAME = "小巴依";
    private static final String BRAND_QUALITY_RULES = """
            【品牌露出——必须有，且要毫不违和】
            1. 正文必须出现品牌名「小巴依」，建议 2～4 次；禁止全文 0 次，也禁止开篇硬广口号堆砌；
            2. 品牌要嵌进论证与场景：如用事实支撑时自然带出「小巴依在…上的做法」，或对比选择时点名，而不是单独甩一句广告；
            3. 第 1 段可以不出现品牌，但从第 2 段起最晚要有一次自然点名；收束段可再自然回扣一次；
            4. 若某节插图已含品牌露出，该节文字品牌可减半，但不能因此让全文变成 0 次。
            """;

    private final AiChatService aiChatService;
    private final SellPointContextService sellPointContextService;
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

        // —— 阶段2：卖点召回 ——
        mark(stages, progress, "sell", "召回卖点", "running", "正在按话题/目标问题召回卖点…");
        GeoSellPointContextVO ctx = sellPointContextService.build(
                placement.getTopicName(), placement.getTargetQuestion(), 12);
        int pointCnt = ctx.getPoints() == null ? 0 : ctx.getPoints().size();
        result.setUsedPointCount(pointCnt);
        String pointsJson;
        try {
            pointsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ctx);
        } catch (Exception e) {
            throw new BusinessException("组装卖点上下文失败");
        }
        mark(stages, progress, "sell", "召回卖点", "done", "召回 " + pointCnt + " 条");

        // —— 阶段3：大纲 ——
        mark(stages, progress, "outline", "生成大纲", "running", "深度思考中：规划论点、品牌落点与证据顺序…");
        String outlineJson = chatOutline(placement, dto, style, skeleton, pointsJson, provider);
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
        Parsed draft = chatExpand(placement, dto, style, skeleton, pointsJson, outlineJson, candidates, provider);
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
            QaResult qa = chatQa(placement, style, draft, provider);
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
                draft = chatRewrite(placement, style, draft, qa.feedback(), pointsJson, provider);
                mark(stages, progress, "qa", "质检与重写", "done",
                        "已达上限 " + MAX_QA_ROUNDS + " 轮，已按末次意见重写");
                break;
            }
            mark(stages, progress, "qa", "质检与重写", "running",
                    "第 " + round + " 轮未通过：" + reason + "；正在重写…");
            draft = chatRewrite(placement, style, draft, qa.feedback(), pointsJson, provider);
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
            fallback.setBrandGuide("须自然出现「小巴依」2～4次，禁止零露出与开篇硬广；图中已有品牌时该节少写文字品牌。");
            return fallback;
        }
        // 简单轮换：按小时取模，避免总用同一包
        int idx = (int) (System.currentTimeMillis() / 3_600_000L % list.size());
        return list.get(idx);
    }

    private GeoArticleSkeleton pickSkeleton() {
        List<GeoArticleSkeleton> list = skeletonMapper.selectList(
                new LambdaQueryWrapper<GeoArticleSkeleton>()
                        .eq(GeoArticleSkeleton::getEnabled, 1)
                        .orderByAsc(GeoArticleSkeleton::getSortOrder)
                        .last("LIMIT 4"));
        if (list == null || list.isEmpty()) {
            GeoArticleSkeleton fallback = new GeoArticleSkeleton();
            fallback.setCode("qa_sections_v1");
            fallback.setName("问答分节骨架");
            fallback.setSectionSchema("{\"lead\":\"导语\",\"sections\":[{\"role\":\"argument\"},{\"role\":\"close\"}]}");
            return fallback;
        }
        return list.get(0);
    }

    private String chatOutline(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                               GeoArticleStylePack style, GeoArticleSkeleton skeleton,
                               String pointsJson, String provider) {
        String system = """
                你是资深 GEO 内容主笔兼策划（品牌：小巴依）。只输出大纲 JSON，不要正文。
                请先在内部深度思考再落大纲（读者疑虑、竞品对照、最强论据排序、品牌自然落点），不求快，求准。

                【质量标杆】学风格包的结构节奏，禁止抄范文；成文目标是比常见知乎/头条范文更扎实、更具体、更有洞察。
                【品牌】大纲里用 brandBeats 标明「小巴依」将在哪几处自然出现（至少 2 处意图）。
                输出 JSON：
                {"title":"...","lead":"导语意图","readerDoubt":"读者最可能的疑虑","brandBeats":["何处自然点名品牌"],"sections":[{"key":"s1","heading":"...","intent":"...","evidenceFocus":"...","needImage":"auto|yes|no"}]}
                """;
        String user = buildBaseUser(placement, dto)
                + "\n风格包：" + style.getName() + "\n语气：" + nz(style.getToneGuide())
                + "\n结构：" + nz(style.getStructureGuide())
                + "\n可摘句式(最多用1～2)：" + nz(style.getSamplePhrases())
                + "\n骨架：" + nz(skeleton.getSectionSchema())
                + "\n品牌规则：" + nz(style.getBrandGuide())
                + "\n" + BRAND_QUALITY_RULES
                + "\n卖点上下文：\n" + pointsJson;
        return aiChatService.chat(system, user, provider, 0.35, true);
    }

    private Parsed chatExpand(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                              GeoArticleStylePack style, GeoArticleSkeleton skeleton,
                              String pointsJson, String outlineJson,
                              List<GeoMediaAsset> candidates, String provider) {
        String mediaHint = buildMediaCatalog(candidates);
        String system = """
                你是小巴依品牌资深图文主笔。按大纲扩写可直接发布的正式中文 HTML。
                请深度思考后再动笔：先想清论证链与品牌落点，再写；不求速度，质量必须明显高于流水账范文。

                【事实】只用卖点库事实，不编造认证编号/数据/门店/捐赠金额。
                【质量】段落短、信息密度高；有具体细节与对比，忌空话口号；结构学风格包，文笔与洞察要更好。
                【句式】样本句式全文最多摘用 1～2 句并改写融入，禁止大段照搬范文。
                """ + BRAND_QUALITY_RULES + """
                【插图】本阶段不要插入 <img>；需要图的节用 <!--IMG:sectionKey--> 占位；每节允许 0 张。
                【排版】title 独立；html=导语+多个<h2>+收束。允许标签：h2,h3,p,strong,em,ul,ol,li,blockquote,br。
                禁止 Markdown、脚本、外链、<h1>、内联 style/class。
                只输出 JSON：{"title":"...","html":"..."}
                """;
        String user = buildBaseUser(placement, dto)
                + "\n大纲：\n" + outlineJson
                + "\n风格：" + style.getName() + " / " + nz(style.getToneGuide())
                + "\n结构：" + nz(style.getStructureGuide())
                + "\n句式样本：" + nz(style.getSamplePhrases())
                + "\n品牌：" + nz(style.getBrandGuide())
                + "\n骨架：" + nz(skeleton.getSectionSchema())
                + "\n可选素材目录（插图阶段再用，此处仅供判断是否留占位）：\n" + mediaHint
                + "\n卖点：\n" + pointsJson
                + "\n自检：输出前确认 html 中「小巴依」出现次数 ≥ 2，且读起来不像硬广。";
        String raw = aiChatService.chat(system, user, provider, 0.5, true);
        return parseArticle(raw);
    }

    private IllustrateResult illustrate(Parsed draft, List<GeoMediaAsset> candidates, String provider) {
        if (candidates == null || candidates.isEmpty()) {
            String html = draft.html().replaceAll("(?is)<!--\\s*IMG:[^>]*-->", "");
            return new IllustrateResult(new Parsed(draft.title(), html), List.of(), Map.of());
        }
        String catalog = buildMediaCatalog(candidates);
        String system = """
                你是插图编辑。根据正文与素材目录，决定每节是否插图（允许 0 张）。
                规则：只返回目录中的 assetId（由服务端写入素材库真实 src，禁止你输出 data:image 或臆造 URL）。
                按 usageHint/tags/sceneTags 与段落意图匹配。图中 brandVisible=1 时该节少写文字品牌。
                只输出 JSON：{"placements":[{"sectionKey":"s1","assetId":12,"afterHeading":true,"caption":"..."}]}
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
        try {
            JsonNode root = readJson(raw);
            ArrayNode arr = (ArrayNode) (root.has("placements") ? root.get("placements") : objectMapper.createArrayNode());
            for (JsonNode p : arr) {
                Long assetId = p.path("assetId").asLong(0);
                if (assetId <= 0 || !byId.containsKey(assetId) || usedOnce.contains(assetId)) {
                    continue;
                }
                GeoMediaAsset asset = byId.get(assetId);
                String sectionKey = p.path("sectionKey").asText("auto");
                String caption = StringUtils.hasText(p.path("caption").asText())
                        ? p.path("caption").asText().trim()
                        : (StringUtils.hasText(asset.getCaption()) ? asset.getCaption() : asset.getTitle());
                // 同源相对路径，直接引用素材库文件；禁止 data:image，避免 Lake 重传失败
                String imgSrc = toArticleImgSrc(asset.getFileUrl());
                String alt = StringUtils.hasText(asset.getAltText()) ? asset.getAltText() : caption;
                String img = "<figure><img src=\"" + escapeAttr(imgSrc) + "\" alt=\"" + escapeAttr(alt)
                        + "\" /><figcaption>" + escapeHtml(caption) + "</figcaption></figure>";
                String placeholder = "<!--IMG:" + sectionKey + "-->";
                if (html.contains(placeholder)) {
                    html = html.replace(placeholder, img);
                } else {
                    html = insertAfterFirstH2(html, img, sectionMap.size());
                }
                if (asset.getBrandVisible() != null && asset.getBrandVisible() == 1) {
                    // 图中已有品牌：在该图后追加软提示注释供质检/读者无感；正文侧靠 rewrite 前约束
                    html = html.replace(img, img + "<!--brand-in-image-->");
                }
                used.add(assetId);
                usedOnce.add(assetId);
                sectionMap.put(sectionKey, assetId);
            }
        } catch (Exception e) {
            log.warn("解析插图决策失败: {}", e.getMessage());
        }
        html = html.replaceAll("(?is)<!--\\s*IMG:[^>]*-->", "");
        // 若有图含品牌，轻量替换减少邻近「小巴依」重复（软处理）
        if (html.contains("<!--brand-in-image-->")) {
            html = dampenBrandNearMarkers(html);
            html = html.replace("<!--brand-in-image-->", "");
        }
        html = stripDataImages(html);
        return new IllustrateResult(new Parsed(draft.title(), html), used, sectionMap);
    }

    private QaResult chatQa(GeoContentPlacement placement, GeoArticleStylePack style, Parsed draft, String provider) {
        int brandHits = countBrandMentions(draft.html());
        if (brandHits < 2) {
            return new QaResult(false,
                    "正文「小巴依」仅出现 " + brandHits + " 次，须毫不违和地补到至少 2 次（嵌进论证/场景，禁止开篇硬广口号）。");
        }
        String system = """
                你是严苛的 GEO 内容质检官与资深主编。不求快，只求准。只输出 JSON：
                {"pass":true/false,"feedback":"不合格时给出可执行修改意见"}
                不合格情形（任一即 fail）：
                1. 未紧扣目标问题，或编造卖点库没有的事实；
                2. 结构缺少导语+h2+收束，或流水账堆砌、空话多、洞察浅，达不到「优于常见范文」；
                3. 「小巴依」为零或生硬硬广；或超过约 5 次且像广告刷屏；
                4. 误用 Markdown / 危险标签。
                品牌规则：""" + BRAND_QUALITY_RULES;
        String user = "目标问题：" + nz(placement.getTargetQuestion())
                + "\n风格品牌指引：" + nz(style.getBrandGuide())
                + "\n已统计「小巴依」出现次数：" + brandHits
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
            return brandHits >= 2 ? new QaResult(true, "") : new QaResult(false, "质检异常且品牌露出不足，请补写自然品牌点名。");
        }
    }

    private Parsed chatRewrite(GeoContentPlacement placement, GeoArticleStylePack style,
                               Parsed draft, String feedback, String pointsJson, String provider) {
        String system = """
                按质检意见深度改写全文。必须原样保留已插入的 <figure>/<img>/<figcaption> 的 src（素材库路径），禁止改成 data:image 或外链臆造地址，除非意见明确要求删图。
                事实只来自卖点库。改写后质量须明显好于流水账范文；品牌「小巴依」须自然出现至少 2 次。
                """ + BRAND_QUALITY_RULES + """
                只输出 JSON：{"title":"...","html":"..."}
                允许标签：h2,h3,p,strong,em,ul,ol,li,blockquote,br,figure,img,figcaption。
                """;
        String user = "目标问题：" + nz(placement.getTargetQuestion())
                + "\n质检意见：" + nz(feedback)
                + "\n品牌：" + nz(style.getBrandGuide())
                + "\n原标题：" + draft.title()
                + "\n原文：\n" + draft.html()
                + "\n卖点：\n" + pointsJson
                + "\n自检：改写后「小巴依」≥2 且不违和。";
        String raw = aiChatService.chat(system, user, provider, 0.4, true);
        return parseArticle(raw);
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

    private static String insertAfterFirstH2(String html, String img, int index) {
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
