package com.base.admin.service;

import com.base.admin.domain.dto.GeoContentAiGenerateDTO;
import com.base.admin.domain.vo.GeoAiGenerateJobVO;
import com.base.admin.domain.vo.GeoContentAiGenerateVO;
import com.base.admin.domain.vo.GeoPipelineStageVO;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 成文异步任务：启动后通过 SSE 推送阶段/打字机草稿，终态再带 result。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoAiGenerateJobService {

    private static final long KEEP_MS = 60 * 60 * 1000L;
    /** 单路 SSE 最长挂起（成文+深度思考可能较久） */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final GeoContentPlacementService contentPlacementService;
    private final ObjectMapper objectMapper;
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
                GeoContentAiGenerateVO result = contentPlacementService.generateArticle(dto, job);
                job.result = result;
                if (result != null && result.getStages() != null && !result.getStages().isEmpty()) {
                    job.replaceStages(result.getStages());
                }
                job.onStage("save", "写入草稿", "done", "已写入草稿");
                job.status = "SUCCESS";
                job.currentMessage = "生成完成";
                job.emitTerminal();
            } catch (Exception e) {
                job.status = "FAILED";
                job.currentMessage = brief(e);
                job.onStage(
                        job.currentCode != null ? job.currentCode : "qa",
                        "失败",
                        "failed",
                        brief(e));
                job.emitTerminal();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return job.snapshot();
    }

    public GeoAiGenerateJobVO get(String jobId) {
        return requireJob(jobId).snapshot();
    }

    /**
     * 订阅任务进度（SSE）。连接后立即推送当前快照；RUNNING 时持续推 stage/draft；终态推 done/error 后结束。
     */
    public SseEmitter subscribe(String jobId) {
        Job job = requireJob(jobId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        job.attach(emitter);
        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private Job requireJob(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new BusinessException("生成任务不存在或已过期");
        }
        return job;
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

    private final class Job implements GeoArticlePipelineService.ProgressListener {
        private final String jobId;
        private final long createdAt = System.currentTimeMillis();
        private final List<GeoPipelineStageVO> stages = new ArrayList<>();
        private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private volatile String status = "RUNNING";
        private volatile String currentCode;
        private volatile String currentMessage = "排队中…";
        private volatile String draftTitle;
        private volatile String draftHtml;
        private volatile GeoContentAiGenerateVO result;
        private volatile long lastDraftEmitAt;

        private Job(String jobId) {
            this.jobId = jobId;
        }

        private void attach(SseEmitter emitter) {
            emitters.add(emitter);
            emitter.onCompletion(() -> emitters.remove(emitter));
            emitter.onTimeout(() -> {
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            });
            emitter.onError(ex -> emitters.remove(emitter));
            // 先推当前快照，避免客户端错过已发生的阶段/草稿
            send(emitter, "progress", snapshot());
            if (!"RUNNING".equals(status)) {
                send(emitter, terminalEventName(), snapshot());
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
                emitters.remove(emitter);
            }
        }

        @Override
        public synchronized void onStage(String code, String name, String stageStatus, String message) {
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
            broadcast("progress", snapshot());
        }

        @Override
        public synchronized void onDraft(String title, String html) {
            if (StringUtils.hasText(title)) {
                draftTitle = title;
            }
            if (StringUtils.hasText(html)) {
                draftHtml = html;
            }
            long now = System.currentTimeMillis();
            // 打字机推送节流，避免 SSE 过密
            if (now - lastDraftEmitAt < 80) {
                return;
            }
            lastDraftEmitAt = now;
            broadcast("progress", snapshot());
        }

        private synchronized void replaceStages(List<GeoPipelineStageVO> next) {
            stages.clear();
            if (next != null) {
                stages.addAll(next);
            }
        }

        private void emitTerminal() {
            // 终态前再推一次完整草稿，避免节流丢掉末包
            broadcast("progress", snapshot());
            broadcast(terminalEventName(), snapshot());
            completeAll();
        }

        private String terminalEventName() {
            return "SUCCESS".equals(status) ? "done" : "error";
        }

        private void broadcast(String event, GeoAiGenerateJobVO payload) {
            for (SseEmitter emitter : emitters) {
                send(emitter, event, payload);
            }
        }

        private void send(SseEmitter emitter, String event, GeoAiGenerateJobVO payload) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                emitter.send(SseEmitter.event()
                        .name(event)
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                emitters.remove(emitter);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // ignore
                }
            } catch (Exception e) {
                log.debug("SSE 推送失败 jobId={}: {}", jobId, e.getMessage());
                emitters.remove(emitter);
            }
        }

        private void completeAll() {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
            emitters.clear();
        }

        private synchronized GeoAiGenerateJobVO snapshot() {
            GeoAiGenerateJobVO vo = new GeoAiGenerateJobVO();
            vo.setJobId(jobId);
            vo.setStatus(status);
            vo.setCurrentCode(currentCode);
            vo.setCurrentMessage(currentMessage);
            vo.setDraftTitle(draftTitle);
            vo.setDraftHtml(draftHtml);
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
