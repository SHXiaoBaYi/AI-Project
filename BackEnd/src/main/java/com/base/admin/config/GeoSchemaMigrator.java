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
            ensureContentPlacementItemTitle(connection);
            ensureContentPlacementSource(connection);
            ensureContentPlacementAiModel(connection);
            ensureContentPlacementRelations(connection);
            ensureContentPlacementProgress(connection);
            ensureContentPlacementViewMenus(connection);
            ensureContentPlacementItemContentForm(connection);
            ensureArticleBoardMenu(connection);
            ensureDailyUniqueKeyWithTermType(connection);
            ensureGeoMenuRestructure(connection);
            ensurePublishedArticleMenu(connection);
            ensureCiteScreenshotColumn(connection);
            ensureItemPublishTimeDateTime(connection);
            ensureGeoStaffRoles(connection);
            retireLegacyGeoRoles(connection);
        } catch (Exception e) {
            log.error("GEO schema migrate failed", e);
            throw e;
        }
        log.info("GEO 平台主数据、看板落库与菜单已同步");
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
                    INSERT INTO sys_user_role (user_id, role_id, is_active)
                    SELECT DISTINCT ur.user_id, %d, 1
                    FROM sys_user_role ur
                    WHERE ur.role_id IN (%s) AND ur.is_active = 1
                      AND NOT EXISTS (
                        SELECT 1 FROM sys_user_role x
                        WHERE x.user_id = ur.user_id
                          AND x.role_id IN (%d, %d)
                          AND x.is_active = 1
                      )
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """.formatted(opsId, legacyIn, opsId, mgmtId));
            // 旧角色菜单并入两个正式角色
            int menusOps = st.executeUpdate("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT %d, rm.menu_id, 1
                    FROM sys_role_menu rm
                    WHERE rm.role_id IN (%s) AND rm.is_active = 1
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """.formatted(opsId, legacyIn));
            int menusMgmt = st.executeUpdate("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT %d, rm.menu_id, 1
                    FROM sys_role_menu rm
                    WHERE rm.role_id IN (%s) AND rm.is_active = 1
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """.formatted(mgmtId, legacyIn));
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
                    INSERT INTO sys_role (role_name, role_key, sort_order, status, remark, is_active)
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
                statement.execute("""
                        INSERT INTO geo_platform (platform_name, platform_type, sort_order, is_active)
                        VALUES ('%s', '内容发布平台', %d, 1)
                        ON DUPLICATE KEY UPDATE is_active = 1, sort_order = VALUES(sort_order)
                        """.formatted(safe, sort));
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
                    ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms), is_active = 1
                    """);
            statement.execute("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (125, 126)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """);
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
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (127, '生成相似问题', 122, 5, '', '', 'F', 'geo:content:generate', '#', 0, 0, '', 1)
                    ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms), is_active = 1
                    """);
            statement.execute("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id = 127
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """);
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
                        sort_order = 8,
                        is_active = 1
                    WHERE menu_id = 122
                    """);
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (128, '我的投放', 100, 9, 'geo/content-placement-work', '', 'C', 'geo:content:work', 'FormOutlined', 0, 0,
                     '一线视角：维护本人话题/目标问题的平台投放与引用详情', 1)
                    ON DUPLICATE KEY UPDATE
                      path = VALUES(path),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      sort_order = VALUES(sort_order),
                      is_active = 1
                    """);
            statement.execute("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (122, 128)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """);
        }
        log.info("已同步内容投放管理/我的投放双视角菜单");
    }

    /** 数据看板：AI露出 + 发布收录聚合页 */
    private void ensureArticleBoardMenu(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                    VALUES
                    (129, '数据看板', 100, 7, 'geo/article-board', '', 'C', 'geo:article:list', 'FundOutlined', 0, 0,
                     'AI露出情况与文章发布/收录情况聚合看板', 1)
                    ON DUPLICATE KEY UPDATE
                      path = VALUES(path),
                      perms = VALUES(perms),
                      icon = VALUES(icon),
                      remark = VALUES(remark),
                      sort_order = VALUES(sort_order),
                      is_active = 1
                    """);
            statement.execute("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id = 129
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """);
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
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
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
                      remark = VALUES(remark),
                      is_active = 1
                    """);

            statement.executeUpdate("UPDATE sys_menu SET parent_id = 130, sort_order = 1, menu_name = '话题管理', is_active = 1 WHERE menu_id = 101");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 130, sort_order = 2, menu_name = '平台管理', is_active = 1 WHERE menu_id = 113");
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 130, sort_order = 3, menu_name = '平台账号管理',
                      path = 'geo/platform-account', perms = 'geo:platformAccount:list', icon = 'IdcardOutlined', is_active = 1
                    WHERE menu_id = 133
                    """);

            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 131, sort_order = 1, menu_name = '日监测数据',
                      path = 'geo/daily', perms = 'geo:daily:list', icon = 'CalendarOutlined', is_active = 1
                    WHERE menu_id = 102
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 131, sort_order = 2, menu_name = '露出看板',
                      path = 'geo/expose-board', perms = 'geo:expose:list', icon = 'LineChartOutlined',
                      remark = '日/周/月/年露出经营看板（已结束周期读落库快照）', is_active = 1
                    WHERE menu_id = 117
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 131, sort_order = 3, menu_name = '全年目标',
                      path = 'geo/yearly-target', perms = 'geo:yearly:list', icon = 'DashboardOutlined',
                      remark = '全年目标配置与达成查看', is_active = 1
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
                      remark = '目标问题生成、分配发布人与投放进度', is_active = 1
                    WHERE menu_id = 122
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 3,
                      path = 'geo/content-placement-work', perms = 'geo:content:work', is_active = 1
                    WHERE menu_id = 128
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET menu_name = '我的投放'
                    WHERE menu_id = 128 AND menu_name IN ('投放执行', '')
                    """);

            statement.executeUpdate("UPDATE sys_menu SET parent_id = 117, perms = 'geo:expose:persist', menu_name = '露出落库' WHERE menu_id = 119");
            statement.executeUpdate("UPDATE sys_menu SET parent_id = 117, is_active = 0 WHERE menu_id IN (120, 121)");

            statement.execute("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (130, 131, 132, 117, 129)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """);
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
                      icon = '#', visible = 0, status = 0, is_active = 1
                    WHERE menu_id = 134 AND (path = 'geo/article' OR perms = 'geo:article:list' OR menu_type = 'C')
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET menu_name = '平台账号修改', parent_id = 133, sort_order = 2,
                      path = '', component = '', menu_type = 'F', perms = 'geo:platformAccount:edit',
                      icon = '#', visible = 0, status = 0, is_active = 1
                    WHERE menu_id = 135 AND (perms LIKE 'geo:article:%' OR parent_id = 134 OR parent_id = 170)
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET menu_name = '平台账号删除', parent_id = 133, sort_order = 3,
                      path = '', component = '', menu_type = 'F', perms = 'geo:platformAccount:delete',
                      icon = '#', visible = 0, status = 0, is_active = 1
                    WHERE menu_id = 136 AND (perms LIKE 'geo:article:%' OR parent_id = 134 OR parent_id = 170)
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET is_active = 0
                    WHERE menu_id = 137 AND perms = 'geo:article:delete'
                    """);

            statement.execute("""
                    INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
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
                      remark = VALUES(remark),
                      is_active = 1
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
                      perms = VALUES(perms),
                      is_active = 1
                    """);
            // 无对应页面的 article-board 占位入口：停用
            statement.executeUpdate("""
                    UPDATE sys_menu SET is_active = 0, visible = 1,
                      remark = '已停用：请使用「内容投放 → 文章列表」'
                    WHERE menu_id = 129 AND path LIKE '%article-board%'
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 2, menu_name = '投放管理',
                      path = 'geo/content-placement-manage', is_active = 1
                    WHERE menu_id = 122
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 3,
                      path = 'geo/content-placement-work', is_active = 1
                    WHERE menu_id = 128
                    """);
            statement.execute("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT rm.role_id, m.menu_id, 1
                    FROM sys_role_menu rm
                    CROSS JOIN sys_menu m
                    WHERE rm.menu_id = 122 AND rm.is_active = 1
                      AND m.menu_id IN (170, 171, 172, 173)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """);
            statement.execute("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active)
                    SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (170, 171, 172, 173)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """);
        }
        log.info("已同步文章列表菜单 menu_id=170 path=geo/article");
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
