package com.base.admin.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一段里粘在上一答后面的「问：」也要切开。
 */
class TrainDocQaCutTest {

    @Test
    void splitsQuestionGluedAfterPreviousAnswer() {
        String plain = "问：积分兑换如何操作？答：收银主界面-点击F4-兑换冲减-输入重量，见图"
                + "问：怎么给顾客办理会员？会员怎么办理？怎么给新顾客办会员？答：收银主界面-点击F4-发卡";
        List<Integer> starts = TrainDocService.listQaStarts(plain);
        assertEquals(2, starts.size());
        assertTrue(plain.substring(starts.get(1)).startsWith("问：怎么给顾客办理会员"));
    }

    @Test
    void doesNotSplitPoliteAskInsideTheAnswer() {
        String plain = "问：怎么退货？答：请在前台操作。如果顾客请问：能不能退，按退货流程。";
        List<Integer> starts = TrainDocService.listQaStarts(plain);
        assertEquals(1, starts.size());
        assertEquals(0, starts.get(0));
    }
}
