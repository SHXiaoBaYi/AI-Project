package com.base.admin.controller;

import com.alibaba.excel.EasyExcel;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrBoardViewDTO;
import com.base.admin.domain.vo.HrBoardVO;
import com.base.admin.service.HrBoardService;
import com.base.admin.service.HrMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "招聘看板")
@RestController
@RequestMapping("/hr/board")
@RequiredArgsConstructor
public class HrBoardController {

    private final HrBoardService boardService;
    private final HrMasterService masterService;

    @Operation(summary = "四块看板")
    @PostMapping
    @RequiresPermission("hr:board:view")
    public Result<HrBoardVO> board(@RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(boardService.board(query));
    }

    @Operation(summary = "下钻明细")
    @PostMapping("/drill")
    @RequiresPermission("hr:board:view")
    public Result<List<HrBoardVO.DrillRow>> drill(@RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(boardService.drill(query));
    }

    @Operation(summary = "导出下钻明细")
    @PostMapping("/export")
    @RequiresPermission("hr:board:export")
    public void export(@RequestBody(required = false) HrBoardQueryDTO query, HttpServletResponse response) throws Exception {
        List<HrBoardVO.DrillRow> rows = boardService.drill(query);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode("招聘明细.xlsx", StandardCharsets.UTF_8));
        List<List<String>> head = List.of(List.of("候选人"), List.of("岗位"), List.of("渠道"), List.of("阶段"),
                List.of("提交人"), List.of("投递日期"), List.of("一面面试官"), List.of("一面时间"));
        List<List<Object>> data = new ArrayList<>();
        for (HrBoardVO.DrillRow row : rows) {
            data.add(List.of(text(row.getCandidateName()), text(row.getJobName()), text(row.getChannel()), text(row.getStageName()),
                    text(row.getSubmitter()), text(row.getSubmittedAt()), text(row.getInterviewer()), text(row.getInterviewAt())));
        }
        EasyExcel.write(response.getOutputStream()).head(head).sheet("明细").doWrite(data);
    }

    @Operation(summary = "指标口径")
    @GetMapping("/metrics")
    @RequiresPermission("hr:board:view")
    public Result<List<Map<String, String>>> metrics() {
        return Result.ok(List.of(
                Map.of("name", "初筛通过", "formula", "简历状态为通过，或筛选结果为通过筛选的投递数"),
                Map.of("name", "邀约成功", "formula", "一面邀约已建成钉钉日程的投递数。取消成功后不再计入"),
                Map.of("name", "到面 / 终面通过 / Offer", "formula", "底稿没有这些环节，显示未采集，不按 0 计算"),
                Map.of("name", "环节转化率", "formula", "下一档有数据的人数 ÷ 上一档有数据的人数"),
                Map.of("name", "环比 / 同比", "formula", "同一筛选下，人数相对上一等长时间段 / 去年同期的变化比例"),
                Map.of("name", "岗位周期", "formula", "有入职日期的需求：入职日 − 需求接收日。没有入职日期的不进平均"),
                Map.of("name", "招聘完成率", "formula", "已到岗人数 ÷（招聘中 + 已完成 + 停止招聘）的需求人数。暂缓不进分母"),
                Map.of("name", "已到岗", "formula", "只认需求表上的入职日期，不拿候选人「已入职」去补")
        ));
    }

    @Operation(summary = "保存视图")
    @PostMapping("/view")
    @RequiresPermission("hr:board:view")
    public Result<Void> saveView(@Valid @RequestBody HrBoardViewDTO dto) {
        masterService.saveView(dto);
        return Result.ok();
    }

    @Operation(summary = "我的视图")
    @GetMapping("/view")
    @RequiresPermission("hr:board:view")
    public Result<List<Map<String, Object>>> views() {
        return Result.ok(masterService.views());
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
