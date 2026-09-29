package com.base.admin.controller;

import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.EcomAclSaveDTO;
import com.base.admin.domain.dto.EcomArchiveWeekDTO;
import com.base.admin.domain.dto.EcomTargetSaveDTO;
import com.base.admin.domain.enums.EcomReportType;
import com.base.admin.domain.vo.EcomImportJobVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.service.ecom.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Tag(name = "电商运营", description = "暗门模块：localhost/bella/ACL；数据仅导入")
@RestController
@RequestMapping("/ecom")
@RequiredArgsConstructor
public class EcomController {

    private final EcomAccessService accessService;
    private final EcomImportJobService importJobService;
    private final EcomArchiveService archiveService;
    private final EcomQueryService queryService;
    private final EcomBoardService boardService;
    private final EcomAclService aclService;
    private final EcomTargetService targetService;
    private final JdbcTemplate jdbc;

    @Operation(summary = "是否可进入电商运营模块")
    @GetMapping("/access")
    public Result<Map<String, Object>> access() {
        boolean allowed = accessService.currentUserCanAccess();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("allowed", allowed);
        body.put("canReturnMain", allowed && accessService.canReturnMain());
        body.put("canManageAcl", allowed && accessService.canManageAcl());
        if (allowed) {
            body.put("scope", accessService.scopeAsMap());
        }
        return Result.ok(body);
    }

    @Operation(summary = "源数据类型列表（受 ACL 平台切片约束）")
    @GetMapping("/report-types")
    public Result<List<Map<String, String>>> reportTypes() {
        accessService.assertAccess();
        EcomAccessService.Scope scope = accessService.currentScope();
        List<Map<String, String>> list = new ArrayList<>();
        for (EcomReportType t : EcomReportType.values()) {
            if (!scope.fullAccess && !scope.allPlatforms
                    && !scope.platforms.contains(t.getPlatform())) {
                continue;
            }
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", t.getCode());
            m.put("label", t.getLabel());
            m.put("grain", t.getGrain());
            m.put("platform", t.getPlatform());
            list.add(m);
        }
        return Result.ok(list);
    }

    @Operation(summary = "店铺列表（只读，受 ACL 切片约束）")
    @GetMapping("/shops")
    public Result<List<Map<String, Object>>> shops(@RequestParam(required = false) String platform) {
        accessService.assertAccess();
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.platform, s.shop_code, s.shop_name, s.create_time
                FROM ecom_shop s WHERE s.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(platform)) {
            accessService.assertPlatformAccess(platform.trim());
            sql.append(" AND s.platform = ? ");
            args.add(platform.trim());
        }
        accessService.appendScopeFilter(sql, args, "s", "id");
        sql.append(" ORDER BY s.platform, s.id ");
        return Result.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @Operation(summary = "报表事实分页查询")
    @GetMapping("/facts")
    public Result<PageResult<Map<String, Object>>> facts(
            @RequestParam String reportType,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(queryService.pageFacts(reportType, shopId, platform, startDate, endDate, pageNum, pageSize));
    }

    @Operation(summary = "看板列表（含目标达成率）")
    @GetMapping("/board")
    public Result<List<Map<String, Object>>> board(
            @RequestParam(defaultValue = "week") String periodType,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(boardService.listBoard(periodType, platform, shopId, startDate, endDate));
    }

    @Operation(summary = "跨平台对比（同一周期）")
    @GetMapping("/board/compare")
    public Result<List<Map<String, Object>>> compare(
            @RequestParam(defaultValue = "week") String periodType,
            @RequestParam String periodKey) {
        return Result.ok(boardService.compareByPlatform(periodType, periodKey));
    }

    @Operation(summary = "手工落库周/月看板")
    @PostMapping("/board/persist")
    public Result<Map<String, Object>> persistBoard(@RequestBody Map<String, Object> body) {
        accessService.assertAccess();
        String periodType = String.valueOf(body.getOrDefault("periodType", "week"));
        Long shopId = body.get("shopId") == null ? null : Long.valueOf(String.valueOf(body.get("shopId")));
        LocalDate start = body.get("startDate") == null ? LocalDate.now().minusWeeks(4)
                : LocalDate.parse(String.valueOf(body.get("startDate")));
        LocalDate end = body.get("endDate") == null ? LocalDate.now()
                : LocalDate.parse(String.valueOf(body.get("endDate")));
        return Result.ok(boardService.persistRange(periodType, start, end, shopId));
    }

    @Operation(summary = "最近导入批次")
    @GetMapping("/import/batches")
    public Result<List<Map<String, Object>>> batches(@RequestParam(defaultValue = "20") int limit) {
        accessService.assertAccess();
        int safe = Math.min(Math.max(limit, 1), 100);
        return Result.ok(jdbc.queryForList("""
                SELECT b.id, b.platform, b.shop_id, s.shop_name, b.report_type, b.file_name,
                       b.import_mode, b.status, b.total_rows, b.success_rows, b.skipped_rows, b.fail_rows,
                       b.message, b.create_by, b.create_time, b.skipped_detail
                FROM ecom_import_batch b
                LEFT JOIN ecom_shop s ON s.id = b.shop_id
                WHERE b.is_active = 1
                ORDER BY b.id DESC
                LIMIT ?
                """, safe));
    }

