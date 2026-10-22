package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.domain.entity.GeoSellDomain;
import com.base.admin.domain.entity.GeoSellPoint;
import com.base.admin.domain.vo.GeoSellPointContextVO;
import com.base.admin.mapper.GeoSellDomainMapper;
import com.base.admin.mapper.GeoSellPointMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 卖点召回：按话题 + 目标问题做关键词/标签打分，产出可喂给生成/审核的上下文包。
 */
@Service
@RequiredArgsConstructor
public class SellPointContextService {

    private static final int DEFAULT_LIMIT = 12;
    private static final int EVIDENCE_MAX = 420;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,，、;；|/\\\\]+");

    private final GeoSellPointMapper pointMapper;
    private final GeoSellDomainMapper domainMapper;
    private final ObjectMapper objectMapper;

    public GeoSellPointContextVO build(String topicName, String targetQuestion, Integer limit) {
        int cap = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, 20);
        String topic = topicName == null ? "" : topicName.trim();
        String question = targetQuestion == null ? "" : targetQuestion.trim();

        GeoSellPointContextVO ctx = new GeoSellPointContextVO();
        ctx.setTopic(topic);
        ctx.setQuestion(question);
        ctx.getForbiddenHints().add("不得编造未收录的认证编号、检测数据、门店地址或捐赠金额");
        ctx.getForbiddenHints().add("不得夸大未出现在卖点库中的功效或承诺");
        ctx.getForbiddenHints().add("不得编造资料未写明的对比合格率、抽检项、精确百分比或「独占/唯一」表述");
        ctx.getForbiddenHints().add("不得把资料未写明的行业机理/因果写成既定真相");
        ctx.getForbiddenHints().add("合作或赞助类事实不得引申为资料没有的检测报告或准入门槛");

        List<GeoSellPoint> points = pointMapper.selectList(new LambdaQueryWrapper<GeoSellPoint>()
                .eq(GeoSellPoint::getStatus, "active")
                .orderByAsc(GeoSellPoint::getSortOrder)
                .orderByDesc(GeoSellPoint::getId)
                .last("LIMIT 800"));
        if (points.isEmpty()) {
            return ctx;
        }

        Map<Long, GeoSellDomain> domainMap = domainMapper.selectList(new LambdaQueryWrapper<GeoSellDomain>()
                        .eq(GeoSellDomain::getEnabled, 1))
                .stream()
                .collect(Collectors.toMap(GeoSellDomain::getId, d -> d, (a, b) -> a));

        Set<String> queryTokens = extractTokens(topic + " " + question);
        List<Scored> scored = new ArrayList<>();
        for (GeoSellPoint point : points) {
            int score = score(point, queryTokens, topic, question);
            if (score <= 0 && queryTokens.isEmpty()) {
                score = "hard".equalsIgnoreCase(point.getEvidenceLevel()) ? 2 : 1;
            }
            if (score > 0) {
                scored.add(new Scored(point, score));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> "hard".equalsIgnoreCase(s.point().getEvidenceLevel()) ? 0 : 1)
                .thenComparing(s -> s.point().getId(), Comparator.reverseOrder()));

        if (scored.isEmpty()) {
            // 无命中时回退：硬事实优先取若干条，保证生成仍有素材
            scored = points.stream()
                    .sorted(Comparator
                            .comparing((GeoSellPoint p) -> "hard".equalsIgnoreCase(p.getEvidenceLevel()) ? 0 : 1)
                            .thenComparing(GeoSellPoint::getSortOrder)
                            .thenComparing(GeoSellPoint::getId, Comparator.reverseOrder()))
                    .limit(cap)
                    .map(p -> new Scored(p, 1))
                    .toList();
        }

        List<String> hardFacts = new ArrayList<>();
        for (Scored s : scored) {
            if (ctx.getPoints().size() >= cap) {
                break;
            }
            GeoSellPoint point = s.point();
            GeoSellPointContextVO.PointItem item = new GeoSellPointContextVO.PointItem();
            item.setId(point.getId());
            GeoSellDomain domain = domainMap.get(point.getDomainId());
            item.setDomain(domain == null ? "" : domain.getName());
            item.setTitle(nz(point.getTitle()));
            item.setClaim(nz(point.getClaim()));
            item.setEvidence(truncate(nz(point.getEvidence()), EVIDENCE_MAX));
            item.setLevel(StringUtils.hasText(point.getEvidenceLevel()) ? point.getEvidenceLevel() : "soft");
            item.setTags(parseJsonList(point.getTagsJson()));
            item.setScore(s.score());
            ctx.getPoints().add(item);
            if ("hard".equalsIgnoreCase(item.getLevel()) && hardFacts.size() < 8) {
                String fact = StringUtils.hasText(item.getClaim()) ? item.getClaim() : item.getTitle();
                if (StringUtils.hasText(fact)) {
                    hardFacts.add(fact);
                }
            }
        }
        ctx.setHardFacts(hardFacts);
        return ctx;
    }

    private int score(GeoSellPoint point, Set<String> queryTokens, String topic, String question) {
        if (queryTokens.isEmpty() && !StringUtils.hasText(topic) && !StringUtils.hasText(question)) {
            return 0;
        }
        String hay = (nz(point.getTitle()) + " " + nz(point.getClaim()) + " " + nz(point.getEvidence())
                + " " + nz(point.getTagsJson()) + " " + nz(point.getKeywordsJson())).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : queryTokens) {
            if (token.length() < 2) {
                continue;
            }
            if (hay.contains(token)) {
                score += token.length() >= 4 ? 4 : 2;
            }
        }
        String q = question.toLowerCase(Locale.ROOT);
        String t = topic.toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(t) && hay.contains(t)) {
            score += 6;
        }
        // 常见场景词加权
        for (String boost : List.of("有机", "冷链", "送礼", "认证", "牧场", "排酸", "售后", "冷鲜", "精品")) {
            if (q.contains(boost) && hay.contains(boost)) {
                score += 5;
            }
        }
        if ("hard".equalsIgnoreCase(point.getEvidenceLevel())) {
            score += 2;
        }
        return score;
    }

    private Set<String> extractTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String part : TOKEN_SPLIT.split(normalized)) {
            String p = part.trim();
            if (p.length() >= 2) {
                tokens.add(p);
            }
        }
        // 中文连续 2～3 字切片，提高短问命中
        String compact = normalized.replaceAll("[\\s\\p{Punct}]+", "");
        for (int i = 0; i < compact.length(); i++) {
            if (i + 2 <= compact.length()) {
                tokens.add(compact.substring(i, i + 2));
            }
            if (i + 3 <= compact.length()) {
                tokens.add(compact.substring(i, i + 3));
            }
        }
        // 去掉无意义过短噪声
        tokens.removeIf(t -> t.length() < 2);
        return tokens;
    }

    private List<String> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private record Scored(GeoSellPoint point, int score) {
    }
}
