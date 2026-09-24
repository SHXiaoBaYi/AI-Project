package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoBoardQueryDTO;
import com.base.admin.domain.dto.GeoDailyBatchDTO;
import com.base.admin.domain.dto.GeoDailyBulkSaveDTO;
import com.base.admin.domain.dto.GeoDailyComboQueryDTO;
import com.base.admin.domain.dto.GeoDailyDTO;
import com.base.admin.domain.dto.GeoDailyQueryDTO;
import com.base.admin.domain.dto.GeoYearTargetDTO;
import com.base.admin.domain.entity.GeoYearTarget;
import com.base.admin.domain.vo.GeoDailyBoardVO;
import com.base.admin.domain.vo.GeoDailyBulkSaveResultVO;
import com.base.admin.domain.vo.GeoDailyComboDetailVO;
import com.base.admin.domain.vo.GeoDailyComboVO;
import com.base.admin.domain.vo.GeoDailyGroupVO;
import com.base.admin.domain.vo.GeoDailyVO;
import com.base.admin.domain.vo.GeoTopicPlatformChartsVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoLatestDateVO;
import com.base.admin.domain.vo.GeoMonthlyBoardVO;
import com.base.admin.domain.vo.GeoOwnerOptionVO;
import com.base.admin.domain.vo.GeoPersistResultVO;
import com.base.admin.domain.vo.GeoWeeklyBoardVO;
import com.base.admin.domain.vo.GeoYearlyBoardVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GeoMonitorService {

    PageResult<GeoDailyVO> listDaily(GeoDailyQueryDTO query);

    /** 话题×关键字×平台 唯一维度分页汇总 */
    PageResult<GeoDailyComboVO> listDailyCombo(GeoDailyComboQueryDTO query);

    /** 组合下的日期明细 + 折线序列 */
    GeoDailyComboDetailVO dailyComboDetail(GeoDailyComboQueryDTO query);

    GeoDailyVO getDaily(Long id);

    void createDaily(GeoDailyDTO dto);

    void updateDaily(GeoDailyDTO dto);

    void deleteDaily(Long id);

    void deleteDailyBatch(List<Long> ids);

    void saveDailyBatch(GeoDailyBatchDTO dto);

    GeoDailyBulkSaveResultVO saveDailyBulk(GeoDailyBulkSaveDTO dto);

    GeoDailyGroupVO getDailyGroup(java.time.LocalDate inspectDate, String keyword);

    GeoLatestDateVO latestInspectDate();

    GeoImportResultVO importDaily(MultipartFile file);

    GeoImportResultVO importDaily(java.io.InputStream inputStream);

    List<String> listPlatforms();

    List<GeoOwnerOptionVO> listOwnerOptions();

    GeoWeeklyBoardVO weeklyBoard(GeoBoardQueryDTO query);

    GeoMonthlyBoardVO monthlyBoard(GeoBoardQueryDTO query);

    GeoDailyBoardVO dailyBoard(GeoBoardQueryDTO query);

    /**
     * 话题×平台分组柱状图：不传 topicId 为话题级；传 topicId 下钻到该话题下的目标问题。
     */
    GeoTopicPlatformChartsVO topicPlatformCharts(GeoBoardQueryDTO query);

    /** 负面/错误内容明细（可点开） */
    List<GeoDailyVO> listNegativeDaily(GeoBoardQueryDTO query);

    GeoYearlyBoardVO yearlyBoard(GeoBoardQueryDTO query);

    GeoPersistResultVO persistWeeklyBoard(GeoBoardQueryDTO query);

    GeoPersistResultVO persistMonthlyBoard(GeoBoardQueryDTO query);

    GeoPersistResultVO persistYearlyBoard(GeoBoardQueryDTO query);

    /** 定时/启动：固化已结束的周报，并标记对应日监测为已统计 */
    GeoPersistResultVO autoPersistCompletedWeekly(int lookbackWeeks);

    /** 定时/启动：固化已结束的月报，并标记对应日监测为已统计 */
    GeoPersistResultVO autoPersistCompletedMonthly(int lookbackMonths);

    /** 定时/启动：固化已结束的年报，并标记对应日监测为已统计 */
    GeoPersistResultVO autoPersistCompletedYearly(int lookbackYears);

    List<GeoYearTarget> listTargets();

    void saveTarget(GeoYearTargetDTO dto);

    void deleteTarget(Long id);
}
