package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.vo.GeoContentPlacementCiteVO;
import com.base.admin.domain.vo.GeoContentPlacementDetailVO;
import com.base.admin.domain.vo.GeoContentPlacementItemVO;
import com.base.admin.domain.vo.GeoContentPlacementListVO;
import com.base.admin.domain.vo.GeoImportResultVO;

import java.io.InputStream;
import java.util.List;

public interface GeoContentPlacementService {

    PageResult<GeoContentPlacementListVO> list(GeoContentPlacementQueryDTO query);

    GeoContentPlacementDetailVO getDetail(Long id);

    List<GeoContentPlacementItemVO> listItems(Long placementId);

    List<GeoContentPlacementCiteVO> listCites(Long placementId, Long itemId);

    Long create(GeoContentPlacementDTO dto);

    void update(GeoContentPlacementDTO dto);

    void delete(Long id);

    /** 基于指定内容投放，生成 3~5 条相似目标问题（待投放、待分配） */
    List<GeoContentPlacementListVO> generateSimilar(Long placementId);

    Long createItem(GeoContentPlacementItemDTO dto);

    void updateItem(GeoContentPlacementItemDTO dto);

    void deleteItem(Long itemId);

    Long createCite(GeoContentPlacementCiteDTO dto);

    void updateCite(GeoContentPlacementCiteDTO dto);

    void deleteCite(Long citeId);

    GeoImportResultVO importExcel(InputStream in);

    /** 表空时从默认 Excel 刷入样例数据 */
    String seedIfEmpty();

    /** 补齐历史话题关联（名称存在则建档） */
    int backfillTopicRelations();

    /** 按发布详情回填主表投放进度 */
    int backfillPlacementProgress();
}
