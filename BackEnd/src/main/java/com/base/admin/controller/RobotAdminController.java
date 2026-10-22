package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.robot.LinkMonitorService;
import com.base.admin.robot.RobotProductRules;
import com.base.admin.robot.RobotSessionService;
import com.base.admin.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "机器人后台")
@RestController
@RequestMapping("/robot")
@RequiredArgsConstructor
public class RobotAdminController {

    private final JdbcTemplate jdbc;
    private final LinkMonitorService linkMonitorService;
    private final RobotSessionService robotSessionService;

    @Operation(summary = "意图列表")
    @GetMapping("/intents")
    @RequiresPermission("robot:intent:list")
    public Result<List<Map<String, Object>>> intents() {
        return Result.ok(jdbc.queryForList("""
                SELECT id,
                       intent_code AS intentCode,
                       intent_name AS intentName,
                       enabled,
                       sample_utterances AS sampleUtterances,
                       handler_bean AS handlerBean,
                       allow_group AS allowGroup,
                       allow_dm AS allowDm,
                       sort_order AS sortOrder,
                       remark,
                       update_time AS updateTime
                FROM robot_intent WHERE is_active = 1 ORDER BY sort_order ASC, id ASC
                """));
    }

    @Operation(summary = "更新意图")
    @PutMapping("/intents/{id}")
    @RequiresPermission("robot:intent:edit")
    @Log(title = "机器人意图", businessType = 2)
    public Result<Void> updateIntent(@PathVariable Long id, @RequestBody IntentUpdateDTO dto) {
        jdbc.update("""
                UPDATE robot_intent SET intent_name = COALESCE(?, intent_name),
                  enabled = COALESCE(?, enabled),
                  sample_utterances = COALESCE(?, sample_utterances),
                  allow_group = COALESCE(?, allow_group),
                  allow_dm = COALESCE(?, allow_dm),
                  remark = COALESCE(?, remark),
                  update_by = ?, update_time = NOW()
                WHERE id = ? AND is_active = 1
                """,
                dto.getIntentName(), dto.getEnabled(), dto.getSampleUtterances(),
                dto.getAllowGroup(), dto.getAllowDm(), dto.getRemark(),
                SecurityUtils.getCurrentUsername(), id);
        if (dto.getEnabled() != null && dto.getEnabled() == 0) {
            jdbc.update("""
                    UPDATE robot_session SET state = ?, intent_code = '', slots_json = '{}', expire_at = NOW()
                    WHERE intent_code = (SELECT intent_code FROM robot_intent WHERE id = ?) AND is_active = 1
                    """, RobotProductRules.STATE_IDLE, id);
        }
        return Result.ok();
    }

