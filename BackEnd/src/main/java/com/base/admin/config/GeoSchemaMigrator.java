package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GeoSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    @Value("${geo.schema.migrate:true}")
    private boolean migrateEnabled;

    /** 空壳日监测清洗：一次性开关，默认关闭，避免每次启动改业务数据 */
    @Value("${geo.cleanup-empty-daily:false}")
    private boolean cleanupEmptyDaily;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!migrateEnabled) {
            log.info("GEO schema migrate 已跳过");
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v3_geo_platform.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v4_geo_daily_board.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v5_geo_board_snapshot.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v9_geo_content_placement.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v10_geo_optimize.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v11_geo_platform_account.sql"));
            ensureBoardLockedColumn(connection);
            ensureOwnerNameColumn(connection);
            ensureTermTypeColumn(connection);
            ensureOwnerUserIdColumn(connection);
            ensurePlatformType(connection);
            ensurePlatformLoginUrl(connection);
            ensurePlatformContentAudit(connection);
            ensureContentPlacementItemTitle(connection);
            ensureContentPlacementSource(connection);
            ensureContentPlacementAiModel(connection);
            ensureContentPlacementRelations(connection);
            ensureContentPlacementProgress(connection);
            ensureContentPlacementViewMenus(connection);
            ensureContentPlacementItemContentForm(connection);
            ensureContentDraftColumns(connection);
            ensureContentArticleReview(connection);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v22_geo_content_review.sql"));
            ensureContentReviewSnapshot(connection);
            ensureContentReviewStage(connection);
            ensureContentReviewAnnotation(connection);
            cleanupQueueArticlePlaceholders(connection);
            ensureArticleBoardMenu(connection);
            ensureDailyUniqueKeyWithTermType(connection);
            ensureGeoMenuRestructure(connection);
            ensurePublishedArticleMenu(connection);
            ensureDailyComboMenu(connection);
            ensureCiteScreenshotColumn(connection);
            ensureItemPublishTimeDateTime(connection);
            ensureGeoStaffRoles(connection);
            retireLegacyGeoRoles(connection);
            ensureContentReviewRule(connection);
            ensureContentReviewAiPreReview(connection);
            ensureContentReviewKb(connection);
            ensureContentPublisherRule(connection);
            ensureSellPointLibrary(connection);
            ensureArticleAiPipeline(connection);
            ensureGeoKnowledgeMenuTree(connection);
            if (cleanupEmptyDaily) {
                cleanupEmptyUninspectedDailyBeforeToday(connection);
            }
        } catch (Exception e) {
            log.error("GEO schema migrate failed", e);
            throw e;
        }
        log.info("GEO 平台主数据、看板落库与菜单已同步");
    }

    /**
     * 清洗「未实际巡查」的空壳日监测：排名/推荐状态/截图/第三方链接/竞品/负面均为空（含 -、无 等占位），
     * 且巡查日期早于今天。逻辑删除（is_active=0），可重复执行。
     */
    private void cleanupEmptyUninspectedDailyBeforeToday(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_monitor_daily")) {
            return;
        }
        String blank = """
                (col IS NULL OR TRIM(col) = '' OR TRIM(col) IN ('-', '—', '－', '无'))
                """.trim();
        String sql = """
                UPDATE geo_monitor_daily
                SET is_active = 0, update_time = NOW()
                WHERE is_active = 1
                  AND inspect_date < CURDATE()
                  AND rank_no IS NULL
                  AND %s
                  AND %s
                  AND %s
                  AND %s
                  AND %s
                """.formatted(
                blank.replace("col", "recommend_status"),
                blank.replace("col", "screenshot_url"),
                blank.replace("col", "third_party_url"),
                blank.replace("col", "competitors"),
                blank.replace("col", "negative_content"));
        try (Statement statement = connection.createStatement()) {
            int n = statement.executeUpdate(sql);
            if (n > 0) {
                log.info("已清洗今日之前未巡查空壳日监测 {} 条", n);
            } else {
                log.info("今日之前未巡查空壳日监测无需清洗");
            }
        }
    }

    /** GEO 选人角色：运营录入 / 管理复盘（不存在则创建） */
    private void ensureGeoStaffRoles(Connection connection) throws Exception {
        ensureRole(connection, "GEO运营录入", "geo_ops_entry", 40, "GEO日监测与内容投放录入");
        ensureRole(connection, "GEO管理复盘", "geo_mgmt_review", 41, "GEO复盘与管理选人");
    }

    /**
     * 退役手工遗留角色 geo / geo1：用户迁到 geo_ops_entry，菜单并到两个正式角色后软删。
     * 正式角色仅保留 geo_ops_entry、geo_mgmt_review。
     */
    private void retireLegacyGeoRoles(Connection connection) throws Exception {
        Long opsId = roleIdByKey(connection, "geo_ops_entry");
        Long mgmtId = roleIdByKey(connection, "geo_mgmt_review");
        if (opsId == null || mgmtId == null) {
            log.warn("正式 GEO 角色尚未就绪，跳过 geo/geo1 退役");
            return;
        }
        java.util.List<Long> legacyIds = new java.util.ArrayList<>();
        Long geoId = roleIdByKeyAny(connection, "geo");
        Long geo1Id = roleIdByKeyAny(connection, "geo1");
        if (geoId != null) {
            legacyIds.add(geoId);
        }
        if (geo1Id != null) {
            legacyIds.add(geo1Id);
        }
        if (legacyIds.isEmpty()) {
            return;
        }
        String legacyIn = legacyIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        try (Statement st = connection.createStatement()) {
            // 仅有旧角色、尚无任一正式 GEO 角色的用户 → 挂上运营录入
            int users = st.executeUpdate("""
                    INSERT IGNORE INTO sys_user_role (user_id, role_id, is_active)
                    SELECT DISTINCT ur.user_id, %d, 1
                    FROM sys_user_role ur
                    WHERE ur.role_id IN (%s) AND ur.is_active = 1
                      AND NOT EXISTS (
                        SELECT 1 FROM sys_user_role x
                        WHERE x.user_id = ur.user_id
                          AND x.role_id IN (%d, %d)
                          AND x.is_active = 1
                      );""".formatted(opsId, legacyIn, opsId, mgmtId));
            // 旧角色菜单并入两个正式角色
            int menusOps = st.executeUpdate("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT %d, rm.menu_id, 1
                    FROM sys_role_menu rm
                    WHERE rm.role_id IN (%s) AND rm.is_active = 1;""".formatted(opsId, legacyIn));
            int menusMgmt = st.executeUpdate("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT %d, rm.menu_id, 1
                    FROM sys_role_menu rm
                    WHERE rm.role_id IN (%s) AND rm.is_active = 1;""".formatted(mgmtId, legacyIn));
            int unlinkUsers = st.executeUpdate(
                    "UPDATE sys_user_role SET is_active = 0 WHERE role_id IN (" + legacyIn + ") AND is_active = 1");
            int unlinkMenus = st.executeUpdate(
                    "UPDATE sys_role_menu SET is_active = 0 WHERE role_id IN (" + legacyIn + ") AND is_active = 1");
            int roles = st.executeUpdate("""
                    UPDATE sys_role
                    SET is_active = 0, status = 1, remark = CONCAT(IFNULL(remark, ''), ' [已退役→geo_ops_entry/geo_mgmt_review]')
                    WHERE role_key IN ('geo', 'geo1') AND is_active = 1
                    """);
            log.info("已退役 geo/geo1：迁用户 {}，并菜单 ops/mgmt {}/{}，解绑用户/菜单 {}/{}，停用角色 {}",
                    users, menusOps, menusMgmt, unlinkUsers, unlinkMenus, roles);
        }
    }

    private Long roleIdByKey(Connection connection, String roleKey) throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT role_id FROM sys_role WHERE role_key = '" + roleKey.replace("'", "''")
                             + "' AND is_active = 1 LIMIT 1")) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    /** 含已软删，便于幂等清理仍挂在旧角色上的关联 */
    private Long roleIdByKeyAny(Connection connection, String roleKey) throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT role_id FROM sys_role WHERE role_key = '" + roleKey.replace("'", "''")
                             + "' ORDER BY is_active DESC, role_id LIMIT 1")) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    private void ensureRole(Connection connection, String roleName, String roleKey, int sortOrder, String remark)
            throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT role_id FROM sys_role WHERE role_key = '" + roleKey.replace("'", "''")
                             + "' AND is_active = 1 LIMIT 1")) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT IGNORE INTO sys_role (role_name, role_key, sort_order, status, remark, is_active)
                    VALUES ('%s', '%s', %d, 0, '%s', 1)
                    """.formatted(
                    roleName.replace("'", "''"),
                    roleKey.replace("'", "''"),
                    sortOrder,
                    remark.replace("'", "''")));
            log.info("已创建 GEO 角色 {} ({})", roleName, roleKey);
        }
    }

    private void ensureBoardLockedColumn(Connection connection) throws Exception {
        if (columnExists(connection, "geo_monitor_daily", "board_locked")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_monitor_daily
                      ADD COLUMN board_locked TINYINT NOT NULL DEFAULT 0
                      COMMENT '是否已被周/月/年统计 1=已统计不可改 0=未统计'
                      AFTER competitors
                    """);
        }
        log.info("已为 geo_monitor_daily 增加 board_locked 字段");
    }

    private void ensureOwnerNameColumn(Connection connection) throws Exception {
        if (columnExists(connection, "geo_monitor_daily", "owner_name")) {
            log.info("geo_monitor_daily.owner_name 已存在，跳过");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_monitor_daily
                      ADD COLUMN owner_name VARCHAR(64) NULL
                      COMMENT '负责人（与关键字绑定）'
                      AFTER keyword
                    """);
        }
        log.info("已为 geo_monitor_daily 增加 owner_name 字段");
    }

    private void ensureTermTypeColumn(Connection connection) throws Exception {
        if (!columnExists(connection, "geo_monitor_daily", "term_type")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_monitor_daily
                          ADD COLUMN term_type VARCHAR(16) NOT NULL DEFAULT '日巡查'
                          COMMENT '话题类型：日巡查/周巡查'
                          AFTER inspect_date
                        """);
            }
            log.info("已为 geo_monitor_daily 增加 term_type 字段");
        } else {
            log.info("geo_monitor_daily.term_type 已存在，跳过建列");
        }
        // 兜底：历史空值统一补成日巡查
        try (Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate("""
                    UPDATE geo_monitor_daily
                    SET term_type = '日巡查'
                    WHERE term_type IS NULL OR term_type = ''
                    """);
            if (updated > 0) {
                log.info("已将 {} 条空话题类型补全为日巡查", updated);
            }
        }
    }

    private void ensureOwnerUserIdColumn(Connection connection) throws Exception {
        if (columnExists(connection, "geo_monitor_daily", "owner_user_id")) {
            log.info("geo_monitor_daily.owner_user_id 已存在，跳过");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_monitor_daily
                      ADD COLUMN owner_user_id BIGINT NULL
                      COMMENT '负责人用户ID（关联 sys_user）'
                      AFTER keyword
                    """);
        }
        log.info("已为 geo_monitor_daily 增加 owner_user_id 字段");
    }

    private void ensurePlatformType(Connection connection) throws Exception {
        if (!columnExists(connection, "geo_platform", "platform_type")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_platform
                          ADD COLUMN platform_type VARCHAR(32) NOT NULL DEFAULT 'AI平台'
                          COMMENT '平台类型：AI平台/内容发布平台'
                          AFTER platform_name
                        """);
            }
            log.info("已为 geo_platform 增加 platform_type 字段");
        } else {
            log.info("geo_platform.platform_type 已存在，跳过建列");
        }

        try (Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate("""
                    UPDATE geo_platform
                    SET platform_type = 'AI平台'
                    WHERE platform_type IS NULL OR platform_type = ''
                    """);
            if (updated > 0) {
                log.info("已将 {} 条平台补全为 AI平台", updated);
            }
        }

        if (indexExists(connection, "geo_platform", "uk_platform_name")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE geo_platform DROP INDEX uk_platform_name");
            }
            log.info("已删除 geo_platform.uk_platform_name");
        }
        if (!indexExists(connection, "geo_platform", "uk_platform_name_type")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_platform
                          ADD UNIQUE KEY uk_platform_name_type (platform_name, platform_type)
                        """);
            }
            log.info("已创建 geo_platform.uk_platform_name_type");
        }

        String[] contentPlatforms = {
                "搜狐", "网易", "今日头条", "淘江湖", "腾讯新闻", "携程",
                "bilibili", "知乎", "trip", "官网", "公众号", "小红书"
        };
        int sort = 101;
        try (Statement statement = connection.createStatement()) {
            for (String name : contentPlatforms) {
                String safe = name.replace("'", "''");
                // 仅补缺失；已逻辑删除的平台不把 is_active 刷回 1
                statement.execute("""
                        INSERT INTO geo_platform (platform_name, platform_type, sort_order, is_active)
                        SELECT '%s', '内容发布平台', %d, 1 FROM DUAL
                        WHERE NOT EXISTS (
                          SELECT 1 FROM geo_platform
                          WHERE platform_name = '%s' AND platform_type = '内容发布平台'
                        )
                        """.formatted(safe, sort, safe));
                statement.execute("""
                        UPDATE geo_platform SET sort_order = %d
                        WHERE platform_name = '%s' AND platform_type = '内容发布平台' AND is_active = 1
                        """.formatted(sort, safe));
                sort++;
            }
        }
        log.info("内容发布平台默认数据已同步");
    }

    /** 登录地址归属平台，并从账号表移除旧字段 */
    private void ensurePlatformLoginUrl(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_platform")) {
            return;
        }
        if (!columnExists(connection, "geo_platform", "login_url")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_platform
                          ADD COLUMN login_url VARCHAR(500) NOT NULL DEFAULT ''
                          COMMENT '平台登录地址'
                          AFTER platform_type
                        """);
            }
            log.info("已为 geo_platform 增加 login_url 字段");
        }
        if (tableExists(connection, "geo_platform_account")
                && columnExists(connection, "geo_platform_account", "login_url")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE geo_platform_account DROP COLUMN login_url");
            }
            log.info("已从 geo_platform_account 移除 login_url 字段");
        }
    }

    /**
     * 内容发布平台：官方审核规范链接 + 摘要；并回填已知平台规范（空则写入）。
     */
    private void ensurePlatformContentAudit(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_platform")) {
            return;
        }
        if (!columnExists(connection, "geo_platform", "content_audit_urls")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_platform
                          ADD COLUMN content_audit_urls TEXT NULL
                          COMMENT '官方内容审核规范链接（多条换行）'
                          AFTER login_url
                        """);
            }
            log.info("已为 geo_platform 增加 content_audit_urls 字段");
        }
        if (!columnExists(connection, "geo_platform", "content_audit_notes")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_platform
                          ADD COLUMN content_audit_notes TEXT NULL
                          COMMENT '官方内容审核规范摘要/补充说明'
                          AFTER content_audit_urls
                        """);
            }
            log.info("已为 geo_platform 增加 content_audit_notes 字段");
        }

        String sharedNotes = """
                【垃圾信息与合规管理】垃圾信息与违规策略破坏平台生态、损害用户体验，甚至可能造成用户财产或人身损失。严禁任何以规避、引流、营销等为目的的诱导用户或谋取平台利益的行为，包括但不限于：
                1. 在头像、封面、昵称、内容头尾等位置添加引导、营销信息；
                2. 引导用户至第三方平台关注、注册或交易；
                3. 发布可能对用户财产或人身安全造成损失的医疗、投资、理财等垃圾营销信息；
                4. 单个账号发布过多营销内容，或多个账号协同推送营销内容；
                5. 通过导流、炒作等方式进行营销；
                6. 通过特殊符号、图片等形式规避平台审核进行营销。
                """.strip();

        // name | urls (\\n) | sort
        String[][] seeds = {
                {"网易",
                        "https://mp.163.com/media_static/html/handbook.html\nhttps://mp.163.com/college.html#/guidance/audit/specification",
                        "10"},
                {"搜狐",
                        "https://www.sohu.com/a/128720690_119436",
                        "20"},
                {"抖音",
                        "https://www.douyin.com/rule/encyclopedia",
                        "30"},
                {"新浪",
                        "https://news.sina.com.cn/m/wl/2016-09-02/doc-ifxvqcts9281976.shtml",
                        "40"},
                {"腾讯",
                        "https://om.qq.com/notice/w/20160429/047194.htm",
                        "50"},
                {"知乎",
                        "https://zhuanlan.zhihu.com/p/506695688",
                        "60"},
                {"携程",
                        "https://you.ctrip.com/htmlpages/eula.html",
                        "70"},
                {"Trip",
                        "https://hk.trip.com/contents/service-guideline/terms.html?locale=zh-HK&curr=HKD",
                        "80"},
                {"小红书",
                        "https://fe.xiaohongshu.com/ditto/vincent/c13cbc5b6a5e4f7794971b4597ab36bd?navHidden=yes&fullscreen=true&source=splash",
                        "90"},
                {"河江湖",
                        "https://terms.alicdn.com/legal-agreement/terms/c_and_product_protocol/20240428114337112/20240428114337112.html",
                        "100"},
                {"今日头条",
                        "https://baike.toutiao.com/detail/236/238/0?enter_from=left_navigation",
                        "110"},
                {"什么值得买",
                        "https://post.smzdm.com/about/\nhttps://www.smzdm.com/post/biji_principles\nhttps://static.smzdm.com/public/rule/wap/index.html?banner_key=tuijianneirongtougaoguifan",
                        "120"},
        };

        try (Statement statement = connection.createStatement()) {
            for (String[] row : seeds) {
                String name = row[0];
                String urls = row[1].replace("'", "''");
                String notes = sharedNotes.replace("'", "''");
                int sort = Integer.parseInt(row[2]);
                // 按 uk_platform_name_type 判重（含软删），避免 is_active=0 旧行导致重复插入
                statement.executeUpdate("""
                        INSERT INTO geo_platform (platform_name, platform_type, login_url, content_audit_urls, content_audit_notes, sort_order, is_active)
                        SELECT '%s', '内容发布平台', '', '%s', '%s', %d, 1 FROM DUAL
                        WHERE NOT EXISTS (
                          SELECT 1 FROM geo_platform
                          WHERE platform_name = '%s' AND platform_type = '内容发布平台'
                        )
                        """.formatted(name, urls, notes, sort, name));
                statement.executeUpdate("""
                        UPDATE geo_platform
                        SET content_audit_urls = CASE
                              WHEN content_audit_urls IS NULL OR content_audit_urls = '' THEN '%s'
                              ELSE content_audit_urls END,
                            content_audit_notes = CASE
                              WHEN content_audit_notes IS NULL OR content_audit_notes = '' THEN '%s'
                              ELSE content_audit_notes END
                        WHERE platform_name = '%s' AND platform_type = '内容发布平台'
                        """.formatted(urls, notes, name));
            }
        }
        log.info("已同步内容发布平台官方审核规范字段与种子数据");
    }

    private void ensureContentPlacementItemTitle(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement_item")) {
            return;
        }
        if (!columnExists(connection, "geo_content_placement_item", "title")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement_item
                          ADD COLUMN title VARCHAR(500) NOT NULL DEFAULT ''
                          COMMENT '标题（归属发布详情）'
                          AFTER placement_id
                        """);
            }
            log.info("已为 geo_content_placement_item 增加 title 字段");
        }
        try (Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate("""
                    UPDATE geo_content_placement_item i
                    INNER JOIN geo_content_placement p ON p.id = i.placement_id AND p.is_active = 1
                    SET i.title = p.title
                    WHERE (i.title IS NULL OR i.title = '')
                      AND p.title IS NOT NULL AND p.title <> ''
                    """);
            if (updated > 0) {
                log.info("已回填 {} 条发布详情标题", updated);
            }
        }
        // 补齐新增/编辑权限菜单
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (125, '内容投放新增', 122, 3, '', '', 'F', 'geo:content:add', '#', 0, 0, '', 1),
                    (126, '内容投放修改', 122, 4, '', '', 'F', 'geo:content:edit', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (125, 126);""");
        }
    }

    private void ensureContentPlacementSource(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement")) {
            return;
        }
        boolean addedSource = false;
        if (!columnExists(connection, "geo_content_placement", "source")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT '导入'
                          COMMENT '来源：导入/手动新增/AI生成'
                          AFTER title
                        """);
            }
            addedSource = true;
            log.info("已为 geo_content_placement 增加 source 字段");
        }
        if (!columnExists(connection, "geo_content_placement", "source_placement_id")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN source_placement_id BIGINT NULL
                          COMMENT 'AI生成参照的内容投放ID'
                          AFTER source
                        """);
            }
            log.info("已为 geo_content_placement 增加 source_placement_id 字段");
        }
        if (addedSource) {
            try (Statement statement = connection.createStatement()) {
                // 无发布详情的历史空壳视为手动新增，其余历史数据视为导入
                statement.executeUpdate("""
                        UPDATE geo_content_placement p
                        SET p.source = '手动新增'
                        WHERE NOT EXISTS (
                          SELECT 1 FROM geo_content_placement_item i
                          WHERE i.placement_id = p.id AND i.is_active = 1
                        )
                        """);
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (127, '生成相似问题', 122, 5, '', '', 'F', 'geo:content:generate', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id = 127;""");
        }
    }

    /** AI 生成来源附带模型展示名 */
    private void ensureContentPlacementAiModel(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement")) {
            return;
        }
        if (columnExists(connection, "geo_content_placement", "source_ai_model")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_content_placement
                      ADD COLUMN source_ai_model VARCHAR(128) NULL
                      COMMENT 'AI生成所用模型展示名'
                      AFTER source_placement_id
                    """);
        }
        log.info("已为 geo_content_placement 增加 source_ai_model 字段");
    }

    private void ensureContentPlacementRelations(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement")) {
            return;
        }
        if (!columnExists(connection, "geo_content_placement", "publisher_user_id")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN publisher_user_id BIGINT NULL
                          COMMENT '发布人用户ID'
                          AFTER publisher_name
                        """);
            }
            log.info("已为 geo_content_placement 增加 publisher_user_id");
        }
        if (!columnExists(connection, "geo_content_placement", "owner_user_id")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN owner_user_id BIGINT NULL
                          COMMENT '归属人用户ID'
                          AFTER owner_name
                        """);
            }
            log.info("已为 geo_content_placement 增加 owner_user_id");
        }
        if (!columnExists(connection, "geo_content_placement", "topic_id")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN topic_id BIGINT NULL
                          COMMENT '话题ID'
                          AFTER owner_user_id
                        """);
            }
            log.info("已为 geo_content_placement 增加 topic_id");
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE geo_content_placement p
                    INNER JOIN sys_user u ON u.is_active = 1
                      AND (u.nickname = p.publisher_name OR u.username = p.publisher_name)
                    SET p.publisher_user_id = u.user_id
                    WHERE p.publisher_user_id IS NULL
                      AND p.publisher_name IS NOT NULL AND p.publisher_name <> ''
                      AND p.publisher_name <> '待分配'
                    """);
            statement.executeUpdate("""
                    UPDATE geo_content_placement p
                    INNER JOIN sys_user u ON u.is_active = 1
                      AND (u.nickname = p.owner_name OR u.username = p.owner_name)
                    SET p.owner_user_id = u.user_id
                    WHERE p.owner_user_id IS NULL
                      AND p.owner_name IS NOT NULL AND p.owner_name <> ''
                      AND p.owner_name <> '待分配'
                    """);
            statement.executeUpdate("""
                    UPDATE geo_content_placement p
                    INNER JOIN geo_topic t ON t.is_active = 1 AND t.topic_name = p.topic_name
                    SET p.topic_id = t.id
                    WHERE p.topic_id IS NULL
                      AND p.topic_name IS NOT NULL AND p.topic_name <> ''
                    """);
        }
    }

    /** 在线撰写：稿件状态 + Lake 正文（一期 text/lake） */
    private void ensureContentDraftColumns(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement")) {
            return;
        }
        if (!columnExists(connection, "geo_content_placement", "content_status")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN content_status VARCHAR(32) NOT NULL DEFAULT 'drafting'
                          COMMENT '稿件状态 drafting/pending_review/revising/approved/queue'
                          AFTER placement_progress
                        """);
            }
            log.info("已为 geo_content_placement 增加 content_status");
        }
        if (!columnExists(connection, "geo_content_placement", "content_title")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN content_title VARCHAR(500) NOT NULL DEFAULT ''
                          COMMENT '在线正文标题'
                          AFTER content_status
                        """);
            }
            log.info("已为 geo_content_placement 增加 content_title");
        }
        if (!columnExists(connection, "geo_content_placement", "content_format")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN content_format VARCHAR(32) NOT NULL DEFAULT 'text/lake'
                          COMMENT '正文格式（一期锁定 text/lake）'
                          AFTER content_title
                        """);
            }
            log.info("已为 geo_content_placement 增加 content_format");
        }
        if (!columnExists(connection, "geo_content_placement", "content_body")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN content_body LONGTEXT NULL
                          COMMENT 'Lake 正文'
                          AFTER content_format
                        """);
            }
            log.info("已为 geo_content_placement 增加 content_body");
        }
        if (!columnExists(connection, "geo_content_placement", "content_version")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN content_version BIGINT NOT NULL DEFAULT 0
                          COMMENT '正文乐观锁版本'
                          AFTER content_body
                        """);
            }
            log.info("已为 geo_content_placement 增加 content_version");
        }
        if (!columnExists(connection, "geo_content_placement", "content_updated_by")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN content_updated_by VARCHAR(64) NOT NULL DEFAULT ''
                          COMMENT '正文最近保存人'
                          AFTER content_version
                        """);
            }
            log.info("已为 geo_content_placement 增加 content_updated_by");
        }
        if (!columnExists(connection, "geo_content_placement", "content_updated_at")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN content_updated_at DATETIME NULL
                          COMMENT '正文最近保存时间'
                          AFTER content_updated_by
                        """);
            }
            log.info("已为 geo_content_placement 增加 content_updated_at");
        }
        if (!indexExists(connection, "geo_content_placement", "idx_content_status")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD INDEX idx_content_status (content_status)
                        """);
            }
            log.info("已为 geo_content_placement 增加 idx_content_status");
        }
    }

    private void ensureContentPlacementProgress(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement")) {
            return;
        }
        if (!columnExists(connection, "geo_content_placement", "placement_progress")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD COLUMN placement_progress VARCHAR(32) NOT NULL DEFAULT '未投放'
                          COMMENT '投放进度：投放完成/部分投放/未投放'
                          AFTER source_placement_id
                        """);
            }
            log.info("已为 geo_content_placement 增加 placement_progress");
        }
        if (!indexExists(connection, "geo_content_placement", "idx_placement_progress")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement
                          ADD INDEX idx_placement_progress (placement_progress)
                        """);
            }
            log.info("已为 geo_content_placement 增加 idx_placement_progress");
        }
        try (Statement statement = connection.createStatement()) {
            // 按子表投放成功占比回填主表进度
            int updated = statement.executeUpdate("""
                    UPDATE geo_content_placement p
                    SET p.placement_progress = CASE
                      WHEN NOT EXISTS (
                        SELECT 1 FROM geo_content_placement_item i
                        WHERE i.placement_id = p.id AND i.is_active = 1
                      ) THEN '未投放'
                      WHEN NOT EXISTS (
                        SELECT 1 FROM geo_content_placement_item i
                        WHERE i.placement_id = p.id AND i.is_active = 1
                          AND i.publish_status <> '投放成功'
                      ) THEN '投放完成'
                      WHEN EXISTS (
                        SELECT 1 FROM geo_content_placement_item i
                        WHERE i.placement_id = p.id AND i.is_active = 1
                          AND i.publish_status = '投放成功'
                      ) THEN '部分投放'
                      ELSE '未投放'
                    END
                    """);
            if (updated > 0) {
                log.info("已回填 geo_content_placement.placement_progress {} 条", updated);
            }
        }
    }

    /** 内容投放拆分为管理视角 / 一线执行视角两套菜单 */
    private void ensureContentPlacementViewMenus(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE sys_menu
                    SET menu_name = '投放管理',
                        path = 'geo/content-placement-manage',
                        perms = 'geo:content:list',
                        icon = 'SendOutlined',
                        remark = '管理视角：目标问题生成、分配发布人、投放进度',
                        sort_order = 8
                    WHERE menu_id = 122 AND is_active = 1
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (128, '我的投放', 100, 9, 'geo/content-placement-work', '', 'C', 'geo:content:work', 'FormOutlined', 0, 0,
                     '一线视角：维护本人话题/目标问题的平台投放与引用详情', 1)
                    ON DUPLICATE KEY UPDATE
                      path = VALUES(path),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      sort_order = VALUES(sort_order)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (122, 128);""");
        }
        log.info("已同步内容投放管理/我的投放双视角菜单");
    }

    /** 数据看板：AI露出 + 发布收录聚合页 */
    private void ensureArticleBoardMenu(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (129, '数据看板', 100, 7, 'geo/article-board', '', 'C', 'geo:article:list', 'FundOutlined', 0, 0,
                     'AI露出情况与文章发布/收录情况聚合看板', 1)
                    ON DUPLICATE KEY UPDATE
                      path = VALUES(path),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      sort_order = VALUES(sort_order)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id = 129;""");
        }
        log.info("已同步数据看板菜单");
    }

    /** 日监测唯一键纳入 term_type，避免日/周巡查互相覆盖 */
    private void ensureDailyUniqueKeyWithTermType(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_monitor_daily")) {
            return;
        }
        if (indexExists(connection, "geo_monitor_daily", "uk_date_platform_keyword_term")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (indexExists(connection, "geo_monitor_daily", "uk_date_platform_keyword")) {
                statement.execute("ALTER TABLE geo_monitor_daily DROP INDEX uk_date_platform_keyword");
            }
            statement.execute("""
                    ALTER TABLE geo_monitor_daily
                      ADD UNIQUE KEY uk_date_platform_keyword_term (inspect_date, platform, keyword, term_type)
                    """);
        }
        log.info("已更新 geo_monitor_daily 唯一键（含 term_type）");
    }

    /**
     * 菜单三分域：基础配置 / AI露出 / 内容投放；
     * 合并露出看板入口，隐藏旧周/月报菜单。
     */
    private void ensureGeoMenuRestructure(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            // 目录 path 使用 geo/xxx 前缀，避免与叶子绝对路径冲突（路由层会扁平化目录）
            statement.execute("""
                    INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (130, '基础配置', 100, 1, 'geo/config', '', 'M', '', 'AppstoreOutlined', 0, 0, '话题/平台主数据', 1),
                    (131, 'AI露出', 100, 2, 'geo/expose', '', 'M', '', 'RadarChartOutlined', 0, 0, '日监测数据与露出看板', 1),
                    (132, '内容投放', 100, 3, 'geo/content', '', 'M', '', 'SendOutlined', 0, 0, '发布/收录与投放作业', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = VALUES(parent_id),
                      sort_order = VALUES(sort_order),
                      path = VALUES(path),
                      menu_type = VALUES(menu_type),
                      icon = VALUES(icon),
                      remark = VALUES(remark)
                    """);

            statement.executeUpdate("UPDATE sys_menu SET parent_id = 130, sort_order = 1, menu_name = '话题管理' WHERE menu_id = 101");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 130, sort_order = 2, menu_name = '平台管理' WHERE menu_id = 113");
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 130, sort_order = 3, menu_name = '平台账号管理',
                      path = 'geo/platform-account', perms = 'geo:platformAccount:list', icon = 'IdcardOutlined'
                    WHERE menu_id = 133
                    """);

            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 131, sort_order = 1, menu_name = '日监测数据',
                      path = 'geo/daily', perms = 'geo:daily:list', icon = 'CalendarOutlined'
                    WHERE menu_id = 102
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 131, sort_order = 2, menu_name = '露出看板',
                      path = 'geo/expose-board', perms = 'geo:expose:list', icon = 'LineChartOutlined',
                      remark = '日/周/月/年露出经营看板（已结束周期读落库快照）'
                    WHERE menu_id = 117
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 131, sort_order = 3, menu_name = '全年目标',
                      path = 'geo/yearly-target', perms = 'geo:yearly:list', icon = 'DashboardOutlined',
                      remark = '全年目标配置与达成查看'
                    WHERE menu_id = 104
                    """);

            statement.executeUpdate("UPDATE sys_menu SET visible = 1, is_active = 0, parent_id = 131 WHERE menu_id IN (103, 118)");

            // 旧 article-board 占位（无页面）：不再挂到内容投放；真正入口是菜单 170 文章列表
            statement.executeUpdate("""
                    UPDATE sys_menu SET is_active = 0, visible = 1, parent_id = 132, sort_order = 99,
                      path = 'geo/article-board',
                      remark = '已停用：请使用「内容投放 → 文章列表」'
                    WHERE menu_id = 129
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 2, menu_name = '投放管理',
                      path = 'geo/content-placement-manage', perms = 'geo:content:list',
                      remark = '目标问题生成、分配发布人与投放进度'
                    WHERE menu_id = 122
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 3,
                      path = 'geo/content-placement-work', perms = 'geo:content:work'
                    WHERE menu_id = 128
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET menu_name = '我的投放'
                    WHERE menu_id = 128 AND menu_name IN ('投放执行', '')
                    """);

            statement.executeUpdate("UPDATE sys_menu SET parent_id = 117, perms = 'geo:expose:persist', menu_name = '露出落库' WHERE menu_id = 119");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 117, is_active = 0 WHERE menu_id IN (120, 121)");

            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (130, 131, 132, 117, 129);""");
        }
        log.info("已同步 GEO 菜单三分域与露出看板合并");
    }

    private void ensureContentPlacementItemContentForm(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement_item")) {
            return;
        }
        if (!columnExists(connection, "geo_content_placement_item", "content_form")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_placement_item
                          ADD COLUMN content_form VARCHAR(16) NOT NULL DEFAULT '图文'
                          COMMENT '内容形态：图文/视频'
                          AFTER platform_name
                        """);
            }
            log.info("已为 geo_content_placement_item 增加 content_form");
        }
        try (Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate("""
                    UPDATE geo_content_placement_item
                    SET content_form = '视频'
                    WHERE content_form = '图文'
                      AND (
                        platform_name LIKE '%bilibili%'
                        OR platform_name LIKE '%哔哩%'
                        OR platform_name LIKE '%抖音%'
                        OR platform_name LIKE '%快手%'
                        OR platform_name LIKE '%视频号%'
                        OR platform_name LIKE '%视频%'
                      )
                    """);
            if (updated > 0) {
                log.info("已按平台回填视频形态 {} 条", updated);
            }
        }
    }

    /**
     * 文章列表（按平台发布明细）：挂在「内容投放」下。
     * 注意：134–136 已被「平台账号」按钮占用，不可复用；使用 170–173。
     */
    private void ensurePublishedArticleMenu(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            // 若此前误把 134–136 改成文章菜单，先恢复平台账号按钮
            statement.executeUpdate("""
                    UPDATE sys_menu SET menu_name = '平台账号新增', parent_id = 133, sort_order = 1,
                      path = '', component = '', menu_type = 'F', perms = 'geo:platformAccount:add',
                      icon = '#', visible = 0, status = 0
                    WHERE menu_id = 134 AND (path = 'geo/article' OR perms = 'geo:article:list' OR menu_type = 'C')
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET menu_name = '平台账号修改', parent_id = 133, sort_order = 2,
                      path = '', component = '', menu_type = 'F', perms = 'geo:platformAccount:edit',
                      icon = '#', visible = 0, status = 0
                    WHERE menu_id = 135 AND (perms LIKE 'geo:article:%' OR parent_id = 134 OR parent_id = 170)
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET menu_name = '平台账号删除', parent_id = 133, sort_order = 3,
                      path = '', component = '', menu_type = 'F', perms = 'geo:platformAccount:delete',
                      icon = '#', visible = 0, status = 0
                    WHERE menu_id = 136 AND (perms LIKE 'geo:article:%' OR parent_id = 134 OR parent_id = 170)
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET is_active = 0
                    WHERE menu_id = 137 AND perms = 'geo:article:delete'
                    """);

            statement.execute("""
                    INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (170, '文章列表', 132, 1, 'geo/article', '', 'C', 'geo:article:list', 'FileTextOutlined', 0, 0,
                     '按发布人/撰写人/话题/目标问题检索各平台发布文章，可改状态与链接', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = '文章列表',
                      parent_id = 132,
                      sort_order = 1,
                      path = 'geo/article',
                      component = '',
                      menu_type = 'C',
                      perms = 'geo:article:list',
                      icon = 'FileTextOutlined',
                      visible = 0,
                      status = 0,
                      remark = VALUES(remark)
                    """);
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (171, '文章新增', 170, 1, '', '', 'F', 'geo:article:add', '#', 0, 0, '', 1),
                    (172, '文章修改', 170, 2, '', '', 'F', 'geo:article:edit', '#', 0, 0, '', 1),
                    (173, '文章删除', 170, 3, '', '', 'F', 'geo:article:delete', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = 170,
                      menu_type = 'F',
                      perms = VALUES(perms)
                    """);
            // 无对应页面的 article-board 占位入口：停用
            statement.executeUpdate("""
                    UPDATE sys_menu SET is_active = 0, visible = 1,
                      remark = '已停用：请使用「内容投放 → 文章列表」'
                    WHERE menu_id = 129 AND path LIKE '%article-board%'
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 2, menu_name = '投放管理',
                      path = 'geo/content-placement-manage'
                    WHERE menu_id = 122
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 3,
                      path = 'geo/content-placement-work'
                    WHERE menu_id = 128
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT rm.role_id, m.menu_id, 1
                    FROM sys_role_menu rm
                    CROSS JOIN sys_menu m
                    WHERE rm.menu_id = 122 AND rm.is_active = 1
                      AND m.menu_id IN (170, 171, 172, 173);""");
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (170, 171, 172, 173);""");
        }
        log.info("已同步文章列表菜单 menu_id=170 path=geo/article");
    }

    /** 话题×关键字×平台 组合分析页（挂在 AI露出 下） */
    private void ensureDailyComboMenu(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (174, '组合趋势', 131, 2, 'geo/daily-combo', '', 'C', 'geo:daily:list', 'LineChartOutlined', 0, 0,
                     '按话题×关键字×平台查看日期明细与折线趋势', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = '组合趋势',
                      parent_id = 131,
                      sort_order = 2,
                      path = 'geo/daily-combo',
                      component = '',
                      menu_type = 'C',
                      perms = 'geo:daily:list',
                      icon = 'LineChartOutlined',
                      visible = 0,
                      status = 0,
                      remark = VALUES(remark)
                    """);
            // 露出看板 / 全年目标顺延
            statement.executeUpdate("""
                    UPDATE sys_menu SET sort_order = 3 WHERE menu_id = 117 AND parent_id = 131
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET sort_order = 4 WHERE menu_id = 104 AND parent_id = 131
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT rm.role_id, 174, 1
                    FROM sys_role_menu rm
                    WHERE rm.menu_id = 102 AND rm.is_active = 1;""");
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, 174, 1 FROM DUAL;""");
        }
        log.info("已同步组合趋势菜单 menu_id=174 path=geo/daily-combo");
    }

    private void ensureCiteScreenshotColumn(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement_cite")) {
            return;
        }
        if (columnExists(connection, "geo_content_placement_cite", "screenshot_url")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_content_placement_cite
                      ADD COLUMN screenshot_url VARCHAR(512) NULL COMMENT '引用截图'
                      AFTER cite_url
                    """);
        }
        log.info("已为 geo_content_placement_cite 增加 screenshot_url");
    }

    private void ensureItemPublishTimeDateTime(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement_item")) {
            return;
        }
        if (!columnExists(connection, "geo_content_placement_item", "publish_time")) {
            return;
        }
        String type = columnDataType(connection, "geo_content_placement_item", "publish_time");
        if (type != null && "datetime".equalsIgnoreCase(type)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_content_placement_item
                      MODIFY COLUMN publish_time DATETIME NULL COMMENT '发布时间'
                    """);
        }
        log.info("已将 geo_content_placement_item.publish_time 调整为 DATETIME");
    }

    /** 审核轮次保存提审时正文快照，供历史对照 */
    /**
     * 审核通过改为回到投放管理待投放，清掉历史上自动插入文章列表的空平台占位。
     */
    private void cleanupQueueArticlePlaceholders(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_placement")
                || !tableExists(connection, "geo_content_placement_item")
                || !columnExists(connection, "geo_content_placement", "content_status")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            int removed = statement.executeUpdate("""
                    UPDATE geo_content_placement_item
                    SET is_active = 0, update_time = NOW()
                    WHERE is_active = 1
                      AND (platform_name IS NULL OR TRIM(platform_name) = '')
                      AND remark LIKE '%审核通过自动生成%'
                    """);
            int queued = statement.executeUpdate("""
                    UPDATE geo_content_placement
                    SET placement_progress = '待投放', update_time = NOW()
                    WHERE is_active = 1
                      AND content_status = 'queue'
                      AND (placement_progress IS NULL OR placement_progress = '' OR placement_progress = '未投放')
                    """);
            if (removed > 0 || queued > 0) {
                log.info("已清理待投放空平台明细 {} 条，回写投放管理待投放 {} 条", removed, queued);
            }
        }
    }

    private void ensureContentReviewSnapshot(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_review")) {
            return;
        }
        if (!columnExists(connection, "geo_content_review", "content_title")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_review
                          ADD COLUMN content_title VARCHAR(500) NOT NULL DEFAULT ''
                          COMMENT '提审时正文标题'
                          AFTER content_version
                        """);
            }
            log.info("已为 geo_content_review 增加 content_title");
        }
        if (!columnExists(connection, "geo_content_review", "content_format")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_review
                          ADD COLUMN content_format VARCHAR(32) NOT NULL DEFAULT 'text/lake'
                          COMMENT '提审时正文格式'
                          AFTER content_title
                        """);
            }
            log.info("已为 geo_content_review 增加 content_format");
        }
        if (!columnExists(connection, "geo_content_review", "content_body")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_review
                          ADD COLUMN content_body LONGTEXT NULL
                          COMMENT '提审时 Lake 正文快照'
                          AFTER content_format
                        """);
            }
            log.info("已为 geo_content_review 增加 content_body");
        }
    }

    /** 两阶段串行审核：1=一审 2=二审；历史轮次默认一审 */
    private void ensureContentReviewStage(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_review")) {
            return;
        }
        if (columnExists(connection, "geo_content_review", "stage_no")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE geo_content_review
                      ADD COLUMN stage_no INT NOT NULL DEFAULT 1
                      COMMENT '审核阶段 1=一审 2=二审'
                      AFTER round_no
                    """);
        }
        log.info("已为 geo_content_review 增加 stage_no");
    }

    /** 审核划词/划句/划段批注 */
    private void ensureContentReviewAnnotation(Connection connection) throws Exception {
        if (tableExists(connection, "geo_content_review_annotation")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE geo_content_review_annotation (
                      id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                      review_id       BIGINT        NOT NULL                COMMENT '审核轮次ID',
                      placement_id    BIGINT        NOT NULL                COMMENT '投放主表ID',
                      quote_text      VARCHAR(2000) NOT NULL                COMMENT '选中原文',
                      quote_prefix    VARCHAR(200)  NOT NULL DEFAULT ''     COMMENT '选区前上下文',
                      quote_suffix    VARCHAR(200)  NOT NULL DEFAULT ''     COMMENT '选区后上下文',
                      start_offset    INT           NULL                    COMMENT '纯文本起点偏移',
                      end_offset      INT           NULL                    COMMENT '纯文本终点偏移',
                      comment_text    VARCHAR(2000) NOT NULL                COMMENT '批注内容',
                      author_user_id  BIGINT        NOT NULL                COMMENT '批注人用户ID',
                      author_name     VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '批注人展示名',
                      create_by       VARCHAR(64)   NULL,
                      create_time     DATETIME      NULL,
                      update_by       VARCHAR(64)   NULL,
                      update_time     DATETIME      NULL,
                      is_active       TINYINT       NOT NULL DEFAULT 1,
                      PRIMARY KEY (id),
                      KEY idx_anno_review (review_id),
                      KEY idx_anno_placement (placement_id)
                    ) COMMENT 'GEO文章审核划词批注'
                    """);
        }
        log.info("已创建 geo_content_review_annotation");
    }

    /**
     * 同一问题下各内容平台文章：默认共用统一稿，也可独立成稿；每篇文章单独内部审核并记录意见。
     */
    private void ensureContentArticleReview(Connection connection) throws Exception {
        if (tableExists(connection, "geo_content_placement_item")) {
            addItemColumn(connection, "content_mode",
                    "ALTER TABLE geo_content_placement_item ADD COLUMN content_mode VARCHAR(16) NOT NULL DEFAULT 'shared' COMMENT 'shared统一稿/custom独立稿'");
            addItemColumn(connection, "content_title",
                    "ALTER TABLE geo_content_placement_item ADD COLUMN content_title VARCHAR(500) NOT NULL DEFAULT '' COMMENT '独立稿标题'");
            addItemColumn(connection, "content_body",
                    "ALTER TABLE geo_content_placement_item ADD COLUMN content_body LONGTEXT NULL COMMENT '独立稿Lake正文'");
            addItemColumn(connection, "content_format",
                    "ALTER TABLE geo_content_placement_item ADD COLUMN content_format VARCHAR(32) NOT NULL DEFAULT 'text/lake' COMMENT '独立稿格式'");
            addItemColumn(connection, "content_status",
                    "ALTER TABLE geo_content_placement_item ADD COLUMN content_status VARCHAR(32) NOT NULL DEFAULT 'drafting' COMMENT '平台文章审核状态'");
            addItemColumn(connection, "content_version",
                    "ALTER TABLE geo_content_placement_item ADD COLUMN content_version BIGINT NOT NULL DEFAULT 0 COMMENT '独立稿版本'");
            addItemColumn(connection, "content_updated_by",
                    "ALTER TABLE geo_content_placement_item ADD COLUMN content_updated_by VARCHAR(64) NULL COMMENT '正文最近保存人'");
            addItemColumn(connection, "content_updated_at",
                    "ALTER TABLE geo_content_placement_item ADD COLUMN content_updated_at DATETIME NULL COMMENT '正文最近保存时间'");
        }
        if (tableExists(connection, "geo_content_review_note")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE geo_content_review_note (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      placement_id BIGINT NOT NULL,
                      item_id BIGINT NOT NULL,
                      opinion VARCHAR(2000) NOT NULL,
                      author_name VARCHAR(64) NOT NULL DEFAULT '',
                      create_by VARCHAR(64) NULL,
                      create_time DATETIME NULL,
                      update_by VARCHAR(64) NULL,
                      update_time DATETIME NULL,
                      is_active TINYINT NOT NULL DEFAULT 1,
                      KEY idx_review_item (item_id),
                      KEY idx_review_placement (placement_id)
                    ) COMMENT '内容投放平台文章内部审核意见'
                    """);
            log.info("已创建 geo_content_review_note");
        }
    }

    /** 按撰写人配置审核策略（目前：是否跳过一审）；菜单挂在基础配置下 */
    private void ensureContentReviewRule(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_review_rule")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_content_review_rule (
                          id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                          writer_user_id BIGINT        NOT NULL                COMMENT '撰写人用户ID',
                          writer_name    VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '撰写人展示名',
                          skip_stage1    TINYINT       NOT NULL DEFAULT 1      COMMENT '是否跳过一审 1=是 0=否',
                          ai_pre_review  TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用AI预审 1=是 0=否',
                          remark         VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '备注',
                          create_by      VARCHAR(64)   DEFAULT ''              COMMENT '创建者',
                          create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_by      VARCHAR(64)   DEFAULT ''              COMMENT '更新者',
                          update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          is_active      TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
                          PRIMARY KEY (id),
                          KEY idx_review_rule_writer (writer_user_id, is_active)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO文章审核规则'
                        """);
            }
            log.info("已创建 geo_content_review_rule");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (178, '文章审核规则', 130, 4, 'geo/review-rule', '', 'C', 'geo:reviewRule:list', 'AuditOutlined', 0, 0,
                     '按撰写人配置是否跳过一审', 1),
                    (179, '审核规则新增', 178, 1, '', '', 'F', 'geo:reviewRule:add', '#', 0, 0, '', 1),
                    (180, '审核规则修改', 178, 2, '', '', 'F', 'geo:reviewRule:edit', '#', 0, 0, '', 1),
                    (181, '审核规则删除', 178, 3, '', '', 'F', 'geo:reviewRule:delete', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = VALUES(parent_id),
                      sort_order = VALUES(sort_order),
                      path = VALUES(path),
                      component = VALUES(component),
                      menu_type = VALUES(menu_type),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      is_active = 1,
                      visible = VALUES(visible)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (178, 179, 180, 181)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT rm.role_id, m.menu_id, 1
                    FROM sys_role_menu rm
                    CROSS JOIN sys_menu m
                    WHERE rm.menu_id = 101 AND rm.is_active = 1
                      AND m.menu_id IN (178, 179, 180, 181)
                    """);
            Long mgmtId = roleIdByKey(connection, "geo_mgmt_review");
            if (mgmtId != null) {
                statement.execute("""
                        INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                        SELECT %d, menu_id, 1 FROM sys_menu WHERE menu_id IN (178, 179, 180, 181)
                        """.formatted(mgmtId));
            }
        }
        log.info("已同步文章审核规则菜单 menu_id=178 path=geo/review-rule");
    }

    /** AI 预审：规则开关 + 结果表 + 反馈表 */
    private void ensureContentReviewAiPreReview(Connection connection) throws Exception {
        if (tableExists(connection, "geo_content_review_rule")
                && !columnExists(connection, "geo_content_review_rule", "ai_pre_review")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_review_rule
                          ADD COLUMN ai_pre_review TINYINT NOT NULL DEFAULT 1
                            COMMENT '是否启用AI预审 1=是 0=否' AFTER skip_stage1
                        """);
                statement.execute("""
                        ALTER TABLE geo_content_review_rule
                          ADD COLUMN ai_mode VARCHAR(32) NOT NULL DEFAULT 'assist'
                            COMMENT 'assist=仅辅助 skip_stage1_on_pass=AI通过可跳过本阶段人工' AFTER ai_pre_review
                        """);
                statement.execute("""
                        ALTER TABLE geo_content_review_rule
                          ADD COLUMN ai_block_approve TINYINT NOT NULL DEFAULT 0
                            COMMENT 'AI未完成前禁止人工通过 1=是 0=否' AFTER ai_mode
                        """);
            }
            log.info("已为 geo_content_review_rule 增加 AI 预审字段");
        }
        if (tableExists(connection, "geo_content_review_rule")
                && columnExists(connection, "geo_content_review_rule", "ai_pre_review")
                && !columnExists(connection, "geo_content_review_rule", "ai_mode")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_content_review_rule
                          ADD COLUMN ai_mode VARCHAR(32) NOT NULL DEFAULT 'assist'
                            COMMENT 'assist=仅辅助 skip_stage1_on_pass=AI通过可跳过本阶段人工' AFTER ai_pre_review
                        """);
                statement.execute("""
                        ALTER TABLE geo_content_review_rule
                          ADD COLUMN ai_block_approve TINYINT NOT NULL DEFAULT 0
                            COMMENT 'AI未完成前禁止人工通过 1=是 0=否' AFTER ai_mode
                        """);
            }
            log.info("已为 geo_content_review_rule 补齐 ai_mode/ai_block_approve");
        }
        if (!tableExists(connection, "geo_content_review_ai")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_content_review_ai (
                          id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
                          review_id       BIGINT         NOT NULL                COMMENT '审核轮次ID',
                          placement_id    BIGINT         NOT NULL                COMMENT '投放主表ID',
                          status          VARCHAR(20)    NOT NULL DEFAULT 'pending'
                            COMMENT 'pending/running/done/failed',
                          suggestion      VARCHAR(20)    NOT NULL DEFAULT ''
                            COMMENT 'approve/reject/空',
                          score           INT            NULL                     COMMENT '0-100',
                          summary         VARCHAR(2000)  NOT NULL DEFAULT ''     COMMENT '风险摘要',
                          issues_json     MEDIUMTEXT     NULL                     COMMENT '划词建议JSON',
                          provider        VARCHAR(64)    NOT NULL DEFAULT ''     COMMENT 'AI厂商',
                          model_name      VARCHAR(128)   NOT NULL DEFAULT ''     COMMENT '模型名',
                          error_msg       VARCHAR(500)   NOT NULL DEFAULT ''     COMMENT '失败原因',
                          adopted         TINYINT        NOT NULL DEFAULT 0      COMMENT '是否已采纳划词',
                          feedback        VARCHAR(20)    NOT NULL DEFAULT ''
                            COMMENT 'accurate/inaccurate/空',
                          feedback_remark VARCHAR(500)   NOT NULL DEFAULT ''     COMMENT '反馈说明',
                          feedback_by     VARCHAR(64)    NOT NULL DEFAULT ''     COMMENT '反馈人',
                          started_at      DATETIME       NULL                     COMMENT '开始时间',
                          finished_at     DATETIME       NULL                     COMMENT '结束时间',
                          create_by       VARCHAR(64)    DEFAULT ''              COMMENT '创建者',
                          create_time     DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_by       VARCHAR(64)    DEFAULT ''              COMMENT '更新者',
                          update_time     DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          is_active       TINYINT        NOT NULL DEFAULT 1      COMMENT '是否有效',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_review_ai_review (review_id),
                          KEY idx_review_ai_placement (placement_id),
                          KEY idx_review_ai_status (status)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO文章审核AI预审结果'
                        """);
            }
            log.info("已创建 geo_content_review_ai");
        }
    }

    /** 内容审核知识库：线上维护，供 AI 预审引用 */
    private void ensureContentReviewKb(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_review_kb")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_review_kb (
                          id           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
                          category     VARCHAR(32)    NOT NULL                COMMENT '分类',
                          code         VARCHAR(64)    NOT NULL                COMMENT '稳定编码',
                          title        VARCHAR(200)   NOT NULL DEFAULT ''     COMMENT '标题',
                          content      MEDIUMTEXT     NOT NULL                COMMENT '正文Markdown',
                          attrs        JSON           NULL                    COMMENT '分类扩展(门店结构化等)',
                          sort_order   INT            NOT NULL DEFAULT 100    COMMENT '排序',
                          enabled      TINYINT        NOT NULL DEFAULT 1      COMMENT '是否启用',
                          source_file  VARCHAR(255)   NOT NULL DEFAULT ''     COMMENT '来源文件',
                          remark       VARCHAR(500)   NOT NULL DEFAULT ''     COMMENT '备注',
                          create_by    VARCHAR(64)    DEFAULT ''              COMMENT '创建者',
                          create_time  DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_by    VARCHAR(64)    DEFAULT ''              COMMENT '更新者',
                          update_time  DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          is_active    TINYINT        NOT NULL DEFAULT 1      COMMENT '是否有效',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_review_kb_code (code),
                          KEY idx_review_kb_cat (category, enabled, sort_order)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO内容审核知识库'
                        """);
            }
            log.info("已创建 geo_review_kb");
        }
        if (tableExists(connection, "geo_review_kb") && !columnExists(connection, "geo_review_kb", "attrs")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_review_kb
                          ADD COLUMN attrs JSON NULL COMMENT '分类扩展(门店结构化等)' AFTER content
                        """);
            }
            log.info("已为 geo_review_kb 增加 attrs 列");
        }
        // 菜单 ID 必须避开招聘占用的 200–230；审稿知识库统一用 311–314
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (311, '审稿知识库', 130, 7, 'geo/review-kb', '', 'C', 'geo:reviewKb:list', 'BookOutlined', 0, 0,
                     '内容审核智能体口径，可线上改', 1),
                    (312, '知识新增', 311, 1, '', '', 'F', 'geo:reviewKb:add', '#', 0, 0, '', 1),
                    (313, '知识修改', 311, 2, '', '', 'F', 'geo:reviewKb:edit', '#', 0, 0, '', 1),
                    (314, '知识删除', 311, 3, '', '', 'F', 'geo:reviewKb:delete', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = VALUES(parent_id),
                      sort_order = VALUES(sort_order),
                      path = VALUES(path),
                      component = VALUES(component),
                      menu_type = VALUES(menu_type),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      is_active = 1,
                      visible = 0,
                      status = 0
                    """);
            grantGeoKnowledgeMenus(statement, "311,312,313,314");
        }
        log.info("已同步审稿知识库菜单 menu_id=311 path=geo/review-kb");
    }

    /**
     * 基础配置下知识库分组（ID 避开招聘 200–230）：
     * - 310 文章生成知识库：卖点182 / 素材192 / 风格196 / 骨架315
     * - 311 审稿知识库
     */
    private void ensureGeoKnowledgeMenuTree(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (310, '文章生成知识库', 130, 6, 'geo/gen-kb', '', 'M', '', 'ReadOutlined', 0, 0,
                     '卖点/素材/风格/骨架，供 AI 成文引用', 1),
                    (311, '审稿知识库', 130, 7, 'geo/review-kb', '', 'C', 'geo:reviewKb:list', 'BookOutlined', 0, 0,
                     '内容审核智能体口径，可线上改', 1),
                    (312, '知识新增', 311, 1, '', '', 'F', 'geo:reviewKb:add', '#', 0, 0, '', 1),
                    (313, '知识修改', 311, 2, '', '', 'F', 'geo:reviewKb:edit', '#', 0, 0, '', 1),
                    (314, '知识删除', 311, 3, '', '', 'F', 'geo:reviewKb:delete', '#', 0, 0, '', 1),
                    (315, '文章骨架', 310, 4, 'geo/skeleton', '', 'C', 'geo:skeleton:list', 'PartitionOutlined', 0, 0,
                     'AI成文章节骨架维护', 1),
                    (316, '骨架新增', 315, 1, '', '', 'F', 'geo:skeleton:add', '#', 0, 0, '', 1),
                    (317, '骨架修改', 315, 2, '', '', 'F', 'geo:skeleton:edit', '#', 0, 0, '', 1),
                    (318, '骨架删除', 315, 3, '', '', 'F', 'geo:skeleton:delete', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = VALUES(parent_id),
                      sort_order = VALUES(sort_order),
                      path = VALUES(path),
                      component = VALUES(component),
                      menu_type = VALUES(menu_type),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      is_active = 1,
                      visible = 0,
                      status = 0
                    """);

            // 基础配置排序
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 130, sort_order = 4, is_active = 1, visible = 0, status = 0 WHERE menu_id = 178");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 130, sort_order = 5, is_active = 1, visible = 0, status = 0 WHERE menu_id = 187");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 130, sort_order = 6, is_active = 1, visible = 0, status = 0 WHERE menu_id = 310");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 130, sort_order = 7, is_active = 1, visible = 0, status = 0 WHERE menu_id = 311");

            // 卖点/素材/风格 挂到「文章生成知识库」下（切勿再挂 209，那是招聘按钮）
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 310, sort_order = 1, menu_name = '卖点库',
                      path = 'geo/sell-point', perms = 'geo:sellPoint:list', menu_type = 'C',
                      is_active = 1, visible = 0, status = 0
                    WHERE menu_id = 182
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 310, sort_order = 2, menu_name = '素材库',
                      path = 'geo/media', perms = 'geo:media:list', menu_type = 'C',
                      is_active = 1, visible = 0, status = 0
                    WHERE menu_id = 192
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 310, sort_order = 3, menu_name = '风格包',
                      path = 'geo/style-pack', perms = 'geo:stylePack:list', menu_type = 'C',
                      is_active = 1, visible = 0, status = 0
                    WHERE menu_id = 196
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 310, sort_order = 4, menu_name = '文章骨架',
                      path = 'geo/skeleton', perms = 'geo:skeleton:list', menu_type = 'C',
                      is_active = 1, visible = 0, status = 0
                    WHERE menu_id = 315
                    """);

            statement.executeUpdate("UPDATE sys_menu SET parent_id = 182, is_active = 1, visible = 0, status = 0 WHERE menu_id BETWEEN 183 AND 186");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 192, is_active = 1, visible = 0, status = 0 WHERE menu_id BETWEEN 193 AND 195");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 196, is_active = 1, visible = 0, status = 0 WHERE menu_id BETWEEN 197 AND 199");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 315, is_active = 1, visible = 0, status = 0 WHERE menu_id BETWEEN 316 AND 318");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 311, is_active = 1, visible = 0, status = 0 WHERE menu_id BETWEEN 312 AND 314");

            // 若误把招聘菜单改成 GEO，这里按 path 纠正挂载（招聘迁移也会再写回）
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 310
                    WHERE path IN ('geo/sell-point','geo/media','geo/style-pack','geo/skeleton')
                      AND menu_type = 'C' AND is_active = 1
                    """);

            grantGeoKnowledgeMenus(statement,
                    "310,311,312,313,314,315,316,317,318,182,183,184,185,186,192,193,194,195,196,197,198,199");
        }
        log.info("已重组 GEO 知识库菜单：基础配置 → 文章生成知识库(310) / 审稿知识库(311)");
    }

    /** 给超管（role_key=admin）、拥有话题/基础配置等权限的角色挂上知识库菜单 */
    private void grantGeoKnowledgeMenus(Statement statement, String menuIdCsv) throws Exception {
        statement.execute("""
                INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT r.role_id, m.menu_id, 1
                FROM sys_role r
                CROSS JOIN sys_menu m
                WHERE r.role_key = 'admin' AND r.is_active = 1
                  AND m.menu_id IN (%s)
                """.formatted(menuIdCsv));
        statement.execute("""
                INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT DISTINCT rm.role_id, m.menu_id, 1
                FROM sys_role_menu rm
                CROSS JOIN sys_menu m
                WHERE rm.is_active = 1
                  AND rm.menu_id IN (1, 100, 101, 113, 130, 178, 182, 187, 192, 196, 310, 311)
                  AND m.menu_id IN (%s)
                """.formatted(menuIdCsv));
        statement.execute("""
                UPDATE sys_role_menu rm
                INNER JOIN sys_role r ON r.role_id = rm.role_id
                SET rm.is_active = 1
                WHERE rm.menu_id IN (%s) AND r.role_key = 'admin'
                """.formatted(menuIdCsv));
    }

    /** 默认发布人规则：未指定发布人时自动套用（种子：小穆） */
    private void ensureContentPublisherRule(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_content_publisher_rule")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_content_publisher_rule (
                          id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                          publisher_user_id  BIGINT        NOT NULL                COMMENT '发布人用户ID',
                          publisher_name     VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '发布人展示名',
                          is_default         TINYINT       NOT NULL DEFAULT 1      COMMENT '是否默认发布人 1=是 0=否',
                          remark             VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '备注',
                          create_by          VARCHAR(64)   DEFAULT ''              COMMENT '创建者',
                          create_time        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_by          VARCHAR(64)   DEFAULT ''              COMMENT '更新者',
                          update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          is_active          TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
                          PRIMARY KEY (id),
                          KEY idx_publisher_rule_user (publisher_user_id, is_active),
                          KEY idx_publisher_rule_default (is_default, is_active)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO文章发布人规则'
                        """);
            }
            log.info("已创建 geo_content_publisher_rule");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO geo_content_publisher_rule (publisher_user_id, publisher_name, is_default, remark, is_active)
                    SELECT u.user_id,
                           COALESCE(NULLIF(TRIM(u.nickname), ''), NULLIF(TRIM(u.username), ''), CONCAT('用户', u.user_id)),
                           1,
                           '系统默认：未指定发布人的文章自动套用',
                           1
                    FROM sys_user u
                    WHERE u.is_active = 1
                      AND u.status = 0
                      AND (TRIM(u.nickname) = '小穆' OR TRIM(u.username) = '小穆')
                      AND NOT EXISTS (
                          SELECT 1 FROM geo_content_publisher_rule r WHERE r.is_active = 1 AND r.is_default = 1
                      )
                    ORDER BY u.user_id
                    LIMIT 1
                    """);
            // 182-186 已被卖点库占用，发布人规则用 187-190，挂在基础配置(130)下
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (187, '发布人规则', 130, 5, 'geo/publisher-rule', '', 'C', 'geo:publisherRule:list', 'UserOutlined', 0, 0,
                     '配置默认发布人；未指定时自动套用', 1),
                    (188, '发布人规则新增', 187, 1, '', '', 'F', 'geo:publisherRule:add', '#', 0, 0, '', 1),
                    (189, '发布人规则修改', 187, 2, '', '', 'F', 'geo:publisherRule:edit', '#', 0, 0, '', 1),
                    (190, '发布人规则删除', 187, 3, '', '', 'F', 'geo:publisherRule:delete', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = VALUES(parent_id),
                      sort_order = VALUES(sort_order),
                      path = VALUES(path),
                      component = VALUES(component),
                      menu_type = VALUES(menu_type),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      is_active = 1,
                      visible = VALUES(visible)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (187, 188, 189, 190)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT rm.role_id, m.menu_id, 1
                    FROM sys_role_menu rm
                    CROSS JOIN sys_menu m
                    WHERE rm.menu_id IN (101, 178) AND rm.is_active = 1
                      AND m.menu_id IN (187, 188, 189, 190)
                    """);
            Long mgmtId = roleIdByKey(connection, "geo_mgmt_review");
            if (mgmtId != null) {
                statement.execute("""
                        INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                        SELECT %d, menu_id, 1 FROM sys_menu WHERE menu_id IN (187, 188, 189, 190)
                        """.formatted(mgmtId));
            }
        }
        log.info("已同步发布人规则菜单 menu_id=187 path=geo/publisher-rule");
    }

    /** 卖点域树 + 卖点条目；菜单挂在内容投放下 */
    private void ensureSellPointLibrary(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_sell_domain")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_sell_domain (
                          id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                          parent_id   BIGINT        NOT NULL DEFAULT 0      COMMENT '父域ID，0=根',
                          name        VARCHAR(128)  NOT NULL                COMMENT '域名称',
                          code        VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '稳定编码',
                          sort_order  INT           NOT NULL DEFAULT 0      COMMENT '排序',
                          enabled     TINYINT       NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
                          remark      VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '备注',
                          create_by   VARCHAR(64)   DEFAULT ''              COMMENT '创建者',
                          create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_by   VARCHAR(64)   DEFAULT ''              COMMENT '更新者',
                          update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          is_active   TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
                          PRIMARY KEY (id),
                          KEY idx_sell_domain_parent (parent_id, is_active),
                          KEY idx_sell_domain_code (code)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO卖点域'
                        """);
            }
            log.info("已创建 geo_sell_domain");
        }
        if (!tableExists(connection, "geo_sell_point")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_sell_point (
                          id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                          domain_id      BIGINT        NOT NULL                COMMENT '所属域',
                          title          VARCHAR(200)  NOT NULL                COMMENT '短标题',
                          claim          VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '一句话主张',
                          evidence       MEDIUMTEXT    NULL                     COMMENT '论据正文',
                          tags_json      VARCHAR(1000) NOT NULL DEFAULT '[]'   COMMENT '标签JSON',
                          keywords_json  VARCHAR(1000) NOT NULL DEFAULT '[]'   COMMENT '检索词JSON',
                          evidence_level VARCHAR(16)   NOT NULL DEFAULT 'soft' COMMENT 'soft/hard',
                          source_sheet   VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '导入sheet',
                          source_row     INT           NULL                     COMMENT '导入行号',
                          status         VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT 'active/draft/disabled',
                          sort_order     INT           NOT NULL DEFAULT 0      COMMENT '排序',
                          version_no     INT           NOT NULL DEFAULT 1      COMMENT '版本',
                          create_by      VARCHAR(64)   DEFAULT ''              COMMENT '创建者',
                          create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_by      VARCHAR(64)   DEFAULT ''              COMMENT '更新者',
                          update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          is_active      TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
                          PRIMARY KEY (id),
                          KEY idx_sell_point_domain (domain_id, status, is_active),
                          KEY idx_sell_point_title (title)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO卖点条目'
                        """);
            }
            log.info("已创建 geo_sell_point");
        }
        // 分类型明细：品控 / 社会责任 / 权威背书（字段与 Excel sheet 对齐，不再硬塞 claim/evidence）
        if (!columnExists(connection, "geo_sell_point", "kind")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_sell_point
                          ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'simple'
                          COMMENT 'simple|quality|social|authority' AFTER domain_id
                        """);
            }
            log.info("已为 geo_sell_point 增加 kind");
        }
        if (!tableExists(connection, "geo_sell_quality")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_sell_quality (
                          point_id      BIGINT        NOT NULL COMMENT '卖点条目ID',
                          module_name   VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '模块',
                          section_name  VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '小节',
                          item_name     VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '项目',
                          standard_text MEDIUMTEXT    NULL COMMENT '内容/标准/要求',
                          PRIMARY KEY (point_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO卖点-品控明细'
                        """);
            }
            log.info("已创建 geo_sell_quality");
        }
        if (!tableExists(connection, "geo_sell_social")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_sell_social (
                          point_id       BIGINT        NOT NULL COMMENT '卖点条目ID',
                          dimension      VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '维度',
                          event_name     VARCHAR(300)  NOT NULL DEFAULT '' COMMENT '卖点/事件名',
                          action_content MEDIUMTEXT    NULL COMMENT '行动内容',
                          event_remark   VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '备注',
                          PRIMARY KEY (point_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO卖点-社会责任明细'
                        """);
            }
            log.info("已创建 geo_sell_social");
        }
        if (!tableExists(connection, "geo_sell_authority")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_sell_authority (
                          point_id   BIGINT        NOT NULL COMMENT '卖点条目ID',
                          channel    VARCHAR(300)  NOT NULL DEFAULT '' COMMENT '背书渠道',
                          admission  MEDIUMTEXT    NULL COMMENT '准入标准',
                          process_text MEDIUMTEXT  NULL COMMENT '遴选流程',
                          meaning    MEDIUMTEXT    NULL COMMENT '背书意义',
                          extra_note MEDIUMTEXT    NULL COMMENT '补充说明/总述正文',
                          PRIMARY KEY (point_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO卖点-权威背书明细'
                        """);
            }
            log.info("已创建 geo_sell_authority");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (182, '卖点库', 310, 1, 'geo/sell-point', '', 'C', 'geo:sellPoint:list', 'BulbOutlined', 0, 0,
                     '品牌卖点维护，供AI生成/审核引用', 1),
                    (183, '卖点新增', 182, 1, '', '', 'F', 'geo:sellPoint:add', '#', 0, 0, '', 1),
                    (184, '卖点修改', 182, 2, '', '', 'F', 'geo:sellPoint:edit', '#', 0, 0, '', 1),
                    (185, '卖点删除', 182, 3, '', '', 'F', 'geo:sellPoint:delete', '#', 0, 0, '', 1),
                    (186, '卖点导入', 182, 4, '', '', 'F', 'geo:sellPoint:import', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = VALUES(parent_id),
                      sort_order = VALUES(sort_order),
                      path = VALUES(path),
                      component = VALUES(component),
                      menu_type = VALUES(menu_type),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      is_active = 1,
                      visible = VALUES(visible)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (182, 183, 184, 185, 186)
                    """);
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT rm.role_id, m.menu_id, 1
                    FROM sys_role_menu rm
                    CROSS JOIN sys_menu m
                    WHERE rm.menu_id = 101 AND rm.is_active = 1
                      AND m.menu_id IN (182, 183, 184, 185, 186)
                    """);
            Long mgmtId = roleIdByKey(connection, "geo_mgmt_review");
            if (mgmtId != null) {
                statement.execute("""
                        INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                        SELECT %d, menu_id, 1 FROM sys_menu WHERE menu_id IN (182, 183, 184, 185, 186)
                        """.formatted(mgmtId));
            }
            Long opsId = roleIdByKey(connection, "geo_ops_entry");
            if (opsId != null) {
                statement.execute("""
                        INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                        SELECT %d, menu_id, 1 FROM sys_menu WHERE menu_id IN (182, 183, 184, 185, 186)
                        """.formatted(opsId));
            }
        }
        log.info("已同步卖点库菜单 menu_id=182 path=geo/sell-point");
    }

    /**
     * AI 成文流水线基建：素材库（须关联话题才能召回）、风格包、文章骨架、使用溯源 + 菜单。
     */
    private void ensureArticleAiPipeline(Connection connection) throws Exception {
        if (!tableExists(connection, "geo_media_asset")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_media_asset (
                          id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
                          title           VARCHAR(200)   NOT NULL DEFAULT '' COMMENT '素材标题',
                          file_url        VARCHAR(500)   NOT NULL COMMENT '图片相对/绝对路径',
                          thumb_url       VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '缩略图',
                          source_type     VARCHAR(32)    NOT NULL DEFAULT 'own' COMMENT 'own|stock_licensed',
                          license_note    VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '授权说明',
                          brand_visible   TINYINT        NOT NULL DEFAULT 0 COMMENT '图中是否已有品牌露出',
                          caption         VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '默认图注',
                          alt_text        VARCHAR(300)   NOT NULL DEFAULT '' COMMENT '无障碍描述',
                          tags_json       VARCHAR(1000)  NOT NULL DEFAULT '[]' COMMENT '标签JSON',
                          width_px        INT            NULL COMMENT '宽',
                          height_px       INT            NULL COMMENT '高',
                          status          VARCHAR(16)    NOT NULL DEFAULT 'active' COMMENT 'active|draft|disabled',
                          remark          VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '备注',
                          create_by       VARCHAR(64)    DEFAULT '' COMMENT '创建者',
                          create_time     DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_by       VARCHAR(64)    DEFAULT '' COMMENT '更新者',
                          update_time     DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          is_active       TINYINT        NOT NULL DEFAULT 1 COMMENT '是否有效',
                          PRIMARY KEY (id),
                          KEY idx_media_status (status, is_active),
                          KEY idx_media_brand (brand_visible, is_active)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO文章素材库'
                        """);
            }
            log.info("已创建 geo_media_asset");
        }
        // 语义用途字段（召回主依据）；话题关联改为可选软优先
        if (!columnExists(connection, "geo_media_asset", "usage_hint")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_media_asset
                          ADD COLUMN usage_hint VARCHAR(500) NOT NULL DEFAULT ''
                          COMMENT '可用于哪里/适用段落场景' AFTER tags_json
                        """);
            }
            log.info("已为 geo_media_asset 增加 usage_hint");
        }
        if (!columnExists(connection, "geo_media_asset", "scene_tags_json")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE geo_media_asset
                          ADD COLUMN scene_tags_json VARCHAR(1000) NOT NULL DEFAULT '[]'
                          COMMENT '场景标签JSON' AFTER usage_hint
                        """);
            }
            log.info("已为 geo_media_asset 增加 scene_tags_json");
        }
        if (!tableExists(connection, "geo_media_asset_topic")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_media_asset_topic (
                          id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
                          asset_id   BIGINT   NOT NULL COMMENT '素材ID',
                          topic_id   BIGINT   NOT NULL COMMENT '话题ID',
                          create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_asset_topic (asset_id, topic_id),
                          KEY idx_topic_asset (topic_id, asset_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材-话题关联（可选软优先）'
                        """);
            }
            log.info("已创建 geo_media_asset_topic");
        }
        if (!tableExists(connection, "geo_article_style_pack")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_article_style_pack (
                          id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                          code            VARCHAR(64)   NOT NULL COMMENT '编码',
                          name            VARCHAR(128)  NOT NULL COMMENT '名称',
                          platform_hint   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '适用平台提示 zhihu/toutiao/...',
                          tone_guide      MEDIUMTEXT    NULL COMMENT '语气与节奏说明',
                          structure_guide MEDIUMTEXT    NULL COMMENT '结构说明',
                          sample_phrases  MEDIUMTEXT    NULL COMMENT '可摘用句式(JSON数组，最多摘1～2句)',
                          brand_guide     VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '品牌露出软约束',
                          enabled         TINYINT       NOT NULL DEFAULT 1 COMMENT '启用',
                          sort_order      INT           NOT NULL DEFAULT 0 COMMENT '排序',
                          create_by       VARCHAR(64)   DEFAULT '',
                          create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
                          update_by       VARCHAR(64)   DEFAULT '',
                          update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          is_active       TINYINT       NOT NULL DEFAULT 1,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_style_code (code)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO文章风格包'
                        """);
            }
            log.info("已创建 geo_article_style_pack");
        }
        if (!tableExists(connection, "geo_article_skeleton")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_article_skeleton (
                          id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                          code            VARCHAR(64)   NOT NULL COMMENT '编码',
                          name            VARCHAR(128)  NOT NULL COMMENT '名称',
                          section_schema  MEDIUMTEXT    NULL COMMENT '章节骨架JSON',
                          enabled         TINYINT       NOT NULL DEFAULT 1,
                          sort_order      INT           NOT NULL DEFAULT 0,
                          create_by       VARCHAR(64)   DEFAULT '',
                          create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
                          update_by       VARCHAR(64)   DEFAULT '',
                          update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          is_active       TINYINT       NOT NULL DEFAULT 1,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_skeleton_code (code)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO文章骨架'
                        """);
            }
            log.info("已创建 geo_article_skeleton");
        }
        if (!tableExists(connection, "geo_media_usage")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_media_usage (
                          id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                          asset_id      BIGINT       NOT NULL COMMENT '素材ID',
                          placement_id  BIGINT       NOT NULL COMMENT '投放ID',
                          item_id       BIGINT       NULL COMMENT '平台稿ID',
                          section_key   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '章节标识',
                          create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (id),
                          KEY idx_usage_placement (placement_id),
                          KEY idx_usage_asset (asset_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材使用溯源'
                        """);
            }
            log.info("已创建 geo_media_usage");
        }
        if (!tableExists(connection, "geo_article_gen_trace")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE geo_article_gen_trace (
                          id             BIGINT       NOT NULL AUTO_INCREMENT,
                          placement_id   BIGINT       NOT NULL,
                          item_id        BIGINT       NULL,
                          style_code     VARCHAR(64)  NOT NULL DEFAULT '',
                          skeleton_code  VARCHAR(64)  NOT NULL DEFAULT '',
                          qa_rounds      INT          NOT NULL DEFAULT 0,
                          used_image_cnt INT          NOT NULL DEFAULT 0,
                          stages_json    MEDIUMTEXT   NULL,
                          create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (id),
                          KEY idx_gen_trace_placement (placement_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI成文流水线溯源'
                        """);
            }
            log.info("已创建 geo_article_gen_trace");
        }
        seedStylePacksAndSkeletons(connection);
        // 188-190 属发布人规则按钮；素材/风格/骨架改用 192+，并修复曾覆盖的发布人规则菜单
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (188, '发布人规则新增', 187, 1, '', '', 'F', 'geo:publisherRule:add', '#', 0, 0, '', 1),
                    (189, '发布人规则修改', 187, 2, '', '', 'F', 'geo:publisherRule:edit', '#', 0, 0, '', 1),
                    (190, '发布人规则删除', 187, 3, '', '', 'F', 'geo:publisherRule:delete', '#', 0, 0, '', 1),
                    (192, '素材库', 310, 2, 'geo/media', '', 'C', 'geo:media:list', 'PictureOutlined', 0, 0,
                     '文章插图素材；按用途/标签语义召回，话题可选', 1),
                    (193, '素材新增', 192, 1, '', '', 'F', 'geo:media:add', '#', 0, 0, '', 1),
                    (194, '素材修改', 192, 2, '', '', 'F', 'geo:media:edit', '#', 0, 0, '', 1),
                    (195, '素材删除', 192, 3, '', '', 'F', 'geo:media:delete', '#', 0, 0, '', 1),
                    (196, '风格包', 310, 3, 'geo/style-pack', '', 'C', 'geo:stylePack:list', 'SkinOutlined', 0, 0,
                     'AI成文语气/结构/可摘句式维护', 1),
                    (197, '风格包新增', 196, 1, '', '', 'F', 'geo:stylePack:add', '#', 0, 0, '', 1),
                    (198, '风格包修改', 196, 2, '', '', 'F', 'geo:stylePack:edit', '#', 0, 0, '', 1),
                    (199, '风格包删除', 196, 3, '', '', 'F', 'geo:stylePack:delete', '#', 0, 0, '', 1),
                    (315, '文章骨架', 310, 4, 'geo/skeleton', '', 'C', 'geo:skeleton:list', 'PartitionOutlined', 0, 0,
                     'AI成文章节骨架维护', 1),
                    (316, '骨架新增', 315, 1, '', '', 'F', 'geo:skeleton:add', '#', 0, 0, '', 1),
                    (317, '骨架修改', 315, 2, '', '', 'F', 'geo:skeleton:edit', '#', 0, 0, '', 1),
                    (318, '骨架删除', 315, 3, '', '', 'F', 'geo:skeleton:delete', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
                      parent_id = VALUES(parent_id),
                      sort_order = VALUES(sort_order),
                      path = VALUES(path),
                      component = VALUES(component),
                      menu_type = VALUES(menu_type),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      is_active = 1,
                      visible = 0,
                      status = 0
                    """);
            // 若曾把 191 建成「素材删除」且挂在错误父级，停用以免脏菜单
            statement.execute("""
                    UPDATE sys_menu SET is_active = 0
                    WHERE menu_id = 191 AND perms = 'geo:media:delete' AND parent_id = 188
                    """);
            String menuIds = "188,189,190,192,193,194,195,196,197,198,199,315,316,317,318";
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (%s)
                    """.formatted(menuIds));
            statement.execute("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT rm.role_id, m.menu_id, 1
                    FROM sys_role_menu rm
                    CROSS JOIN sys_menu m
                    WHERE rm.menu_id = 101 AND rm.is_active = 1
                      AND m.menu_id IN (%s)
                    """.formatted(menuIds));
            Long mgmtId = roleIdByKey(connection, "geo_mgmt_review");
            if (mgmtId != null) {
                statement.execute("""
                        INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                        SELECT %d, menu_id, 1 FROM sys_menu WHERE menu_id IN (%s)
                        """.formatted(mgmtId, menuIds));
            }
            Long opsId = roleIdByKey(connection, "geo_ops_entry");
            if (opsId != null) {
                statement.execute("""
                        INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                        SELECT %d, menu_id, 1 FROM sys_menu WHERE menu_id IN (%s)
                        """.formatted(opsId, menuIds));
            }
        }
        log.info("已同步素材库/风格包/骨架菜单 menu_id=192,196,315");
    }

    private void seedStylePacksAndSkeletons(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO geo_article_style_pack
                      (code, name, platform_hint, tone_guide, structure_guide, sample_phrases, brand_guide, enabled, sort_order, is_active)
                    SELECT * FROM (
                      SELECT
                        'zhihu_explain' AS code,
                        '知乎阐释体' AS name,
                        'zhihu' AS platform_hint,
                        '先答读者问题，再展开；口语克制，少口号。' AS tone_guide,
                        '结构随话题而定。' AS structure_guide,
                        '[]' AS sample_phrases,
                        '论证需要时自然点名「小巴依」。' AS brand_guide,
                        1 AS enabled, 1 AS sort_order, 1 AS is_active
                    ) t
                    WHERE NOT EXISTS (SELECT 1 FROM geo_article_style_pack x WHERE x.code = 'zhihu_explain' AND x.is_active = 1)
                    """);
            statement.execute("""
                    INSERT INTO geo_article_style_pack
                      (code, name, platform_hint, tone_guide, structure_guide, sample_phrases, brand_guide, enabled, sort_order, is_active)
                    SELECT * FROM (
                      SELECT
                        'toutiao_story' AS code,
                        '头条叙事体' AS name,
                        'toutiao' AS platform_hint,
                        '可读性强，数据点自然穿插。' AS tone_guide,
                        '结构随话题而定。' AS structure_guide,
                        '[]' AS sample_phrases,
                        '论证需要时自然点名「小巴依」。' AS brand_guide,
                        1 AS enabled, 2 AS sort_order, 1 AS is_active
                    ) t
                    WHERE NOT EXISTS (SELECT 1 FROM geo_article_style_pack x WHERE x.code = 'toutiao_story' AND x.is_active = 1)
                    """);
            statement.execute("""
                    INSERT INTO geo_article_skeleton
                      (code, name, section_schema, enabled, sort_order, is_active)
                    SELECT * FROM (
                      SELECT
                        'qa_sections_v1' AS code,
                        '问答分节骨架' AS name,
                        '{"lead":"导语1-2段直答","sections":[{"role":"point","min":2,"max":4},"{"role":"evidence"},{"role":"close"}]' AS section_schema,
                        1 AS enabled, 1 AS sort_order, 1 AS is_active
                    ) t
                    WHERE NOT EXISTS (SELECT 1 FROM geo_article_skeleton x WHERE x.code = 'qa_sections_v1' AND x.is_active = 1)
                    """);
        }
        // 修正骨架 JSON（上面字符串易写错），幂等更新
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE geo_article_skeleton
                    SET section_schema = '{"note":"结构由话题决定，勿套固定模板"}'
                    WHERE code = 'qa_sections_v1' AND is_active = 1
                    """);
            // v2：风格包只保留语气，去掉品牌次数/固定结构强迫
            String brandGuide = "论证需要时自然点名「小巴依」。";
            statement.execute("""
                    UPDATE geo_article_style_pack
                    SET brand_guide = '%s',
                        structure_guide = '结构随话题而定。',
                        sample_phrases = '[]',
                        tone_guide = CASE code
                          WHEN 'zhihu_explain' THEN '先答读者问题，再展开；口语克制，少口号。'
                          WHEN 'toutiao_story' THEN '可读性强，数据点自然穿插。'
                          ELSE tone_guide END
                    WHERE code IN ('zhihu_explain', 'toutiao_story') AND is_active = 1
                    """.formatted(brandGuide.replace("'", "''")));
        }
    }

    private void addItemColumn(Connection connection, String column, String sql) throws Exception {
        if (columnExists(connection, "geo_content_placement_item", column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            log.info("已为 geo_content_placement_item 增加 {}", column);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT COUNT(1) AS cnt
                     FROM INFORMATION_SCHEMA.TABLES
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = '%s'
                     """.formatted(table))) {
            return rs.next() && rs.getInt("cnt") > 0;
        }
    }

    private boolean indexExists(Connection connection, String table, String indexName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT COUNT(1) AS cnt
                     FROM INFORMATION_SCHEMA.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = '%s'
                       AND INDEX_NAME = '%s'
                     """.formatted(table, indexName))) {
            return rs.next() && rs.getInt("cnt") > 0;
        }
    }

    private String columnDataType(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT DATA_TYPE
                     FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = '%s'
                       AND COLUMN_NAME = '%s'
                     """.formatted(table, column))) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        // 优先用 INFORMATION_SCHEMA，避免 RDS/驱动 catalog 不一致导致误判
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT COUNT(1) AS cnt
                     FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = '%s'
                       AND COLUMN_NAME = '%s'
                     """.formatted(table, column))) {
            if (rs.next() && rs.getInt("cnt") > 0) {
                return true;
            }
        }
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet rs = metaData.getColumns(catalog, null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getColumns(catalog, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }
}
