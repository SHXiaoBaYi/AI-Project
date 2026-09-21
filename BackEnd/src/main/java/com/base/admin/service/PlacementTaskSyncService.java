package com.base.admin.service;

import com.base.admin.domain.entity.GeoContentPlacement;

/** 将投放管理中待分配发布人/撰写人的目标问题刷入任务列表 */
public interface PlacementTaskSyncService {

    /**
     * 幂等刷数：缺发布人 →「GEO文章待分配发布人」；缺撰写人 →「GEO文章待分配撰写人」。
     *
     * @return 本次新建任务数摘要
     */
    String syncUnassignedPlacementTasks();

    /**
     * 针对单条投放：缺字段则自动生成对应任务（幂等）。
     *
     * @return 本次新建任务数
     */
    int ensureTasksForPlacement(GeoContentPlacement placement);

    /** 按 ID 加载后生成（新增入口调用） */
    int ensureTasksForPlacement(Long placementId);

    /** 投放已有发布记录时，把关联的「文章发布」任务标为已完成 */
    void completeArticlePublishTasks(Long placementId);

    /** 扫描未完成的「文章发布」任务，有发布记录的自动完成 */
    void completeArticlePublishTasks();

    /** 删除投放时，同步删除该投放生成的任务 */
    void deleteTasksForPlacement(Long placementId);
}
