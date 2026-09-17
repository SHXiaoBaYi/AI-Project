package com.base.admin.service;

import com.base.admin.domain.dto.BoardChartDrillQueryDTO;
import com.base.admin.domain.vo.BoardChartDrillVO;

public interface BoardChartDrillService {

    BoardChartDrillVO drill(BoardChartDrillQueryDTO query);
}
