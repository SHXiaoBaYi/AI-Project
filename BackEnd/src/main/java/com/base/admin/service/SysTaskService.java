package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.SysTaskAssignDTO;
import com.base.admin.domain.dto.SysTaskBatchAssignDTO;
import com.base.admin.domain.dto.SysTaskBatchCompleteDTO;
import com.base.admin.domain.dto.SysTaskBatchDeleteDTO;
import com.base.admin.domain.dto.SysTaskCompleteDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.dto.SysTaskQueryDTO;
import com.base.admin.domain.vo.SysTaskFileVO;
import com.base.admin.domain.vo.SysTaskVO;

import java.util.List;

public interface SysTaskService {

    PageResult<SysTaskVO> list(SysTaskQueryDTO query);

    SysTaskVO getById(Long id);

    Long create(SysTaskDTO dto);

    void update(SysTaskDTO dto);

    void delete(Long id);

    /** 批量删除任务 */
    String deleteBatch(SysTaskBatchDeleteDTO dto);

    /** 分配负责人/执行人；GEO 待分配类任务会回写投放发布人/撰写人 */
    void assign(Long id, SysTaskAssignDTO dto);

    /** 批量分配；不可分配的任务会跳过 */
    String batchAssign(SysTaskBatchAssignDTO dto);

    /** 执行人去完成；按类型可能要求上传证明，附件关联业务并带入下一子任务 */
    void complete(Long id, SysTaskCompleteDTO dto);

    /** 批量完成；要求证明附件的类型会跳过 */
    String batchComplete(SysTaskBatchCompleteDTO dto);

    /** 任务附件列表 */
    List<SysTaskFileVO> listFilesByTaskId(Long taskId);

    /** 业务主表关联附件（跨任务） */
    List<SysTaskFileVO> listFilesByBiz(String bizType, Long bizId);

    /**
     * 回滚误跑的「批量派生子任务」：仅保留 thh 的那条已完成撰写人分配 + 对应文章撰写，
     * 其余误完成的待分配任务恢复为待分配，并删除多余的文章撰写/发布子任务。
     */
    String rollbackMistakenSpawnBackfill();
}
