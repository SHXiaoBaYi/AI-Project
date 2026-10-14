package com.base.admin.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 收银答疑答案卫士：拦截程序代码 / 整页 HTML / JSON 载荷，避免下发给前台收银员。
 */
public final class TrainQaAnswerSanitizer {

    private static final Pattern HTML_DOCUMENT = Pattern.compile(
            "(?i)<!DOCTYPE\\s+html\\b|<html\\b|</html>|<head\\b|<body\\b|<script\\b|<style\\b");

    private static final Pattern MARKDOWN_FENCE = Pattern.compile("```[a-zA-Z0-9_+-]*");

    private static final Pattern JSON_PAYLOAD = Pattern.compile(
            "(?s)^\\s*\\{\\s*\"(?:answer|question|index|code|items)\"\\s*:");

    private static final Pattern[] CODE_SIGNATURES = {
            Pattern.compile("(?i)\\bpackage\\s+[\\w.]+\\s*;"),
            Pattern.compile("(?i)\\bimport\\s+(static\\s+)?[\\w.*]+\\s*;"),
            Pattern.compile("(?i)\\bpublic\\s+(class|interface|enum|record)\\b"),
            Pattern.compile("(?i)\\b(public|private|protected)\\s+(static\\s+)?(final\\s+)?(void|int|String|boolean|long)\\b"),
            Pattern.compile("(?i)\\bfunction\\s+\\w+\\s*\\("),
            Pattern.compile("(?i)\\b(export\\s+default|module\\.exports)\\b"),
            Pattern.compile("(?i)\\b(const|let|var)\\s+\\w+\\s*=\\s*(function|async|\\(|\\[|\\{)"),
            Pattern.compile("(?i)\\bdef\\s+\\w+\\s*\\("),
            Pattern.compile("(?i)#include\\s*[<\"]"),
            Pattern.compile("(?i)<\\?php\\b"),
            Pattern.compile("(?i)\\bSystem\\.out\\.print"),
            Pattern.compile("(?i)\\bconsole\\.(log|error|warn)\\s*\\("),
            Pattern.compile("(?i)\\bdocument\\.(getElementById|querySelector)"),
            Pattern.compile("(?i)@(Override|RestController|SpringBootApplication|RequestMapping|Data|Slf4j)\\b"),
            Pattern.compile("(?i)\\bclass\\s+\\w+\\s*\\{"),
            Pattern.compile("(?i)=>\\s*\\{"),
            Pattern.compile("(?i)\\bSELECT\\s+[\\w*,.\\s]+\\s+FROM\\s+\\w+"),
            Pattern.compile("(?i)\\b(?:fn|pub)\\s+(?:fn\\s+)?\\w+\\s*\\("),
    };

    private TrainQaAnswerSanitizer() {
    }

    public static boolean looksLikeSourceCode(String answer, String answerHtml) {
        if (looksLike(answer)) {
            return true;
        }
        if (!StringUtils.hasText(answerHtml)) {
            return false;
        }
        String html = answerHtml.trim();
        boolean ourWrapper = html.contains("train-qa-answer");
        if (!ourWrapper && HTML_DOCUMENT.matcher(html).find()) {
            return true;
        }
        return looksLike(stripTags(html));
    }

    private static boolean looksLike(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String text = raw.trim();
        if (text.length() < 12) {
            return false;
        }
        if (MARKDOWN_FENCE.matcher(text).find() || JSON_PAYLOAD.matcher(text).find()) {
            return true;
        }
        if (HTML_DOCUMENT.matcher(text).find()) {
            return true;
        }
        for (Pattern p : CODE_SIGNATURES) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return highSymbolDensity(text);
    }

    /** 大段花括号/分号且几乎没有中文：视为源码，而不是收银操作说明。 */
    private static boolean highSymbolDensity(String text) {
        if (text.length() < 80) {
            return false;
        }
        int symbols = 0;
        int han = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '}' || c == ';' || c == '(' || c == ')' || c == '[' || c == ']') {
                symbols++;
            }
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                han++;
            }
        }
        return symbols >= 12 && han * 8 < text.length();
    }

    private static String stripTags(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
