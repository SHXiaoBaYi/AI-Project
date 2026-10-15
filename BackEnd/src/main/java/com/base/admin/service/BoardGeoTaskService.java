package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.BoardGeoTaskAggQueryDTO;
import com.base.admin.domain.dto.BoardGeoTaskDrillQueryDTO;
import com.base.admin.domain.dto.BoardGeoTaskQueryDTO;
import com.base.admin.domain.vo.BoardGeoTaskAggRowVO;
import com.base.admin.domain.vo.BoardGeoTaskRowVO;
import com.base.admin.domain.vo.BoardGeoTaskSummaryVO;

import java.util.List;

public interface BoardGeoTaskService {

    BoardGeoTaskSummaryVO summary(BoardGeoTaskQueryDTO query);

    List<BoardGeoTaskAggRowVO> aggregate(BoardGeoTaskAggQueryDTO query);

    PageResult<BoardGeoTaskRowVO> drill(BoardGeoTaskDrillQueryDTO query);
}
