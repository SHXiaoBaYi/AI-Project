package com.base.admin.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 收银答疑意图：对用户问法做停用词切除 + 分词/二字切分，再和手册标题对齐。
 * 「如何折扣」应对上「如何进行折扣改价/改价」，而不是要求整句连续相等。
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
    };

    private TrainQaIntentMatcher() {}

    public static boolean titleMatches(String userQuestion, String title) {
        String t = compact(title);
        if (t.length() < 2) {
            return false;
        }
        if (conflicts(userQuestion, title)) {
            return false;
        }
        String leftover = leftover(userQuestion);
        if (leftover.length() >= 2 && t.contains(leftover)) {
            return true;
        }
        for (String tok : contentTokens(userQuestion)) {
            if (tok.length() >= 2 && (t.contains(tok) || familyHit(tok, t))) {
                return true;
            }
        }
        String user = compact(userQuestion);
        return user.length() >= 2 && user.length() <= 8 && (t.contains(user) || leftover.length() >= 2 && t.contains(leftover));
    }

    /**
     * 按「核心词在标题里出现的次数」排序：问「如何折扣」时，
     * 「如何进行折扣」会叠中 如何+折扣，「扫码、…、折扣、…」只中一次折扣，应落选。
     */
    public static int score(String userQuestion, String title) {
        String t = compact(title);
        if (t.length() < 2 || !StringUtils.hasText(userQuestion)) {
            return 0;
        }
        if (conflicts(userQuestion, title)) {
            return 0;
        }
        String leftover = leftover(userQuestion);
        String user = compact(userQuestion);
        int hits = 0;
        if (leftover.length() >= 2) {
            hits += countOccurrences(t, leftover);
            for (int i = 0; i + 2 <= leftover.length(); i++) {
                hits += countOccurrences(t, leftover.substring(i, i + 2));
            }
            if (leftover.length() >= 3) {
                for (int i = 0; i + 3 <= leftover.length(); i++) {
                    hits += countOccurrences(t, leftover.substring(i, i + 3));
                }
            }
        }
        // 用户原问里的问法词（如何/怎么/进行）叠中标题，专用问答题干会明显高于「罗列词条」
        hits += cueHits(userQuestion, title);
        if (hits == 0 && leftover.length() >= 2 && familyHit(leftover, t)) {
            hits = 1;
        }
        int score = hits * 100;
        if (leftover.length() >= 2 && t.contains(leftover)) {
            score += 30;
        }
        if (user.length() >= 2 && t.contains(user)) {
            score += 80;
        }
        // 顿号/逗号罗列多个主题：即使夹着核心词，命中次数也远少于专问标题
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

    public static Set<String> contentTokens(String q) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        String left = leftover(q);
        if (left.length() >= 2) {
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
        return tokens;
    }

    public static String compact(String s) {
        if (s == null) {
            return "";
        }
        return PUNCT.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static boolean conflicts(String userQuestion, String title) {
        String u = compact(userQuestion);
        String t = compact(title);
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
                if (tok.contains(w) || w.contains(tok) && tok.length() >= 2) {
                    inTok = true;
                    break;
                }
            }
            if (!inTok) {
                continue;
            }
            for (String w : fam) {
                if (titleCompact.contains(w)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean familyPresent(String text, String[] fam) {
        for (String w : fam) {
            if (text.contains(w)) {
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
