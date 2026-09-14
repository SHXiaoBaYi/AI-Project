package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoBoardQueryDTO;
import com.base.admin.domain.dto.GeoDailyBatchDTO;
import com.base.admin.domain.dto.GeoDailyDTO;
import com.base.admin.domain.dto.GeoDailyQueryDTO;
import com.base.admin.domain.dto.GeoYearTargetDTO;
import com.base.admin.domain.entity.GeoYearTarget;
import com.base.admin.domain.vo.GeoDailyBoardVO;
import com.base.admin.domain.vo.GeoDailyGroupVO;
import com.base.admin.domain.vo.GeoDailyVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoLatestDateVO;
import com.base.admin.domain.vo.GeoWeeklyBoardVO;
import com.base.admin.domain.vo.GeoYearlyBoardVO;
import com.base.admin.service.FileStorageService;
import com.base.admin.service.GeoMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "GEO监测", description = "日监测导入、看板实时聚合")
@RestController
@RequestMapping("/geo")
@RequiredArgsConstructor
public class GeoMonitorController {

    private final GeoMonitorService monitorService;
    private final FileStorageService fileStorageService;

    @Operation(summary = "日监测分页")
    @PostMapping("/daily/list")
    @RequiresPermission("geo:daily:list")
    public Result<PageResult<GeoDailyVO>> listDaily(@RequestBody GeoDailyQueryDTO query) {
        return Result.ok(monitorService.listDaily(query));
    }

    @Operation(summary = "日监测详情")
    @GetMapping("/daily/{id:\\d+}")
    @RequiresPermission("geo:daily:list")
    public Result<GeoDailyVO> getDaily(@PathVariable Long id) {
        return Result.ok(monitorService.getDaily(id));
    }

    @Operation(summary = "新增日监测")
    @PostMapping("/daily")
    @RequiresPermission("geo:daily:add")
    @Log(title = "GEO日监测", businessType = 1)
    public Result<Void> createDaily(@Valid @RequestBody GeoDailyDTO dto) {
        monitorService.createDaily(dto);
        return Result.ok();
    }

    @Operation(summary = "修改日监测")
    @PutMapping("/daily")
    @RequiresPermission("geo:daily:edit")
    @Log(title = "GEO日监测", businessType = 2)
    public Result<Void> updateDaily(@Valid @RequestBody GeoDailyDTO dto) {
        monitorService.updateDaily(dto);
        return Result.ok();
    }

    @Operation(summary = "删除日监测（软删除，不计入看板）")
    @DeleteMapping("/daily/{id}")
    @RequiresPermission("geo:daily:delete")
    @Log(title = "GEO日监测", businessType = 3)
    public Result<Void> deleteDaily(@PathVariable Long id) {
        monitorService.deleteDaily(id);
        return Result.ok();
    }

    @Operation(summary = "批量保存日监测（同一天+关键字，多平台）")
    @PostMapping("/daily/batch")
    @RequiresPermission("geo:daily:add")
    @Log(title = "GEO日监测批量", businessType = 1)
    public Result<Void> createDailyBatch(@Valid @RequestBody GeoDailyBatchDTO dto) {
        monitorService.saveDailyBatch(dto);
        return Result.ok();
    }

    @Operation(summary = "批量更新日监测（同一天+关键字，多平台）")
    @PutMapping("/daily/batch")
    @RequiresPermission("geo:daily:edit")
    @Log(title = "GEO日监测批量", businessType = 2)
    public Result<Void> updateDailyBatch(@Valid @RequestBody GeoDailyBatchDTO dto) {
        monitorService.saveDailyBatch(dto);
        return Result.ok();
    }

    @Operation(summary = "按日期+关键字加载多平台记录")
    @GetMapping("/daily/group")
    @RequiresPermission("geo:daily:list")
    public Result<GeoDailyGroupVO> dailyGroup(@RequestParam LocalDate inspectDate, @RequestParam String keyword) {
        return Result.ok(monitorService.getDailyGroup(inspectDate, keyword));
    }

    @Operation(summary = "最近一次更新对应的巡查日期")
    @GetMapping("/daily/latest-date")
    @RequiresPermission("geo:daily:list")
    public Result<GeoLatestDateVO> latestDate() {
        return Result.ok(monitorService.latestInspectDate());
    }

    @Operation(summary = "导入日监测宽表Excel")
    @PostMapping("/daily/import")
    @RequiresPermission("geo:daily:import")
    @Log(title = "GEO日监测导入", businessType = 1)
    public Result<GeoImportResultVO> importDaily(@RequestParam("file") MultipartFile file) {
        return Result.ok(monitorService.importDaily(file));
    }

    @Operation(summary = "上传监测截图")
    @PostMapping("/daily/screenshot")
    @RequiresPermission("geo:daily:list")
    public Result<Map<String, String>> uploadScreenshot(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.saveGeoImage(file);
        return Result.ok(Map.of("url", url));
    }

    @Operation(summary = "平台下拉")
    @GetMapping("/daily/platforms")
    @RequiresPermission("geo:daily:list")
    public Result<List<String>> platforms() {
        return Result.ok(monitorService.listPlatforms());
    }

    @Operation(summary = "周报看板（实时聚合，默认近5周）")
    @PostMapping("/weekly/board")
    @RequiresPermission("geo:weekly:list")
    public Result<GeoWeeklyBoardVO> weekly(@RequestBody(required = false) GeoBoardQueryDTO query) {
        return Result.ok(monitorService.weeklyBoard(query == null ? new GeoBoardQueryDTO() : query));
    }

    @Operation(summary = "日报看板（实时聚合，默认近14天）")
    @PostMapping("/day/board")
    @RequiresPermission("geo:day:list")
    public Result<GeoDailyBoardVO> dailyBoard(@RequestBody(required = false) GeoBoardQueryDTO query) {
        return Result.ok(monitorService.dailyBoard(query == null ? new GeoBoardQueryDTO() : query));
    }

    @Operation(summary = "全年目标看板（实时聚合，默认近两年）")
    @PostMapping("/yearly/board")
    @RequiresPermission("geo:yearly:list")
    public Result<GeoYearlyBoardVO> yearly(@RequestBody(required = false) GeoBoardQueryDTO query) {
        return Result.ok(monitorService.yearlyBoard(query == null ? new GeoBoardQueryDTO() : query));
    }

    @Operation(summary = "全年目标配置列表")
    @GetMapping("/yearly/targets")
    @RequiresPermission("geo:yearly:list")
    public Result<List<GeoYearTarget>> targets() {
        return Result.ok(monitorService.listTargets());
    }

    @Operation(summary = "保存全年目标配置")
    @PostMapping("/yearly/targets")
    @RequiresPermission("geo:yearly:target")
    @Log(title = "GEO全年目标", businessType = 1)
    public Result<Void> saveTarget(@Valid @RequestBody GeoYearTargetDTO dto) {
        monitorService.saveTarget(dto);
        return Result.ok();
    }

    @Operation(summary = "删除全年目标配置")
    @DeleteMapping("/yearly/targets/{id}")
    @RequiresPermission("geo:yearly:target")
    @Log(title = "GEO全年目标", businessType = 3)
    public Result<Void> deleteTarget(@PathVariable Long id) {
        monitorService.deleteTarget(id);
        return Result.ok();
    }
}
