package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.BoardTaskDrillQueryDTO;
import com.base.admin.domain.dto.BoardTaskSummaryQueryDTO;
import com.base.admin.domain.vo.BoardTaskSummaryVO;
import com.base.admin.domain.vo.SysTaskVO;

public interface BoardService {

    BoardTaskSummaryVO taskSummary(BoardTaskSummaryQueryDTO query);

    PageResult<SysTaskVO> taskDrill(BoardTaskDrillQueryDTO query);
}
