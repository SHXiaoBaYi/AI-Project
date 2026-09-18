package com.base.admin.util;

/**
 * 当前线程的导入进度。未绑定时 report 为空操作，样例刷数不受影响。
 */
public final class GeoImportProgress {

    private static final ThreadLocal<GeoImportProgress> CURRENT = new ThreadLocal<>();

    private volatile int total;
    private volatile int processed;
    private volatile String message = "正在准备";

    public GeoImportProgress() {}

    public static GeoImportProgress bind() {
        GeoImportProgress progress = new GeoImportProgress();
        CURRENT.set(progress);
        return progress;
    }

    public static void bind(GeoImportProgress progress) {
        CURRENT.set(progress);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void report(int processed, int total) {
        GeoImportProgress progress = CURRENT.get();
        if (progress == null) {
            return;
        }
        int safeTotal = Math.max(total, 0);
        int safeProcessed = Math.max(processed, 0);
        if (safeTotal > 0 && safeProcessed > safeTotal) {
            safeProcessed = safeTotal;
        }
        progress.total = safeTotal;
        progress.processed = safeProcessed;
        progress.message = safeTotal <= 0
                ? "正在准备"
                : "已处理 " + safeProcessed + " / " + safeTotal;
    }

    public int getTotal() {
        return total;
    }

    public int getProcessed() {
        return processed;
    }

    public String getMessage() {
        return message;
    }

    public int percent() {
        if (total <= 0) {
            return 0;
        }
        return Math.min(99, processed * 100 / total);
    }
}
