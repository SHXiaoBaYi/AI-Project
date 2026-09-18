package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.domain.dto.GeoYearTargetDTO;
import com.base.admin.domain.entity.GeoBoardPeriodStat;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.GeoYearTarget;
import com.base.admin.domain.enums.GeoPeriodType;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.mapper.GeoBoardPeriodStatMapper;
import com.base.admin.mapper.GeoYearTargetMapper;
import com.base.admin.util.ExcelCellUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoSeedService {

    private final GeoPlatformService platformService;
    private final GeoTopicService topicService;
    private final GeoMonitorService monitorService;
    private final GeoYearTargetMapper targetMapper;
    private final GeoBoardPeriodStatMapper periodStatMapper;

    @Transactional
    public String seed() {
        platformService.getOrCreate("豆包");
        platformService.getOrCreate("DS");
        platformService.getOrCreate("小红书");

        GeoImportResultVO daily = new GeoImportResultVO();
        try (InputStream in = openSeed("db/seed/geo-daily.xlsx", "GEO优化监测样例.xlsx")) {
            daily = monitorService.importDaily(in);
        } catch (Exception e) {
            throw new RuntimeException("导入日监测样例失败: " + e.getMessage(), e);
        }

        String yearly = seedYearlySample();
        return "日监测 新增 %d / 更新 %d / 失败 %d；%s".formatted(
                daily.getInsertCount(), daily.getUpdateCount(), daily.getFailureCount(), yearly);
    }

    /**
     * 按《全年目标达成情况.xlsx》洗入默认目标 + 各 AI 平台实际达成/达成率快照。
     * 平台列动态识别，不硬编码豆包/DS。
     */
    @Transactional
    public String seedYearlySample() {
        int targetCount = 0;
        int snapshotCount = 0;
        try (InputStream in = openSeed("db/seed/geo-yearly.xlsx", "全年目标达成情况.xlsx");
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            PlatformColumns columns = parsePlatformColumns(sheet);
            for (String platform : columns.actualCols().keySet()) {
                platformService.getOrCreate(platform);
            }

            String lastPeriod = "";
            int sort = 0;
            for (int r = 4; r <= sheet.getLastRowNum(); r++) {
                String period = ExcelCellUtils.str(sheet, r, 3).replace("\n", "").trim();
                String scene = ExcelCellUtils.str(sheet, r, 4).replace("\n", " ").trim();
                if (StringUtils.hasText(period)) {
                    lastPeriod = period;
                }
                if (!StringUtils.hasText(lastPeriod) || !StringUtils.hasText(scene)
                        || scene.contains("目标场景") || scene.contains("时间")
                        || scene.contains("全年目标达成率")) {
                    continue;
                }
                BigDecimal targetRate = toPercent(ExcelCellUtils.decimal(sheet, r, 5), new BigDecimal("80"));
                GeoTopic topic = topicService.getOrCreate(scene, null);
                LocalDate[] range = periodRange(lastPeriod);
                upsertTarget(lastPeriod, range, topic.getId(), targetRate, sort++);
                targetCount++;

                for (Map.Entry<String, Integer> entry : columns.actualCols().entrySet()) {
                    String platform = entry.getKey();
                    BigDecimal actual = toPercent(ExcelCellUtils.decimal(sheet, r, entry.getValue()), null);
                    if (actual == null) {
                        continue;
                    }
                    Integer achieveCol = columns.achieveCols().get(platform);
                    BigDecimal achieve = achieveCol == null
                            ? null
                            : toPercent(ExcelCellUtils.decimal(sheet, r, achieveCol), null);
                    if (achieve == null && targetRate.signum() != 0) {
                        achieve = actual.multiply(BigDecimal.valueOf(100))
                                .divide(targetRate, 2, RoundingMode.HALF_UP);
                    }
                    upsertYearSnapshot(lastPeriod, range, topic, platform, targetRate, actual, achieve);
                    snapshotCount++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("导入全年目标失败: " + e.getMessage(), e);
        }
        return "全年目标 %d 条 / 平台达成快照 %d 条".formatted(targetCount, snapshotCount);
    }

    private void upsertTarget(String periodLabel, LocalDate[] range, Long topicId, BigDecimal rate, int sort) {
        GeoYearTarget existing = targetMapper.selectOne(new LambdaQueryWrapper<GeoYearTarget>()
                .eq(GeoYearTarget::getPeriodLabel, periodLabel)
                .eq(GeoYearTarget::getTopicId, topicId)
                .last("LIMIT 1"));
        if (existing == null) {
            GeoYearTargetDTO dto = new GeoYearTargetDTO();
            dto.setPeriodLabel(periodLabel);
            dto.setPeriodStart(range[0]);
            dto.setPeriodEnd(range[1]);
            dto.setTopicId(topicId);
            dto.setTargetRate(rate);
            dto.setSortOrder(sort);
            monitorService.saveTarget(dto);
            return;
        }
        existing.setPeriodStart(range[0]);
        existing.setPeriodEnd(range[1]);
        existing.setTargetRate(rate);
        existing.setSortOrder(sort);
        targetMapper.updateById(existing);
    }

    private void upsertYearSnapshot(String periodLabel, LocalDate[] range, GeoTopic topic, String platform,
                                    BigDecimal targetRate, BigDecimal actualRate, BigDecimal achieveRate) {
        String periodKey = periodLabel + "@" + range[0] + "~" + range[1];
        GeoBoardPeriodStat existing = periodStatMapper.selectOne(new LambdaQueryWrapper<GeoBoardPeriodStat>()
                .eq(GeoBoardPeriodStat::getPeriodType, GeoPeriodType.YEAR.getCode())
                .eq(GeoBoardPeriodStat::getPeriodKey, periodKey)
                .eq(GeoBoardPeriodStat::getTopicId, topic.getId())
                .eq(GeoBoardPeriodStat::getPlatform, platform)
                .last("LIMIT 1"));
        GeoBoardPeriodStat entity = existing != null ? existing : new GeoBoardPeriodStat();
        entity.setPeriodType(GeoPeriodType.YEAR.getCode());
        entity.setPeriodKey(periodKey);
        entity.setPeriodLabel(periodLabel);
        entity.setPeriodStart(range[0]);
        entity.setPeriodEnd(range[1]);
        entity.setTopicId(topic.getId());
        entity.setTopicName(topic.getTopicName());
        entity.setPlatform(platform);
        entity.setSampleCount(existing == null || existing.getSampleCount() == null ? 0 : existing.getSampleCount());
        entity.setMentionRate(actualRate);
        entity.setFirstMentionRate(BigDecimal.ZERO);
        entity.setRecommendCount(0);
        entity.setCompetitorTop("");
        entity.setCitePlatformTop("");
        entity.setTargetRate(targetRate);
        entity.setActualRate(actualRate);
        entity.setAchieveRate(achieveRate);
        entity.setLockedAt(LocalDateTime.now());
        if (existing == null) {
            periodStatMapper.insert(entity);
        } else {
            periodStatMapper.updateById(entity);
        }
    }

    /**
     * 表头：第 3 行平台名，第 4 行「实际达成 / 达成率」。
     * 示例列：豆包实际、DS实际、豆包达成率、DS达成率；系统可有更多 AI 平台列。
     */
    private static PlatformColumns parsePlatformColumns(Sheet sheet) {
        Row nameRow = sheet.getRow(2);
        Row metricRow = sheet.getRow(3);
        Map<String, Integer> actual = new LinkedHashMap<>();
        Map<String, Integer> achieve = new LinkedHashMap<>();
        if (nameRow == null || metricRow == null) {
            return new PlatformColumns(actual, achieve);
        }
        int last = Math.max(nameRow.getLastCellNum(), metricRow.getLastCellNum());
        for (int c = 6; c < last; c++) {
            String platform = ExcelCellUtils.str(sheet, 2, c).trim();
            String metric = ExcelCellUtils.str(sheet, 3, c).trim();
            if (!StringUtils.hasText(platform) || !StringUtils.hasText(metric)) {
                continue;
            }
            if (metric.contains("实际")) {
                actual.putIfAbsent(platform, c);
            } else if (metric.contains("达成")) {
                achieve.putIfAbsent(platform, c);
            }
        }
        return new PlatformColumns(actual, achieve);
    }

    private static BigDecimal toPercent(BigDecimal raw, BigDecimal fallback) {
        if (raw == null) {
            return fallback;
        }
        // Excel 常把 80% 存成 0.8
        if (raw.abs().compareTo(BigDecimal.valueOf(2)) < 0) {
            return raw.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    private static InputStream openSeed(String classpath, String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpath);
        if (resource.exists()) {
            return resource.getInputStream();
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("GEO").resolve(fileName),
                cwd.resolve("../GEO").resolve(fileName).normalize(),
                cwd.resolve("../../GEO").resolve(fileName).normalize(),
                cwd.getParent() == null ? cwd : cwd.getParent().resolve("GEO").resolve(fileName)
        };
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                return Files.newInputStream(path);
            }
        }
        throw new RuntimeException("找不到种子文件: " + fileName);
    }

    private static LocalDate[] periodRange(String label) {
        int year = LocalDate.now().getYear();
        if (!StringUtils.hasText(label) || label.contains("全年")) {
            return new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
        }
        List<Integer> months = new ArrayList<>();
        Matcher matcher = Pattern.compile("(\\d{1,2})(?=月|-|$)").matcher(label);
        while (matcher.find()) {
            int month = Integer.parseInt(matcher.group(1));
            if (month >= 1 && month <= 12) {
                months.add(month);
            }
        }
        if (months.isEmpty()) {
            return new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
        }
        int startMonth = months.getFirst();
        int endMonth = months.getLast();
        int startDay = label.contains("中") && !label.contains("初") ? 15 : 1;
        int endDay = label.contains("初") ? 7 : YearMonth.of(year, endMonth).lengthOfMonth();
        return new LocalDate[]{
                LocalDate.of(year, startMonth, startDay),
                LocalDate.of(year, endMonth, endDay)
        };
    }

    private record PlatformColumns(Map<String, Integer> actualCols, Map<String, Integer> achieveCols) {}
}
