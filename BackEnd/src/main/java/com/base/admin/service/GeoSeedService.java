package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.domain.dto.GeoYearTargetDTO;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.GeoYearTarget;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.mapper.GeoYearTargetMapper;
import com.base.admin.util.ExcelCellUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
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

        int targetCount = seedYearTargets();
        return "日监测 新增 %d / 更新 %d / 失败 %d；全年目标 %d 条".formatted(
                daily.getInsertCount(), daily.getUpdateCount(), daily.getFailureCount(), targetCount);
    }

    private int seedYearTargets() {
        int count = 0;
        try (InputStream in = openSeed("db/seed/geo-yearly.xlsx", "全年目标达成情况.xlsx");
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            String lastPeriod = "";
            for (int r = 4; r <= sheet.getLastRowNum(); r++) {
                String period = ExcelCellUtils.str(sheet, r, 3);
                String scene = ExcelCellUtils.str(sheet, r, 4);
                String targetText = ExcelCellUtils.str(sheet, r, 5);
                if (StringUtils.hasText(period)) {
                    lastPeriod = period.replace("\n", "").trim();
                }
                if (!StringUtils.hasText(scene) || !StringUtils.hasText(targetText)
                        || scene.contains("目标场景") || scene.contains("时间")) {
                    continue;
                }
                GeoTopic topic = topicService.getOrCreate(scene.replace("\n", "").trim(), null);
                BigDecimal rate = parsePercent(targetText);
                LocalDate[] range = periodRange(lastPeriod);
                GeoYearTarget existing = targetMapper.selectOne(new LambdaQueryWrapper<GeoYearTarget>()
                        .eq(GeoYearTarget::getPeriodLabel, lastPeriod)
                        .eq(GeoYearTarget::getTopicId, topic.getId()));
                if (existing == null) {
                    GeoYearTargetDTO dto = new GeoYearTargetDTO();
                    dto.setPeriodLabel(lastPeriod);
                    dto.setPeriodStart(range[0]);
                    dto.setPeriodEnd(range[1]);
                    dto.setTopicId(topic.getId());
                    dto.setTargetRate(rate);
                    dto.setSortOrder(count);
                    monitorService.saveTarget(dto);
                } else {
                    existing.setPeriodStart(range[0]);
                    existing.setPeriodEnd(range[1]);
                    existing.setTargetRate(rate);
                    existing.setSortOrder(count);
                    targetMapper.updateById(existing);
                }
                count++;
            }
        } catch (Exception e) {
            throw new RuntimeException("导入全年目标失败: " + e.getMessage(), e);
        }
        return count;
    }

    private static BigDecimal parsePercent(String raw) {
        String num = raw.replace("%", "").trim();
        try {
            return new BigDecimal(num);
        } catch (Exception e) {
            return new BigDecimal("80");
        }
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
}
