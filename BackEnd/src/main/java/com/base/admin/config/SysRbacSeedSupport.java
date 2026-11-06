package com.base.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RBAC 种子：发版只补「缺失」数据，不覆盖运营在后台改过的配置。
 * <ul>
 *   <li>{@code sys_role}：按 role_key 不存在时才 INSERT</li>
 *   <li>{@code sys_user_role}：仅新建用户时挂角色；已有用户不再改角色</li>
 *   <li>{@code sys_role_menu}：仅 INSERT IGNORE；禁止 ON DUPLICATE 把 is_active 改回 1</li>
 *   <li>角色默认菜单矩阵：仅在该 role_id 从未有过任何 menu 绑定时写入一次；之后只在 Migrator 里对<strong>新发版菜单</strong>调用 {@link #linkRoleMenusIfAbsent}</li>
 * </ul>
 */
public final class SysRbacSeedSupport {

    private static final Logger log = LoggerFactory.getLogger(SysRbacSeedSupport.class);

    private SysRbacSeedSupport() {
    }

    public static void ensureRoleIfAbsent(JdbcTemplate jdbc, String roleKey, String roleName, int sortOrder,
                                          String remark) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM sys_role WHERE role_key = ?", Integer.class, roleKey);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO sys_role (role_name, role_key, sort_order, status, remark, is_active)
                VALUES (?, ?, ?, 0, ?, 1)
                """, roleName, roleKey, sortOrder, remark);
        log.info("已创建角色 {} ({})", roleName, roleKey);
    }

    public static void linkUserRoleIfAbsent(JdbcTemplate jdbc, long userId, long roleId) {
        jdbc.update("INSERT IGNORE INTO sys_user_role (user_id, role_id, is_active) VALUES (?, ?, 1)", userId, roleId);
    }

    /** 仅当 (role_id, menu_id) 尚不存在时插入；不修改已有行（含 is_active=0 的运营裁剪）。 */
    public static void linkRoleMenusIfAbsent(JdbcTemplate jdbc, long roleId, int... menuIds) {
        for (int menuId : menuIds) {
            jdbc.update("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active) VALUES (?, ?, 1)
                    """, roleId, menuId);
        }
    }

    public static void linkRoleMenusIfAbsentByKey(JdbcTemplate jdbc, String roleKey, int... menuIds) {
        Long roleId = jdbc.query("""
                SELECT role_id FROM sys_role WHERE role_key = ? AND is_active = 1 LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, roleKey);
        if (roleId == null) {
            return;
        }
        linkRoleMenusIfAbsent(jdbc, roleId, menuIds);
    }

    /**
     * 空库首次：写入模块默认菜单矩阵。只要该角色历史上出现过任意 menu 绑定（含已软删），则整表跳过，交给运营配置。
     */
    public static void bootstrapRoleMenusIfNeverBound(JdbcTemplate jdbc, long roleId, int... defaultMenuIds) {
        if (defaultMenuIds.length == 0) {
            return;
        }
        Integer ever = jdbc.queryForObject(
                "SELECT COUNT(1) FROM sys_role_menu WHERE role_id = ?", Integer.class, roleId);
        if (ever != null && ever > 0) {
            return;
        }
        linkRoleMenusIfAbsent(jdbc, roleId, defaultMenuIds);
        log.info("已为 role_id={} 写入首次默认菜单 {} 项", roleId, defaultMenuIds.length);
    }

    public static void bootstrapRoleMenusIfNeverBoundByKey(JdbcTemplate jdbc, String roleKey, int... defaultMenuIds) {
        Long roleId = jdbc.query("""
                SELECT role_id FROM sys_role WHERE role_key = ? AND is_active = 1 LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, roleKey);
        if (roleId == null) {
            return;
        }
        bootstrapRoleMenusIfNeverBound(jdbc, roleId, defaultMenuIds);
    }
}
