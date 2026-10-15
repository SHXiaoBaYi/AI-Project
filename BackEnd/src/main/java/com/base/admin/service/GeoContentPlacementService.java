package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentDraftSaveDTO;
import com.base.admin.domain.dto.GeoContentItemDraftSaveDTO;
import com.base.admin.domain.dto.GeoContentOpinionSaveDTO;
import com.base.admin.domain.dto.GeoContentPlatformArticleDTO;
import com.base.admin.domain.dto.GeoContentReviewActionDTO;
import com.base.admin.domain.dto.GeoContentPlacementArticleQueryDTO;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.dto.GeoContentAiGenerateDTO;
import com.base.admin.domain.dto.GeoSimilarQuestionCommitDTO;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.GeoContentAiGenerateVO;
import com.base.admin.domain.vo.GeoContentArticleBundleVO;
import com.base.admin.domain.vo.GeoContentArticleVO;
import com.base.admin.domain.vo.GeoContentDraftVO;
import com.base.admin.domain.vo.GeoSimilarQuestionPreviewVO;
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

    /** 在线正文（Lakex text/lake） */
    GeoContentDraftVO getDraft(Long placementId);

    /** 保存在线正文（自动保存/手动保存） */
    GeoContentDraftVO saveDraft(GeoContentDraftSaveDTO dto);

    /** 一个问题的统一稿 + 各平台文章（含审核意见） */
    GeoContentArticleBundleVO getArticleBundle(Long placementId);

    /** 为问题增加一个内容平台文章，默认沿用统一稿 */
    GeoContentArticleVO addPlatformArticle(GeoContentPlatformArticleDTO dto);

    /** 切换统一稿/独立稿，或保存独立稿 */
    GeoContentArticleVO saveItemDraft(GeoContentItemDraftSaveDTO dto);

    /** 撰写人提交该平台文章内部审核 */
    GeoContentArticleVO submitArticleReview(Long itemId);

    /** 内部审核：通过或退回，退回必须写意见 */
    GeoContentArticleVO reviewArticle(GeoContentReviewActionDTO dto);

    /** 追加内部意见，不改变审核状态 */
    GeoContentArticleVO addArticleOpinion(GeoContentOpinionSaveDTO dto);

    Long create(GeoContentPlacementDTO dto);

    void update(GeoContentPlacementDTO dto);

    void delete(Long id);

    void deleteBatch(List<Long> ids);

    /** 预览相似问题，不落库、不生成任务 */
    List<GeoSimilarQuestionPreviewVO> generateSimilar(Long placementId, String provider, Integer count);

    /** 批量预览相似问题，不落库、不生成任务 */
    List<GeoSimilarQuestionPreviewVO> generateSimilarBatch(List<Long> placementIds, String provider, Integer count);

    /** 把勾选的相似问题落库，并生成对应任务 */
    List<GeoContentPlacementListVO> saveSimilarQuestions(GeoSimilarQuestionCommitDTO dto);

    /** 生成相似问题可用的 AI 厂商列表 */
    List<GeoAiProviderOptionVO> listAiProviders();

    /** 按卖点库 + 话题/目标问题 AI 生成文章草稿并写入统一稿或独立稿 */
    GeoContentAiGenerateVO generateArticle(GeoContentAiGenerateDTO dto);

    Long createItem(GeoContentPlacementItemDTO dto);

    /**
     * 审核通过进入待投放：主表稿件状态=queue、投放进度=待投放，在「投放管理」可见；
     * 不再自动往文章列表插入空平台明细。
     */
    void ensureQueueArticleItems(Long placementId);

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
