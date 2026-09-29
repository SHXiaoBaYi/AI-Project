package com.base.admin.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EcomReportType {
    JD_TRADE_OVERVIEW("jd_trade_overview", "京东-交易概况", "shop_day", "jd"),
    JD_SPU_OVERVIEW("jd_spu_overview", "京东-商品概况", "shop_day", "jd"),
    JD_TRAFFIC_OVERVIEW("jd_traffic_overview", "京东-流量概况", "shop_day", "jd"),
    JD_USER_INSIGHT("jd_user_insight", "京东-用户洞察", "shop_day", "jd"),
    JD_TRAFFIC_SOURCE("jd_traffic_source", "京东-流量来源", "traffic_source", "jd"),
    JD_SPU_DETAIL("jd_spu_detail", "京东-商品明细", "spu_day", "jd"),
    JD_AD_SKU_PLAN("jd_ad_sku_plan", "京东-单品推广计划", "ad_plan", "jd"),
    JD_AD_PLAN_EFFECT("jd_ad_plan_effect", "京东-推广计划效果", "ad_effect", "jd"),

    TMALL_TRADE_OVERVIEW("tmall_trade_overview", "天猫-交易概况", "shop_day", "tmall"),
    TMALL_TRAFFIC_OVERVIEW("tmall_traffic_overview", "天猫-流量概况", "shop_day", "tmall"),
    TMALL_SPU_DETAIL("tmall_spu_detail", "天猫-商品明细", "spu_day", "tmall"),

    DOUYIN_TRADE_OVERVIEW("douyin_trade_overview", "抖音-交易概况", "shop_day", "douyin"),
    DOUYIN_TRAFFIC_OVERVIEW("douyin_traffic_overview", "抖音-流量概况", "shop_day", "douyin"),
    DOUYIN_SPU_DETAIL("douyin_spu_detail", "抖音-商品明细", "spu_day", "douyin");

    private final String code;
    private final String label;
    private final String grain;
    private final String platform;

    public static EcomReportType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (EcomReportType t : values()) {
            if (t.code.equalsIgnoreCase(code.trim())) {
                return t;
            }
        }
        return null;
    }

    /** 看板主指标优先取该报表类型 */
    public static boolean isTradeOverview(String reportType) {
        return reportType != null && reportType.endsWith("_trade_overview");
    }

    public static boolean isTrafficOverview(String reportType) {
        return reportType != null && reportType.endsWith("_traffic_overview");
    }
}