    @Operation(summary = "开始导入")
    @PostMapping("/import/start")
    public Result<EcomImportJobVO> startImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean ignoreLocked,
            @RequestParam(defaultValue = "false") boolean forceUpdate) throws IOException {
        accessService.assertAccess();
        String name = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        return Result.ok(importJobService.start(file.getBytes(), name, ignoreLocked, forceUpdate));
    }

    @Operation(summary = "查询导入进度")
    @GetMapping("/import/progress/{jobId}")
    public Result<EcomImportJobVO> importProgress(@PathVariable String jobId) {
        accessService.assertAccess();
        return Result.ok(importJobService.get(jobId));
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/import/template/{reportType}")
    public void template(@PathVariable String reportType, HttpServletResponse response) throws IOException {
        accessService.assertAccess();
        EcomReportType type = EcomReportType.fromCode(reportType);
        if (type == null) {
            throw new BusinessException("未知源数据类型");
        }
        accessService.assertPlatformAccess(type.getPlatform());
        List<String> headers = templateHeaders(type);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode(type.getLabel() + "-导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("template");
            Row row = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                row.createCell(i).setCellValue(headers.get(i));
            }
            wb.write(response.getOutputStream());
        }
    }

    @Operation(summary = "手工归档本周")
    @PostMapping("/archive/week")
    public Result<Map<String, Object>> archiveWeek(@RequestBody(required = false) EcomArchiveWeekDTO dto) {
        Map<String, Object> archived = archiveService.archiveWeek(dto == null ? new EcomArchiveWeekDTO() : dto);
        LocalDate start = LocalDate.parse(String.valueOf(archived.get("periodStart")));
        LocalDate end = LocalDate.parse(String.valueOf(archived.get("periodEnd")));
        Long shopId = dto != null ? dto.getShopId() : null;
        Map<String, Object> board = boardService.persistRange("week", start, end, shopId);
        archived.put("boardSnapshotCount", board.get("snapshotCount"));
        return Result.ok(archived);
    }

    @Operation(summary = "已归档周期列表")
    @GetMapping("/archive/periods")
    public Result<List<Map<String, Object>>> periods(@RequestParam(required = false) Long shopId) {
        accessService.assertAccess();
        if (shopId != null) {
            return Result.ok(jdbc.queryForList("""
                    SELECT * FROM ecom_stat_period WHERE is_active = 1 AND shop_id = ?
                    ORDER BY period_start DESC
                    """, shopId));
        }
        return Result.ok(jdbc.queryForList("""
                SELECT p.*, s.shop_name FROM ecom_stat_period p
                LEFT JOIN ecom_shop s ON s.id = p.shop_id
                WHERE p.is_active = 1
                ORDER BY p.period_start DESC LIMIT 100
                """));
    }

    @Operation(summary = "ACL 列表（Bella）")
    @GetMapping("/acl")
    public Result<List<Map<String, Object>>> aclList() {
        return Result.ok(aclService.list());
    }

    @Operation(summary = "ACL 候选用户")
    @GetMapping("/acl/users")
    public Result<List<Map<String, Object>>> aclUsers(@RequestParam(required = false) String keyword) {
        return Result.ok(aclService.candidateUsers(keyword));
    }

    @Operation(summary = "保存 ACL")
    @PutMapping("/acl")
    public Result<Void> saveAcl(@Valid @RequestBody EcomAclSaveDTO dto) {
        aclService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "移除 ACL")
    @DeleteMapping("/acl/{userId}")
    public Result<Void> removeAcl(@PathVariable Long userId) {
        aclService.remove(userId);
        return Result.ok();
    }

    @Operation(summary = "目标列表")
    @GetMapping("/targets")
    public Result<List<Map<String, Object>>> targets(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String periodType) {
        return Result.ok(targetService.list(platform, shopId, periodType));
    }

    @Operation(summary = "保存目标")
    @PutMapping("/targets")
    public Result<Void> saveTarget(@Valid @RequestBody EcomTargetSaveDTO dto) {
        targetService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除目标")
    @DeleteMapping("/targets/{id}")
    public Result<Void> removeTarget(@PathVariable Long id) {
        targetService.remove(id);
        return Result.ok();
    }

    private static List<String> templateHeaders(EcomReportType type) {
        return switch (type.getGrain()) {
            case "shop_day" -> List.of("时间", "成交金额", "成交商品件数", "成交客户数", "成交单量",
                    "客单价", "店铺浏览量", "店铺访客数", "加购客户数", "退款金额");
            case "traffic_source" -> List.of("时间", "一级渠道", "二级渠道", "三级渠道", "四级渠道",
                    "引入商详访客数", "引入成交金额", "引入成交客户数", "UV价值", "客单价");
            case "spu_day" -> List.of("时间", "SPU", "SPU名称", "一级类目", "二级类目", "三级类目", "货号",
                    "成交金额", "成交商品件数", "成交单量", "成交客户数", "商品访客数");
            case "ad_plan" -> List.of("计划类型", "商品计划名称", "ID", "状态", "创建时间", "日预算",
                    "出价方式", "花费", "全站投产比", "全站交易额", "全站订单行");
            case "ad_effect" -> List.of("点击时间", "计划ID", "推广计划", "展现数", "点击数", "花费",
                    "总订单行", "总订单金额", "投产比", "转化率(%)");
            default -> List.of("时间", "成交金额");
        };
    }
}
