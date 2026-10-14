package com.base.admin.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 仅拦截「几乎没有中文的程序源码」。培训文档里的操作说明、带图 HTML、Word 残留标签一律放行。
 */
public final class TrainQaAnswerSanitizer {

    private static final Pattern JSON_PAYLOAD = Pattern.compile(
            "(?s)^\\s*\\{\\s*\"(?:answer|question|index|code|items)\"\\s*:");

    /** 强特征：正常收银手册几乎不会同时出现这些。 */
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

    public static boolean looksLikeSourceCode(String answer, String answerHtml) {
        String text = plainText(answer, answerHtml);
        if (text.length() < 24) {
            return false;
        }
        int han = countHan(text);
        // 主体是中文操作说明：即使夹了少量英文/符号也放行
        if (han >= 15) {
            return false;
        }
        if (JSON_PAYLOAD.matcher(text).find() && han < 8) {
            return true;
        }
        int hits = 0;
        for (Pattern p : SOURCE_SIGNATURES) {
            if (p.matcher(text).find()) {
                hits++;
            }
        }
        return hits >= 1;
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
