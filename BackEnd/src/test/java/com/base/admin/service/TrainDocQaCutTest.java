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
    void splitsQuestionsSeparatedByWordLineBreak() {
        String xml = """
                <w:p>
                  <w:r><w:t>问：积分兑换如何操作？</w:t></w:r>
                  <w:r><w:t>答：收银主界面-点击F4-兑换冲减，见图</w:t></w:r>
                  <w:r><w:br w:type="textWrapping"/></w:r>
                  <w:r><w:t>问：怎么给顾客办理会员？会员怎么办理？</w:t></w:r>
                  <w:r><w:t>怎么给新顾客办会员？</w:t></w:r>
                  <w:r><w:br w:type="textWrapping"/></w:r>
                  <w:r><w:t>答：收银主界面-点击F4-发卡，完成会员新办</w:t></w:r>
                </w:p>
                """;
        String plain = TrainDocService.renderDocxParagraph(xml, rId -> "");
        List<Integer> starts = TrainDocService.listQaStarts(plain);
        assertEquals(2, starts.size());
        String member = plain.substring(starts.get(1));
        assertTrue(member.startsWith("问：怎么给顾客办理会员"));
        assertTrue(member.contains("发卡"));
        assertTrue(member.contains("完成会员新办"));
        assertTrue(!plain.substring(starts.get(0), starts.get(1)).contains("办理会员"));
    }

    @Test
    void doesNotSplitPoliteAskInsideTheAnswer() {
        String plain = "问：怎么退货？答：请在前台操作。如果顾客请问：能不能退，按退货流程。";
        List<Integer> starts = TrainDocService.listQaStarts(plain);
        assertEquals(1, starts.size());
        assertEquals(0, starts.get(0));
        String xml = "<w:p><w:r><w:t>问：怎么退货？答：如果顾客请问：能不能退，按退货流程。</w:t></w:r></w:p>";
        String rendered = TrainDocService.renderDocxParagraph(xml, rId -> "");
        assertEquals(1, TrainDocService.listQaStarts(rendered).size());
        assertTrue(rendered.contains("请问：能不能退"));
    }
}
