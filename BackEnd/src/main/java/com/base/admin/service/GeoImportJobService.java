package com.base.admin.service;

import com.base.admin.domain.vo.GeoImportJobVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.GeoImportProgress;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class GeoImportJobService {

    private static final long KEEP_MS = 30 * 60 * 1000L;

    private final GeoMonitorService monitorService;
    private final GeoContentPlacementService contentPlacementService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "geo-import");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public GeoImportJobVO startDaily(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("请上传 Excel 文件");
        }
        return submit("正在导入日监测", () -> monitorService.importDaily(new ByteArrayInputStream(bytes)));
    }

    public GeoImportJobVO startPlacement(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("请上传 Excel 文件");
        }
        return submit("正在导入内容投放", () -> contentPlacementService.importExcel(new ByteArrayInputStream(bytes)));
    }

    public GeoImportJobVO get(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new BusinessException("导入任务不存在或已过期");
        }
        return job.snapshot();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private GeoImportJobVO submit(String preparing, ImportCall call) {
        purge();
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, preparing);
        jobs.put(jobId, job);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        executor.submit(() -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            GeoImportProgress.bind(job.progress);
            try {
                GeoImportResultVO result = call.run();
                job.result = result;
                job.status = "SUCCESS";
                job.message = "导入完成";
            } catch (Exception e) {
                job.status = "FAILED";
                job.message = brief(e);
            } finally {
                GeoImportProgress.clear();
                SecurityContextHolder.clearContext();
            }
        });
        return job.snapshot();
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
            return "导入失败";
        }
        return msg.length() > 180 ? msg.substring(0, 180) : msg;
    }

    @FunctionalInterface
    private interface ImportCall {
        GeoImportResultVO run() throws Exception;
    }

    private static final class Job {
        private final String jobId;
        private final long createdAt = System.currentTimeMillis();
        private final GeoImportProgress progress = new GeoImportProgress();
        private volatile String status = "RUNNING";
        private volatile String message;
        private volatile GeoImportResultVO result;

        private Job(String jobId, String message) {
            this.jobId = jobId;
            this.message = message;
        }

        private GeoImportJobVO snapshot() {
            GeoImportJobVO vo = new GeoImportJobVO();
            vo.setJobId(jobId);
            vo.setStatus(status);
            vo.setTotal(progress.getTotal());
            vo.setProcessed(progress.getProcessed());
            vo.setMessage("SUCCESS".equals(status) ? "导入完成" : message);
            if ("RUNNING".equals(status) && progress.getTotal() > 0) {
                vo.setMessage(progress.getMessage());
            }
            vo.setPercent("SUCCESS".equals(status) ? 100 : progress.percent());
            vo.setResult(result);
            return vo;
        }
    }
}
