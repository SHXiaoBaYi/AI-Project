package com.base.admin.util;

import org.apache.poi.ss.usermodel.Sheet;

import java.util.ArrayList;
import java.util.List;

/**
 * 日监测宽表的指标列位置。按表头识别「提及 / 排名 / 推荐 / 截图 / 链接 / 负面 / 竞品」，
 * 每个指标下各有一列对应一个 AI 平台。没有「链接」表头的旧表仍按 6 个指标的固定位置读取。
 */
public final class GeoDailyImportLayout {

    /** 增加「链接」列之前的指标个数 */
    private static final int LEGACY_METRIC_COUNT = 6;

    private static final int KIND_NONE = 0;
    private static final int KIND_MENTION = 1;
    private static final int KIND_RANK = 2;
    private static final int KIND_RECOMMEND = 3;
    private static final int KIND_SHOT = 4;
    private static final int KIND_LINK = 5;
    private static final int KIND_NEGATIVE = 6;
    private static final int KIND_COMPETITOR = 7;

    private GeoDailyImportLayout() {}

    /**
     * @param metricRow 指标表头行（「提及情况」「链接」所在行）
     * @param start     日期块起始列（含）
     * @param end       日期块结束列（不含）
     */
    public static MetricBases resolve(Sheet sheet, int metricRow, int start, int end) {
        if (sheet == null || end <= start) {
            return MetricBases.empty();
        }
        List<int[]> markers = new ArrayList<>();
        int prevKind = KIND_NONE;
        for (int col = start; col < end; col++) {
            int kind = classify(normalizeHeader(ExcelCellUtils.str(sheet, metricRow, col)));
            if (kind == KIND_NONE) {
                prevKind = KIND_NONE;
                continue;
            }
            if (kind == prevKind) {
                continue;
            }
            markers.add(new int[]{col, kind});
            prevKind = kind;
        }
        int mentionCol = find(markers, KIND_MENTION);
        int rankCol = find(markers, KIND_RANK);
        int span = -1;
        if (mentionCol >= 0 && rankCol > mentionCol) {
            span = rankCol - mentionCol;
        } else if (markers.size() >= 2) {
            span = markers.get(1)[0] - markers.get(0)[0];
        }
        if (span >= 1 && mentionCol >= 0) {
            if (mentionCol + span > end) {
                span = end - mentionCol;
            }
            if (span < 1) {
                return MetricBases.empty();
            }
            return new MetricBases(
                    span,
                    mentionCol,
                    rankCol > mentionCol ? rankCol : -1,
                    find(markers, KIND_RECOMMEND),
                    find(markers, KIND_SHOT),
                    find(markers, KIND_LINK),
                    find(markers, KIND_NEGATIVE),
                    find(markers, KIND_COMPETITOR));
        }
        int legacy = (end - start) / LEGACY_METRIC_COUNT;
        if (legacy < 1) {
            return MetricBases.empty();
        }
        return new MetricBases(
                legacy,
                start,
                start + legacy,
                start + 2 * legacy,
                start + 3 * legacy,
                -1,
                start + 4 * legacy,
                start + 5 * legacy);
    }

    private static int find(List<int[]> markers, int kind) {
        for (int[] marker : markers) {
            if (marker[1] == kind) {
                return marker[0];
            }
        }
        return -1;
    }

    private static int classify(String header) {
        if (header == null || header.isBlank()) {
            return KIND_NONE;
        }
        if (header.contains("截图")) {
            return KIND_SHOT;
        }
        if (header.contains("链接")) {
            return KIND_LINK;
        }
        if (header.contains("提及")) {
            return KIND_MENTION;
        }
        if (header.contains("排名")) {
            return KIND_RANK;
        }
        if (header.contains("推荐")) {
            return KIND_RECOMMEND;
        }
        if (header.contains("负面") || header.contains("错误")) {
            return KIND_NEGATIVE;
        }
        if (header.contains("竞品")) {
            return KIND_COMPETITOR;
        }
        return KIND_NONE;
    }

    private static String normalizeHeader(String raw) {
        return raw == null ? "" : raw.replace(" ", "").replace("（必填）", "").replace("(必填)", "").trim();
    }

    public record MetricBases(
            int platformCount,
            int mentionBase,
            int rankBase,
            int recommendBase,
            int shotBase,
            int linkBase,
            int negativeBase,
            int competitorBase) {

        public static MetricBases empty() {
            return new MetricBases(0, -1, -1, -1, -1, -1, -1, -1);
        }
    }
}
