package com.base.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 菜单种子写入：校验号段归属，禁止跨模块 ON DUPLICATE 覆盖。
 * SchemaMigrator 新增/更新菜单应走本类，勿直接裸写 sys_menu。
 */
public final class SysMenuSeedSupport {

    private static final Logger log = LoggerFactory.getLogger(SysMenuSeedSupport.class);

    private SysMenuSeedSupport() {
    }

    public static void upsert(JdbcTemplate jdbc, SysMenuIdRanges.Owner owner, int id, String name,
                              int parent, int sort, String path, String component, String type,
                              String perms, String icon, String remark) {
        if (!SysMenuIdRanges.allows(owner, id)) {
            throw new IllegalStateException(
                    "menu_id=" + id + " 不在 " + owner + " 预留号段内，见 docs/sys-menu-id-registry.md 与 SysMenuIdRanges");
        }
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT menu_name, path, perms FROM sys_menu WHERE menu_id = ?", id);
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.get(0);
            String oldPath = row.get("path") == null ? "" : String.valueOf(row.get("path"));
            String oldPerms = row.get("perms") == null ? "" : String.valueOf(row.get("perms"));
            String oldName = row.get("menu_name") == null ? "" : String.valueOf(row.get("menu_name"));
            SysMenuIdRanges.Owner oldOwner = SysMenuIdRanges.inferOwner(oldPath, oldPerms);
            if (oldOwner != null && oldOwner != owner) {
                // 不抛异常以免卡死启动；拒绝覆盖，避免再次「抢 ID」
                log.error("拒绝覆盖菜单 menu_id={}：库中归属 {}（{} / path={}），本次写入归属 {}。请改用空闲 ID 并更新 docs/sys-menu-id-registry.md",
                        id, oldOwner, oldName, oldPath, owner);
                return;
            }
        }
        jdbc.update("""
                INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 1)
                ON DUPLICATE KEY UPDATE
                  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), sort_order = VALUES(sort_order),
                  path = VALUES(path), component = VALUES(component), menu_type = VALUES(menu_type),
                  perms = VALUES(perms), icon = VALUES(icon), remark = VALUES(remark)
                """, id, name, parent, sort, path, component, type, perms, icon, remark);
    }

    /** 批量号段断言（用于仍用手写多行 INSERT 的 migrator）。 */
    public static void assertIdsInRange(SysMenuIdRanges.Owner owner, int... ids) {
        for (int id : ids) {
            if (!SysMenuIdRanges.allows(owner, id)) {
                throw new IllegalStateException(
                        "menu_id=" + id + " 不在 " + owner + " 预留号段内，见 docs/sys-menu-id-registry.md");
            }
        }
    }

    /** 启动时扫描：已占用 ID 若 path/perms 与号段登记矛盾则打 error（不中断，避免历史脏数据卡死）。 */
    public static void auditConflicts(JdbcTemplate jdbc) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT menu_id, menu_name, path, perms FROM sys_menu WHERE IFNULL(is_active, 1) = 1");
        int issues = 0;
        for (Map<String, Object> row : rows) {
            int id = ((Number) row.get("menu_id")).intValue();
            String path = row.get("path") == null ? "" : String.valueOf(row.get("path"));
            String perms = row.get("perms") == null ? "" : String.valueOf(row.get("perms"));
            String name = row.get("menu_name") == null ? "" : String.valueOf(row.get("menu_name"));
            SysMenuIdRanges.Owner inferred = SysMenuIdRanges.inferOwner(path, perms);
            if (inferred == null) {
                continue;
            }
            if (!SysMenuIdRanges.allows(inferred, id)) {
                issues++;
                log.error("菜单号段异常 menu_id={} name={} owner={} path={} —— 与 SysMenuIdRanges 不符",
                        id, name, inferred, path);
            }
        }
        if (issues == 0) {
            log.info("sys_menu 号段审计通过");
        } else {
            log.error("sys_menu 号段审计发现 {} 处异常，请核对 docs/sys-menu-id-registry.md", issues);
        }
    }
}
