package com.base.admin.controller;

import com.alibaba.excel.EasyExcel;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrBoardViewDTO;
import com.base.admin.domain.vo.HrBoardDrillVO;
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
import java.util.Locale;
import java.util.Map;

@Tag(name = "招聘看板")
@RestController
@RequestMapping("/hr/board")
@RequiredArgsConstructor
public class HrBoardController {

    private final HrBoardService boardService;
    private final HrMasterService masterService;

    @Operation(summary = "招聘漏斗与周期")
    @PostMapping
    @RequiresPermission("hr:board:view")
    public Result<HrBoardVO> board(@RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(boardService.board(query));
    }

    @Operation(summary = "岗位实时明细（日/周进展）")
    @PostMapping("/job-details")
    @RequiresPermission("hr:board:view")
    public Result<List<com.base.admin.domain.vo.HrJobDetailVO>> jobDetails(
            @RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(boardService.jobDetails(query));
    }

    @Operation(summary = "岗位类别选项")
    @GetMapping("/jobs")
    @RequiresPermission("hr:board:view")
    public Result<List<String>> jobs() {
        return Result.ok(boardService.jobNames());
    }

    @Operation(summary = "看板下钻明细")
    @PostMapping("/drill")
    @RequiresPermission("hr:board:view")
    public Result<HrBoardDrillVO> drill(@RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(boardService.drill(query));
    }

    @Operation(summary = "导出表格明细")
    @PostMapping("/export")
    @RequiresPermission("hr:board:export")
    public void export(@RequestBody(required = false) HrBoardQueryDTO query, HttpServletResponse response) throws Exception {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        String kind = q.getDrillKind() == null ? "STAGE" : q.getDrillKind().trim().toUpperCase(Locale.ROOT);
        if ("PROGRESS".equals(kind)) {
            q.setDrillKind("HC");
            if (q.getHcMetric() == null) {
                q.setHcMetric("DEMAND");
            }
            kind = "HC";
        }
        HrBoardDrillVO drill = boardService.drill(q);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if ("HC".equals(kind)) {
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode("岗位进度明细.xlsx", StandardCharsets.UTF_8));
            List<List<String>> head = List.of(List.of("岗位名称"), List.of("需求人数"), List.of("目标到岗"), List.of("当前已到岗"),
                    List.of("剩余缺口"), List.of("岗位状态"), List.of("紧急等级"), List.of("招聘负责人"));
            List<List<Object>> data = new ArrayList<>();
            for (HrBoardVO.ProgressRow row : drill.getRequisitions()) {
                data.add(List.of(text(row.getJobName()), row.getHeadcount(), text(row.getTargetDate() != null ? row.getTargetDate() : row.getTargetText()),
                        row.getArrived(), row.getGap(), text(row.getProgressStatus()), text(row.getPriorityLabel()), text(row.getOwnerName())));
            }
            EasyExcel.write(response.getOutputStream()).head(head).sheet("岗位进度").doWrite(data);
            return;
        }
        if ("INTERVIEWER".equals(kind) || "FAIL_REASON".equals(kind)) {
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode("面试记录明细.xlsx", StandardCharsets.UTF_8));
            List<List<String>> head = List.of(List.of("候选人"), List.of("岗位"), List.of("轮次"), List.of("结论"),
                    List.of("未通过原因"), List.of("评语"), List.of("面试官"), List.of("面试时间"));
            List<List<Object>> data = new ArrayList<>();
            for (HrBoardVO.InterviewRecordRow row : drill.getInterviews()) {
                String conclusion = "PASS".equals(row.getConclusion()) ? "通过" : "FAIL".equals(row.getConclusion()) ? "未通过" : text(row.getConclusion());
                data.add(List.of(text(row.getCandidateName()), text(row.getJobName()), text(row.getRoundName()), conclusion,
                        text(row.getFailReason()), text(row.getComment()), text(row.getInterviewerName()), text(row.getInterviewedAt())));
            }
            EasyExcel.write(response.getOutputStream()).head(head).sheet("面试记录").doWrite(data);
            return;
        }
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode("招聘明细.xlsx", StandardCharsets.UTF_8));
        List<List<String>> head = List.of(List.of("候选人"), List.of("岗位类别"), List.of("渠道"), List.of("紧急等级"),
                List.of("招聘负责人"), List.of("当前阶段"), List.of("环节"), List.of("到达日期"), List.of("投递日期"));
        List<List<Object>> data = new ArrayList<>();
        for (HrBoardVO.DrillRow row : drill.getCandidates()) {
            data.add(List.of(text(row.getCandidateName()), text(row.getJobName()), text(row.getChannel()), text(row.getPriorityLabel()),
                    text(row.getOwnerName()), text(row.getStageName()), text(row.getFunnelStage()), text(row.getReachedAt()), text(row.getSubmittedAt())));
        }
        EasyExcel.write(response.getOutputStream()).head(head).sheet("明细").doWrite(data);
    }

    @Operation(summary = "指标口径")
    @GetMapping("/metrics")
    @RequiresPermission("hr:board:view")
    public Result<List<Map<String, String>>> metrics() {
        return Result.ok(List.of(
                Map.of("name", "招聘漏斗", "formula", "按投递日期落入所选时间的候选人，统计已到达的环节。后一环节包含已进入更后环节的人"),
                Map.of("name", "环节转化率", "formula", "本环节人数 ÷ 上一环节人数。到面率 = 到面人数 ÷ 邀约成功人数"),
                Map.of("name", "招聘完成率", "formula", "已到岗人数 ÷ 总需求HC（招聘中+已完成+停止招聘）。冻结HC单独统计"),
                Map.of("name", "岗位进度状态", "formula", "待启动/简历收集中/面试中/offer中/已完成/暂停冻结，按需求关联候选人进展推导"),
                Map.of("name", "面试通过率", "formula", "该轮结论为通过的评价数 ÷（通过+未通过）。爽约=已邀约且面试时间已过但仍未到面"),
                Map.of("name", "面试官通过率", "formula", "按面试评价时间聚合，通过 ÷（通过+未通过）。点击柱子查看该时段面试记录"),
                Map.of("name", "淘汰原因", "formula", "面试记录结论为未通过的 fail_reason，按未通过原因配置排序"),
                Map.of("name", "面试量", "formula", "按面试评价时间统计各需求当日去重候选人数。点击折线点查看该需求该时段候选人")
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
