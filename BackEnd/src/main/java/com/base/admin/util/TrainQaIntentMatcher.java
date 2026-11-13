package com.base.admin.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 收银答疑意图：对用户问法做停用词切除 + 分词/二字切分，再和手册标题/正文对齐。
 * 「如何折扣」应对上「如何进行折扣改价/改价」；「找厂子订货」应对上「找厂家要货」。
 */
public final class TrainQaIntentMatcher {

    private static final Pattern PUNCT = Pattern.compile(
            "[\\s\\p{Punct}？?！!，,。、；;：:\\\"'（）()【】\\[\\]《》<>·…—\\-_/\\\\|]+");

    /** 问法里不承载业务含义的词，按长短优先剥掉。 */
    private static final Pattern STOP = Pattern.compile(
            "怎么样|如何|怎么|怎样|请问|能否|能不能|可以吗|一下|进行|操作|步骤|方法|帮我|告诉"
                    + "|什么|哪个|哪些|是否|问题\\d*|问答\\d*|^问");

    private static final String[][] FAMILIES = {
            {"折扣", "打折", "改价"},           // 0
            {"退货", "退款", "退单"},           // 1
            {"登录", "登陆", "签到"},           // 2
            {"寄存", "暂存"},                   // 3
            {"挂单", "取单"},                   // 4
            {"积分", "兑换"},                   // 5  积分兑换（勿与会员登记绑死）
            {"发票", "开票"},                   // 6
            {"支付", "收款", "结算", "结账", "云闪付", "闪付", "刷卡", "银行卡", "付款码"}, // 7  支付方式；勿把「收银差」算进结算
            {"订货", "要货", "下单", "采购", "补货"}, // 8
            {"厂子", "厂家", "厂商", "厂方", "工厂", "供应商"}, // 9
            {"流水", "销售明细", "查销售", "销售查询", "顾客销售"}, // 10
            {"报表", "统计"},                   // 11
            {"会员登记", "办理会员", "开卡", "办会员"}, // 12 会员开卡/登记
            {"长短款", "收银差", "对账"},       // 13
            {"肉票", "储值卡"},                 // 14
    };

    /**
     * 互斥话题族下标对：用户只带其中一族、标题只带另一族时判冲突。
     * 例：问寄存却命中「销售流水」→ 硬闸门拦截。
     */
    private static final int[][] EXCLUSIVE_FAMILY_PAIRS = {
            {0, 1},   // 折扣 ↔ 退货
            {1, 3},   // 退货/退款 ↔ 寄存
            {3, 10},  // 寄存 ↔ 流水/销售查询
            {3, 11},  // 寄存 ↔ 报表
            {3, 8},   // 寄存 ↔ 订货
            {4, 10},  // 挂单 ↔ 流水
            {5, 12},  // 积分兑换 ↔ 会员登记
            {7, 13},  // 支付结算 ↔ 长短款/收银差
            {8, 10},  // 订货 ↔ 流水
            {14, 13}, // 肉票/储值卡 ↔ 长短款
    };

    /** 归一：同义写法落到同一核心词，便于「订货/要货」「厂子/厂家」同意图。 */
    private static final String[][] CANON_REPLACEMENTS = {
            {"供应商", "厂家"},
            {"厂子", "厂家"},
            {"厂商", "厂家"},
            {"厂方", "厂家"},
            {"工厂", "厂家"},
            {"要货", "订货"},
            {"下单", "订货"},
            {"补货", "订货"},
            {"采购", "订货"},
    };

    private TrainQaIntentMatcher() {}

    public static boolean titleMatches(String userQuestion, String title) {
        return textMatches(userQuestion, title, 1);
    }

    /**
     * HIT 前硬闸门：互斥话题冲突则否；用户有明确业务核心词时，题干须有词面/同义族重合。
     * 核心过短（&lt;2）时不做词面强校验，仍拦互斥冲突。
     */
    public static boolean passesHardGate(String userQuestion, String title) {
        return passesHardGate(userQuestion, title, null);
    }

