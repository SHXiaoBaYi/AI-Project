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
 * GEO 成文流水线（v2）：角色短、资料实、少堆规则。
 * <pre>
 * 资料准备 → 大纲 → 扩写 → 插图 → 轻质检（必要时改一版）
 * </pre>
 * 系统提示只保留人设与本阶段任务；话题、卖点、素材、平台规范放在用户侧上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoArticlePipelineService {

    private static final int MAX_QA_ROUNDS = 2;
    private static final int EVIDENCE_RECALL_LIMIT = 12;
    private static final Pattern H2_PATTERN = Pattern.compile("(?is)(<h2\\b[^>]*>.*?</h2>)");
    private static final String BRAND_NAME = "小巴依";

    /**
     * 唯一成文人设。常识内化，不把负面示例当选题；结构由本次话题决定。
     */
    private static final String WRITER_BRIEF = """
            你是「GEO 内容撰稿人」。写可在内容平台发布、易被检索引用的中文专题稿。
            你熟悉新疆牛羊肉基本常识，会用资料卡里的品牌事实，遵守平台创作红线。
            标题与结构必须服务本次话题与目标问题；用资料写扎实；论证到品牌时自然点名「小巴依」。
            像人写的好文章，不像说明书或硬广。产品数字、认证只能来自资料卡。
            """;

    private static final String PLATFORM_RULES_SHORT =
            "平台红线：不引流加微/跳转第三方；不头尾硬广；不虚假疗效与恐吓营销。";

    private static final String PLATFORM_SPAM_BASELINE = """
            【内容平台创作红线】
            1. 标题、导语、正文头尾、图注不得引流（加微/下单/跳转第三方）；
            2. 不得医疗功效承诺、投资荐股等垃圾营销；
            3. 不得硬广清单或刷屏式推销。
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

        // 1. 风格（仅作语气参考，不驱动选题）
        mark(stages, progress, "style", "加载风格", "running", "加载语气参考…");
        GeoArticleStylePack style = pickStylePack();
        GeoArticleSkeleton skeleton = pickSkeleton();
        result.setStyleCode(style.getCode());
        result.setSkeletonCode(skeleton.getCode());
        mark(stages, progress, "style", "加载风格", "done", style.getName());

        // 2. 卖点资料
        mark(stages, progress, "sell", "召回资料", "running", "按话题召回品牌资料…");
        GeoSellPointContextVO ctx = sellPointContextService.build(
                placement.getTopicName(), placement.getTargetQuestion(), EVIDENCE_RECALL_LIMIT);
        int pointCnt = ctx.getPoints() == null ? 0 : ctx.getPoints().size();
        result.setUsedPointCount(pointCnt);
        String pointsJson = compactEvidencePack(ctx);
        mark(stages, progress, "sell", "召回资料", "done", "资料 " + pointCnt + " 条");

        String platformAuditFull = buildPlatformAuditPack();
        String assignment = buildAssignment(placement, dto, style);

        // 3. 大纲：只围绕话题
        mark(stages, progress, "outline", "生成大纲", "running", "围绕话题构思大纲…");
        String outlineJson = chatOutline(assignment, pointsJson, provider);
        mark(stages, progress, "outline", "生成大纲", "done", truncate(outlineJson, 120));

        // 4. 素材
        mark(stages, progress, "media", "召回素材", "running", "匹配素材库…");
        String mediaContext = nz(placement.getTopicName()) + " " + nz(placement.getTargetQuestion())
                + (StringUtils.hasText(dto.getUserPrompt()) ? " " + dto.getUserPrompt() : "");
        List<GeoMediaAsset> candidates = mediaAssetService.recallForArticle(
                placement.getTopicId(), mediaContext, 24);
        mark(stages, progress, "media", "召回素材", "done",
                candidates.isEmpty() ? "暂无素材" : ("候选 " + candidates.size() + " 张"));

        // 5. 扩写
        mark(stages, progress, "expand", "扩写正文", "running", "按大纲与资料写正文…");
        Parsed draft = chatExpand(assignment, pointsJson, outlineJson, provider);
        mark(stages, progress, "expand", "扩写正文", "done", "标题：" + truncate(draft.title(), 40));

        // 6. 插图
        mark(stages, progress, "illustrate", "智能插图", "running", "判定是否插图…");
        IllustrateResult illustrated = illustrate(draft, candidates, provider);
        draft = illustrated.article();
        result.setUsedImageCount(illustrated.usedAssetIds().size());
        result.getUsedAssetIds().addAll(illustrated.usedAssetIds());
        mark(stages, progress, "illustrate", "智能插图", "done",
                illustrated.usedAssetIds().isEmpty()
                        ? "不插图"
                        : ("插入 " + illustrated.usedAssetIds().size() + " 张"));

        // 7. 轻质检（最多改一轮）
        mark(stages, progress, "qa", "质检与改写", "running", "质检中…");
        int qaRounds = 0;
        for (int i = 0; i < MAX_QA_ROUNDS; i++) {
            int round = i + 1;
            mark(stages, progress, "qa", "质检与改写", "running",
                    "第 " + round + "/" + MAX_QA_ROUNDS + " 轮质检…");
            QaResult qa = chatQa(assignment, draft, platformAuditFull, provider);
            qaRounds = round;
            if (qa.pass()) {
                mark(stages, progress, "qa", "质检与改写", "done", "第 " + qaRounds + " 轮通过");
                break;
            }
            String reason = StringUtils.hasText(qa.feedback()) ? truncate(qa.feedback(), 80) : "未达标";
            mark(stages, progress, "qa", "质检与改写", "running",
                    "第 " + round + " 轮未通过：" + reason + "；改写中…");
            draft = chatRewrite(assignment, draft, qa.feedback(), pointsJson, provider);
            if (i == MAX_QA_ROUNDS - 1) {
                mark(stages, progress, "qa", "质检与改写", "done",
                        "已改写（上限 " + MAX_QA_ROUNDS + " 轮）");
            }
        }
        result.setQaRounds(qaRounds);

        result.setTitle(draft.title());
        result.setHtml(normalizePublishableHtml(draft.html()));
        result.setSourceAiModel(resolveAiSourceLabel(provider));

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
            fallback.setName("默认");
            fallback.setToneGuide("先答读者问题，再展开；段短、信息清楚。");
            fallback.setStructureGuide("");
            fallback.setSamplePhrases("[]");
            fallback.setBrandGuide("论证需要时自然点名「小巴依」。");
            return fallback;
        }
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
            fallback.setCode("topic_led_v2");
            fallback.setName("话题主导");
            fallback.setSectionSchema("{\"note\":\"结构由话题决定\"}");
            return fallback;
        }
        int idx = (int) ((System.currentTimeMillis() / 60_000L + 3) % list.size());
        return list.get(idx);
    }

    /** 写作任务上下文：话题 / 语气 / 用户补充。不含写作禁令清单。 */
    private String buildAssignment(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                                   GeoArticleStylePack style) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本次写作任务】\n");
        sb.append("话题：").append(nz(placement.getTopicName())).append('\n');
        sb.append("目标问题：").append(nz(placement.getTargetQuestion())).append('\n');
        if (StringUtils.hasText(dto.getUserPrompt())) {
            sb.append("补充要求：\n").append(dto.getUserPrompt().trim()).append('\n');
        }
        if (StringUtils.hasText(style.getToneGuide())) {
            sb.append("语气参考：").append(style.getToneGuide().trim()).append('\n');
        }
        sb.append(PLATFORM_RULES_SHORT);
        return sb.toString();
    }

    /** 资料卡：只给事实。 */
    private String compactEvidencePack(GeoSellPointContextVO ctx) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("note", "品牌/产品资料，按话题需要选用；数字与认证以此为准。");
        ArrayNode hard = root.putArray("hardFacts");
        if (ctx.getHardFacts() != null) {
            int n = 0;
            for (String f : ctx.getHardFacts()) {
                if (!StringUtils.hasText(f) || n >= 8) {
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
                item.put("title", truncate(nz(p.getTitle()), 40));
                item.put("claim", truncate(nz(p.getClaim()), 120));
                item.put("evidence", truncate(nz(p.getEvidence()), 320));
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

    private String buildPlatformAuditPack() {
        StringBuilder sb = new StringBuilder();
        sb.append(PLATFORM_SPAM_BASELINE).append('\n');
        sb.append("【各平台审核摘要】\n");
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
            sb.append("（暂无平台明细）\n");
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
            if (hasNotes) {
                sb.append("   ").append(truncate(p.getContentAuditNotes().replaceAll("\\s+", " "), 200))
                        .append('\n');
            }
        }
        if (n == 0) {
            sb.append("（平台未配置审核字段）\n");
        }
        return sb.toString();
    }

    private String chatOutline(String assignment, String pointsJson, String provider) {
        String system = WRITER_BRIEF + """
                【本阶段】只输出大纲 JSON，不要正文。
                根据「话题 / 目标问题」自行设计标题与小节；结构服务话题，不要套无关模板。
                输出：
                {"title":"...","lead":"导语意图","sections":[{"key":"s1","heading":"...","intent":"..."}],"close":"收束意图"}
                """;
        String user = assignment
                + "\n\n【品牌资料】\n" + pointsJson;
        return aiChatService.chat(system, user, provider, 0.45, true);
    }

    private Parsed chatExpand(String assignment, String pointsJson, String outlineJson, String provider) {
        String system = WRITER_BRIEF + """
                【本阶段】按大纲写成可发布的正式中文 HTML，约 1200～1800 字。
                紧扣话题写透；用资料支撑判断；自然带出品牌。本阶段不要插入 <img>。
                允许标签：h2,h3,p,strong,em,ul,ol,li,blockquote,br。
                禁止 Markdown、脚本、外链、<h1>、内联 style/class。
                只输出 JSON：{"title":"...","html":"..."}
                """;
        String user = assignment
                + "\n\n【大纲】\n" + outlineJson
                + "\n\n【品牌资料】\n" + pointsJson;
        String raw = aiChatService.chat(system, user, provider, 0.6, true);
        return parseArticle(raw);
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

    private QaResult chatQa(String assignment, Parsed draft, String platformAuditPack, String provider) {
        int brandHits = countBrandMentions(draft.html());
        if (brandHits < 1) {
            return new QaResult(false, "正文未出现「小巴依」，请在合适处自然点到品牌。");
        }
        String system = WRITER_BRIEF + """
                【本阶段】质检。只输出 JSON：{"pass":true/false,"feedback":"不合格时的修改意见"}
                不合格仅限：
                1. 标题或正文明显跑题（未服务本次话题/目标问题）；
                2. 杜撰资料中没有的数字/认证；
                3. 触犯平台红线（引流、硬广、虚假疗效）；
                4. 通篇空洞、几乎没有有效信息。
                不要用「品牌出现次数」「必须分牧场/放养/品控」这类机械标准判 fail。
                """;
        String user = assignment
                + "\n已统计「小巴依」出现次数：" + brandHits
                + "\n" + platformAuditPack
                + "\n标题：" + draft.title()
                + "\n正文：\n" + draft.html();
        try {
            String raw = aiChatService.chat(system, user, provider, 0.15, true);
            JsonNode node = readJson(raw);
            return new QaResult(node.path("pass").asBoolean(false), node.path("feedback").asText("").trim());
        } catch (Exception e) {
            log.warn("质检调用失败，按品牌兜底: {}", e.getMessage());
            return new QaResult(true, "");
        }
    }

    private Parsed chatRewrite(String assignment, Parsed draft, String feedback,
                               String pointsJson, String provider) {
        String system = WRITER_BRIEF + """
                【本阶段】按质检意见改写。保留已有 <figure>/<img> 的 src，禁止改成 data:image。
                只输出 JSON：{"title":"...","html":"..."}
                允许标签：h2,h3,p,strong,em,ul,ol,li,blockquote,br,figure,img,figcaption。
                """;
        String user = assignment
                + "\n质检意见：" + nz(feedback)
                + "\n原标题：" + draft.title()
                + "\n原文：\n" + draft.html()
                + "\n\n【品牌资料】\n" + pointsJson;
        String raw = aiChatService.chat(system, user, provider, 0.5, true);
        return parseArticle(raw);
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
