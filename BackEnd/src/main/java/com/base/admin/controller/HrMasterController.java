package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.exception.BusinessException;
import com.base.admin.domain.dto.HrSchoolQueryDTO;
import com.base.admin.domain.vo.HrApplicationImportResultVO;
import com.base.admin.domain.vo.HrDingTalkIdentityVO;
import com.base.admin.domain.vo.HrInterviewReviewVO;
import com.base.admin.domain.vo.HrInviteSaveVO;
import com.base.admin.domain.vo.HrSchoolVO;
import com.base.admin.service.HrApplicationImportService;
import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrDepartmentDTO;
import com.base.admin.domain.dto.HrApplicationAiDTO;
import com.base.admin.domain.dto.HrApplicationDTO;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.HrApplicationAiVO;
import com.base.admin.service.HrApplicationAiService;
import com.base.admin.domain.dto.HrDingTalkBindDTO;
import com.base.admin.domain.dto.HrInterviewRecordDTO;
import com.base.admin.domain.dto.HrInterviewVerdictDTO;
import com.base.admin.domain.dto.HrInviteCreateDTO;
import com.base.admin.domain.dto.HrInviteTransferDTO;
import com.base.admin.domain.dto.HrRequisitionDTO;
import com.base.admin.domain.dto.HrRequisitionStatusDTO;
import com.base.admin.domain.dto.HrFailReasonDTO;
import com.base.admin.domain.dto.HrTargetOptionDTO;
import com.base.admin.service.HrInterviewRecordService;
import com.base.admin.service.HrInviteService;
import com.base.admin.service.HrMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Tag(name = "招聘主数据")
@RestController
@RequestMapping("/hr")
@RequiredArgsConstructor
public class HrMasterController {

    private final HrMasterService masterService;
    private final HrInviteService inviteService;
    private final HrInterviewRecordService recordService;
    private final HrApplicationImportService applicationImportService;
    private final HrApplicationAiService applicationAiService;
    private final com.base.admin.service.HrResumeParseService resumeParseService;

    @Operation(summary = "需求列表")
    @PostMapping("/requisition/list")
    @RequiresPermission("hr:requisition:list")
    public Result<List<Map<String, Object>>> requisitions(@RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(masterService.requisitions(query));
    }

    @Operation(summary = "新增招聘需求")
    @PostMapping("/requisition")
    @RequiresPermission("hr:requisition:add")
    @Log(title = "招聘需求", businessType = 1)
    public Result<Void> createRequisition(@Valid @RequestBody HrRequisitionDTO dto) {
        dto.setId(null);
        masterService.saveRequisition(dto);
        return Result.ok();
    }

    @Operation(summary = "修改招聘需求")
    @PutMapping("/requisition")
    @RequiresPermission("hr:requisition:edit")
    @Log(title = "招聘需求", businessType = 2)
    public Result<Void> updateRequisition(@Valid @RequestBody HrRequisitionDTO dto) {
        masterService.saveRequisition(dto);
        return Result.ok();
    }

    @Operation(summary = "暂缓、归档或恢复招聘需求")
    @PostMapping("/requisition/{id}/status")
    @RequiresPermission("hr:requisition:edit")
    @Log(title = "招聘需求", businessType = 2)
    public Result<Void> requisitionStatus(@PathVariable Long id, @Valid @RequestBody HrRequisitionStatusDTO dto) {
        masterService.changeRequisitionStatus(id, dto.getStatus());
        return Result.ok();
    }

