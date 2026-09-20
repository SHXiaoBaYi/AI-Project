package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentPlacementArticleQueryDTO;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.GeoContentPlacementArticleListVO;
import com.base.admin.domain.vo.GeoContentPlacementCiteVO;
import com.base.admin.domain.vo.GeoContentPlacementDetailVO;
import com.base.admin.domain.vo.GeoContentPlacementItemVO;
import com.base.admin.domain.vo.GeoContentPlacementListVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoPersistResultVO;
import com.base.admin.domain.vo.GeoTargetQuestionOptionVO;
import com.base.admin.domain.vo.SysTaskFileVO;

import java.io.InputStream;
import java.util.List;

public interface GeoContentPlacementService {

    PageResult<GeoContentPlacementListVO> list(GeoContentPlacementQueryDTO query);

    /** 已发布文章：投放明细联查主表 */
    PageResult<GeoContentPlacementArticleListVO> listArticles(GeoContentPlacementArticleQueryDTO query);

    /** 按话题加载目标问题（新建文章级联） */
    List<GeoTargetQuestionOptionVO> listTargetQuestions(Long topicId);

    GeoContentPlacementDetailVO getDetail(Long id);

    List<GeoContentPlacementItemVO> listItems(Long placementId);

    List<GeoContentPlacementCiteVO> listCites(Long placementId, Long itemId);

    /** 关联任务完成证明/业务附件 */
    List<SysTaskFileVO> listProofFiles(Long placementId);

    Long create(GeoContentPlacementDTO dto);

    void update(GeoContentPlacementDTO dto);

    void delete(Long id);

    void deleteBatch(List<Long> ids);

    /** 基于指定内容投放，生成相似目标问题（可指定 AI 厂商） */
    List<GeoContentPlacementListVO> generateSimilar(Long placementId, String provider);

    /** 批量生成相似目标问题 */
    List<GeoContentPlacementListVO> generateSimilarBatch(List<Long> placementIds, String provider);

    /** 生成相似问题可用的 AI 厂商列表 */
    List<GeoAiProviderOptionVO> listAiProviders();


    Long createItem(GeoContentPlacementItemDTO dto);

    void updateItem(GeoContentPlacementItemDTO dto);

    void deleteItem(Long itemId);

    void deleteItems(List<Long> itemIds);

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

    /** 固化已结束的内容投放周报 */
    GeoPersistResultVO autoPersistCompletedWeekly(int lookbackWeeks);

    /** 固化已结束的内容投放月报 */
    GeoPersistResultVO autoPersistCompletedMonthly(int lookbackMonths);

    /** 按日期区间固化内容投放周/月快照（历史演示区间用） */
    GeoPersistResultVO persistContentBoardRange(java.time.LocalDate start, java.time.LocalDate end);
}
