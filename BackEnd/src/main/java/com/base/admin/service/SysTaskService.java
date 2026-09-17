package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.SysTaskAssignDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.dto.SysTaskQueryDTO;
import com.base.admin.domain.vo.SysTaskVO;

public interface SysTaskService {

    PageResult<SysTaskVO> list(SysTaskQueryDTO query);

    SysTaskVO getById(Long id);

    Long create(SysTaskDTO dto);

    void update(SysTaskDTO dto);

    void delete(Long id);

    /** 分配负责人/执行人；GEO 待分配类任务会回写投放发布人/撰写人 */
    void assign(Long id, SysTaskAssignDTO dto);

    /**
     * 回滚误跑的「批量派生子任务」：仅保留 thh 的那条已完成撰写人分配 + 对应文章撰写，
     * 其余误完成的待分配任务恢复为待分配，并删除多余的文章撰写/发布子任务。
     */
    String rollbackMistakenSpawnBackfill();
}
