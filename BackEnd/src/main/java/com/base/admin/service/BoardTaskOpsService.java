package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.BoardTaskOpsDrillQueryDTO;
import com.base.admin.domain.dto.BoardTaskOpsQueryDTO;
import com.base.admin.domain.vo.BoardTaskOpsPersonRateVO;
import com.base.admin.domain.vo.BoardTaskOpsRowVO;
import com.base.admin.domain.vo.BoardTaskOpsSummaryVO;

public interface BoardTaskOpsService {

    BoardTaskOpsSummaryVO summary(BoardTaskOpsQueryDTO query);

    /** 完成率按员工汇总（管理者下钻第一层） */
    PageResult<BoardTaskOpsPersonRateVO> personRate(BoardTaskOpsDrillQueryDTO query);

    PageResult<BoardTaskOpsRowVO> drill(BoardTaskOpsDrillQueryDTO query);
}
