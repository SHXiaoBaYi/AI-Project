package com.base.admin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 硬闸门保险丝：已知错配问法不得放行。
 */
class TrainQaIntentMatcherTest {

    @Test
    void blocksKnownCrossTopicMismatches() {
        assertFalse(TrainQaIntentMatcher.passesHardGate(
                "顾客申请退款怎么处理？", "问：前台寄存产品怎么操作？"));
        assertFalse(TrainQaIntentMatcher.passesHardGate(
                "输入会员登记信息。电话，能否复制粘贴", "问：如何操作积分兑换？"));
        assertFalse(TrainQaIntentMatcher.passesHardGate(
                "会员怎么登记信息？", "问：取消积分兑换怎么操作？"));
        assertFalse(TrainQaIntentMatcher.passesHardGate(
                "使用肉票卡怎么结算。", "问：对账出现长短款怎么办产生收银差怎么办？"));
        assertFalse(TrainQaIntentMatcher.passesHardGate(
                "寄存货品怎么操作。", "问：当天的销售流水在哪里可以查到，或者某个顾客的销售怎么查呢？"));
    }

    @Test
    void allowsSameOperationTitles() {
        assertTrue(TrainQaIntentMatcher.passesHardGate(
                "顾客申请退款怎么处理？",
                "问：如何退货/退货步骤 如果客户购买后不想要商品了，可在前台进行退款操作，退货有两种方法？"));
        assertTrue(TrainQaIntentMatcher.passesHardGate(
                "使用肉票卡怎么结算。",
                "问：支付过程中顾客选择多张肉票或者多张储值卡如何支付？"));
        assertTrue(TrainQaIntentMatcher.passesHardGate(
                "退单怎么处理？",
                "问：如何退货/退货步骤 如果客户购买后不想要商品了，可在前台进行退款操作，退货有两种方法？"));
    }
}
