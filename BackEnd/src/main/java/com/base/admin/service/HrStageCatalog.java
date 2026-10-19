package com.base.admin.service;

import com.base.admin.domain.vo.HrStageCatalogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 招聘候选人阶段中央目录：展示名、流水线大段、看板漏斗 bucket 的唯一映射入口。
 * <p>
 * 一期：不改变存库 code，只统一展示与聚合口径。漏斗「电话通过」单独一层；「复试」仅二面。
 */
@Component
@RequiredArgsConstructor
public class HrStageCatalog {

    public static final String PHASE_SCREEN = "SCREEN";
    public static final String PHASE_PHONE = "PHONE";
    public static final String PHASE_INTERVIEW = "INTERVIEW";
    public static final String PHASE_ONBOARD = "ONBOARD";

    private static final List<PhaseDef> PHASES = List.of(
            new PhaseDef(PHASE_SCREEN, "简历筛选"),
            new PhaseDef(PHASE_PHONE, "电话沟通"),
            new PhaseDef(PHASE_INTERVIEW, "面试沟通"),
            new PhaseDef(PHASE_ONBOARD, "待入职"));

    /** 漏斗存量：互斥，一人只落一层；复试 = 仅二面。 */
    private static final List<FunnelBucketDef> FUNNEL = List.of(
            new FunnelBucketDef("SCREEN", "HR初筛通过", "相对上一层", Set.of("SCREEN_PASS")),
            new FunnelBucketDef("PHONE", "电话通过", "相对上一层", Set.of("PHONE_PASS")),
            new FunnelBucketDef("INVITE", "邀约成功", "相对上一层", Set.of("INVITED")),
            new FunnelBucketDef("FIRST", "候选人初试", "相对上一层",
                    Set.of("SHOW_UP", "FIRST_ROUND", "FIRST_PENDING", "R1_DISPUTE")),
            new FunnelBucketDef("RETEST", "候选人复试", "相对上一层",
                    Set.of("SECOND_ROUND", "R2_PENDING", "R2_DISPUTE")),
            new FunnelBucketDef("FINAL_INT", "候选人终试", "相对上一层", Set.of(
                    "R3_PASS", "R3_PENDING", "R3_DISPUTE",
                    "R4_PASS", "R4_PENDING", "R4_DISPUTE",
                    "R5_PASS", "R5_PENDING", "R5_DISPUTE")),
            new FunnelBucketDef("FINAL_PASS", "终试通过", "相对上一层", Set.of("FINAL")),
            new FunnelBucketDef("SALARY", "薪资沟通中", "相对上一层", Set.of("SALARY")),
            new FunnelBucketDef("BG_COLLECT", "背调资料收集中", "相对上一层", Set.of("BG_COLLECT")),
            new FunnelBucketDef("BG_CHECK", "背调中", "相对上一层", Set.of("BG_CHECK")),
            new FunnelBucketDef("MEDICAL", "体检中", "相对上一层", Set.of("MEDICAL")),
            new FunnelBucketDef("OFFER_PENDING", "待发offer", "相对上一层", Set.of("OFFER_PENDING")),
            new FunnelBucketDef("PENDING_ONBOARD", "待入职", "相对上一层", Set.of("PENDING_ONBOARD")),
            new FunnelBucketDef("OFFER", "发放Offer", "相对上一层", Set.of("OFFER_SENT")),
            new FunnelBucketDef("ACCEPT", "Offer接受", "相对上一层", Set.of("OFFER_ACCEPTED", "ACCEPTED")),
            new FunnelBucketDef("ONBOARD", "候选人入职", "相对上一层", Set.of("ONBOARDED")));