    @Operation(summary = "删除招聘需求")
    @DeleteMapping("/requisition/{id:\\d+}")
    @RequiresPermission("hr:requisition:delete")
    @Log(title = "招聘需求", businessType = 3)
    public Result<Void> deleteRequisition(@PathVariable Long id) {
        masterService.deleteRequisition(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除招聘需求")
    @DeleteMapping("/requisition/batch")
    @RequiresPermission("hr:requisition:delete")
    @Log(title = "招聘需求-批量删除", businessType = 3)
    public Result<Void> deleteRequisitionBatch(@RequestBody List<Long> ids) {
        masterService.deleteRequisitionBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "投递列表")
    @PostMapping("/application/list")
    @RequiresPermission("hr:application:list")
    public Result<List<Map<String, Object>>> applications(@RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(masterService.applications(query));
    }

    @Operation(summary = "下载候选人导入模板")
    @GetMapping("/application/import/template")
    @RequiresPermission("hr:application:add")
    public void applicationTemplate(HttpServletResponse response) throws IOException {
        applicationImportService.writeTemplate(response);
    }

    @Operation(summary = "导入候选人，任意错误则整次失败")
    @PostMapping(value = "/application/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("hr:application:add")
    @Log(title = "候选人导入", businessType = 1)
    public Result<HrApplicationImportResultVO> importApplications(@RequestParam("file") MultipartFile file) {
        return Result.ok(applicationImportService.importFile(file));
    }

    @Operation(summary = "候选人可用的 AI 模型")
    @GetMapping("/application/ai/providers")
    @RequiresPermission("hr:application:list")
    public Result<List<GeoAiProviderOptionVO>> applicationAiProviders() {
        return Result.ok(applicationAiService.providers());
    }

    @Operation(summary = "候选人的 AI 分析记录")
    @GetMapping("/application/{id}/ai")
    @RequiresPermission("hr:application:list")
    public Result<List<HrApplicationAiVO>> applicationAi(@PathVariable Long id) {
        return Result.ok(applicationAiService.list(id));
    }

    @Operation(summary = "按所选模型生成一份 AI 分析")
    @PostMapping("/application/ai")
    @RequiresPermission("hr:application:edit")
    @Log(title = "候选人AI分析", businessType = 1)
    public Result<HrApplicationAiVO> generateApplicationAi(@Valid @RequestBody HrApplicationAiDTO dto) {
        return Result.ok(applicationAiService.generate(dto.getApplicationId(), dto.getProvider()));
    }

    @Operation(summary = "新增候选人")
    @PostMapping("/application")
    @RequiresPermission("hr:application:add")
    @Log(title = "候选人", businessType = 1)
    public Result<Long> createApplication(@Valid @RequestBody HrApplicationDTO dto) {
        dto.setId(null);
        return Result.ok(masterService.saveApplication(dto));
    }

    @Operation(summary = "修改候选人")
    @PutMapping("/application")
    @RequiresPermission("hr:application:edit")
    @Log(title = "候选人", businessType = 2)
    public Result<Long> updateApplication(@Valid @RequestBody HrApplicationDTO dto) {
        return Result.ok(masterService.saveApplication(dto));
    }

    @Operation(summary = "解析简历（姓名/电话/邮箱，供新增时预填核对）")
    @PostMapping(value = "/application/resume/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission({"hr:application:add", "hr:application:edit"})
    public Result<com.base.admin.domain.vo.HrResumeParsePreviewVO> parseResume(@RequestParam("file") MultipartFile file) {
        return Result.ok(resumeParseService.parse(file));
    }

    @Operation(summary = "上传候选人简历")
    @PostMapping(value = "/application/{id}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission({"hr:application:add", "hr:application:edit"})
    @Log(title = "候选人简历", businessType = 2)
    public Result<Void> uploadResume(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        masterService.uploadResume(id, file);
        return Result.ok();
    }

    @Operation(summary = "作品集列表")
    @GetMapping("/application/{id}/portfolio")
    @RequiresPermission("hr:application:list")
    public Result<List<com.base.admin.domain.vo.HrCandidatePortfolioVO>> listPortfolio(@PathVariable Long id) {
        return Result.ok(masterService.listPortfolios(id));
    }

    @Operation(summary = "上传作品集附件")
    @PostMapping(value = "/application/{id}/portfolio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission({"hr:application:add", "hr:application:edit"})
    @Log(title = "候选人作品集", businessType = 1)
    public Result<Long> uploadPortfolio(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.ok(masterService.uploadPortfolio(id, file));
    }

    @Operation(summary = "删除作品集附件")
    @DeleteMapping("/application/portfolio/{portfolioId}")
    @RequiresPermission("hr:application:edit")
    @Log(title = "候选人作品集", businessType = 3)
    public Result<Void> deletePortfolio(@PathVariable Long portfolioId) {
        masterService.deletePortfolio(portfolioId);
        return Result.ok();
    }

    @Operation(summary = "下载作品集附件")
    @GetMapping("/application/portfolio/{portfolioId}/file")
    @RequiresPermission("hr:application:list")
    public ResponseEntity<Resource> downloadPortfolio(@PathVariable Long portfolioId) {
        Path path = masterService.portfolioFile(portfolioId);
        String displayName = masterService.portfolioDisplayName(portfolioId);
        String encoded = URLEncoder.encode(displayName == null ? "portfolio" : displayName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(new FileSystemResource(path));
    }

    @Operation(summary = "删除候选人")
    @DeleteMapping("/application/{id:\\d+}")
    @RequiresPermission("hr:application:delete")
    @Log(title = "候选人", businessType = 3)
    public Result<Void> deleteApplication(@PathVariable Long id) {
        masterService.deleteApplication(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除候选人")
    @DeleteMapping("/application/batch")
    @RequiresPermission("hr:application:delete")
    @Log(title = "候选人-批量删除", businessType = 3)
    public Result<Void> deleteApplicationBatch(@RequestBody List<Long> ids) {
        masterService.deleteApplicationBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "投递详情")
    @GetMapping("/application/{id}")
    @RequiresPermission("hr:application:list")
    public Result<Map<String, Object>> application(@PathVariable Long id) {
        return Result.ok(masterService.applicationDetail(id));
    }

    @Operation(summary = "下载简历")
    @GetMapping("/application/{id}/resume")
    @RequiresPermission("hr:application:list")
    public ResponseEntity<Resource> resume(@PathVariable Long id) {
        Path path = masterService.findLocalResumeFile(id);
        String displayName = masterService.resumeDisplayName(id);
        // 本地无文件（常见：本机后端连远程库）时跳转公网 uploads
        if (path == null || !Files.isRegularFile(path)) {
            String publicUrl = masterService.resumePublicUrl(id);
            if (publicUrl != null && !publicUrl.isBlank()) {
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(publicUrl)).build();
            }
            throw new BusinessException("简历文件不在服务器上");
        }
        String storedName = path.getFileName().toString();
        if (displayName == null || displayName.isBlank()) {
            displayName = storedName;
        }
        String filename = URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20");
        String lower = displayName.toLowerCase();
        if (!lower.contains(".") && storedName.contains(".")) {
            lower = storedName.toLowerCase();
        }
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (lower.endsWith(".pdf")) {
            type = MediaType.APPLICATION_PDF;
        } else if (lower.endsWith(".docx")) {
            type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else if (lower.endsWith(".doc")) {
            type = MediaType.parseMediaType("application/msword");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + filename)
                .contentType(type)
                .body(new FileSystemResource(path));
    }

    @Operation(summary = "部门树")
    @GetMapping("/department/tree")
    @RequiresPermission("hr:dept:list")
    public Result<List<Map<String, Object>>> departments() {
        return Result.ok(masterService.departmentTree());
    }

    @Operation(summary = "保存部门")
    @PostMapping("/department")
    @RequiresPermission("hr:dept:edit")
    @Log(title = "招聘部门", businessType = 1)
    public Result<Void> createDepartment(@Valid @RequestBody HrDepartmentDTO dto) {
        dto.setId(null);
        masterService.saveDepartment(dto);
        return Result.ok();
    }

    @Operation(summary = "修改部门")
    @PutMapping("/department")
    @RequiresPermission("hr:dept:edit")
    @Log(title = "招聘部门", businessType = 2)
    public Result<Void> updateDepartment(@Valid @RequestBody HrDepartmentDTO dto) {
        masterService.saveDepartment(dto);
        return Result.ok();
    }

    @Operation(summary = "可选用户")
    @GetMapping("/user/options")
    @RequiresPermission({"hr:invite:add", "hr:dept:edit", "hr:board:view", "hr:requisition:list"})
    public Result<List<Map<String, Object>>> users(@RequestParam(required = false) String scope) {
        return Result.ok(masterService.users(scope));
    }

    @Operation(summary = "目标到岗选项")
    @GetMapping("/target/options")
    @RequiresPermission({"hr:target:list", "hr:requisition:list", "hr:board:view"})
    public Result<List<Map<String, Object>>> targetOptions() {
        return Result.ok(masterService.targetOptions());
    }

    @Operation(summary = "新增目标到岗")
    @PostMapping("/target")
    @RequiresPermission("hr:target:edit")
    @Log(title = "目标到岗", businessType = 1)
    public Result<Void> createTarget(@Valid @RequestBody HrTargetOptionDTO dto) {
        dto.setId(null);
        masterService.saveTarget(dto);
        return Result.ok();
    }

    @Operation(summary = "修改目标到岗")
    @PutMapping("/target")
    @RequiresPermission("hr:target:edit")
    @Log(title = "目标到岗", businessType = 2)
    public Result<Void> updateTarget(@Valid @RequestBody HrTargetOptionDTO dto) {
        masterService.saveTarget(dto);
        return Result.ok();
    }

    @Operation(summary = "删除目标到岗")
    @DeleteMapping("/target/{id}")
    @RequiresPermission("hr:target:edit")
    @Log(title = "目标到岗", businessType = 3)
    public Result<Void> deleteTarget(@PathVariable Long id) {
        masterService.deleteTarget(id);
        return Result.ok();
    }

    @Operation(summary = "未通过原因选项")
    @GetMapping("/fail-reason/options")
    @RequiresPermission({"hr:fail-reason:list", "hr:interview:mine", "hr:record:list", "hr:record:add", "hr:application:list"})
    public Result<List<Map<String, Object>>> failReasonOptions() {
        return Result.ok(masterService.failReasonOptions());
    }

    @Operation(summary = "新增未通过原因")
    @PostMapping("/fail-reason")
    @RequiresPermission("hr:fail-reason:edit")
    @Log(title = "未通过原因", businessType = 1)
    public Result<Void> createFailReason(@Valid @RequestBody HrFailReasonDTO dto) {
        dto.setId(null);
        masterService.saveFailReason(dto);
        return Result.ok();
    }

    @Operation(summary = "修改未通过原因")
    @PutMapping("/fail-reason")
    @RequiresPermission("hr:fail-reason:edit")
    @Log(title = "未通过原因", businessType = 2)
    public Result<Void> updateFailReason(@Valid @RequestBody HrFailReasonDTO dto) {
        masterService.saveFailReason(dto);
        return Result.ok();
    }

    @Operation(summary = "删除未通过原因")
    @DeleteMapping("/fail-reason/{id}")
    @RequiresPermission("hr:fail-reason:edit")
    @Log(title = "未通过原因", businessType = 3)
    public Result<Void> deleteFailReason(@PathVariable Long id) {
        masterService.deleteFailReason(id);
        return Result.ok();
    }

    @Operation(summary = "阶段")
    @GetMapping("/stage/options")
    @RequiresPermission({"hr:application:list", "hr:board:view"})
    public Result<List<Map<String, Object>>> stages() {
        return Result.ok(masterService.stages());
    }

    @Operation(summary = "渠道")
    @GetMapping("/channel/options")
    @RequiresPermission({"hr:board:view", "hr:application:list"})
    public Result<List<Map<String, Object>>> channels() {
        return Result.ok(masterService.channels());
    }

    @Operation(summary = "院校信息")
    @PostMapping("/school/list")
    @RequiresPermission("hr:school:list")
    public Result<PageResult<HrSchoolVO>> schools(@RequestBody HrSchoolQueryDTO query) {
        return Result.ok(masterService.schools(query));
    }

    @Operation(summary = "按系统用户手机号预览钉钉身份")
    @GetMapping("/dingtalk/preview/{userId}")
    @RequiresPermission({"system:user:edit", "hr:dingtalk:edit"})
    public Result<HrDingTalkIdentityVO> previewDingTalk(@PathVariable Long userId) {
        return Result.ok(masterService.previewDingTalk(userId));
    }

    @Operation(summary = "按系统用户手机号绑定钉钉")
    @PostMapping("/dingtalk/bind")
    @RequiresPermission({"system:user:edit", "hr:dingtalk:edit"})
    @Log(title = "钉钉绑定", businessType = 2)
    public Result<Void> bindDingTalk(@Valid @RequestBody HrDingTalkBindDTO dto) {
        masterService.bindDingTalk(dto);
        return Result.ok();
    }

    @Operation(summary = "解除钉钉绑定")
    @DeleteMapping("/dingtalk/{userId}")
    @RequiresPermission({"system:user:edit", "hr:dingtalk:edit"})
    @Log(title = "钉钉绑定", businessType = 3)
    public Result<Void> unbindDingTalk(@PathVariable Long userId) {
        masterService.unbindDingTalk(userId);
        return Result.ok();
    }

    @Operation(summary = "发起面试邀约并建钉钉日程")
    @PostMapping("/invite")
    @RequiresPermission("hr:invite:add")
    @Log(title = "面试邀约", businessType = 1)
    public Result<HrInviteSaveVO> invite(@Valid @RequestBody HrInviteCreateDTO dto) {
        return Result.ok(inviteService.create(dto));
    }

    @Operation(summary = "取消面试邀约并取消钉钉日程")
    @PostMapping("/invite/{id}/cancel")
    @RequiresPermission("hr:invite:cancel")
    @Log(title = "面试邀约", businessType = 3)
    public Result<Void> cancelInvite(@PathVariable Long id) {
        inviteService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "邀约列表")
    @PostMapping("/invite/list")
    @RequiresPermission({"hr:invite:list", "hr:invite:add", "hr:interview:mine"})
    public Result<List<Map<String, Object>>> invites(@RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(inviteService.list(query));
    }

    @Operation(summary = "我的待面试")
    @PostMapping("/invite/mine")
    @RequiresPermission("hr:interview:mine")
    public Result<List<Map<String, Object>>> myInvites(@RequestBody(required = false) HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        q.setMine(true);
        return Result.ok(inviteService.list(q));
    }

    @Operation(summary = "修改邀约并重建钉钉日程")
    @PutMapping("/invite/{id}")
    @RequiresPermission("hr:invite:edit")
    @Log(title = "面试邀约", businessType = 2)
    public Result<HrInviteSaveVO> updateInvite(@PathVariable Long id, @Valid @RequestBody HrInviteCreateDTO dto) {
        return Result.ok(inviteService.update(id, dto));
    }

    @Operation(summary = "为未建日程的邀约创建钉钉日程")
    @PostMapping("/invite/{id:\\d+}/calendar")
    @RequiresPermission("hr:invite:edit")
    @Log(title = "面试邀约", businessType = 2)
    public Result<Void> createInviteCalendar(@PathVariable Long id) {
        inviteService.createCalendar(id);
        return Result.ok();
    }

    @Operation(summary = "批量为未建日程的邀约创建钉钉日程")
    @PostMapping("/invite/calendar/batch")
    @RequiresPermission("hr:invite:edit")
    @Log(title = "面试邀约-批量创建钉钉日程", businessType = 2)
    public Result<HrInviteSaveVO> createInviteCalendarBatch(@RequestBody List<Long> ids) {
        return Result.ok(inviteService.createCalendarBatch(ids));
    }

    @Operation(summary = "删除邀约")
    @DeleteMapping("/invite/{id:\\d+}")
    @RequiresPermission("hr:invite:delete")
    @Log(title = "面试邀约", businessType = 3)
    public Result<HrInviteSaveVO> deleteInvite(@PathVariable Long id) {
        return Result.ok(inviteService.remove(id));
    }

    @Operation(summary = "批量删除邀约")
    @DeleteMapping("/invite/batch")
    @RequiresPermission("hr:invite:delete")
    @Log(title = "面试邀约-批量删除", businessType = 3)
    public Result<HrInviteSaveVO> deleteInviteBatch(@RequestBody List<Long> ids) {
        return Result.ok(inviteService.removeBatch(ids));
    }

    @Operation(summary = "把日程转给其他面试官")
    @PostMapping("/invite/{id}/forward")
    @RequiresPermission("hr:interview:mine")
    @Log(title = "面试邀约", businessType = 2)
    public Result<HrInviteSaveVO> forwardInvite(@PathVariable Long id, @Valid @RequestBody HrInviteTransferDTO dto) {
        return Result.ok(inviteService.forward(id, dto.getInterviewerUserId()));
    }

    @Operation(summary = "给这场面试增加面试官")
    @PostMapping("/invite/{id}/interviewer")
    @RequiresPermission("hr:interview:mine")
    @Log(title = "面试邀约", businessType = 1)
    public Result<HrInviteSaveVO> addInviteInterviewer(@PathVariable Long id, @Valid @RequestBody HrInviteTransferDTO dto) {
        return Result.ok(inviteService.addInterviewer(id, dto.getInterviewerUserId()));
    }

    @Operation(summary = "面试记录列表")
    @PostMapping("/interview-record/list")
    @RequiresPermission({"hr:record:list", "hr:interview:mine"})
    public Result<List<Map<String, Object>>> records(@RequestBody(required = false) HrBoardQueryDTO query) {
        return Result.ok(recordService.list(query));
    }

    @Operation(summary = "保存面试记录")
    @PostMapping("/interview-record")
    @RequiresPermission({"hr:record:add", "hr:record:edit", "hr:interview:mine"})
    @Log(title = "面试记录", businessType = 1)
    public Result<String> saveRecord(@Valid @RequestBody HrInterviewRecordDTO dto) {
        return Result.ok(recordService.save(dto));
    }

    @Operation(summary = "候选人的全部面试评价")
    @GetMapping("/application/{id:\\d+}/reviews")
    @RequiresPermission({"hr:application:list", "hr:record:list", "hr:interview:mine"})
    public Result<List<HrInterviewReviewVO>> reviews(@PathVariable Long id) {
        return Result.ok(recordService.reviews(id));
    }

    @Operation(summary = "同一轮次结论不一致时提交联合评价")
    @PostMapping("/application/verdict")
    @RequiresPermission({"hr:application:edit", "hr:record:edit"})
    @Log(title = "面试联合评价", businessType = 1)
    public Result<String> saveVerdict(@Valid @RequestBody HrInterviewVerdictDTO dto) {
        return Result.ok(recordService.saveVerdict(dto));
    }

    @Operation(summary = "删除自己的面试评价")
    @DeleteMapping("/interview-record/mine/{id:\\d+}")
    @RequiresPermission("hr:interview:mine")
    @Log(title = "面试记录", businessType = 3)
    public Result<Void> deleteOwnRecord(@PathVariable Long id) {
        recordService.deleteOwn(id);
        return Result.ok();
    }

    @Operation(summary = "删除面试记录")
    @DeleteMapping("/interview-record/{id:\\d+}")
    @RequiresPermission("hr:record:delete")
    @Log(title = "面试记录", businessType = 3)
    public Result<Void> deleteRecord(@PathVariable Long id) {
        recordService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除面试记录")
    @DeleteMapping("/interview-record/batch")
    @RequiresPermission("hr:record:delete")
    @Log(title = "面试记录-批量删除", businessType = 3)
    public Result<Void> deleteRecordBatch(@RequestBody List<Long> ids) {
        recordService.deleteBatch(ids);
        return Result.ok();
    }
}
