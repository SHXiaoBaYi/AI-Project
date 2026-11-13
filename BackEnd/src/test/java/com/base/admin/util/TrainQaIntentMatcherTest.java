package com.base.admin.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                "支付方式能不能用云闪付", "问：对账出现长短款怎么办产生收银差怎么办？"));
        assertFalse(TrainQaIntentMatcher.passesHardGate(
                "能不能用云闪付", "问：对账出现长短款怎么办产生收银差怎么办？"));
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
                "支付方式能不能用云闪付",
                "问：云闪付怎么收款？"));
        assertTrue(TrainQaIntentMatcher.passesHardGate(
                "退单怎么处理？",
                "问：如何退货/退货步骤 如果客户购买后不想要商品了，可在前台进行退款操作，退货有两种方法？"));
    }

    @Test
    void hitsTheQuestionThatActuallyCoversTheAsk() {
        List<String> titles = catalog();
        List<List<String>> aliases = aliases();

        String refund = pick("顾客申请退款怎么处理？", titles, aliases);
        assertNotNull(refund);
        assertTrue(refund.contains("退货") && refund.contains("退款"));
        assertFalse(refund.contains("寄存") || refund.contains("理赔") || refund.contains("会员"));

        assertTrue(pick("退款怎么处理呢？", titles, aliases).contains("退款"));
        assertTrue(pick("采购入库", titles, aliases).contains("采购入库"));
        assertFalse(pick("采购入库", titles, aliases).contains("要货"));
        assertTrue(pick("寄存货品怎么操作。", titles, aliases).contains("寄存"));
        assertFalse(pick("寄存货品怎么操作。", titles, aliases).contains("流水"));
        assertTrue(pick("取消积分兑换怎么操作", titles, aliases).contains("取消积分兑换"));
        assertTrue(pick("储值卡怎么结账呢？", titles, aliases).contains("储值卡"));
        assertTrue(pick("肉票怎么结账呢？", titles, aliases).contains("肉票"));
        assertTrue(pick("同时使用多张肉票的方法", titles, aliases).contains("多张肉票"));
        assertTrue(pick("如何传称", titles, aliases).contains("传称"));
        assertTrue(pick("怎么找厂子要货", titles, aliases).contains("找厂子要货"));
        assertTrue(pick("怎么找厂子订货", titles, aliases).contains("找厂子要货"));
        assertTrue(pick("要货怎么操作？", titles, aliases).contains("要货"));
    }

    @Test
    void leavesInvalidAndMissingQuestionsUnmatched() {
        List<String> titles = catalog();
        List<List<String>> aliases = aliases();
        for (String q : List.of(
                "不简洁，界面繁琐",
                "会员输入老是不对",
                "问题回复不准确  答非所问",
                "反应速度慢",
                "怎么查？",
                "支付方式能不能用云闪付？",
                "相同的东西为什么扫码的时候不能累计到一起",
                "回款怎么销帐？",
                "回款怎么收账",
                "快递费怎么收？",
                "退票",
                "肉票卡购买家庭套餐，怎么样出票。",
                "输入会员登记信息。电话，能否复制粘贴",
                "输入顾客会员手机号，不能复制粘贴",
                "积分兑换忒别麻烦，一次只能兑换一个品名",
                "答非说问，我的意思是，同一单销售，收银页面跟实际出票金额有误差，几分钱的差额，是什么原因",
                "查看当日当天的会员卡充值，怎么查这才是那个啥",
                "储值卡和现金怎么出票")) {
            assertNull(pick(q, titles, aliases), q);
        }
    }

    private static String pick(String question, List<String> titles, List<List<String>> aliases) {
        return TrainQaIntentMatcher.selectSpecificQuestion(question, titles, aliases);
    }

    private static List<String> catalog() {
        return List.of(
                "问：如何退货/退货步骤 如果客户购买后不想要商品了，可在前台进行退款操作，退货有两种方法？",
                "问：如何退货/退货步骤 如果客户购买后不想要商品了，可在前台进行退款操作，退货有两种方法？款项原路退回功能已关闭。",
                "问：理赔中涉及给顾客退款退货的，货品退回店面怎么处理？",
                "问：前端销售退单时发现找不到单据，提示单据不存在怎么办？",
                "问：前台寄存产品怎么操作？",
                "问：寄存商品要进行提货怎么操作？",
                "问：寄存商品查询？",
                "问：当天的销售流水在哪里可以查到，或者某个顾客的销售怎么查呢？",
                "问：怎么找厂子要货，要货单操作？",
                "问：采购单品怎么入库，类似于鸡蛋，果丹皮，等外采的产品？",
                "问：采购单品怎么入库，类似于鸡蛋，果丹皮，等外采的产品？采购入库怎么入？",
                "问：采购商品入库错误，怎么完成采购退货？",
                "问：取消积分兑换怎么操作？",
                "问：如何操作积分兑换？",
                "问：积分怎么获取？积分兑换怎么操作？",
                "问：怎么给顾客办理会员？会员怎么办理？",
                "问：支付过程中顾客选择多张肉票或者多张储值卡如何支付？",
                "问：支付中现金收款如何操作？",
                "问：如何进行商品录入与结算？",
                "问：如何查询会员积分、储值卡余额，肉票余额？",
                "问：一张储值卡显示过期的，后台激活后，刷出一次后 顾客当天再次消费还是显示过期。",
                "问：怎么查储值卡预收款？核对储值卡预收款项怎么核对？",
                "问：怎么传称？产品价格更新了，需要做传称？",
                "问：如果收款结算界面需要点结算才能扫顾客微信？",
                "问：如何进行日结与对账？怎么查营业额？",
                "问：对账出现长短款怎么办产生收银差怎么办？",
                "问：如何进行充值操作中的普通充值、反冲以及批量充值？",
                "问：挂账客户出票-退单怎么操作?",
                "问：组合支付现金加微信怎么操作？");
    }

    private static List<List<String>> aliases() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < catalog().size(); i++) {
            rows.add(List.of());
        }
        rows.set(4, List.of("顾客如果寄存货品，怎么操作。"));
        rows.set(8, List.of("怎么厂下要货"));
        return rows;
    }
}
