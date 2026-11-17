package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.admin.domain.dto.GeoAutoExternalResultItemDTO;
import com.base.admin.domain.dto.GeoAutoExternalResultSaveDTO;
import com.base.admin.domain.entity.GeoMonitorQuestion;
import com.base.admin.domain.entity.GeoMonitorResult;
import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.domain.entity.GeoMonitorTask;
import com.base.admin.domain.vo.GeoAutoExternalSaveVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoMonitorQuestionMapper;
import com.base.admin.mapper.GeoMonitorResultMapper;
import com.base.admin.mapper.GeoMonitorRunMapper;
import com.base.admin.mapper.GeoMonitorTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 外网免登录提交监控结果：写成一条运行批次和对应明细。
 * 同一 external runId 再次提交会删掉上一轮明细后重写。
 */
@Service
@RequiredArgsConstructor
public class GeoAutoExternalResultService {

    private static final int MAX_SCREENSHOT_BYTES = 6 * 1024 * 1024;
    private static final int MAX_SCREENSHOT_TEXT = 8_500_000;
    private static final Set<String> RUN_STATUS = Set.of(
            GeoMonitorRun.STATUS_SUCCESS,
            GeoMonitorRun.STATUS_PARTIAL,
            GeoMonitorRun.STATUS_FAILED,
            GeoMonitorRun.STATUS_RUNNING);

    private final GeoMonitorTaskMapper taskMapper;
    private final GeoMonitorQuestionMapper questionMapper;
    private final GeoMonitorRunMapper runMapper;
    private final GeoMonitorResultMapper resultMapper;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public GeoAutoExternalSaveVO save(GeoAutoExternalResultSaveDTO dto) {
        GeoMonitorTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null) {
            throw new BusinessException("监测任务不存在：" + dto.getTaskId());
        }
        String externalRunId = dto.getRunId().trim();
        LocalDateTime start = dto.getStartTime() != null ? dto.getStartTime() : LocalDateTime.now();
        LocalDateTime end = dto.getEndTime();

        List<PreparedItem> prepared = prepare(dto.getResults());
        int success = 0;
        int failed = 0;
        for (PreparedItem item : prepared) {
            if (isSuccessStatus(item.status())) {
                success++;
            } else {
                failed++;
            }
        }

