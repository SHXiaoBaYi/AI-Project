package com.base.admin.service;

/**
 * 区间进展、考核得分取时间：先用「调整阶段」写入的业务时间，没有调整记录再用原口径。
 */
public final class HrStageAdjustClock {

    private HrStageAdjustClock() {
    }

    /** 该投递最近一次调到指定阶段的业务时间。没有记录时子查询为空。 */
    public static String latestBusinessAt(String applicationIdExpr, String stageCodeExpr) {
        return """
                (SELECT l.business_at FROM hr_stage_adjust_log l
                 WHERE l.is_active = 1 AND l.application_id = %s AND l.to_stage = %s
                 ORDER BY l.operated_at DESC, l.id DESC LIMIT 1)""".formatted(applicationIdExpr, stageCodeExpr);
    }
}
