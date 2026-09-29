package com.base.admin.service.ecom;

import com.base.admin.domain.vo.EcomImportJobVO;
import com.base.admin.domain.vo.EcomImportResultVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.GeoImportProgress;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class EcomImportJobService {

    private static final long KEEP_MS = 30 * 60 * 1000L;

    private final EcomImportService importService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ecom-import");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public EcomImportJobVO start(byte[] bytes, String fileName, boolean ignoreLocked, boolean forceUpdate) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("请上传文件");
        }
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名不能为空");
        }
        purge();
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, "正在解析并导入");
        jobs.put(jobId, job);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        byte[] copy = bytes;
        String name = fileName;
        executor.submit(() -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            GeoImportProgress.bind(job.progress);
            try {
                EcomImportResultVO result = importService.importFile(copy, name, ignoreLocked, forceUpdate);
                job.result = result;
                if (result.isNeedConfirm()) {
                    job.status = "NEED_CONFIRM";
                    job.message = result.getMessage();
                } else {
                    job.status = "SUCCESS";
                    job.message = result.getMessage();
                }
            } catch (Exception e) {
                job.status = "FAILED";
                job.message = e.getMessage() == null ? "导入失败" : e.getMessage();
            } finally {
                GeoImportProgress.clear();
                SecurityContextHolder.clearContext();
                job.finishedAt = System.currentTimeMillis();
            }
        });
        return job.snapshot();
    }

    public EcomImportJobVO get(String jobId) {
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

    private void purge() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Job>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Job job = it.next().getValue();
            if (job.finishedAt > 0 && now - job.finishedAt > KEEP_MS) {
                it.remove();
            }
        }
    }

    private static final class Job {
        private final String jobId;
        private final GeoImportProgress progress = new GeoImportProgress();
        private volatile String status = "RUNNING";
        private volatile String message;
        private volatile EcomImportResultVO result;
        private volatile long finishedAt;

        private Job(String jobId, String message) {
            this.jobId = jobId;
            this.message = message;
        }

        private EcomImportJobVO snapshot() {
            EcomImportJobVO vo = new EcomImportJobVO();
            vo.setJobId(jobId);
            vo.setStatus(status);
            vo.setTotal(progress.getTotal());
            vo.setProcessed(progress.getProcessed());
            vo.setPercent(progress.percent());
            vo.setMessage(message);
            vo.setResult(result);
            return vo;
        }
    }
}
