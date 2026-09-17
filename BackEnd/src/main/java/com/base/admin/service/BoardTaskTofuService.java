package com.base.admin.service;

import com.base.admin.domain.dto.BoardTaskTofuQueryDTO;
import com.base.admin.domain.vo.BoardTaskTofuChartVO;
import com.base.admin.domain.vo.GeoContentArticleDetailRowVO;

import java.util.List;

public interface BoardTaskTofuService {

    /** 员工收录看板豆腐块（按 chartType + 下钻维度聚合） */
    BoardTaskTofuChartVO chart(BoardTaskTofuQueryDTO query);

    /** 话题发布数量最细层：发布明细抽屉 */
    List<GeoContentArticleDetailRowVO> publishDetail(BoardTaskTofuQueryDTO query);
}
