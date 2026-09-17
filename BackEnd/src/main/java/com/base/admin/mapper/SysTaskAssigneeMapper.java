package com.base.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.admin.domain.entity.SysTaskAssignee;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysTaskAssigneeMapper extends BaseMapper<SysTaskAssignee> {

    /** 物理删除，避免软删后唯一键 uk_task_user 冲突 */
    @Delete("DELETE FROM sys_task_assignee WHERE task_id = #{taskId}")
    int physicalDeleteByTaskId(@Param("taskId") Long taskId);
}