    /** 库无记录时的展示名兜底（与一期种子一致）。 */
    private static final Map<String, String> ALIASES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ENTERED", "待初筛");
        m.put("SCREEN_PASS", "初筛通过（待电话）");
        m.put("SCREEN_FAIL", "初筛不合适");
        m.put("PHONE_PASS", "电话通过（待邀约）");
        m.put("PHONE_FAIL", "电话不合适");
        m.put("INVITED", "已邀约（待到面）");
        m.put("SHOW_UP", "已到面（待出评价）");
        m.put("FIRST_PENDING", "一面待定");
        m.put("R1_DISPUTE", "一面待商榷");
        m.put("FIRST_ROUND", "一面通过");
        m.put("FIRST_FAIL", "一面未通过");
        m.put("R2_PENDING", "二面待定");
        m.put("R2_DISPUTE", "二面待商榷");
        m.put("R2_FAIL", "二面未通过");
        m.put("SECOND_ROUND", "二面通过");
        m.put("R3_PASS", "三面通过");
        m.put("R3_PENDING", "三面待定");
        m.put("R3_DISPUTE", "三面待商榷");
        m.put("R3_FAIL", "三面未通过");
        m.put("R4_PASS", "四面通过");
        m.put("R4_PENDING", "四面待定");
        m.put("R4_DISPUTE", "四面待商榷");
        m.put("R4_FAIL", "四面未通过");
        m.put("R5_PASS", "五面通过");
        m.put("R5_PENDING", "五面待定");
        m.put("R5_DISPUTE", "五面待商榷");
        m.put("R5_FAIL", "五面未通过");
        m.put("FINAL", "终面通过");
        m.put("SALARY", "薪资沟通中");
        m.put("BG_COLLECT", "背调资料收集中");
        m.put("BG_CHECK", "背调中");
        m.put("MEDICAL", "体检中");
        m.put("OFFER_PENDING", "待发offer");
        m.put("PENDING_ONBOARD", "待入职");
        m.put("OFFER_SENT", "发放Offer");
        m.put("OFFER_ACCEPTED", "Offer接受");
        m.put("ACCEPTED", "Offer接受");
        m.put("CANDIDATE_REJECT", "候选人拒绝");
        m.put("ONBOARDED", "已入职");
        ALIASES = Collections.unmodifiableMap(m);
    }

    private static final Set<String> ONBOARD_STAGES = Set.of(
            "FINAL", "SALARY", "BG_COLLECT", "BG_CHECK", "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD",
            "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");

    private final JdbcTemplate jdbc;

    public List<PhaseDef> phases() {
        return PHASES;
    }

    public List<FunnelBucketDef> funnelBuckets() {
        return FUNNEL;
    }

    public String phaseLabel(String phase) {
        if (phase == null) {
            return "";
        }
        for (PhaseDef def : PHASES) {
            if (def.code().equals(phase)) {
                return def.name();
            }
        }
        return phase;
    }

    /**
     * 流水线四列：SCREEN / PHONE / INTERVIEW / ONBOARD。
     * 与历史 {@code HrPipelineService.resolvePhase} 口径一致。
     */
    public String phaseOf(String stage) {
        if (!StringUtils.hasText(stage)) {
            return PHASE_SCREEN;
        }
        String code = stage.trim().toUpperCase(Locale.ROOT);
        if ("ENTERED".equals(code) || "SCREEN_FAIL".equals(code)) {
            return PHASE_SCREEN;
        }
        if ("SCREEN_PASS".equals(code) || "PHONE_FAIL".equals(code)) {
            return PHASE_PHONE;
        }
        if (ONBOARD_STAGES.contains(code)) {
            return PHASE_ONBOARD;
        }
        if ("PHONE_PASS".equals(code) || code.startsWith("FIRST") || code.startsWith("SECOND")
                || code.startsWith("R") || "INVITED".equals(code) || "SHOW_UP".equals(code)
                || "CANDIDATE_REJECT".equals(code)) {
            return PHASE_INTERVIEW;
        }
        return PHASE_SCREEN;
    }

    public String funnelBucketOf(String stage) {
        if (!StringUtils.hasText(stage)) {
            return null;
        }
        String code = stage.trim().toUpperCase(Locale.ROOT);
        for (FunnelBucketDef bucket : FUNNEL) {
            if (bucket.memberCodes().contains(code)) {
                return bucket.code();
            }
        }
        return null;
    }

    public Set<String> codesInFunnelBucket(String bucketCode) {
        if (!StringUtils.hasText(bucketCode)) {
            return Set.of();
        }
        String code = bucketCode.trim().toUpperCase(Locale.ROOT);
        for (FunnelBucketDef bucket : FUNNEL) {
            if (bucket.code().equals(code)) {
                return bucket.memberCodes();
            }
        }
        return Set.of();
    }

    public boolean funnelHit(String stage, int funnelIndex) {
        if (funnelIndex < 0 || funnelIndex >= FUNNEL.size()) {
            return false;
        }
        if (!StringUtils.hasText(stage)) {
            return false;
        }
        return FUNNEL.get(funnelIndex).memberCodes().contains(stage.trim().toUpperCase(Locale.ROOT));
    }

    public String displayName(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        String key = code.trim().toUpperCase(Locale.ROOT);
        String fromDb = jdbc.query("""
                SELECT stage_name FROM hr_stage_def WHERE stage_code = ? AND is_active = 1 LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null, key);
        if (StringUtils.hasText(fromDb)) {
            return fromDb;
        }
        return ALIASES.getOrDefault(key, key);
    }

    public boolean isTerminal(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String key = code.trim().toUpperCase(Locale.ROOT);
        Integer terminal = jdbc.query("""
                SELECT terminal FROM hr_stage_def WHERE stage_code = ? AND is_active = 1 LIMIT 1
                """, rs -> rs.next() ? rs.getInt(1) : null, key);
        if (terminal != null) {
            return terminal == 1;
        }
        return key.endsWith("_FAIL") || "SCREEN_FAIL".equals(key) || "PHONE_FAIL".equals(key)
                || "CANDIDATE_REJECT".equals(key) || "ONBOARDED".equals(key);
    }

    /** 二面通过（复试通过）阶段码；一期「复试」仅二面。 */
    public Set<String> retestPassCodes() {
        return Set.of("SECOND_ROUND");
    }

    public HrStageCatalogVO catalog() {
        HrStageCatalogVO vo = new HrStageCatalogVO();
        Map<String, StageRow> db = loadStageRows();
        Set<String> allCodes = new LinkedHashSet<>(db.keySet());
        allCodes.addAll(ALIASES.keySet());

        List<HrStageCatalogVO.StageItem> stages = new ArrayList<>();
        for (String code : sortedCodes(allCodes, db)) {
            StageRow row = db.get(code);
            HrStageCatalogVO.StageItem item = new HrStageCatalogVO.StageItem();
            item.setCode(code);
            item.setName(row != null && StringUtils.hasText(row.name()) ? row.name() : ALIASES.getOrDefault(code, code));
            item.setSortNo(row != null ? row.sortNo() : null);
            item.setTerminal(row != null ? row.terminal() : isTerminal(code));
            item.setPhase(phaseOf(code));
            item.setFunnelBucket(funnelBucketOf(code));
            stages.add(item);
        }
        vo.setStages(stages);

        List<HrStageCatalogVO.PhaseItem> phases = new ArrayList<>();
        for (int i = 0; i < PHASES.size(); i++) {
            PhaseDef def = PHASES.get(i);
            HrStageCatalogVO.PhaseItem item = new HrStageCatalogVO.PhaseItem();
            item.setCode(def.code());
            item.setName(def.name());
            item.setOrder(i);
            phases.add(item);
        }
        vo.setPhases(phases);

        List<HrStageCatalogVO.FunnelItem> funnel = new ArrayList<>();
        for (int i = 0; i < FUNNEL.size(); i++) {
            FunnelBucketDef def = FUNNEL.get(i);
            HrStageCatalogVO.FunnelItem item = new HrStageCatalogVO.FunnelItem();
            item.setCode(def.code());
            item.setName(def.name());
            item.setOrder(i);
            item.setConversionLabel(def.conversionLabel());
            item.setMemberStageCodes(new ArrayList<>(def.memberCodes()));
            funnel.add(item);
        }
        vo.setFunnel(funnel);
        vo.setAliases(new LinkedHashMap<>(ALIASES));
        return vo;
    }

    private Map<String, StageRow> loadStageRows() {
        Map<String, StageRow> map = new LinkedHashMap<>();
        jdbc.query("""
                SELECT stage_code, stage_name, sort_no, terminal
                FROM hr_stage_def WHERE is_active = 1 ORDER BY sort_no, stage_code
                """, rs -> {
            while (rs.next()) {
                String code = rs.getString("stage_code");
                if (code == null) {
                    continue;
                }
                map.put(code.toUpperCase(Locale.ROOT), new StageRow(
                        rs.getString("stage_name"),
                        rs.getObject("sort_no") == null ? null : rs.getInt("sort_no"),
                        rs.getInt("terminal") == 1));
            }
            return null;
        });
        return map;
    }

    private static List<String> sortedCodes(Set<String> codes, Map<String, StageRow> db) {
        List<String> list = new ArrayList<>(codes);
        list.sort((a, b) -> {
            Integer sa = db.containsKey(a) ? db.get(a).sortNo() : null;
            Integer sb = db.containsKey(b) ? db.get(b).sortNo() : null;
            if (sa == null && sb == null) {
                return a.compareTo(b);
            }
            if (sa == null) {
                return 1;
            }
            if (sb == null) {
                return -1;
            }
            int c = Integer.compare(sa, sb);
            return c != 0 ? c : a.compareTo(b);
        });
        return list;
    }

    public record PhaseDef(String code, String name) {
    }

    public record FunnelBucketDef(String code, String name, String conversionLabel, Set<String> memberCodes) {
    }

    private record StageRow(String name, Integer sortNo, boolean terminal) {
    }
}