        GeoMonitorRun existing = runMapper.selectOne(new LambdaQueryWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getExternalRunId, externalRunId)
                .last("LIMIT 1"));
        boolean replaced = existing != null;
        if (replaced) {
            List<GeoMonitorResult> oldRows = resultMapper.selectList(new LambdaQueryWrapper<GeoMonitorResult>()
                    .eq(GeoMonitorResult::getRunId, existing.getId()));
            for (GeoMonitorResult old : oldRows) {
                fileStorageService.deleteGeoUpload(old.getScreenshotUrl());
            }
            resultMapper.delete(new LambdaQueryWrapper<GeoMonitorResult>()
                    .eq(GeoMonitorResult::getRunId, existing.getId()));
        }

        GeoMonitorRun run = replaced ? existing : new GeoMonitorRun();
        run.setTaskId(task.getId());
        run.setTaskName(StringUtils.hasText(dto.getTaskName()) ? clip(dto.getTaskName().trim(), 128) : task.getTaskName());
        run.setRunDate(start.toLocalDate());
        run.setTriggerType(GeoMonitorRun.TRIGGER_EXTERNAL);
        run.setExternalRunId(externalRunId);
        run.setExternalSource(StringUtils.hasText(dto.getSource()) ? clip(dto.getSource().trim(), 32) : "");
        run.setStatus(resolveRunStatus(dto.getStatus(), success, failed));
        run.setTotalCount(prepared.size());
        run.setDoneCount(prepared.size());
        run.setSuccessCount(success);
        run.setFailedCount(failed);
        run.setBatchSize(prepared.size());
        run.setBatchTotal(1);
        run.setBatchDone(1);
        run.setCurrentBatchNo(1);
        run.setStartTime(start);
        run.setEndTime(end);
        run.setErrorMsg("");
        if (replaced) {
            runMapper.updateById(run);
        } else {
            runMapper.insert(run);
        }

        String source = run.getExternalSource();
        for (PreparedItem item : prepared) {
            String screenshotUrl = "";
            if (item.image() != null) {
                screenshotUrl = fileStorageService.saveGeoImageBytes(item.image(), item.ext());
                if (!StringUtils.hasText(screenshotUrl)) {
                    throw new BusinessException("第 " + item.index() + " 条截图格式不支持，仅接受 png/jpg/gif/webp");
                }
            }
            GeoMonitorResult row = new GeoMonitorResult();
            row.setRunId(run.getId());
            row.setQuestionId(item.question().getId());
            row.setQuestionText(clip(item.questionText(), 512));
            row.setTopicId(item.question().getTopicId() == null ? 0L : item.question().getTopicId());
            row.setPlatform(item.platform());
            row.setChannel("BROWSER");
            row.setAnswerModel(clip(item.platform() + " 网页版", 64));
            row.setAnswerText(clip(item.answer(), 60_000));
            row.setDeepThink(0);
            row.setProbeUrl(clip(item.resultUrl(), 1000));
            row.setProbeRequest(probeJson(item, source, externalRunId));
            row.setScreenshotUrl(screenshotUrl == null ? "" : screenshotUrl);
            row.setScreenshotType(StringUtils.hasText(screenshotUrl) ? "BROWSER_SHOT" : "");
            row.setMentioned(0);
            row.setStatus(item.status());
            row.setErrorMsg(clip(item.errorMessage(), 450));
            row.setCreateBy(StringUtils.hasText(source) ? source : "external");
            if (item.collectedAt() != null) {
                row.setCreateTime(item.collectedAt());
            }
            resultMapper.insert(row);
        }

        taskMapper.update(null, new LambdaUpdateWrapper<GeoMonitorTask>()
                .eq(GeoMonitorTask::getId, task.getId())
                .set(GeoMonitorTask::getLastRunId, run.getId()));

        GeoAutoExternalSaveVO vo = new GeoAutoExternalSaveVO();
        vo.setId(run.getId());
        vo.setRunId(externalRunId);
        vo.setTotalCount(prepared.size());
        vo.setSuccessCount(success);
        vo.setFailedCount(failed);
        vo.setReplaced(replaced);
        return vo;
    }

    private List<PreparedItem> prepare(List<GeoAutoExternalResultItemDTO> items) {
        Map<Long, GeoMonitorQuestion> byId = new LinkedHashMap<>();
        List<PreparedItem> prepared = new java.util.ArrayList<>();
        int index = 0;
        for (GeoAutoExternalResultItemDTO item : items) {
            index++;
            if (item == null) {
                throw new BusinessException("第 " + index + " 条结果为空");
            }
            if (!StringUtils.hasText(item.getPlatform())) {
                throw new BusinessException("第 " + index + " 条缺少平台名");
            }
            String platform = item.getPlatform().trim();
            if (platform.length() > 32) {
                throw new BusinessException("第 " + index + " 条平台名过长");
            }
            GeoMonitorQuestion question = resolveQuestion(item, byId);
            if (question == null) {
                String hint = item.getQuestionId() != null ? "questionId=" + item.getQuestionId() : item.getQuestion();
                throw new BusinessException("第 " + index + " 条找不到监控问题：" + hint);
            }
            String questionText = StringUtils.hasText(item.getQuestion()) ? item.getQuestion().trim() : question.getQuestionText();
            String status = normalizeResultStatus(item.getStatus(), item.getErrorMessage());
            String resultUrl = httpUrl(item.getResultUrl(), index, "resultUrl");
            String platformUrl = httpUrl(item.getPlatformUrl(), index, "platformUrl");
            DecodedImage image = decodeScreenshot(item.getScreenshot(), index);
            prepared.add(new PreparedItem(index, question, questionText, platform, platformUrl, resultUrl,
                    status, item.getAnswer(), item.getErrorMessage(), item.getCollectedAt(),
                    image == null ? null : image.bytes(), image == null ? null : image.ext()));
        }
        return prepared;
    }

    private GeoMonitorQuestion resolveQuestion(GeoAutoExternalResultItemDTO item, Map<Long, GeoMonitorQuestion> byId) {
        if (item.getQuestionId() != null && item.getQuestionId() > 0) {
            GeoMonitorQuestion cached = byId.get(item.getQuestionId());
            if (cached != null) {
                return cached;
            }
            GeoMonitorQuestion found = questionMapper.selectById(item.getQuestionId());
            if (found != null) {
                byId.put(found.getId(), found);
                return found;
            }
        }
        if (!StringUtils.hasText(item.getQuestion())) {
            return null;
        }
        return questionMapper.selectOne(new LambdaQueryWrapper<GeoMonitorQuestion>()
                .eq(GeoMonitorQuestion::getQuestionText, item.getQuestion().trim())
                .last("LIMIT 1"));
    }

    private DecodedImage decodeScreenshot(String raw, int index) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        int comma = text.indexOf(',');
        if (text.startsWith("data:") && comma > 0) {
            text = text.substring(comma + 1);
        }
        text = text.replaceAll("\\s", "");
        if (text.length() > MAX_SCREENSHOT_TEXT) {
            throw new BusinessException("第 " + index + " 条截图超过 6MB");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("第 " + index + " 条截图不是合法的 Base64");
        }
        if (bytes.length > MAX_SCREENSHOT_BYTES) {
            throw new BusinessException("第 " + index + " 条截图超过 6MB");
        }
        String ext = imageExt(bytes);
        if (ext == null) {
            throw new BusinessException("第 " + index + " 条截图格式不支持，仅接受 png/jpg/gif/webp");
        }
        return new DecodedImage(bytes, ext);
    }

    private static String imageExt(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return ".png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return ".jpg";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return ".gif";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return ".webp";
        }
        return null;
    }

    private String httpUrl(String url, int index, String field) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String value = url.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new BusinessException("第 " + index + " 条 " + field + " 必须是 http 或 https 链接");
        }
        return value;
    }

    private String probeJson(PreparedItem item, String source, String externalRunId) {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("source", source);
        diag.put("externalRunId", externalRunId);
        diag.put("platformUrl", item.platformUrl());
        if (item.collectedAt() != null) {
            diag.put("collectedAt", item.collectedAt().toString());
        }
        try {
            return objectMapper.writeValueAsString(diag);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String resolveRunStatus(String raw, int success, int failed) {
        if (StringUtils.hasText(raw)) {
            String status = raw.trim().toUpperCase(Locale.ROOT);
            if (!RUN_STATUS.contains(status)) {
                throw new BusinessException("批次状态只支持 SUCCESS、PARTIAL、FAILED、RUNNING");
            }
            return status;
        }
        if (failed == 0) {
            return GeoMonitorRun.STATUS_SUCCESS;
        }
        if (success == 0) {
            return GeoMonitorRun.STATUS_FAILED;
        }
        return GeoMonitorRun.STATUS_PARTIAL;
    }

    private static String normalizeResultStatus(String raw, String errorMessage) {
        if (!StringUtils.hasText(raw)) {
            return StringUtils.hasText(errorMessage)
                    ? GeoMonitorResult.STATUS_CALL_FAILED
                    : GeoMonitorResult.STATUS_SUCCESS;
        }
        String status = raw.trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "SUCCESS" -> GeoMonitorResult.STATUS_SUCCESS;
            case "SKIPPED_MANUAL" -> GeoMonitorResult.STATUS_SKIPPED_MANUAL;
            case "JUDGE_FAILED" -> GeoMonitorResult.STATUS_JUDGE_FAILED;
            case "CALL_FAILED", "FAILED", "ERROR", "FAIL" -> GeoMonitorResult.STATUS_CALL_FAILED;
            default -> throw new BusinessException("结果状态不支持：" + raw.trim());
        };
    }

    private static boolean isSuccessStatus(String status) {
        return GeoMonitorResult.STATUS_SUCCESS.equals(status)
                || GeoMonitorResult.STATUS_SKIPPED_MANUAL.equals(status);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record PreparedItem(
            int index,
            GeoMonitorQuestion question,
            String questionText,
            String platform,
            String platformUrl,
            String resultUrl,
            String status,
            String answer,
            String errorMessage,
            LocalDateTime collectedAt,
            byte[] image,
            String ext) {
    }

    private record DecodedImage(byte[] bytes, String ext) {
    }
}
