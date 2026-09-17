package com.base.admin.service.taskbiz;

/**
 * 按 bizType 回写主业务字段。
 * 新增业务时实现本接口并注册为 Spring Bean 即可。
 */
public interface TaskBizFieldWriter {

    /** 支持的业务类型，对应 sys_task.biz_type / sys_task_type.biz_type */
    String bizType();

    /**
     * 将分配的用户写入业务记录指定字段。
     *
     * @param bizId       业务主键（sys_task.biz_id）
     * @param assignField 字段编码（sys_task_type.assign_field）
     * @param userId      被分配用户ID
     * @param displayName 展示名
     */
    void writeAssignedUser(Long bizId, String assignField, Long userId, String displayName);
}
