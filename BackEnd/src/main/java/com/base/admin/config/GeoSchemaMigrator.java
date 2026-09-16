package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v3_geo_platform.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v4_geo_daily_board.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v5_geo_board_snapshot.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v9_geo_content_placement.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/v10_geo_optimize.sql"));
            ensureBoardLockedColumn(connection);
            ensureOwnerNameColumn(connection);
            ensureTermTypeColumn(connection);
            ensureOwnerUserIdColumn(connection);
            ensurePlatformType(connection);
            ensureContentPlacementItemTitle(connection);
            ensureContentPlacementSource(connection);
            ensureContentPlacementRelations(connection);
            ensureContentPlacementProgress(connection);
            ensureContentPlacementViewMenus(connection);
            ensureContentPlacementItemContentForm(connection);
            ensureArticleBoardMenu(connection);
            ensureDailyUniqueKeyWithTermType(connection);
            ensureGeoMenuRestructure(connection);
        } catch (Exception e) {
            log.error("GEO schema migrate failed", e);
            throw e;
        }
        log.info("GEO 平台主数据、看板落库与菜单已同步");
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
                    (128, '投放执行', 100, 9, 'geo/content-placement-work', '', 'C', 'geo:content:work', 'FormOutlined', 0, 0,
                     '一线视角：维护本人话题/目标问题的平台投放与引用详情', 1)
                    ON DUPLICATE KEY UPDATE
                      menu_name = VALUES(menu_name),
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
        log.info("已同步内容投放管理/执行双视角菜单");
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

            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 1,
                      path = 'geo/article-board', perms = 'geo:article:list', icon = 'FundOutlined', is_active = 1
                    WHERE menu_id = 129
                    """);
            // 修复：菜单管理曾把父级 path 拼到绝对叶子 path 上
            statement.executeUpdate("""
                    UPDATE sys_menu SET path = 'geo/article-board'
                    WHERE menu_id = 129 AND path <> 'geo/article-board' AND path LIKE '%article-board%'
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET menu_name = '数据看板'
                    WHERE menu_id = 129 AND menu_name IN ('我的文章看板', '')
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 2, menu_name = '投放管理',
                      path = 'geo/content-placement-manage', perms = 'geo:content:list', is_active = 1
                    WHERE menu_id = 122
                    """);
            statement.executeUpdate("""
                    UPDATE sys_menu SET parent_id = 132, sort_order = 3, menu_name = '投放执行',
                      path = 'geo/content-placement-work', perms = 'geo:content:work', is_active = 1
                    WHERE menu_id = 128
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
