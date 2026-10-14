package com.base.admin.util;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 答疑正文清洗：去掉 Word/OOXML 标签；拦截真正的程序源码，禁止下发前台。
 */
public final class TrainQaAnswerSanitizer {

    private static final Pattern JSON_PAYLOAD = Pattern.compile(
            "(?s)^\\s*\\{\\s*\"(?:answer|question|index|code|items)\"\\s*:");

    private static final Pattern WT_TEXT = Pattern.compile("(?is)<w:t[^>]*>(.*?)</w:t>");

    private static final Pattern OFFICE_TAG = Pattern.compile(
            "(?is)</?(?:w|a|r|wp|wps|v|o|m|pic|c)\\b[^>]*>");

    private static final Pattern[] SOURCE_SIGNATURES = {
            Pattern.compile("(?i)\\bpackage\\s+[\\w.]+\\s*;"),
            Pattern.compile("(?i)\\bimport\\s+(static\\s+)?[\\w.*]+\\s*;"),
            Pattern.compile("(?i)\\bpublic\\s+(class|interface|enum|record)\\b"),
            Pattern.compile("(?i)\\bpublic\\s+static\\s+void\\s+main\\s*\\("),
            Pattern.compile("(?i)\\bSystem\\.out\\.print"),
            Pattern.compile("(?i)\\bconsole\\.(log|error|warn)\\s*\\("),
            Pattern.compile("(?i)<\\?php\\b"),
            Pattern.compile("(?i)#include\\s*[<\"]"),
            Pattern.compile("(?i)@(Override|RestController|SpringBootApplication|RequestMapping)\\b"),
            Pattern.compile("(?i)\\b(export\\s+default|module\\.exports)\\b"),
            Pattern.compile("```(?:java|javascript|typescript|python|go|sql|json|html|css)\\b"),
    };

    private TrainQaAnswerSanitizer() {
    }

    /** 去掉 Word 文档泄漏的 w:pPr / w:rFonts 等标签，保留中文与 [图N]。 */
    public static String stripOfficeMarkup(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw == null ? "" : raw;
        }
        if (!raw.contains("<w:") && !raw.contains("<a:") && !raw.contains("<wp:")) {
            return raw;
        }
        String s = raw;
        Matcher m = WT_TEXT.matcher(s);
        StringBuffer wt = new StringBuffer();
        boolean anyT = false;
        while (m.find()) {
            anyT = true;
            m.appendReplacement(wt, Matcher.quoteReplacement(m.group(1)));
        }
        if (anyT) {
            m.appendTail(wt);
            s = wt.toString();
        }
        s = OFFICE_TAG.matcher(s).replaceAll("");
        s = s.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"");
        if (s.contains("<w:")) {
            s = OFFICE_TAG.matcher(s).replaceAll("");
        }
        return s.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public static boolean looksLikeSourceCode(String answer, String answerHtml) {
        String text = plainText(stripOfficeMarkup(answer), stripOfficeMarkup(answerHtml));
        if (text.length() < 24) {
            return false;
        }
        if (JSON_PAYLOAD.matcher(text).find() && countHan(text) < 8) {
            return true;
        }
        for (Pattern p : SOURCE_SIGNATURES) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static String plainText(String answer, String answerHtml) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(answer)) {
            sb.append(stripTags(answer)).append('\n');
        }
        if (StringUtils.hasText(answerHtml)) {
            sb.append(stripTags(answerHtml));
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static int countHan(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                n++;
            }
        }
        return n;
    }

    private static String stripTags(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
