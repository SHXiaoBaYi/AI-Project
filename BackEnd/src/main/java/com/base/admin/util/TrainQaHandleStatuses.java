package com.base.admin.util;

import com.base.admin.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/** 答疑校准 / 未命中提问的人工处理状态。 */
public final class TrainQaHandleStatuses {

    public static final String OPEN = "OPEN";
    public static final String INTENT_BAD = "INTENT_BAD";
    public static final String IGNORE = "IGNORE";
    public static final String DOC_MISSING = "DOC_MISSING";

    private static final Set<String> ALL = Set.of(OPEN, INTENT_BAD, IGNORE, DOC_MISSING);

    private TrainQaHandleStatuses() {
    }

    public static String normalize(String raw) {
        String status = StringUtils.hasText(raw) ? raw.trim().toUpperCase(Locale.ROOT) : OPEN;
        if (!ALL.contains(status)) {
            throw new BusinessException("处理状态不合法");
        }
        return status;
    }

    public static boolean isHandled(String status) {
        return StringUtils.hasText(status) && !OPEN.equalsIgnoreCase(status.trim());
    }

    public static String label(String status) {
        if (!StringUtils.hasText(status)) {
            return "未处理";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case INTENT_BAD -> "用户意图不准";
            case IGNORE -> "忽略这样的问题";
            case DOC_MISSING -> "培训文档缺失";
            case OPEN -> "未处理";
            default -> status;
        };
    }
}