    public static boolean passesHardGate(String userQuestion, String title, Iterable<String> aliases) {
        if (!StringUtils.hasText(userQuestion) || !StringUtils.hasText(title)) {
            return false;
        }
        if (conflicts(userQuestion, title)) {
            return false;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (StringUtils.hasText(alias) && conflicts(userQuestion, alias)) {
                    return false;
                }
            }
        }
        String canon = canonicalCore(userQuestion);
        if (canon.length() < 2) {
            return true;
        }
        if (lexicalOrFamilyOverlap(canon, title)) {
            return true;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (StringUtils.hasText(alias) && lexicalOrFamilyOverlap(canon, alias)) {
                    return true;
                }
            }
        }
        // 用户带了明确话题族，题干/别名却完全无该族且无词面重合 → 拦截
        return !hasExclusiveTopic(canon);
    }

    /** 锚点分达到此值，才允许在不靠模型时直接命中。 */
    public static final int SPECIFIC_HIT = 160;

    /**
     * 题干（或别名）是否盖住用户这句的具体操作。
     * 0 表示不能当确定命中：无效提问、手册里没有的点、只沾一个词的近题，都是 0。
     */
    public static int specificScore(String userQuestion, String title, Iterable<String> aliases) {
        if (!StringUtils.hasText(userQuestion) || !StringUtils.hasText(title)) {
            return 0;
        }
        int best = scoreSpecific(userQuestion, title);
        if (aliases != null) {
            for (String alias : aliases) {
                if (StringUtils.hasText(alias)) {
                    best = Math.max(best, scoreSpecific(userQuestion, alias));
                }
            }
        }
        return best;
    }

    /**
     * 候选里有唯一操作被锚点盖住时，返回该题干；并列的同一题（多份文档各有一份）取较短的一条。
     * 没有明确赢家时返回 null，交给未命中，不用近题凑。
     */
    public static String selectSpecificQuestion(String userQuestion, List<String> titles, List<? extends Iterable<String>> aliases) {
        if (!StringUtils.hasText(userQuestion) || titles == null || titles.isEmpty()) {
            return null;
        }
        int best = 0;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            Iterable<String> alias = aliases != null && i < aliases.size() ? aliases.get(i) : null;
            int score = specificScore(userQuestion, titles.get(i), alias);
            if (score > best) {
                best = score;
                idx.clear();
                idx.add(i);
            } else if (score == best && score > 0) {
                idx.add(i);
            }
        }
        if (best < SPECIFIC_HIT || idx.isEmpty()) {
            return null;
        }
        String chosen = titles.get(idx.get(0));
        for (int i : idx) {
            if (compact(titles.get(i)).length() < compact(chosen).length()) {
                chosen = titles.get(i);
            }
        }
        return chosen;
    }

    /**
     * 用户点名了某个操作时，题干必须带上对应的字，否则是近题。
     */
    private static int scoreSpecific(String userQuestion, String title) {
        if (conflicts(userQuestion, title)) {
            return 0;
        }
        String user = compact(userQuestion);
        String t = compact(title);
        if (user.isEmpty() || t.isEmpty()) {
            return 0;
        }
        int groups = 0;
        if (user.contains("传称")) {
            if (!t.contains("传称")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("入库")) {
            if (!t.contains("入库")) {
                return 0;
            }
            if (!user.contains("退货") && !user.contains("错误") && (t.contains("退货") || t.contains("错误"))) {
                return 0;
            }
            groups++;
        }
        if (user.contains("取消") && (user.contains("积分") || user.contains("兑换"))) {
            if (!t.contains("取消") || (!t.contains("积分") && !t.contains("兑换"))) {
                return 0;
            }
            groups++;
        }
        if (user.contains("云闪付") || user.contains("闪付")) {
            if (!t.contains("云闪付") && !t.contains("闪付")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("粘贴") || user.contains("复制")) {
            if (!t.contains("粘贴") && !t.contains("复制")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("寄存") || user.contains("寄存货品")) {
            if (!t.contains("寄存")) {
                return 0;
            }
            if (!user.contains("查询") && !user.contains("提货") && (t.contains("查询") || t.contains("提货"))) {
                return 0;
            }
            groups++;
        }
        if (user.contains("退票") && !user.contains("退款") && !user.contains("退货") && !user.contains("退单")) {
            if (!t.contains("退票")) {
                return 0;
            }
            groups++;
        } else if (user.contains("退款") || user.contains("退货") || user.contains("退单")) {
            if (!t.contains("退款") && !t.contains("退货") && !t.contains("退单")) {
                return 0;
            }
            if (!user.contains("理赔") && t.contains("理赔")) {
                return 0;
            }
            if (!user.contains("挂账") && t.contains("挂账")) {
                return 0;
            }
            if (!user.contains("找不到") && !user.contains("无法")
                    && (t.contains("找不到") || t.contains("无法"))) {
                return 0;
            }
            groups++;
        }
        if (user.contains("多张") && user.contains("肉票")) {
            if (!t.contains("多张") || !t.contains("肉票")) {
                return 0;
            }
            groups++;
        } else if (user.contains("肉票") && containsPay(user) && !user.contains("出票")) {
            if (!t.contains("肉票") || !containsPay(t) || t.contains("查询") || t.contains("余额") || t.contains("过期")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("储值卡") && containsPay(user) && !user.contains("出票")) {
            if (!t.contains("储值卡") || !containsPay(t) || t.contains("查询") || t.contains("核对")
                    || t.contains("预收") || t.contains("过期") || t.contains("余额")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("出票") && !user.contains("退")) {
            if (!t.contains("出票") || (!user.contains("挂账") && t.contains("挂账"))) {
                return 0;
            }
            groups++;
        }
        boolean factory = user.contains("厂子") || user.contains("厂家") || user.contains("厂商") || user.contains("供应商");
        boolean order = user.contains("要货") || user.contains("订货");
        if (factory && order && !user.contains("入库")) {
            if (!t.contains("要货") && !t.contains("厂子") && !t.contains("厂家") && !t.contains("厂商")) {
                return 0;
            }
            groups++;
        } else if (order && !factory && !user.contains("入库") && !user.contains("采购")) {
            if (!t.contains("要货") && !t.contains("订货")) {
                return 0;
            }
            groups++;
        }
        if ((user.contains("充值")) && (user.contains("查") || user.contains("查询"))) {
            if (!t.contains("充值") || (!t.contains("查") && !t.contains("查询"))) {
                return 0;
            }
            groups++;
        }
        if (user.contains("差额") || user.contains("误差") || user.contains("几分")) {
            if (!t.contains("差额") && !t.contains("误差") && !t.contains("几分")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("回款") || user.contains("销帐") || user.contains("销账") || user.contains("收账")) {
            if (!t.contains("回款") && !t.contains("销帐") && !t.contains("销账") && !t.contains("收账")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("快递")) {
            if (!t.contains("快递")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("累计") || (user.contains("扫码") && user.contains("不能"))) {
            if (!t.contains("累计") && !t.contains("扫码")) {
                return 0;
            }
            groups++;
        }
        if (user.contains("只能") || user.contains("麻烦")) {
            if (!t.contains("只能") && !t.contains("麻烦")) {
                return 0;
            }
            groups++;
        }
        if (groups == 0) {
            return 0;
        }
        int bonus = 0;
        if (user.length() >= 4 && t.contains(user)) {
            bonus += 80;
        }
        if (user.contains("退款") && t.contains("退款")) {
            bonus += 50;
        }
        if (user.contains("肉票") && t.contains("肉票")) {
            bonus += 20;
        }
        if (user.contains("储值卡") && t.contains("储值卡")) {
            bonus += 20;
        }
        return 140 + groups * 40 + bonus;
    }

    /** 用户原话点名了某种操作。题录盖不住时不能再拿近题充数。 */
    public static boolean hasSpecificConstraint(String userQuestion) {
        String user = compact(userQuestion);
        if (user.isEmpty()) {
            return false;
        }
        boolean factory = user.contains("厂子") || user.contains("厂家") || user.contains("厂商") || user.contains("供应商");
        boolean order = user.contains("要货") || user.contains("订货");
        return user.contains("传称")
                || user.contains("入库")
                || (user.contains("取消") && (user.contains("积分") || user.contains("兑换")))
                || user.contains("云闪付") || user.contains("闪付")
                || user.contains("粘贴") || user.contains("复制")
                || user.contains("寄存") || user.contains("寄存货品")
                || user.contains("退票") || user.contains("退款") || user.contains("退货") || user.contains("退单")
                || (user.contains("多张") && user.contains("肉票"))
                || (user.contains("肉票") && containsPay(user) && !user.contains("出票"))
                || (user.contains("储值卡") && containsPay(user) && !user.contains("出票"))
                || (user.contains("出票") && !user.contains("退"))
                || (factory && order && !user.contains("入库"))
                || (order && !factory && !user.contains("入库") && !user.contains("采购"))
                || (user.contains("充值") && (user.contains("查") || user.contains("查询")))
                || user.contains("差额") || user.contains("误差") || user.contains("几分")
                || user.contains("回款") || user.contains("销帐") || user.contains("销账") || user.contains("收账")
                || user.contains("快递")
                || user.contains("累计") || (user.contains("扫码") && user.contains("不能"))
                || user.contains("只能") || user.contains("麻烦");
    }

    private static boolean containsPay(String text) {
        return text.contains("支付") || text.contains("结账") || text.contains("结算") || text.contains("收款");
    }

    /** 关键词重合度 0~1，供向量召回融合加分。 */
    public static float keywordOverlap(String userQuestion, String title) {
        Set<String> tokens = contentTokens(userQuestion);
        if (tokens.isEmpty() || !StringUtils.hasText(title)) {
            return 0f;
        }
        String t = canonicalize(compact(title));
        int hit = 0;
        int total = 0;
        for (String tok : tokens) {
            if (tok.length() < 2) {
                continue;
            }
            total++;
            String cTok = canonicalize(tok);
            if (t.contains(tok) || t.contains(cTok) || familyHit(tok, t) || familyHit(cTok, t)) {
                hit++;
            }
        }
        if (total == 0) {
            return 0f;
        }
        return hit / (float) total;
    }

    public static float keywordOverlap(String userQuestion, String title, Iterable<String> aliases) {
        float best = keywordOverlap(userQuestion, title);
        if (aliases == null) {
            return best;
        }
        for (String alias : aliases) {
            if (!StringUtils.hasText(alias)) {
                continue;
            }
            best = Math.max(best, keywordOverlap(userQuestion, alias));
        }
        return best;
    }

    /**
     * 正文召回：整段核心意图命中，或（问句同时含厂家类+订货类时）正文两类都要出现；
     * 其余情况至少 2 个业务词命中，避免长文误中。
     */
    public static boolean bodyMatches(String userQuestion, String body) {
        String t = canonicalize(compact(body));
        if (t.length() < 4) {
            return false;
        }
        if (conflicts(userQuestion, body)) {
            return false;
        }
        String canon = canonicalCore(userQuestion);
        if (canon.length() >= 4 && t.contains(canon)) {
            return true;
        }
        boolean needFactory = familyPresent(canon, FAMILIES[9]);
        boolean needOrder = familyPresent(canon, FAMILIES[8]);
        if (needFactory && needOrder) {
            return familyPresent(t, FAMILIES[9]) && familyPresent(t, FAMILIES[8]);
        }
        return textMatches(userQuestion, body, 2);
    }

    private static boolean textMatches(String userQuestion, String text, int minTokenHits) {
        String t = canonicalize(compact(text));
        if (t.length() < 2) {
            return false;
        }
        if (conflicts(userQuestion, text)) {
            return false;
        }
        String leftover = leftover(userQuestion);
        String canon = canonicalCore(userQuestion);
        if (canon.length() >= 2 && t.contains(canon)) {
            return true;
        }
        if (leftover.length() >= 2 && (t.contains(leftover) || t.contains(canonicalize(leftover)))) {
            return true;
        }
        int hits = 0;
        for (String tok : contentTokens(userQuestion)) {
            if (tok.length() < 2) {
                continue;
            }
            String cTok = canonicalize(tok);
            if (t.contains(tok) || t.contains(cTok) || familyHit(tok, t) || familyHit(cTok, t)) {
                hits++;
                if (hits >= minTokenHits) {
                    return true;
                }
            }
        }
        if (minTokenHits <= 1) {
            String user = compact(userQuestion);
            return user.length() >= 2 && user.length() <= 8
                    && (t.contains(user) || t.contains(canonicalize(user))
                    || leftover.length() >= 2 && t.contains(canonicalize(leftover)));
        }
        return false;
    }

    /**
     * 按「核心词在标题里出现的次数」排序：问「如何折扣」时，
     * 「如何进行折扣」会叠中 如何+折扣，「扫码、…、折扣、…」只中一次折扣，应落选。
     */
    public static int score(String userQuestion, String title) {
        String t = canonicalize(compact(title));
        if (t.length() < 2 || !StringUtils.hasText(userQuestion)) {
            return 0;
        }
        if (conflicts(userQuestion, title)) {
            return 0;
        }
        String leftover = leftover(userQuestion);
        String canon = canonicalCore(userQuestion);
        String user = compact(userQuestion);
        int hits = 0;
        if (canon.length() >= 2) {
            hits += countOccurrences(t, canon);
            for (int i = 0; i + 2 <= canon.length(); i++) {
                hits += countOccurrences(t, canon.substring(i, i + 2));
            }
            if (canon.length() >= 3) {
                for (int i = 0; i + 3 <= canon.length(); i++) {
                    hits += countOccurrences(t, canon.substring(i, i + 3));
                }
            }
        } else if (leftover.length() >= 2) {
            hits += countOccurrences(t, leftover);
            for (int i = 0; i + 2 <= leftover.length(); i++) {
                hits += countOccurrences(t, leftover.substring(i, i + 2));
            }
        }
        hits += cueHits(userQuestion, title);
        if (hits == 0 && canon.length() >= 2 && familyHit(canon, t)) {
            hits = 1;
        }
        int score = hits * 100;
        if (canon.length() >= 2 && t.contains(canon)) {
            score += 50;
        }
        if (leftover.length() >= 2 && t.contains(leftover)) {
            score += 30;
        }
        if (user.length() >= 2 && t.contains(canonicalize(user))) {
            score += 80;
        }
        int catalog = catalogTopics(title);
        if (catalog >= 3) {
            score -= (catalog - 2) * 80;
        }
        return Math.max(0, score);
    }

    /** 如何/怎么/进行 等问法线索在标题中的叠中次数。 */
    public static int cueHits(String userQuestion, String title) {
        String raw = compact(userQuestion);
        String t = compact(title);
        int n = 0;
        for (String cue : List.of("如何", "怎么", "怎样", "进行")) {
            if (raw.contains(cue) && t.contains(cue)) {
                n += countOccurrences(t, cue);
            }
        }
        return n;
    }

    public static String leftover(String q) {
        String s = compact(q);
        if (s.isEmpty()) {
            return "";
        }
        String prev;
        do {
            prev = s;
            s = STOP.matcher(s).replaceAll("");
        } while (!s.equals(prev));
        return s;
    }

    /** 去停用词后再做同义归一，用作校准 intent_norm / 同意图判断。 */
    public static String canonicalCore(String q) {
        return canonicalize(leftover(q));
    }

    public static String canonicalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String out = s;
        for (String[] pair : CANON_REPLACEMENTS) {
            out = out.replace(pair[0], pair[1]);
        }
        return out;
    }

    public static Set<String> contentTokens(String q) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        addTokens(tokens, leftover(q));
        addTokens(tokens, canonicalCore(q));
        return tokens;
    }

    private static void addTokens(Set<String> tokens, String left) {
        if (left.length() < 2) {
            return;
        }
        tokens.add(left);
        for (int i = 0; i + 2 <= left.length(); i++) {
            tokens.add(left.substring(i, i + 2));
        }
        if (left.length() >= 3) {
            for (int i = 0; i + 3 <= left.length(); i++) {
                tokens.add(left.substring(i, i + 3));
            }
        }
    }

    public static String compact(String s) {
        if (s == null) {
            return "";
        }
        return PUNCT.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static boolean conflicts(String userQuestion, String title) {
        String u = canonicalize(compact(userQuestion));
        String t = canonicalize(compact(title));
        for (int[] pair : EXCLUSIVE_FAMILY_PAIRS) {
            boolean uA = familyPresent(u, FAMILIES[pair[0]]);
            boolean uB = familyPresent(u, FAMILIES[pair[1]]);
            boolean tA = familyPresent(t, FAMILIES[pair[0]]);
            boolean tB = familyPresent(t, FAMILIES[pair[1]]);
            if (uA && !uB && tB && !tA) {
                return true;
            }
            if (uB && !uA && tA && !tB) {
                return true;
            }
        }
        return false;
    }

    private static boolean lexicalOrFamilyOverlap(String userCanon, String text) {
        String t = canonicalize(compact(text));
        if (t.isEmpty()) {
            return false;
        }
        if (userCanon.length() >= 2 && t.contains(userCanon)) {
            return true;
        }
        for (String tok : contentTokens(userCanon)) {
            if (tok.length() < 2) {
                continue;
            }
            String cTok = canonicalize(tok);
            if (t.contains(tok) || t.contains(cTok) || familyHit(tok, t) || familyHit(cTok, t)) {
                return true;
            }
        }
        // 仅话题族：用户核心含某族，文本也含同族
        for (String[] fam : FAMILIES) {
            if (familyPresent(userCanon, fam) && familyPresent(t, fam)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExclusiveTopic(String userCanon) {
        for (int[] pair : EXCLUSIVE_FAMILY_PAIRS) {
            if (familyPresent(userCanon, FAMILIES[pair[0]]) || familyPresent(userCanon, FAMILIES[pair[1]])) {
                return true;
            }
        }
        return false;
    }

    private static boolean familyHit(String tok, String titleCompact) {
        for (String[] fam : FAMILIES) {
            boolean inTok = false;
            for (String w : fam) {
                if (tok.contains(w) || (w.contains(tok) && tok.length() >= 2)) {
                    inTok = true;
                    break;
                }
            }
            if (!inTok) {
                continue;
            }
            for (String w : fam) {
                if (titleCompact.contains(w) || titleCompact.contains(canonicalize(w))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean familyPresent(String text, String[] fam) {
        for (String w : fam) {
            if (text.contains(w) || text.contains(canonicalize(w))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> tokenList(String q) {
        return new ArrayList<>(contentTokens(q));
    }

    private static int countOccurrences(String hay, String needle) {
        if (!StringUtils.hasText(hay) || !StringUtils.hasText(needle)) {
            return 0;
        }
        int n = 0;
        int from = 0;
        while (from <= hay.length() - needle.length()) {
            int at = hay.indexOf(needle, from);
            if (at < 0) {
                break;
            }
            n++;
            from = at + 1;
        }
        return n;
    }

    /** 标题被顿号/逗号切成多少个并列主题。 */
    private static int catalogTopics(String title) {
        if (!StringUtils.hasText(title)) {
            return 0;
        }
        String[] parts = title.split("[、，,；;／/]");
        int n = 0;
        for (String p : parts) {
            if (compact(p).length() >= 2) {
                n++;
            }
        }
        return n;
    }
}
