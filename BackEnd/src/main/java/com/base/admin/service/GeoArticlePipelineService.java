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

    private static final int EVIDENCE_RECALL_LIMIT = 12;
    private static final Pattern H2_PATTERN = Pattern.compile("(?is)(<h2\\b[^>]*>.*?</h2>)");
    private static final Pattern MD_H1 = Pattern.compile("(?m)^\\s*#\\s+(.+)$");
    private static final Pattern MD_H2 = Pattern.compile("(?m)^\\s*##\\s+(.+)$");
    private static final String BRAND_NAME = "小巴依";

    /**
     * 外网对话式人设。问题主线 + 资料封闭世界；不写死任何具体选题或个案反例。
     */
    private static final String WRITER_BRIEF = """
            你是资深自媒体撰稿人，擅长美食与农产品专题。
            请像在通义网页里写稿一样：一口气写出可读、可信的完整中文文章。

            【主线】读者带着「目标问题」来；标题与小节必须服务回答该问题；资料是论据不是目录。

            【事实封闭世界——宁缺毋滥】
            1. 品牌相关的数字、指标、认证、抽检、赛事/合作、独家/唯一表述，只能来自我提供的资料原文；资料没有的一律不写。
            2. 禁止为增强说服力而补造：对比合格率、抽检项数、未给出的精确百分比、未写明的「独占/唯一」、未写明的因果机理。
            3. 行业常识只用于避免外行吃法/形态硬伤；资料未写明的机理、因果、优劣判断不要自行发挥成「行业真相」。
            4. 合作/赞助/背书类事实：资料怎么写就怎么写，不要引申成资料没有的检测、报告或门槛。
            5. 资料写了区间或约数，就保持区间/约数，不要改成更「好看」的精确值或标杆话术。
            6. 品牌「小巴依」在论证需要时自然出现；不引流加微、不硬广口号、不虚假疗效。
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

        // 1. 语气参考（不驱动结构）
        mark(stages, progress, "style", "加载风格", "running", "加载语气参考…");
        GeoArticleStylePack style = pickStylePack();
        GeoArticleSkeleton skeleton = pickSkeleton();
        result.setStyleCode(style.getCode());
        result.setSkeletonCode(skeleton.getCode());
        mark(stages, progress, "style", "加载风格", "done", style.getName());

        // 2. 卖点
        mark(stages, progress, "sell", "召回卖点", "running", "按话题召回品牌卖点…");
        GeoSellPointContextVO ctx = sellPointContextService.build(
                placement.getTopicName(), placement.getTargetQuestion(), EVIDENCE_RECALL_LIMIT);
        int pointCnt = ctx.getPoints() == null ? 0 : ctx.getPoints().size();
        result.setUsedPointCount(pointCnt);
        String evidenceProse = formatEvidenceProse(ctx);
        mark(stages, progress, "sell", "召回卖点", "done", "卖点 " + pointCnt + " 条");

        // 3. 素材（写作时只告知有图，插图阶段再挂）
        mark(stages, progress, "media", "召回素材", "running", "匹配素材库…");
        String mediaContext = nz(placement.getTopicName()) + " " + nz(placement.getTargetQuestion())
                + (StringUtils.hasText(dto.getUserPrompt()) ? " " + dto.getUserPrompt() : "");
        List<GeoMediaAsset> candidates = mediaAssetService.recallForArticle(
                placement.getTopicId(), mediaContext, 24);
        mark(stages, progress, "media", "召回素材", "done",
                candidates.isEmpty() ? "暂无素材" : ("候选 " + candidates.size() + " 张"));

        // 4. 外网模式：一气呵成写正文
        mark(stages, progress, "write", "撰写正文", "running", "外网模式一气呵成写稿（较久）…");
        Parsed draft = chatWriteOnce(placement, dto, style, evidenceProse, provider);
        mark(stages, progress, "write", "撰写正文", "done", "标题：" + truncate(draft.title(), 40));

        // 5. 缺品牌时轻补（不整篇质检重写）
        int brandHits = countBrandMentions(draft.html());
        result.setQaRounds(0);
        if (brandHits < 1) {
            mark(stages, progress, "brand", "品牌轻补", "running", "正文未点品牌，轻补一版…");
            draft = chatWeaveBrand(placement, draft, evidenceProse, provider);
            result.setQaRounds(1);
            mark(stages, progress, "brand", "品牌轻补", "done",
                    "小巴依 ×" + countBrandMentions(draft.html()));
        }

        // 6. 插图
        mark(stages, progress, "illustrate", "智能插图", "running", "按正文挂载素材…");
        IllustrateResult illustrated = illustrate(draft, candidates, provider);
        draft = illustrated.article();
        result.setUsedImageCount(illustrated.usedAssetIds().size());
        result.getUsedAssetIds().addAll(illustrated.usedAssetIds());
        mark(stages, progress, "illustrate", "智能插图", "done",
                illustrated.usedAssetIds().isEmpty()
                        ? "不插图"
                        : ("插入 " + illustrated.usedAssetIds().size() + " 张"));

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
            fallback.setCode("web_once_v1");
            fallback.setName("外网一次成稿");
            fallback.setSectionSchema("{\"mode\":\"one-shot\"}");
            return fallback;
        }
        int idx = (int) ((System.currentTimeMillis() / 60_000L + 3) % list.size());
        return list.get(idx);
    }

    /** 卖点转成自然语言论据；封闭世界 + 按问题选用。 */
    private String formatEvidenceProse(GeoSellPointContextVO ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("用法：这是本次唯一允许引用的品牌/产品事实来源（封闭世界）。\n")
                .append("- 只挑能支撑「回答目标问题」的条目；\n")
                .append("- 不要按资料目录巡展，不要用资料顺序当大纲；\n")
                .append("- 原文没有的数字、对比、独家表述、机理因果，禁止补写。\n\n");
        if (ctx.getHardFacts() != null && !ctx.getHardFacts().isEmpty()) {
            sb.append("硬事实（可直接写进正文，勿改数字）：\n");
            int i = 1;
            for (String f : ctx.getHardFacts()) {
                if (StringUtils.hasText(f)) {
                    sb.append(i++).append(". ").append(f.trim()).append('\n');
                }
            }
            sb.append('\n');
        }
        if (ctx.getPoints() != null && !ctx.getPoints().isEmpty()) {
            sb.append("候选论据：\n");
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
                    sb.append("   （软依据：转述宜克制，勿升级成精确数据或独家结论）\n");
                }
            }
        }
        if (ctx.getForbiddenHints() != null) {
            for (String h : ctx.getForbiddenHints()) {
                if (StringUtils.hasText(h)) {
                    sb.append("注意：").append(h.trim()).append('\n');
                }
            }
        }
        String out = sb.toString().trim();
        return StringUtils.hasText(out) ? out : "（暂无卖点资料：只答目标问题，勿编造任何品牌数字或认证）";
    }

    /**
     * 外网模式主调用：一次生成完整文章。
     * 用户侧顺序：问题主线 → 自定义要求 → 语气 → 论据资料（故意把资料放后面，降低「资料绑架结构」）。
     */
    private Parsed chatWriteOnce(GeoContentPlacement placement, GeoContentAiGenerateDTO dto,
                                 GeoArticleStylePack style, String evidenceProse, String provider) {
        String topic = nz(placement.getTopicName());
        String question = nz(placement.getTargetQuestion());
        String spine = StringUtils.hasText(question) ? question : topic;
        String system = WRITER_BRIEF + """
                输出格式（二选一，优先 A）：
                A) Markdown：第一行用「# 标题」，正文用「## 小节」分段，不要代码块包裹。
                B) JSON：{"title":"...","html":"..."}，html 用 h2/p 等简单标签。
                字数大约 1200～1800 字。专心写好文章，不要解释你的写法。
                """;

        StringBuilder user = new StringBuilder();
        user.append("请写一篇能直接回答读者问题的专题文章。\n\n");
        user.append("【成文主线——全文必须围绕它展开】\n");
        user.append(spine).append('\n');
        if (StringUtils.hasText(question) && StringUtils.hasText(topic) && !question.equals(topic)) {
            user.append("（所属话题：").append(topic).append("）\n");
        }
        user.append("\n自检：①读者能否直接得到对主线的回答；②文中每个具体数字/认证/对比/独家表述是否都能在下方资料中找到原文依据；")
                .append("③有无资料未写明的行业因果被写成「真相」。通不过就删改，宁可少写一句，也不要补造。\n");

        if (StringUtils.hasText(dto.getUserPrompt())) {
            user.append("\n【自定义写作要求——必须遵守】\n")
                    .append(dto.getUserPrompt().trim()).append('\n');
        }
        if (StringUtils.hasText(style.getToneGuide())) {
            user.append("\n语气参考：").append(style.getToneGuide().trim()).append('\n');
        }
        user.append("\n【唯一事实来源——封闭世界；按主线挑选，禁止补造】\n")
                .append(evidenceProse).append('\n');
        user.append("\n配图稍后由系统从素材库插入，你只需写好文字。");

        // 略降温度：减少「为显得专业而编机理/数字」
        String raw = aiChatService.chat(system, user.toString(), provider, 0.55, true);
        return parseArticleFlexible(raw);
    }

    private Parsed chatWeaveBrand(GeoContentPlacement placement, Parsed draft,
                                  String evidenceProse, String provider) {
        String spine = StringUtils.hasText(placement.getTargetQuestion())
                ? placement.getTargetQuestion().trim()
                : nz(placement.getTopicName());
        String system = """
                你是撰稿人。原文未出现品牌「小巴依」。
                请在不偏离成文主线、不大改结构的前提下，自然点到「小巴依」一两处（嵌在回答问题的判断或论据里）。
                严禁借机补造资料没有的数字、对比、独家表述或行业机理。
                不要扩成品牌说明书。保留原有 HTML；figure/img 的 src 不要改。
                输出 Markdown（# 标题 + 正文）或 JSON {"title","html"}。
                """;
        String user = "成文主线：" + spine
                + "\n\n【可用论据】\n" + evidenceProse
                + "\n\n原标题：" + draft.title()
                + "\n原文：\n" + draft.html();
        String raw = aiChatService.chat(system, user, provider, 0.45, true);
        return parseArticleFlexible(raw);
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

    private record IllustrateResult(Parsed article, List<Long> usedAssetIds, Map<String, Long> sectionAssetMap) {}
}