    @Operation(summary = "会话列表")
    @GetMapping("/sessions")
    @RequiresPermission("robot:session:list")
    public Result<PageResult<Map<String, Object>>> sessions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String state) {
        List<Object> args = new ArrayList<>();
        String where = " WHERE is_active = 1 ";
        if (StringUtils.hasText(state)) {
            where += " AND state = ? ";
            args.add(state.trim());
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(1) FROM robot_session" + where, Integer.class, args.toArray());
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        args.add(pageSize);
        args.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, session_key, staff_id, open_conversation_id, conversation_type, state, intent_code,
                       slots_json, expire_at, update_time
                FROM robot_session
                """ + where + " ORDER BY update_time DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(total == null ? 0 : total, rows));
    }

    @Operation(summary = "强制结束会话")
    @PostMapping("/sessions/{id}/end")
    @RequiresPermission("robot:session:end")
    @Log(title = "机器人会话", businessType = 2)
    public Result<Void> endSession(@PathVariable Long id) {
        robotSessionService.forceEnd(id);
        return Result.ok();
    }

    @Operation(summary = "消息流水")
    @GetMapping("/messages")
    @RequiresPermission("robot:message:list")
    public Result<PageResult<Map<String, Object>>> messages(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String intentCode) {
        List<Object> args = new ArrayList<>();
        String where = " WHERE 1=1 ";
        if (StringUtils.hasText(intentCode)) {
            where += " AND intent_code = ? ";
            args.add(intentCode.trim());
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(1) FROM robot_message_log" + where, Integer.class, args.toArray());
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        args.add(pageSize);
        args.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, direction, staff_id, open_conversation_id, conversation_type, intent_code,
                       content, success, error_msg, create_time
                FROM robot_message_log
                """ + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(total == null ? 0 : total, rows));
    }

    @Operation(summary = "链接监控列表")
    @GetMapping("/link-monitors")
    @RequiresPermission("robot:link-monitor:list")
    public Result<PageResult<Map<String, Object>>> linkMonitors(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<Object> args = new ArrayList<>();
        String where = " WHERE m.is_active = 1 ";
        if (StringUtils.hasText(status)) {
            where += " AND m.status = ? ";
            args.add(status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            where += " AND m.url LIKE ? ";
            args.add("%" + keyword.trim() + "%");
        }
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(1) FROM ops_link_monitor m" + where, Integer.class, args.toArray());
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        args.add(pageSize);
        args.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.id, m.url, m.name, m.remark, m.scope_type, m.source, m.open_conversation_id, m.conversation_type,
                       m.status, m.interval_sec, m.last_check_at, m.last_ok, m.last_status_code, m.last_error,
                       m.last_alert_at, m.create_by, m.create_time,
                       g.title AS group_title
                FROM ops_link_monitor m
                LEFT JOIN robot_group_profile g ON g.open_conversation_id = m.open_conversation_id AND g.is_active = 1
                """ + where + " ORDER BY m.id DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(total == null ? 0 : total, rows));
    }

    @Operation(summary = "新增链接监控")
    @PostMapping("/link-monitors")
    @RequiresPermission("robot:link-monitor:add")
    @Log(title = "链接监控", businessType = 1)
    public Result<Map<String, Object>> addLinkMonitor(@RequestBody LinkMonitorSaveDTO dto) {
        long id = linkMonitorService.addMonitor(dto.getUrl(), "", "1", "",
                SecurityUtils.getCurrentUserId(), "admin", SecurityUtils.getCurrentUsername());
        if (StringUtils.hasText(dto.getName()) || StringUtils.hasText(dto.getRemark())) {
            jdbc.update("UPDATE ops_link_monitor SET name = ?, remark = ? WHERE id = ?",
                    blank(dto.getName()), blank(dto.getRemark()), id);
        }
        return Result.ok(Map.of("id", id));
    }

    @Operation(summary = "编辑链接监控")
    @PutMapping("/link-monitors/{id}")
    @RequiresPermission("robot:link-monitor:edit")
    @Log(title = "链接监控", businessType = 2)
    public Result<Void> editLinkMonitor(@PathVariable Long id, @RequestBody LinkMonitorSaveDTO dto) {
        jdbc.update("""
                UPDATE ops_link_monitor SET name = COALESCE(?, name), remark = COALESCE(?, remark),
                  interval_sec = COALESCE(?, interval_sec), timeout_ms = COALESCE(?, timeout_ms),
                  status = COALESCE(?, status), update_by = ?, update_time = NOW()
                WHERE id = ? AND is_active = 1
                """, dto.getName(), dto.getRemark(), dto.getIntervalSec(), dto.getTimeoutMs(),
                dto.getStatus(), SecurityUtils.getCurrentUsername(), id);
        return Result.ok();
    }

    @Operation(summary = "删除链接监控")
    @DeleteMapping("/link-monitors/{id}")
    @RequiresPermission("robot:link-monitor:delete")
    @Log(title = "链接监控", businessType = 3)
    public Result<Void> deleteLinkMonitor(@PathVariable Long id) {
        linkMonitorService.softDelete(id, "", false);
        return Result.ok();
    }

    @Operation(summary = "立即探测")
    @PostMapping("/link-monitors/{id}/probe")
    @RequiresPermission("robot:link-monitor:probe")
    @Log(title = "链接监控探测", businessType = 2)
    public Result<Map<String, Object>> probe(@PathVariable Long id) {
        // 后台手动探测：失败必告警（推群@创建人，失败则通知 wangfangyang）
        LinkMonitorService.ProbeResult r = linkMonitorService.probeOne(id, true);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("ok", r.ok());
        data.put("error", r.error() == null ? "" : r.error());
        data.put("alerted", r.alerted());
        return Result.ok(data);
    }

    @Operation(summary = "探测日志")
    @GetMapping("/link-monitors/{id}/checks")
    @RequiresPermission("robot:link-monitor:list")
    public Result<List<Map<String, Object>>> checks(@PathVariable Long id) {
        return Result.ok(jdbc.queryForList("""
                SELECT id, checked_at, ok, http_status, latency_ms, error_msg
                FROM ops_link_monitor_check_log WHERE monitor_id = ?
                ORDER BY id DESC LIMIT 50
                """, id));
    }

    @Operation(summary = "告警记录")
    @GetMapping("/link-monitors/{id}/alerts")
    @RequiresPermission("robot:link-monitor:list")
    public Result<List<Map<String, Object>>> alerts(@PathVariable Long id) {
        return Result.ok(jdbc.queryForList("""
                SELECT id, channel, target, content, success, error_msg, create_time
                FROM ops_link_monitor_alert_log WHERE monitor_id = ?
                ORDER BY id DESC LIMIT 50
                """, id));
    }

    private static String blank(String s) {
        return s == null ? "" : s.trim();
    }

    @Data
    @Schema(description = "意图更新")
    public static class IntentUpdateDTO {
        @Schema(description = "名称")
        private String intentName;
        @Schema(description = "启用 1/0")
        private Integer enabled;
        @Schema(description = "示例问法")
        private String sampleUtterances;
        @Schema(description = "允许群")
        private Integer allowGroup;
        @Schema(description = "允许单聊")
        private Integer allowDm;
        @Schema(description = "备注")
        private String remark;
    }

    @Data
    @Schema(description = "链接监控保存")
    public static class LinkMonitorSaveDTO {
        @Schema(description = "URL")
        private String url;
        @Schema(description = "名称")
        private String name;
        @Schema(description = "备注")
        private String remark;
        @Schema(description = "间隔秒")
        private Integer intervalSec;
        @Schema(description = "超时毫秒")
        private Integer timeoutMs;
        @Schema(description = "状态")
        private String status;
    }
}
