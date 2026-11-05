package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoAutoQuestionDTO;
import com.base.admin.domain.dto.GeoAutoQuestionQueryDTO;
import com.base.admin.domain.dto.GeoAutoResultQueryDTO;
import com.base.admin.domain.dto.GeoAutoTaskDTO;
import com.base.admin.domain.dto.GeoAutoTaskQueryDTO;
import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.domain.vo.GeoAutoPlatformOptionVO;
import com.base.admin.domain.vo.GeoAutoQuestionOptionVO;
import com.base.admin.domain.vo.GeoAutoQuestionVO;
import com.base.admin.domain.vo.GeoAutoResultDetailVO;
import com.base.admin.domain.vo.GeoAutoResultMatrixVO;
import com.base.admin.domain.vo.GeoAutoResultVO;
import com.base.admin.domain.vo.GeoAutoSeedResultVO;
import com.base.admin.domain.vo.GeoAutoTaskVO;

import java.util.List;

/**
 * GEO 自动监测（问题监控）：问题库、监测任务、运行批次与结果查询。
 * 执行链路见 {@link GeoAutoMonitorExecutor}。
 */
public interface GeoAutoMonitorService {

    // ---------- 监控问题库 ----------

    PageResult<GeoAutoQuestionVO> listQuestions(GeoAutoQuestionQueryDTO query);

    List<GeoAutoQuestionOptionVO> questionOptions(Long topicId);

    void createQuestion(GeoAutoQuestionDTO dto);

    void updateQuestion(GeoAutoQuestionDTO dto);

    void deleteQuestion(Long id);

    void deleteQuestionBatch(List<Long> ids);

    void setQuestionsEnabled(List<Long> ids, Integer enabled);

    /** 从历史数据洗入问题库：日监测关键字 + 内容投放目标问题（去重） */
    GeoAutoSeedResultVO seedQuestions();

    // ---------- 监测任务 ----------

    PageResult<GeoAutoTaskVO> listTasks(GeoAutoTaskQueryDTO query);

    List<GeoAutoPlatformOptionVO> platformOptions();

    void createTask(GeoAutoTaskDTO dto);

    void updateTask(GeoAutoTaskDTO dto);

    void deleteTask(Long id);

    void toggleTask(Long id, Integer enabled);

    GeoMonitorRun startTaskRun(Long taskId);

    // ---------- 运行批次与结果 ----------

    PageResult<GeoMonitorRun> listRuns(Long taskId, Integer pageNum, Integer pageSize);

    GeoMonitorRun getRun(Long id);

    PageResult<GeoAutoResultVO> listResults(GeoAutoResultQueryDTO query);

    GeoAutoResultDetailVO getResultDetail(Long id);

    /** 问题×平台矩阵视图（每个格子=区间内该平台最新一次监测） */
    GeoAutoResultMatrixVO resultMatrix(GeoAutoResultQueryDTO query);
}
