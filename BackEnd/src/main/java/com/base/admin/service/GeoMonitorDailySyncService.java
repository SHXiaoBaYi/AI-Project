package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.GeoMonitorQuestion;
import com.base.admin.domain.entity.GeoMonitorResult;
import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.domain.entity.GeoMonitorTask;
import com.base.admin.mapper.GeoMonitorDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 自动监测结果回写 geo_monitor_daily（全站看板统一数据源）。
 * 口径：
 * - 回写键 = (inspect_date, platform, keyword, term_type)，term_type 由问题频率映射（每日→日巡查 / 每周→周巡查）；
 * - 同键已有人工记录且任务未开覆盖 → 跳过（人工优先），result 标记 SKIPPED_MANUAL；
 * - 已被周/月/年统计锁定（board_locked=1）→ 跳过，保护历史固化数据；
 * - data_source 标记来源（人工/浏览器），自动结果可覆盖自动结果，人工记录默认优先；
 * - 浏览器通道的整页截图与官方分享链接一并回写（screenshot_url / third_party_url），日监测页可直接取证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoMonitorDailySyncService {

    private static final String SOURCE_MANUAL = "人工";
    private static final String SOURCE_BROWSER = "浏览器";
    /** 自动来源（含历史 API 通道数据），同来源可覆盖，人工记录默认优先 */
    private static final java.util.Set<String> AUTO_SOURCES = java.util.Set.of(SOURCE_BROWSER, "API");
    private static final String TERM_DAILY = "日巡查";
    private static final String TERM_WEEKLY = "周巡查";

    private final GeoMonitorDailyMapper dailyMapper;

    /** 回写结果到日监测；返回 daily 行ID。调用方据返回的 skipManual 标记 result 状态。 */
    public SyncOutcome sync(GeoMonitorRun run, GeoMonitorTask task, GeoMonitorQuestion question, GeoMonitorResult result) {
        String termType = GeoMonitorTask.FREQ_WEEKLY.equals(question.getFreq()) ? TERM_WEEKLY : TERM_DAILY;
        GeoMonitorDaily existing = dailyMapper.selectOne(new LambdaQueryWrapper<GeoMonitorDaily>()
                .eq(GeoMonitorDaily::getInspectDate, run.getRunDate())
                .eq(GeoMonitorDaily::getPlatform, result.getPlatform())
                .eq(GeoMonitorDaily::getKeyword, question.getQuestionText())
                .eq(GeoMonitorDaily::getTermType, termType)
                .last("LIMIT 1"));

        if (existing == null) {
            GeoMonitorDaily daily = new GeoMonitorDaily();
            daily.setInspectDate(run.getRunDate());
            daily.setTermType(termType);
            daily.setPlatform(result.getPlatform());
            daily.setKeyword(question.getQuestionText());
            daily.setTopicId(question.getTopicId());
            daily.setOwnerUserId(question.getOwnerUserId() != null ? question.getOwnerUserId() : 0L);
            daily.setOwnerName(question.getOwnerName() == null ? "" : question.getOwnerName());
            applyJudgeFields(daily, result);
            daily.setDataSource(SOURCE_BROWSER);
            dailyMapper.insert(daily);
            return new SyncOutcome(daily.getId(), false);
        }

        boolean manualRecord = !AUTO_SOURCES.contains(StringUtils.hasText(existing.getDataSource())
                ? existing.getDataSource() : SOURCE_MANUAL);
        if (existing.getBoardLocked() != null && existing.getBoardLocked() == 1) {
            log.info("日监测已锁定，跳过回写：dailyId={} {} {} {}", existing.getId(),
                    run.getRunDate(), result.getPlatform(), question.getQuestionText());
            return new SyncOutcome(existing.getId(), true);
        }
        if (manualRecord && (task.getOverwriteManual() == null || task.getOverwriteManual() == 0)) {
            return new SyncOutcome(existing.getId(), true);
        }

        GeoMonitorDaily patch = new GeoMonitorDaily();
        patch.setId(existing.getId());
        applyJudgeFields(patch, result);
        patch.setDataSource(SOURCE_BROWSER);
        dailyMapper.updateById(patch);
        return new SyncOutcome(existing.getId(), false);
    }

    private void applyJudgeFields(GeoMonitorDaily daily, GeoMonitorResult result) {
        daily.setMentioned(result.getMentioned());
        daily.setRankNo(result.getRankNo());
        daily.setRecommendStatus(result.getRecommendStatus());
        daily.setNegativeContent(result.getNegativeContent());
        daily.setCompetitors(result.getCompetitors());
        // 有取证才回写，避免用空串覆盖人工已录的截图/链接
        if (StringUtils.hasText(result.getScreenshotUrl())) {
            daily.setScreenshotUrl(result.getScreenshotUrl());
        }
        if (StringUtils.hasText(result.getProbeUrl())) {
            daily.setThirdPartyUrl(result.getProbeUrl());
        }
    }

    /** 回写结果：dailyId + 是否跳过（人工优先或已锁定） */
    public record SyncOutcome(Long dailyId, boolean skipped) {
    }
}
