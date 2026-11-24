package com.base.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HrTalentBlacklistMatchTest {

    @Test
    void phoneKeyKeepsLast11Digits() {
        assertEquals("", HrTalentBlacklistMatch.phoneKey(null));
        assertEquals("", HrTalentBlacklistMatch.phoneKey("  "));
        assertEquals("13800138000", HrTalentBlacklistMatch.phoneKey("138-0013-8000"));
        assertEquals("13800138000", HrTalentBlacklistMatch.phoneKey("+86 138 0013 8000"));
        assertEquals("13800138000", HrTalentBlacklistMatch.phoneKey("008613800138000"));
        assertEquals("12345", HrTalentBlacklistMatch.phoneKey("12345"));
    }

    @Test
    void nameKeyOnlyWhenNoContact() {
        assertEquals("", HrTalentBlacklistMatch.nameKey("张三", "13800138000", ""));
        assertEquals("", HrTalentBlacklistMatch.nameKey("张三", "", "a@b.com"));
        assertEquals("张三", HrTalentBlacklistMatch.nameKey(" 张三 ", "", ""));
        assertEquals("a@b.com", HrTalentBlacklistMatch.emailKey(" A@B.com "));
    }
}
