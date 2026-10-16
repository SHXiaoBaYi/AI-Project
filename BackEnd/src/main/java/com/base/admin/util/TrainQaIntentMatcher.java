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
            {"折扣", "打折", "改价"},
            {"退货", "退款", "退单"},
            {"登录", "登陆", "签到"},
            {"寄存", "暂存"},
            {"挂单", "取单"},
            {"会员", "积分"},
            {"发票", "开票"},
            {"支付", "收款", "结算", "收银"},
            {"订货", "要货", "下单", "采购", "补货"},
            {"厂子", "厂家", "厂商", "厂方", "工厂", "供应商"},
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
        boolean userDiscount = familyPresent(u, FAMILIES[0]);
        boolean userRefund = familyPresent(u, FAMILIES[1]);
        boolean titleDiscount = familyPresent(t, FAMILIES[0]);
        boolean titleRefund = familyPresent(t, FAMILIES[1]);
        if (userDiscount && !userRefund && titleRefund && !titleDiscount) {
            return true;
        }
        if (userRefund && !userDiscount && titleDiscount && !titleRefund) {
            return true;
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
