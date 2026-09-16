package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentArticleBoardQueryDTO;
import com.base.admin.domain.dto.GeoContentArticleDetailQueryDTO;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.dto.GeoContentPublisherWeekDetailQueryDTO;
import com.base.admin.domain.dto.GeoContentPublisherWeekQueryDTO;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.GeoContentArticleBoardVO;
import com.base.admin.domain.vo.GeoContentArticleDetailRowVO;
import com.base.admin.domain.vo.GeoContentPlacementCiteVO;
import com.base.admin.domain.vo.GeoContentPlacementDetailVO;
import com.base.admin.domain.vo.GeoContentPlacementItemVO;
import com.base.admin.domain.vo.GeoContentPlacementListVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekBoardVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekDetailVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoPersistResultVO;

import java.io.InputStream;
import java.util.List;

public interface GeoContentPlacementService {

    PageResult<GeoContentPlacementListVO> list(GeoContentPlacementQueryDTO query);

    /** 发布人维度周看板（按 ISO 周拆行） */
    GeoContentPublisherWeekBoardVO publisherWeeklyBoard(GeoContentPublisherWeekQueryDTO query);

    /** 发布人周看板指标明细 */
    List<GeoContentPublisherWeekDetailVO> publisherWeeklyDetail(GeoContentPublisherWeekDetailQueryDTO query);

    GeoContentPlacementDetailVO getDetail(Long id);

    List<GeoContentPlacementItemVO> listItems(Long placementId);

    List<GeoContentPlacementCiteVO> listCites(Long placementId, Long itemId);

    Long create(GeoContentPlacementDTO dto);

    void update(GeoContentPlacementDTO dto);

    void delete(Long id);

    /** 基于指定内容投放，生成相似目标问题（可指定 AI 厂商） */
    List<GeoContentPlacementListVO> generateSimilar(Long placementId, String provider);

    /** 生成相似问题可用的 AI 厂商列表 */
    List<GeoAiProviderOptionVO> listAiProviders();


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

    /** 文章发布/收录看板 */
    GeoContentArticleBoardVO articlePublishBoard(GeoContentArticleBoardQueryDTO query);

    /** 文章发布/收录下钻明细 */
    List<GeoContentArticleDetailRowVO> articlePublishDetail(GeoContentArticleDetailQueryDTO query);

    /** 固化已结束的内容投放周报 */
    GeoPersistResultVO autoPersistCompletedWeekly(int lookbackWeeks);

    /** 固化已结束的内容投放月报 */
    GeoPersistResultVO autoPersistCompletedMonthly(int lookbackMonths);
}
