package com.base.admin.service;

/**
 * 黑名单人才身份。有手机号按手机号后 11 位，否则按邮箱；两者都空时，仅拦截「同名且同样没有联系方式」的新入库。
 * 已在库的记录只按手机号、邮箱或当时记下的候选人 ID 命中，避免同名不同人被一起移出正常库。
 */
public final class HrTalentBlacklistMatch {

    private HrTalentBlacklistMatch() {
    }

    public static String phoneKey(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() >= 11) {
            return digits.substring(digits.length() - 11);
        }
        return digits;
    }

    public static String emailKey(String email) {
        if (email == null) {
            return "";
        }
        String text = email.trim().toLowerCase();
        if (text.length() > 128) {
            text = text.substring(0, 128);
        }
        return text;
    }

    /** 仅在没有手机号和邮箱时保留姓名，供新入库拦截。 */
    public static String nameKey(String name, String phoneKey, String emailKey) {
        if (phoneKey != null && !phoneKey.isEmpty()) {
            return "";
        }
        if (emailKey != null && !emailKey.isEmpty()) {
            return "";
        }
        if (name == null) {
            return "";
        }
        String text = name.trim();
        if (text.length() > 64) {
            text = text.substring(0, 64);
        }
        return text;
    }

    /** 候选人别名当前是否命中一条有效黑名单。 */
    public static String activeExistsSql(String candidateAlias) {
        String phone = phoneKeySql(candidateAlias);
        return """
                EXISTS (
                  SELECT 1 FROM hr_talent_blacklist b
                  WHERE b.is_active = 1 AND b.status = 'IN'
                    AND (
                      (b.phone_key <> '' AND b.phone_key = %s)
                      OR (b.email_key <> '' AND b.email_key = LOWER(TRIM(IFNULL(%s.email, ''))))
                      OR FIND_IN_SET(CAST(%s.id AS CHAR), IFNULL(b.candidate_ids, ''))
                    )
                )""".formatted(phone, candidateAlias, candidateAlias);
    }

    public static String notActiveSql(String candidateAlias) {
        return " AND NOT " + activeExistsSql(candidateAlias) + " ";
    }

    /** 与 {@link #phoneKey(String)} 相同：只留数字，满 11 位取末 11 位。 */
    public static String phoneKeySql(String candidateAlias) {
        String digits = "REGEXP_REPLACE(IFNULL(" + candidateAlias + ".phone, ''), '[^0-9]', '')";
        return "CASE WHEN CHAR_LENGTH(" + digits + ") >= 11 THEN RIGHT(" + digits + ", 11) ELSE " + digits + " END";
    }
}
