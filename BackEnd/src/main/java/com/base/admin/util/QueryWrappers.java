package com.base.admin.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.base.admin.common.PageQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 分页查询公共条件 */
public final class QueryWrappers {

    private QueryWrappers() {}

    /** 按创建时间闭区间筛选（止日含当天 23:59:59） */
    public static <T> void applyCreateTimeRange(
            LambdaQueryWrapper<T> wrapper,
            PageQuery query,
            SFunction<T, LocalDateTime> createTimeColumn) {
        if (query == null || createTimeColumn == null) {
            return;
        }
        LocalDate start = query.getCreateTimeStart();
        LocalDate end = query.getCreateTimeEnd();
        if (start != null) {
            wrapper.ge(createTimeColumn, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.lt(createTimeColumn, end.plusDays(1).atStartOfDay());
        }
    }
}
