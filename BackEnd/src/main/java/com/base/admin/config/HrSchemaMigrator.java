package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class HrSchemaMigrator implements ApplicationRunner {

    static final String DEFAULT_PASSWORD = "Hr@123456";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String sql = StreamUtils.copyToString(new ClassPathResource("db/v20_hr.sql").getInputStream(), StandardCharsets.UTF_8);
        for (String statement : splitSql(sql)) {
            jdbc.execute(statement);
        }
        seedRoles();
        seedUsers();
        seedMenus();
        seedAliases();
        cleanupOrphanOwnerAliases();
        cleanupEmptyInterviewRecords();
        seedTaskType();
        ensureFailReasonColumn();
        ensureInviteCcEventColumn();
        ensureCandidatePortfolioTable();
        ensureRequisitionCardColumns();
        ensurePipelineSchema();
        ensureKpiGradeColumns();
        ensureBaseDictTable();
        ensureRequisitionPanelColumns();
        backfillWorkAddressFromLocation();
        ensureCandidateProfileColumns();
        alignStageDisplayNames();
        ensureKpiAssessmentSchema();
        log.info("招聘表结构、角色、用户和菜单已同步");
    }

    /**
     * 软删无结论且无评语的空壳面试记录（历史上由「轮次→记录」启动灌数产生）。
     * 面试记录应以真实评价为准；仅有邀约不应出现在面试记录列表。
     */
    private void cleanupEmptyInterviewRecords() {
        int n = jdbc.update("""
                UPDATE hr_interview_record
                SET is_active = 0, update_by = 'hr-cleanup-empty-record'
                WHERE is_active = 1
                  AND (conclusion IS NULL OR TRIM(conclusion) = '')
                  AND (comment IS NULL OR TRIM(comment) = '')
                """);
        if (n > 0) {
            log.info("已软删 {} 条无结论且无评语的空壳面试记录", n);
        }
    }

    /** 一期：统一 hr_stage_def 展示名；电话通过进漏斗可见。 */
    private void alignStageDisplayNames() {
        Object[][] rows = {
                {"ENTERED", "待初筛", 10, 0, 0},
                {"SCREEN_PASS", "初筛通过（待电话）", 20, 1, 0},
                {"SCREEN_FAIL", "初筛不合适", 21, 0, 1},
                {"PHONE_PASS", "电话通过（待邀约）", 25, 1, 0},
                {"PHONE_FAIL", "电话不合适", 26, 0, 1},
                {"INVITED", "已邀约（待到面）", 30, 1, 0},
                {"SHOW_UP", "已到面（待出评价）", 40, 1, 0},
                {"FIRST_PENDING", "一面待定", 50, 0, 0},
                {"R1_DISPUTE", "一面待商榷", 55, 0, 0},
                {"FIRST_ROUND", "一面通过", 60, 1, 0},
                {"FIRST_FAIL", "一面未通过", 70, 0, 1},
                {"R2_PENDING", "二面待定", 72, 0, 0},
                {"R2_DISPUTE", "二面待商榷", 73, 0, 0},
                {"R2_FAIL", "二面未通过", 74, 0, 1},
                {"SECOND_ROUND", "二面通过", 80, 1, 0},
                {"R3_PASS", "三面通过", 81, 1, 0},
                {"R3_PENDING", "三面待定", 82, 0, 0},
                {"R3_DISPUTE", "三面待商榷", 83, 0, 0},
                {"R3_FAIL", "三面未通过", 84, 0, 1},
                {"R4_PASS", "四面通过", 85, 1, 0},
                {"R4_PENDING", "四面待定", 86, 0, 0},
                {"R4_DISPUTE", "四面待商榷", 87, 0, 0},
                {"R4_FAIL", "四面未通过", 88, 0, 1},
                {"R5_PASS", "五面通过", 89, 1, 0},
                {"FINAL", "终面通过", 90, 1, 0},
                {"SALARY", "薪资沟通中", 91, 1, 0},
                {"BG_COLLECT", "背调资料收集中", 92, 1, 0},
                {"BG_CHECK", "背调中", 93, 1, 0},
                {"MEDICAL", "体检中", 94, 1, 0},
                {"OFFER_PENDING", "待发offer", 95, 1, 0},
                {"PENDING_ONBOARD", "待入职", 96, 1, 0},
                {"R5_PENDING", "五面待定", 97, 0, 0},
                {"R5_DISPUTE", "五面待商榷", 98, 0, 0},
                {"R5_FAIL", "五面未通过", 99, 0, 1},
                {"OFFER_SENT", "发放Offer", 100, 0, 0},
                {"OFFER_ACCEPTED", "Offer接受", 110, 0, 0},
                {"CANDIDATE_REJECT", "候选人拒绝", 120, 0, 1},
                {"ONBOARDED", "已入职", 130, 1, 1},
        };
        for (Object[] row : rows) {
            jdbc.update("""
                    INSERT INTO hr_stage_def (stage_code, stage_name, sort_no, funnel_visible, data_ready, terminal, is_active)
                    VALUES (?, ?, ?, ?, 1, ?, 1)
                    ON DUPLICATE KEY UPDATE
                      stage_name = VALUES(stage_name),
                      sort_no = VALUES(sort_no),
                      funnel_visible = VALUES(funnel_visible),
                      terminal = VALUES(terminal),
                      is_active = 1
                    """, row[0], row[1], row[2], row[3], row[4]);
        }
    }

    private void ensureKpiGradeColumns() {
        addColumnIfMissing("hr_requisition", "importance_level",
                "ALTER TABLE hr_requisition ADD COLUMN importance_level TINYINT NULL COMMENT '重要性1高2中3低' AFTER priority");
        addColumnIfMissing("hr_requisition", "urgency_level",
                "ALTER TABLE hr_requisition ADD COLUMN urgency_level TINYINT NULL COMMENT '紧急程度1紧急2常规' AFTER importance_level");
        addColumnIfMissing("hr_requisition", "difficulty_level",
                "ALTER TABLE hr_requisition ADD COLUMN difficulty_level TINYINT NULL COMMENT '难度1高2中3低' AFTER urgency_level");
    }

    private void ensureRequisitionCardColumns() {
        addColumnIfMissing("hr_requisition", "salary_range",
                "ALTER TABLE hr_requisition ADD COLUMN salary_range VARCHAR(64) NULL COMMENT '薪资范围' AFTER job_desc");
        addColumnIfMissing("hr_requisition", "education_req",
                "ALTER TABLE hr_requisition ADD COLUMN education_req VARCHAR(64) NULL COMMENT '学历要求' AFTER salary_range");
        addColumnIfMissing("hr_requisition", "experience_req",
                "ALTER TABLE hr_requisition ADD COLUMN experience_req VARCHAR(64) NULL COMMENT '经验要求' AFTER education_req");
        addColumnIfMissing("hr_requisition", "skill_req",
                "ALTER TABLE hr_requisition ADD COLUMN skill_req VARCHAR(255) NULL COMMENT '技能要求' AFTER experience_req");
    }

    private void ensurePipelineSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS hr_phone_screen (
                  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  application_id  BIGINT       NOT NULL                COMMENT '投递',
                  called_at       DATETIME     NOT NULL                COMMENT '沟通时间',
                  result          VARCHAR(16)  NOT NULL                COMMENT 'PASS/FAIL',
                  interview_at    DATETIME     NULL                    COMMENT '约面时间',
                  reject_reason   VARCHAR(255) NULL                    COMMENT '不合适原因',
                  remark          VARCHAR(500) NULL                    COMMENT '备注',
                  create_by       VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
                  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by       VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_hr_phone_screen_app (application_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电话沟通'
                """);
        addColumnIfMissing("hr_offer", "salary_amount",
                "ALTER TABLE hr_offer ADD COLUMN salary_amount DECIMAL(12,2) NULL COMMENT '最终offer金额' AFTER application_id");
        addColumnIfMissing("hr_application", "screen_reject_reason",
                "ALTER TABLE hr_application ADD COLUMN screen_reject_reason VARCHAR(255) NULL COMMENT '初筛不合适原因' AFTER screen_result");
        jdbc.update("""
                INSERT INTO hr_stage_def (stage_code, stage_name, sort_no, funnel_visible, data_ready, terminal, is_active) VALUES
                ('SCREEN_FAIL', '初筛不合适', 21, 0, 1, 1, 1),
                ('PHONE_PASS', '电话通过（待邀约）', 25, 1, 1, 0, 1),
                ('PHONE_FAIL', '电话不合适', 26, 0, 1, 1, 1)
                ON DUPLICATE KEY UPDATE stage_name = VALUES(stage_name), sort_no = VALUES(sort_no),
                  funnel_visible = VALUES(funnel_visible), data_ready = VALUES(data_ready),
                  terminal = VALUES(terminal)
                """);
    }

    private void ensureCandidatePortfolioTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS hr_candidate_portfolio (
                  id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                  candidate_id    BIGINT        NOT NULL                COMMENT '候选人',
                  file_name       VARCHAR(512)  NOT NULL                COMMENT '文件名',
                  storage_path    VARCHAR(512)  NOT NULL                COMMENT '本地路径',
                  file_ext        VARCHAR(16)   NULL                    COMMENT '扩展名',
                  file_size       BIGINT        NOT NULL DEFAULT 0      COMMENT '字节数',
                  create_by       VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
                  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by       VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
                  update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
                  PRIMARY KEY (id),
                  KEY idx_hr_portfolio_candidate (candidate_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选人作品集附件'
                """);
    }

    private void seedRoles() {
        insertRole("招聘管理员", "hr_admin", 10, "全部招聘数据、导出、邀约、主数据");
        insertRole("招聘负责人", "hr_owner", 11, "只看自己负责的需求");
        insertRole("高管", "hr_exec", 12, "全公司看板可下钻，不可改主数据");
        insertRole("普通账号", "hr_user", 13, "看板只读");
        insertRole("面试官", "hr_interviewer", 14, "只看自己参与的面试");
    }

    private void insertRole(String name, String key, int sort, String remark) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM sys_role WHERE role_key = ?", Integer.class, key);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO sys_role (role_name, role_key, sort_order, status, remark, is_active)
                VALUES (?, ?, ?, 0, ?, 1)
                """, name, key, sort, remark);
    }

    private void seedUsers() {
        String hash = passwordEncoder.encode(DEFAULT_PASSWORD);
        long hrAdmin = roleId("hr_admin");
        long owner = roleId("hr_owner");
        long interviewer = roleId("hr_interviewer");
        long plain = roleId("hr_user");
        user("wangyan", "王艳", hash, "简历提交主力，招聘管理员", hrAdmin);
        user("zhuyiling", "朱怡领", hash, "简历提交与一面面试官，招聘管理员", hrAdmin);
        user("pangzhuowen", "庞焯文", hash, "岗位负责人（庞）与一面主面试官", owner, interviewer);
        user("suwenyue", "苏文越", hash, "一面面试官", interviewer);
        user("bella", "Bella", hash, "一面、二面面试官", interviewer);
        user("chenbiao", "陈彪", hash, "一面面试官", interviewer);
        user("maoyijun", "毛依俊", hash, "二面面试官", interviewer);
        user("wangfangyang", "王方扬", hash, "底稿中出现的更新人，普通只读", plain);
    }

    private void user(String username, String nickname, String hash, String remark, long... roleIds) {
        Long userId = jdbc.query("SELECT user_id FROM sys_user WHERE username = ?",
                rs -> rs.next() ? rs.getLong(1) : null, username);
        if (userId == null) {
            jdbc.update("""
                    INSERT INTO sys_user (username, password, nickname, status, remark, is_active)
                    VALUES (?, ?, ?, 0, ?, 1)
                    """, username, hash, nickname, remark);
            userId = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username = ?", Long.class, username);
            log.info("已创建招聘用户 {} / {}", username, nickname);
        }
        for (long roleId : roleIds) {
            Integer linked = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM sys_user_role WHERE user_id = ? AND role_id = ?",
                    Integer.class, userId, roleId);
            if (linked == null || linked == 0) {
                jdbc.update("INSERT IGNORE INTO sys_user_role (user_id, role_id, is_active) VALUES (?, ?, 1)", userId, roleId);
            }
        }
    }

    private long roleId(String key) {
        return jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_key = ?", Long.class, key);
    }

    private void seedMenus() {
        menu(200, "招聘管理", 0, 3, "/hr", "", "M", "", "TeamOutlined", "招聘需求、候选人与基础数据");
        int boardParent = menuId("数据看板", "M");
        int systemParent = menuId("系统设置", "M");
        if (systemParent == 0) {
            systemParent = menuId("系统管理", "M");
        }
        menu(201, "招聘看板", boardParent == 0 ? 200 : boardParent, 4, "hr/board", "hr/board/index", "C", "hr:board:view", "DashboardOutlined", "漏斗、周期、HC、面试");
        menu(249, "招聘面板", 200, 0, "hr/panel", "hr/panel/index", "C", "hr:panel:view", "LayoutOutlined", "在招岗位、今日进展与岗位列表");
        menu(202, "招聘需求", 200, 1, "hr/requisition", "hr/requisition/index", "C", "hr:requisition:list", "ProfileOutlined", "HC与面试流程");
        menu(234, "岗位看板", 200, 2, "hr/job-board", "hr/job-board/index", "C", "hr:job-board:list", "AppstoreOutlined", "按部门分组的岗位信息卡片");
        menu(235, "简历流程", 200, 3, "hr/pipeline", "hr/pipeline/index", "C", "hr:pipeline:list", "PartitionOutlined", "单岗位简历全流程跟踪");
        menu(236, "流程操作", 235, 1, "", "", "F", "hr:pipeline:edit", "#", "初筛/电话/待入职更新");
        menu(237, "KPI看板", 200, 4, "hr/kpi", "hr/kpi/index", "C", "hr:kpi:view", "FundProjectionScreenOutlined", "岗位分级与招聘人员绩效");
        menu(238, "KPI导出", 237, 1, "", "", "F", "hr:kpi:export", "#", "导出绩效报表");
        menu(203, "候选人", 200, 5, "hr/application", "hr/application/index", "C", "hr:application:list", "IdcardOutlined", "候选人与简历");
        menu(220, "面试邀约记录", 200, 6, "hr/invite", "hr/invite/index", "C", "hr:invite:list", "CalendarOutlined", "邀约并建钉钉日程");
        // 250–260 已占用为机器人菜单，面试日历用 261，考核得分用 262
        menu(261, "HR面试日历", 200, 6, "hr/calendar", "hr/calendar/index", "C", "hr:calendar:view", "ScheduleOutlined", "按月查看面试日程，可按负责人筛选");
        repairCalendarMenuIdConflict();
        menu(262, "考核得分", 200, 5, "hr/assessment", "hr/assessment/index", "C", "hr:kpi:view", "TrophyOutlined", "P0/P1考核任务与月度得分");
        menu(221, "面试记录", 200, 7, "hr/record", "hr/record/index", "C", "hr:record:list", "FormOutlined", "各轮面试官评语");
        menu(222, "我的面试", 200, 8, "hr/mine", "hr/mine/index", "C", "hr:interview:mine", "ScheduleOutlined", "面试官待面日程与结论");
        menu(204, "部门管理", systemParent == 0 ? 200 : systemParent, 9, "hr/department", "hr/department/index", "C", "hr:dept:list", "BankOutlined", "部门树");
        jdbc.update("UPDATE sys_menu SET is_active = 0 WHERE menu_id IN (228, 229)");
        menu(218, "基础数据", 200, 6, "hr/base", "", "M", "", "DatabaseOutlined", "招聘字典");
        menu(219, "院校信息", 218, 1, "hr/school", "hr/school/index", "C", "hr:school:list", "ReadOutlined", "国内院校与QS");
        menu(230, "目标到岗", 218, 2, "hr/target", "hr/target/index", "C", "hr:target:list", "FlagOutlined", "招聘需求可选的目标到岗");
        menu(231, "目标到岗编辑", 230, 1, "", "", "F", "hr:target:edit", "#", "");
        menu(232, "未通过原因", 218, 3, "hr/fail-reason", "hr/fail-reason/index", "C", "hr:fail-reason:list", "TagsOutlined", "面试评价未通过可选原因");
        menu(233, "未通过原因编辑", 232, 1, "", "", "F", "hr:fail-reason:edit", "#", "");
        menu(239, "工作地址", 218, 4, "hr/work-address", "hr/work-address/index", "C", "hr:work-address:list", "EnvironmentOutlined", "招聘工作地址");
        menu(240, "工作地址编辑", 239, 1, "", "", "F", "hr:work-address:edit", "#", "");
        menu(241, "背调不通过原因", 218, 5, "hr/bg-fail", "hr/bg-fail/index", "C", "hr:bg-fail:list", "StopOutlined", "背调不通过可选原因");
        menu(242, "背调不通过原因编辑", 241, 1, "", "", "F", "hr:bg-fail:edit", "#", "");
        menu(243, "offer未发原因", 218, 6, "hr/offer-unsent", "hr/offer-unsent/index", "C", "hr:offer-unsent:list", "ExceptionOutlined", "offer未发可选原因");
        menu(244, "offer未发原因编辑", 243, 1, "", "", "F", "hr:offer-unsent:edit", "#", "");
        menu(245, "电话&面试不合适理由", 218, 7, "hr/unsuitable-reason", "hr/unsuitable-reason/index", "C", "hr:unsuitable:list", "CommentOutlined", "电话沟通与面试不合适理由");
        menu(246, "电话&面试不合适理由编辑", 245, 1, "", "", "F", "hr:unsuitable:edit", "#", "");
        menu(247, "岗位优先级", 218, 8, "hr/job-priority", "hr/job-priority/index", "C", "hr:job-priority:list", "ThunderboltOutlined", "P0/P1等到岗优先级，独立于紧急/优先/常规");
        menu(248, "岗位优先级编辑", 247, 1, "", "", "F", "hr:job-priority:edit", "#", "");
        menu(263, "考核计分规则", 218, 9, "hr/kpi-rule", "hr/kpi-rule/index", "C", "hr:kpi-rule:view", "SettingOutlined", "P0入职计分、P1合格简历或入职、任务周期");
        menu(264, "考核计分规则编辑", 263, 1, "", "", "F", "hr:kpi-rule:edit", "#", "");
        menu(205, "发起邀约", 203, 1, "", "", "F", "hr:invite:add", "#", "");
        menu(206, "取消邀约", 203, 2, "", "", "F", "hr:invite:cancel", "#", "");
        menu(207, "看板导出", 201, 1, "", "", "F", "hr:board:export", "#", "");
        menu(208, "部门编辑", 204, 1, "", "", "F", "hr:dept:edit", "#", "");
        menu(209, "全部数据", 201, 2, "", "", "F", "hr:scope:all", "#", "不按负责人或面试官裁剪");
        menu(210, "负责范围", 201, 3, "", "", "F", "hr:scope:owner", "#", "只看自己负责的需求");
        menu(211, "面试范围", 201, 4, "", "", "F", "hr:scope:interviewer", "#", "只看自己的面试");
        menu(212, "需求新增", 202, 1, "", "", "F", "hr:requisition:add", "#", "");
        menu(213, "需求修改", 202, 2, "", "", "F", "hr:requisition:edit", "#", "含暂缓、归档、恢复");
        menu(214, "需求删除", 202, 3, "", "", "F", "hr:requisition:delete", "#", "");
        menu(215, "候选人新增", 203, 3, "", "", "F", "hr:application:add", "#", "");
        menu(216, "候选人修改", 203, 4, "", "", "F", "hr:application:edit", "#", "");
        menu(217, "候选人删除", 203, 5, "", "", "F", "hr:application:delete", "#", "");
        menu(223, "邀约修改", 220, 1, "", "", "F", "hr:invite:edit", "#", "");
        menu(224, "邀约删除", 220, 2, "", "", "F", "hr:invite:delete", "#", "");
        menu(225, "面试记录新增", 221, 1, "", "", "F", "hr:record:add", "#", "");
        menu(226, "面试记录修改", 221, 2, "", "", "F", "hr:record:edit", "#", "");
        menu(227, "面试记录删除", 221, 3, "", "", "F", "hr:record:delete", "#", "");

        long admin = roleId("admin");
        long hrAdmin = roleId("hr_admin");
        long owner = roleId("hr_owner");
        long exec = roleId("hr_exec");
        long plain = roleId("hr_user");
        long interviewer = roleId("hr_interviewer");
        grant(admin, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 261, 262, 263, 264);
        grant(hrAdmin, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 261, 262, 263, 264);
        grant(owner, 200, 201, 202, 203, 210, 218, 219, 220, 221, 222, 205, 225, 232, 234, 235, 236, 237, 239, 241, 243, 245, 247, 249, 261, 262, 263);
        grant(exec, 200, 201, 203, 207, 209, 218, 219, 220, 221, 232, 234, 235, 237, 238, 239, 241, 243, 245, 247, 249, 261, 262, 263);
        grant(plain, 200, 201, 209, 234, 235, 237, 249);
        grant(interviewer, 200, 201, 203, 211, 220, 221, 222, 225, 232, 234, 235, 249, 261);
    }

    private int menuId(String name, String type) {
        List<Integer> ids = jdbc.query("""
                SELECT menu_id FROM sys_menu
                WHERE menu_name = ? AND menu_type = ? AND is_active = 1
                ORDER BY menu_id LIMIT 1
                """, (rs, row) -> rs.getInt(1), name, type);
        return ids.isEmpty() ? 0 : ids.get(0);
    }

    /**
     * 曾误用 menu_id=250（机器人顶级菜单）。把误绑角色迁到 261，交还 250，并清掉招聘角色上误授的机器人顶级菜单。
     */
    private void repairCalendarMenuIdConflict() {
        Integer hijacked = jdbc.queryForObject("""
                SELECT COUNT(1) FROM sys_menu
                WHERE menu_id = 250 AND (path = 'hr/calendar' OR perms = 'hr:calendar:view')
                """, Integer.class);
        if (hijacked == null || hijacked == 0) {
            return;
        }
        jdbc.update("""
                INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT role_id, 261, 1 FROM sys_role_menu
                WHERE menu_id = 250 AND IFNULL(is_active, 1) = 1
                """);
        jdbc.update("""
                UPDATE sys_menu SET
                  menu_name = '机器人', parent_id = 0, sort_order = 45, path = '/robot', component = '',
                  menu_type = 'M', perms = '', icon = 'RobotOutlined', remark = '多意图机器人后台',
                  visible = 0, status = 0, is_active = 1
                WHERE menu_id = 250
                """);
        jdbc.update("""
                DELETE rm FROM sys_role_menu rm
                INNER JOIN sys_role r ON r.role_id = rm.role_id
                WHERE rm.menu_id = 250
                  AND r.role_key IN ('hr_admin', 'hr_owner', 'hr_exec', 'hr_interviewer', 'hr_user')
                """);
        log.info("已将误占的 menu_id=250 交还给机器人，HR面试日历改用 261");
    }

    private void menu(int id, String name, int parent, int sort, String path, String component, String type,
                      String perms, String icon, String remark) {
        SysMenuSeedSupport.upsert(jdbc, SysMenuIdRanges.Owner.HR, id, name, parent, sort, path, component, type,
                perms, icon, remark);
    }

    private void grant(long roleId, int... menuIds) {
        for (int menuId : menuIds) {
            jdbc.update("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active) VALUES (?, ?, 1);""", roleId, menuId);
        }
    }

    private void seedTaskType() {
        Integer ready = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'sys_task_type'
                """, Integer.class);
        if (ready == null || ready == 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO sys_task_type (type_name, sort_order, remark, is_active)
                SELECT '待创建面试日程', 30, '面试通过且还有下一轮时生成，由招聘需求负责人手动完成', 1
                FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = '待创建面试日程')
                """);
        jdbc.update("""
                INSERT INTO sys_task_type (type_name, sort_order, remark, is_active)
                SELECT '面试提醒', 28, '电话约面后提醒发起面试邀约；候选人有邀约记录后自动完成', 1
                FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = '面试提醒')
                """);
        Integer proof = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'sys_task_type' AND column_name = 'require_proof'
                """, Integer.class);
        if (proof != null && proof > 0) {
            jdbc.update("""
                    UPDATE sys_task_type
                    SET require_proof = 0, biz_type = '', assign_field = '', spawn_task_type = ''
                    WHERE type_name = '待创建面试日程'
                    """);
            jdbc.update("""
                    UPDATE sys_task_type
                    SET require_proof = 0, biz_type = 'hr_interview_remind', assign_field = '', spawn_task_type = ''
                    WHERE type_name = '面试提醒' AND is_active = 1
                    """);
            seedProofTaskType(40, "薪资沟通", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(41, "背调资料收集", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(42, "背调", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(43, "体检", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(44, "待发offer", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(45, "待入职", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(46, "办理候选人入职", "入职前任务全部完成后生成，完成时必须上传资料，可多份");
        }
    }

    private void ensureBaseDictTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS hr_base_dict (
                  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                  dict_type    VARCHAR(32)   NOT NULL                COMMENT 'WORK_ADDRESS/BG_FAIL/OFFER_UNSENT/UNSUITABLE/JOB_PRIORITY',
                  code         VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '编码，岗位优先级如P0/P1',
                  category     VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '地址类别，仅工作地址使用',
                  name         VARCHAR(128)  NOT NULL                COMMENT '名称',
                  sort_no      INT           NOT NULL DEFAULT 0      COMMENT '排序',
                  create_by    VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
                  create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by    VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
                  update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active    TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_hr_base_dict_name (dict_type, name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘基础数据字典'
                """);
        jdbc.update("""
                INSERT INTO hr_base_dict (dict_type, code, name, sort_no, create_by, is_active) VALUES
                ('WORK_ADDRESS', '', '上海', 1, 'hr-seed', 1),
                ('WORK_ADDRESS', '', '新疆', 2, 'hr-seed', 1),
                ('JOB_PRIORITY', 'P0', '本月必须到岗', 1, 'hr-seed', 1),
                ('JOB_PRIORITY', 'P1', 'x份合格简历/到岗', 2, 'hr-seed', 1)
                ON DUPLICATE KEY UPDATE code = VALUES(code), sort_no = VALUES(sort_no)
                """);
        addColumnIfMissing("hr_base_dict", "category",
                "ALTER TABLE hr_base_dict ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT '' COMMENT '地址类别，仅工作地址使用' AFTER code");
    }

    private void ensureRequisitionPanelColumns() {
        addColumnIfMissing("hr_requisition", "job_priority_code",
                "ALTER TABLE hr_requisition ADD COLUMN job_priority_code VARCHAR(32) NULL COMMENT '岗位优先级P0/P1，独立于priority紧急优先常规' AFTER priority");
        addColumnIfMissing("hr_requisition", "work_address_id",
                "ALTER TABLE hr_requisition ADD COLUMN work_address_id BIGINT NULL COMMENT '工作地址hr_base_dict.id' AFTER location_code");
        // 历史列 dingtalk_open_conversation_id 已废弃（催评改发面试官工作通知），保留列以免破坏存量库
    }

    /** P0/P1 考核任务 + 面试「简历是否合格」字段 */
    private void ensureKpiAssessmentSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS hr_kpi_task (
                  id                  BIGINT       NOT NULL AUTO_INCREMENT,
                  requisition_id      BIGINT       NOT NULL,
                  owner_user_id       BIGINT       NOT NULL,
                  slot_no             INT          NOT NULL DEFAULT 1 COMMENT '该负责人下第几条HC槽位',
                  priority_code       VARCHAR(16)  NOT NULL COMMENT 'P0/P1快照',
                  published_at        DATETIME     NOT NULL COMMENT '任务发布日',
                  due_at              DATETIME     NOT NULL COMMENT '到期日，可延期',
                  origin_due_at       DATETIME     NOT NULL COMMENT '首次到期日',
                  status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/DONE/CANCELLED',
                  done_at             DATETIME     NULL,
                  done_reason         VARCHAR(32)  NULL COMMENT 'ONBOARD/QUALIFIED_RESUME',
                  done_application_id BIGINT       NULL,
                  score               INT          NOT NULL DEFAULT 0,
                  create_by           VARCHAR(64)  DEFAULT '',
                  create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active           TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_hr_kpi_task_slot (requisition_id, owner_user_id, slot_no),
                  KEY idx_hr_kpi_task_due (due_at, status),
                  KEY idx_hr_kpi_task_owner (owner_user_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘P0/P1考核任务'
                """);
        addColumnIfMissing("hr_interview_record", "resume_qualified",
                "ALTER TABLE hr_interview_record ADD COLUMN resume_qualified TINYINT NULL COMMENT '简历是否合格1是0否；FAIL时必填，PASS默认1' AFTER fail_reason");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS hr_interview_review_push_log (
                  id              BIGINT       NOT NULL AUTO_INCREMENT,
                  invite_id       BIGINT       NOT NULL,
                  interviewer_user_id BIGINT   NOT NULL,
                  push_kind       VARCHAR(32)  NOT NULL DEFAULT 'AFTER' COMMENT 'BEFORE/AFTER/REMIND',
                  push_status     VARCHAR(16)  NOT NULL DEFAULT 'OK' COMMENT 'OK/SKIP/FAIL',
                  push_msg        VARCHAR(500) DEFAULT '',
                  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_hr_review_push_invite (invite_id, interviewer_user_id, push_kind)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试评价催填推送记录'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS hr_kpi_score_rule (
                  id                         BIGINT   NOT NULL,
                  base_score                 INT      NOT NULL DEFAULT 30 COMMENT '考核基准分（完成率×基准分=实得分）',
                  p0_onboard_count           INT      NOT NULL DEFAULT 1 COMMENT 'P0完成多少人入职',
                  p0_score                   INT      NOT NULL DEFAULT 2 COMMENT 'P0记多少分',
                  p1_qualified_resume_count  INT      NOT NULL DEFAULT 3 COMMENT 'P1周期内合格简历份数',
                  p1_onboard_count           INT      NOT NULL DEFAULT 1 COMMENT 'P1周期内入职人数',
                  p1_score                   INT      NOT NULL DEFAULT 1 COMMENT 'P1达成记多少分',
                  due_months                 INT      NOT NULL DEFAULT 1 COMMENT '任务默认周期（月）',
                  update_by                  VARCHAR(64) DEFAULT '',
                  update_time                DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘考核计分规则（单行）'
                """);
        addColumnIfMissing("hr_kpi_score_rule", "base_score",
                "ALTER TABLE hr_kpi_score_rule ADD COLUMN base_score INT NOT NULL DEFAULT 30 COMMENT '考核基准分（完成率×基准分=实得分）' AFTER id");
        addColumnIfMissing("hr_kpi_score_rule", "p0_onboard_count",
                "ALTER TABLE hr_kpi_score_rule ADD COLUMN p0_onboard_count INT NOT NULL DEFAULT 1 COMMENT 'P0完成多少人入职' AFTER base_score");
        addColumnIfMissing("hr_kpi_score_rule", "p1_qualified_resume_count",
                "ALTER TABLE hr_kpi_score_rule ADD COLUMN p1_qualified_resume_count INT NOT NULL DEFAULT 3 COMMENT 'P1周期内合格简历份数' AFTER p0_score");
        addColumnIfMissing("hr_kpi_score_rule", "p1_onboard_count",
                "ALTER TABLE hr_kpi_score_rule ADD COLUMN p1_onboard_count INT NOT NULL DEFAULT 1 COMMENT 'P1周期内入职人数' AFTER p1_qualified_resume_count");
        // 兼容旧列名
        try {
            jdbc.update("""
                    UPDATE hr_kpi_score_rule
                    SET p1_qualified_resume_count = COALESCE(NULLIF(p1_qualified_resume_count, 0), qualified_resume_count, 3)
                    WHERE id = 1
                    """);
        } catch (Exception ignored) {
            // 无库无旧列则忽略
        }
        jdbc.update("""
                INSERT INTO hr_kpi_score_rule (id, base_score, p0_onboard_count, p0_score, p1_qualified_resume_count,
                  p1_onboard_count, p1_score, due_months, update_by)
                VALUES (1, 30, 1, 2, 3, 1, 1, 1, 'system')
                ON DUPLICATE KEY UPDATE id = id
                """);
    }

    private void ensureCandidateProfileColumns() {
        addColumnIfMissing("hr_candidate", "gender",
                "ALTER TABLE hr_candidate ADD COLUMN gender VARCHAR(8) NULL COMMENT '性别男/女' AFTER email");
        addColumnIfMissing("hr_candidate", "age",
                "ALTER TABLE hr_candidate ADD COLUMN age INT NULL COMMENT '年龄' AFTER gender");
        addColumnIfMissing("hr_candidate", "education",
                "ALTER TABLE hr_candidate ADD COLUMN education VARCHAR(64) NULL COMMENT '学历' AFTER age");
        addColumnIfMissing("hr_candidate", "candidate_no",
                "ALTER TABLE hr_candidate ADD COLUMN candidate_no VARCHAR(32) NULL COMMENT '人选编号' AFTER education");
        addColumnIfMissing("hr_candidate", "current_title",
                "ALTER TABLE hr_candidate ADD COLUMN current_title VARCHAR(128) NULL COMMENT '目前职位' AFTER candidate_no");
        addColumnIfMissing("hr_candidate", "current_company",
                "ALTER TABLE hr_candidate ADD COLUMN current_company VARCHAR(128) NULL COMMENT '目前公司' AFTER current_title");
        jdbc.update("""
                UPDATE hr_candidate c
                JOIN hr_application a ON a.candidate_id = c.id AND a.is_active = 1
                JOIN hr_resume_parse p ON p.application_id = a.id AND p.is_active = 1
                SET c.current_company = COALESCE(NULLIF(c.current_company, ''), NULLIF(p.last_company, '')),
                    c.education = COALESCE(NULLIF(c.education, ''), NULLIF(p.degree, ''))
                WHERE c.is_active = 1
                """);
        jdbc.update("""
                UPDATE hr_candidate
                SET candidate_no = CAST(14000000 + id AS CHAR)
                WHERE is_active = 1 AND (candidate_no IS NULL OR TRIM(candidate_no) = '')
                """);
    }

    /** 原地点 SH/XJ 对应到工作地址：优先精确名，其次名称包含上海/新疆；新疆若缺失则补一条城市级地址 */
    private void backfillWorkAddressFromLocation() {
        ensureCityWorkAddress("新疆");
        ensureCityWorkAddress("上海");
        int n = jdbc.update("""
                UPDATE hr_requisition r
                SET r.work_address_id = (
                    SELECT d.id FROM hr_base_dict d
                    WHERE d.dict_type = 'WORK_ADDRESS' AND d.is_active = 1
                      AND (
                        (r.location_code = 'XJ' AND (d.name = '新疆' OR d.name LIKE '%新疆%'))
                        OR (IFNULL(r.location_code, 'SH') <> 'XJ' AND (d.name = '上海' OR d.name LIKE '%上海%'))
                      )
                    ORDER BY CASE
                      WHEN r.location_code = 'XJ' AND d.name = '新疆' THEN 0
                      WHEN IFNULL(r.location_code, 'SH') <> 'XJ' AND d.name = '上海' THEN 0
                      ELSE 1
                    END, d.id
                    LIMIT 1
                )
                WHERE r.work_address_id IS NULL
                """);
        if (n > 0) {
            log.info("已将 {} 条招聘需求地点映射到工作地址", n);
        }
    }

    private void ensureCityWorkAddress(String name) {
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_base_dict
                WHERE dict_type = 'WORK_ADDRESS' AND is_active = 1
                  AND (name = ? OR name LIKE ?)
                """, Integer.class, name, "%" + name + "%");
        if (active != null && active > 0) {
            return;
        }
        int revived = jdbc.update("""
                UPDATE hr_base_dict
                SET is_active = 1, category = IF(category = '', '城市', category)
                WHERE dict_type = 'WORK_ADDRESS' AND name = ?
                """, name);
        if (revived > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO hr_base_dict (dict_type, code, category, name, sort_no, create_by, is_active)
                VALUES ('WORK_ADDRESS', '', '城市', ?, 0, 'hr-seed', 1)
                """, name);
    }

    private void ensureFailReasonColumn() {
        addColumnIfMissing("hr_interview_record", "fail_reason",
                "ALTER TABLE hr_interview_record ADD COLUMN fail_reason VARCHAR(64) NULL COMMENT '未通过原因' AFTER conclusion");
        addColumnIfMissing("hr_interview_verdict", "fail_reason",
                "ALTER TABLE hr_interview_verdict ADD COLUMN fail_reason VARCHAR(64) NULL COMMENT '未通过原因' AFTER conclusion");
    }

    private void ensureInviteCcEventColumn() {
        addColumnIfMissing("hr_interview_invite_cc", "dingtalk_event_id",
                "ALTER TABLE hr_interview_invite_cc ADD COLUMN dingtalk_event_id VARCHAR(64) NULL COMMENT '抄送人钉钉日程ID' AFTER cc_user_id");
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        if (count == null || count == 0) {
            jdbc.execute(ddl);
        }
    }

    private void seedProofTaskType(int sort, String name, String remark) {
        // 仅补缺失；已逻辑删除的行仍占 type_name，不再 INSERT / 不把 is_active 刷回 1
        jdbc.update("""
                INSERT INTO sys_task_type (type_name, sort_order, remark, require_proof, biz_type, assign_field, spawn_task_type, is_active)
                SELECT ?, ?, ?, 1, '', '', '', 1
                FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = ?)
                """, name, sort, remark, name);
        jdbc.update("""
                UPDATE sys_task_type
                SET require_proof = 1, sort_order = ?, remark = ?, biz_type = '', assign_field = '', spawn_task_type = ''
                WHERE type_name = ? AND is_active = 1
                """, sort, remark, name);
    }

    private void seedAliases() {
        alias("王", "王艳", userIdByNickname("王艳"));
        alias("庞", "庞焯文", userIdByNickname("庞焯文"));
        alias("肖", "肖霞飞", userIdByNickname("肖霞飞"));
    }

    /** 软删灌数遗留的 alias=肖 且未绑用户的脏负责人行，避免列表 owner_names 拼出「肖、肖霞飞」。 */
    private void cleanupOrphanOwnerAliases() {
        int n = jdbc.update("""
                UPDATE hr_requisition_owner
                SET is_active = 0
                WHERE is_active = 1 AND user_id IS NULL AND alias = '肖'
                """);
        if (n > 0) {
            log.info("已软删 {} 条未绑定用户的 alias=肖 脏负责人", n);
        }
    }

    private void alias(String alias, String displayName, Long userId) {
        // 仅补缺失；已逻辑删除的 alias 不复活
        jdbc.update("""
                INSERT INTO hr_owner_alias (alias, display_name, user_id, create_by, is_active)
                SELECT ?, ?, ?, 'hr-seed', 1 FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM hr_owner_alias WHERE alias = ?)
                """, alias, displayName, userId, alias);
        jdbc.update("""
                UPDATE hr_owner_alias
                SET display_name = ?, user_id = COALESCE(user_id, ?)
                WHERE alias = ? AND is_active = 1
                """, displayName, userId, alias);
    }

    private Long userIdByNickname(String nickname) {
        return jdbc.query("SELECT user_id FROM sys_user WHERE nickname = ? AND is_active = 1 ORDER BY user_id LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, nickname);
    }

    static List<String> splitSql(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String statement = current.toString().trim();
                if (statement.endsWith(";")) {
                    statement = statement.substring(0, statement.length() - 1).trim();
                }
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
            }
        }
        return statements;
    }
}
