package com.base.admin.service;

import com.base.admin.domain.dto.GeoContentAiGenerateDTO;
import com.base.admin.domain.vo.GeoAiGenerateJobVO;
import com.base.admin.domain.vo.GeoContentAiGenerateVO;
import com.base.admin.domain.vo.GeoPipelineStageVO;
import com.base.admin.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class GeoAiGenerateJobService {

    private static final long KEEP_MS = 60 * 60 * 1000L;

    private final GeoContentPlacementService contentPlacementService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "geo-ai-generate");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public GeoAiGenerateJobVO start(GeoContentAiGenerateDTO dto) {
        if (dto == null || dto.getPlacementId() == null) {
            throw new BusinessException("内容投放ID不能为空");
        }
        purge();
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId);
        jobs.put(jobId, job);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        executor.submit(() -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            try {
                GeoContentAiGenerateVO result = contentPlacementService.generateArticle(dto, job::onStage);
                job.result = result;
                if (result != null && result.getStages() != null && !result.getStages().isEmpty()) {
                    job.replaceStages(result.getStages());
                }
                job.onStage("save", "写入草稿", "done", "已写入草稿");
                job.status = "SUCCESS";
                job.currentMessage = "生成完成";
            } catch (Exception e) {
                job.status = "FAILED";
                job.currentMessage = brief(e);
                job.onStage(
                        job.currentCode != null ? job.currentCode : "qa",
                        "失败",
                        "failed",
                        brief(e));
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return job.snapshot();
    }

    public GeoAiGenerateJobVO get(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new BusinessException("生成任务不存在或已过期");
        }
        return job.snapshot();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void purge() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Job>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Job> entry = it.next();
            if (now - entry.getValue().createdAt > KEEP_MS) {
                it.remove();
            }
        }
    }

    private static String brief(Exception e) {
        String msg = e.getMessage();
        if (!StringUtils.hasText(msg)) {
            return "AI 生成失败";
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private static final class Job {
        private final String jobId;
        private final long createdAt = System.currentTimeMillis();
        private final List<GeoPipelineStageVO> stages = new ArrayList<>();
        private volatile String status = "RUNNING";
        private volatile String currentCode;
        private volatile String currentMessage = "排队中…";
        private volatile GeoContentAiGenerateVO result;

        private Job(String jobId) {
            this.jobId = jobId;
        }

        private synchronized void onStage(String code, String name, String stageStatus, String message) {
            currentCode = code;
            currentMessage = message;
            boolean updated = false;
            for (GeoPipelineStageVO s : stages) {
                if (code.equals(s.getCode())) {
                    s.setName(name);
                    s.setStatus(stageStatus);
                    s.setMessage(message);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                stages.add(new GeoPipelineStageVO(code, name, stageStatus, message));
            }
        }

        private synchronized void replaceStages(List<GeoPipelineStageVO> next) {
            stages.clear();
            if (next != null) {
                stages.addAll(next);
            }
        }

        private synchronized GeoAiGenerateJobVO snapshot() {
            GeoAiGenerateJobVO vo = new GeoAiGenerateJobVO();
            vo.setJobId(jobId);
            vo.setStatus(status);
            vo.setCurrentCode(currentCode);
            vo.setCurrentMessage(currentMessage);
            List<GeoPipelineStageVO> copy = new ArrayList<>(stages.size());
            for (GeoPipelineStageVO s : stages) {
                copy.add(new GeoPipelineStageVO(s.getCode(), s.getName(), s.getStatus(), s.getMessage()));
            }
            vo.setStages(copy);
            vo.setResult(result);
            return vo;
        }
    }
}
