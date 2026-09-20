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
        seedTaskType();
        log.info("招聘表结构、角色、用户和菜单已同步");
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
                jdbc.update("INSERT INTO sys_user_role (user_id, role_id, is_active) VALUES (?, ?, 1)", userId, roleId);
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
        menu(202, "招聘需求", 200, 1, "hr/requisition", "hr/requisition/index", "C", "hr:requisition:list", "ProfileOutlined", "HC与面试流程");
        menu(203, "候选人", 200, 2, "hr/application", "hr/application/index", "C", "hr:application:list", "IdcardOutlined", "候选人与简历");
        menu(220, "面试邀约记录", 200, 3, "hr/invite", "hr/invite/index", "C", "hr:invite:list", "CalendarOutlined", "邀约并建钉钉日程");
        menu(221, "面试记录", 200, 4, "hr/record", "hr/record/index", "C", "hr:record:list", "FormOutlined", "各轮面试官评语");
        menu(222, "我的面试", 200, 5, "hr/mine", "hr/mine/index", "C", "hr:interview:mine", "ScheduleOutlined", "面试官待面日程与结论");
        menu(204, "部门管理", systemParent == 0 ? 200 : systemParent, 9, "hr/department", "hr/department/index", "C", "hr:dept:list", "BankOutlined", "部门树");
        jdbc.update("UPDATE sys_menu SET is_active = 0 WHERE menu_id IN (228, 229)");
        menu(218, "基础数据", 200, 6, "hr/base", "", "M", "", "DatabaseOutlined", "招聘字典");
        menu(219, "院校信息", 218, 1, "hr/school", "hr/school/index", "C", "hr:school:list", "ReadOutlined", "国内院校与QS");
        menu(230, "目标到岗", 218, 2, "hr/target", "hr/target/index", "C", "hr:target:list", "FlagOutlined", "招聘需求可选的目标到岗");
        menu(231, "目标到岗编辑", 230, 1, "", "", "F", "hr:target:edit", "#", "");
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
        grant(admin, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 230, 231);
        grant(hrAdmin, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 230, 231);
        grant(owner, 200, 201, 202, 203, 210, 218, 219, 220, 221, 222, 205, 225);
        grant(exec, 200, 201, 203, 207, 209, 218, 219, 220, 221);
        grant(plain, 200, 201, 209);
        grant(interviewer, 200, 201, 203, 211, 220, 221, 222, 225);
    }

    private int menuId(String name, String type) {
        List<Integer> ids = jdbc.query("""
                SELECT menu_id FROM sys_menu
                WHERE menu_name = ? AND menu_type = ? AND is_active = 1
                ORDER BY menu_id LIMIT 1
                """, (rs, row) -> rs.getInt(1), name, type);
        return ids.isEmpty() ? 0 : ids.get(0);
    }

    private void menu(int id, String name, int parent, int sort, String path, String component, String type,
                      String perms, String icon, String remark) {
        jdbc.update("""
                INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 1)
                ON DUPLICATE KEY UPDATE
                  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), sort_order = VALUES(sort_order),
                  path = VALUES(path), component = VALUES(component), menu_type = VALUES(menu_type),
                  perms = VALUES(perms), icon = VALUES(icon), remark = VALUES(remark), is_active = 1
                """, id, name, parent, sort, path, component, type, perms, icon, remark);
    }

    private void grant(long roleId, int... menuIds) {
        for (int menuId : menuIds) {
            jdbc.update("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active) VALUES (?, ?, 1)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """, roleId, menuId);
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
            seedProofTaskType(40, "薪资沟通", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(41, "背调资料收集", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(42, "背调", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(43, "体检", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(44, "待发offer", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(45, "待入职", "终面通过后下发，完成时必须上传资料，可多份");
            seedProofTaskType(46, "办理候选人入职", "入职前任务全部完成后生成，完成时必须上传资料，可多份");
        }
    }

    private void seedProofTaskType(int sort, String name, String remark) {
        jdbc.update("""
                INSERT INTO sys_task_type (type_name, sort_order, remark, require_proof, biz_type, assign_field, spawn_task_type, is_active)
                SELECT ?, ?, ?, 1, '', '', '', 1
                FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = ?)
                """, name, sort, remark, name);
        jdbc.update("""
                UPDATE sys_task_type
                SET require_proof = 1, sort_order = ?, remark = ?, biz_type = '', assign_field = '', spawn_task_type = '', is_active = 1
                WHERE type_name = ?
                """, sort, remark, name);
    }

    private void seedAliases() {
        alias("王", "王艳", userIdByNickname("王艳"));
        alias("庞", "庞焯文", userIdByNickname("庞焯文"));
        alias("肖", "肖", null);
    }

    private void alias(String alias, String displayName, Long userId) {
        jdbc.update("""
                INSERT INTO hr_owner_alias (alias, display_name, user_id, create_by, is_active)
                VALUES (?, ?, ?, 'hr-seed', 1)
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name),
                  user_id = COALESCE(hr_owner_alias.user_id, VALUES(user_id)), is_active = 1
                """, alias, displayName, userId);
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
