package com.base.admin.service;

import com.alibaba.excel.EasyExcel;
import com.base.admin.domain.dto.HrApplicationDTO;
import com.base.admin.domain.vo.HrApplicationImportErrorVO;
import com.base.admin.domain.vo.HrApplicationImportResultVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.ExcelCellUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HrApplicationImportService {

    private static final String[] HEADERS = {"姓名", "电话", "邮箱", "岗位", "渠道", "阶段", "提交人", "投递日期"};

    private final JdbcTemplate jdbc;
    private final HrMasterService masterService;

    public void writeTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode("候选人导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);

        List<List<String>> example = List.of(List.of(
                "张三", "13800138000", "zhangsan@example.com", "（改成招聘需求里已有的岗位名称）", "BOSS", "已录入", "王艳", "2026-09-20"));
        List<List<String>> head = new ArrayList<>();
        for (String header : HEADERS) {
            head.add(List.of(header));
        }
        List<List<String>> rules = new ArrayList<>();
        rules.add(List.of("姓名", "是", "候选人姓名", "张三"));
        rules.add(List.of("电话", "否", "手机号", "13800138000"));
        rules.add(List.of("邮箱", "否", "需要包含 @", "zhangsan@example.com"));
        rules.add(List.of("岗位", "是", "必须和招聘需求里的岗位名称完全一致，不能为空，系统里没有的岗位会导致整次导入失败", "财务BP"));
        rules.add(List.of("渠道", "否", "可填渠道名或渠道码：" + names("SELECT CONCAT(channel_name, '(', channel_code, ')') FROM hr_channel WHERE is_active = 1"), "BOSS"));
        rules.add(List.of("阶段", "是", "可填阶段名或阶段码：" + names("SELECT CONCAT(stage_name, '(', stage_code, ')') FROM hr_stage_def WHERE is_active = 1 ORDER BY sort_no"), "已录入"));
        rules.add(List.of("提交人", "否", "必须是系统用户的姓名或登录账号，不能随便写", "王艳"));
        rules.add(List.of("投递日期", "是", "格式 yyyy-MM-dd", "2026-09-20"));
        rules.add(List.of("整次导入", "是", "任意一行有空岗位、不存在的岗位或其他错误，整份文件都不会写入", ""));

        List<List<String>> ruleHead = List.of(List.of("字段"), List.of("必填"), List.of("填写规则"), List.of("示例"));
        var writer = EasyExcel.write(response.getOutputStream()).build();
        writer.write(example, EasyExcel.writerSheet(0, "候选人").head(head).build());
        writer.write(rules, EasyExcel.writerSheet(1, "填写说明").head(ruleHead).build());
        writer.finish();
    }

    @Transactional
    public HrApplicationImportResultVO importFile(MultipartFile file) {
        HrApplicationImportResultVO result = new HrApplicationImportResultVO();
        if (file == null || file.isEmpty()) {
            return failed(result, 0, 0, "请上传 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            return failed(result, 0, 0, "只支持 .xlsx 或 .xls");
        }
        List<ReadyRow> ready = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                return failed(result, 0, 0, "Excel 里没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            for (int col = 0; col < HEADERS.length; col++) {
                String actual = ExcelCellUtils.str(sheet, 0, col);
                if (!HEADERS[col].equals(actual)) {
                    return failed(result, 0, 1, "第 1 行表头与模板不一致，第 " + (col + 1) + " 列应为「" + HEADERS[col] + "」，实际是「" + actual + "」。请下载最新模板");
                }
            }
            Map<String, String> channels = codeMap("SELECT channel_code, channel_name FROM hr_channel WHERE is_active = 1");
            Map<String, String> stages = codeMap("SELECT stage_code, stage_name FROM hr_stage_def WHERE is_active = 1");
            int last = sheet.getLastRowNum();
            for (int row = 1; row <= last; row++) {
                String name = ExcelCellUtils.str(sheet, row, 0);
                String phone = ExcelCellUtils.str(sheet, row, 1);
                String email = ExcelCellUtils.str(sheet, row, 2);
                String job = ExcelCellUtils.str(sheet, row, 3);
                String channel = ExcelCellUtils.str(sheet, row, 4);
                String stage = ExcelCellUtils.str(sheet, row, 5);
                String submitter = ExcelCellUtils.str(sheet, row, 6);
                String dateText = ExcelCellUtils.str(sheet, row, 7);
                if (name.isEmpty() && phone.isEmpty() && email.isEmpty() && job.isEmpty() && channel.isEmpty()
                        && stage.isEmpty() && submitter.isEmpty() && dateText.isEmpty()) {
                    continue;
                }
                int excelRow = row + 1;
                result.setTotal(result.getTotal() + 1);
                List<String> messages = new ArrayList<>();
                if (name.isEmpty()) {
                    messages.add("姓名不能为空");
                } else if (name.length() > 64) {
                    messages.add("姓名超过 64 个字");
                }
                if (phone.length() > 32) {
                    messages.add("电话超过 32 个字");
                }
                if (!email.isEmpty() && (!email.contains("@") || email.length() > 128)) {
                    messages.add("邮箱格式不正确");
                }
                Long requisitionId = null;
                if (job.isEmpty()) {
                    messages.add("岗位不能为空");
                } else if (job.startsWith("（改成")) {
                    messages.add("岗位还是模板里的示例，请改成招聘需求中已有的岗位名称");
                } else {
                    requisitionId = jdbc.query("""
                            SELECT id FROM hr_requisition
                            WHERE is_active = 1 AND TRIM(job_name) = ?
                            ORDER BY CASE status WHEN 'OPEN' THEN 0 ELSE 1 END, received_date DESC, id DESC
                            LIMIT 1
                            """, rs -> rs.next() ? rs.getLong(1) : null, job);
                    if (requisitionId == null) {
                        messages.add("岗位「" + job + "」在招聘需求中不存在");
                    }
                }
                String channelCode = null;
                if (!channel.isEmpty()) {
                    channelCode = channels.get(channel);
                    if (channelCode == null) {
                        messages.add("渠道「" + channel + "」不存在");
                    }
                }
                String stageCode = stages.get(stage);
                if (stage.isEmpty()) {
                    messages.add("阶段不能为空");
                } else if (stageCode == null) {
                    messages.add("阶段「" + stage + "」不存在");
                }
                Long submitterUserId = null;
                String submitterName = "";
                if (!submitter.isEmpty()) {
                    List<Long> userIds = jdbc.query("""
                            SELECT user_id FROM sys_user
                            WHERE is_active = 1 AND status = 0 AND (nickname = ? OR username = ?)
                            """, (rs, index) -> rs.getLong(1), submitter, submitter);
                    if (userIds.isEmpty()) {
                        messages.add("提交人「" + submitter + "」不是系统用户，请填写用户姓名或登录账号");
                    } else if (userIds.size() > 1) {
                        messages.add("提交人「" + submitter + "」对应多个用户，请改成登录账号");
                    } else {
                        submitterUserId = userIds.get(0);
                        submitterName = submitter;
                    }
                }
                LocalDate submittedAt = ExcelCellUtils.date(sheet, row, 7);
                if (dateText.isEmpty() && submittedAt == null) {
                    messages.add("投递日期不能为空");
                } else if (submittedAt == null) {
                    messages.add("投递日期「" + dateText + "」无法识别，请用 yyyy-MM-dd");
                }
                if (!messages.isEmpty()) {
                    addError(result, excelRow, String.join("；", messages));
                    continue;
                }
                ReadyRow item = new ReadyRow();
                item.name = name;
                item.phone = phone;
                item.email = email;
                item.requisitionId = requisitionId;
                item.channelCode = channelCode;
                item.stageCode = stageCode;
                item.submitterUserId = submitterUserId;
                item.submitter = submitterName;
                item.submittedAt = submittedAt;
                ready.add(item);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            return failed(result, result.getTotal(), 0, "Excel 无法读取：" + ex.getMessage());
        }
        if (result.getTotal() == 0) {
            return failed(result, 0, 0, "没有可导入的候选人。请从第 2 行开始填写，示例行如果岗位不是真实岗位也要改掉");
        }
        if (!result.getErrors().isEmpty()) {
            result.setSuccess(false);
            result.setSuccessCount(0);
            return result;
        }
        for (ReadyRow item : ready) {
            HrApplicationDTO dto = new HrApplicationDTO();
            dto.setDisplayName(item.name);
            dto.setPhone(item.phone);
            dto.setEmail(item.email);
            dto.setRequisitionId(item.requisitionId);
            dto.setChannelCode(item.channelCode);
            dto.setCurrentStage(item.stageCode);
            dto.setSubmitterUserId(item.submitterUserId);
            dto.setSubmitterName(item.submitter);
            dto.setSubmittedAt(item.submittedAt);
            masterService.saveApplication(dto);
        }
        result.setSuccess(true);
        result.setSuccessCount(ready.size());
        return result;
    }

    private Map<String, String> codeMap(String sql) {
        Map<String, String> map = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            String code = rs.getString(1);
            String name = rs.getString(2);
            map.put(code, code);
            if (name != null && !name.isBlank()) {
                map.put(name.trim(), code);
            }
        });
        return map;
    }

    private String names(String sql) {
        List<String> values = jdbc.query(sql, (rs, row) -> rs.getString(1));
        return values.isEmpty() ? "（暂无）" : String.join("、", values);
    }

    private HrApplicationImportResultVO failed(HrApplicationImportResultVO result, int total, int row, String message) {
        result.setSuccess(false);
        result.setTotal(total);
        result.setSuccessCount(0);
        addError(result, row, message);
        return result;
    }

    private void addError(HrApplicationImportResultVO result, int row, String message) {
        HrApplicationImportErrorVO error = new HrApplicationImportErrorVO();
        error.setRowIndex(row);
        error.setMessage(message);
        result.getErrors().add(error);
    }

    private static final class ReadyRow {
        private String name;
        private String phone;
        private String email;
        private Long requisitionId;
        private String channelCode;
        private String stageCode;
        private Long submitterUserId;
        private String submitter;
        private LocalDate submittedAt;
    }
}
