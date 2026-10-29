package com.base.admin.config;

/**
 * sys_menu.menu_id 分段登记。新增菜单前必须落在本模块号段内，并同步更新
 * {@code docs/sys-menu-id-registry.md}。禁止跨模块占用或 ON DUPLICATE 覆盖他模块菜单。
 */
public final class SysMenuIdRanges {

    public enum Owner {
        /** 系统管理扩展（AI/钉钉等挂在系统下） */
        SYSTEM_EXT,
        /** GEO 监测/内容/知识库 */
        GEO,
        /** 数据看板 */
        BOARD,
        /** 招聘 */
        HR,
        /** 多意图机器人 */
        ROBOT,
        /** 收银培训 */
        TRAIN,
        /** 尚未分配，仅作预留说明 */
        RESERVED
    }

    private SysMenuIdRanges() {
    }

    /** 号段是否允许该模块写入该 menu_id。 */
    public static boolean allows(Owner owner, int menuId) {
        return switch (owner) {
            case SYSTEM_EXT -> menuId >= 140 && menuId <= 149;
            case GEO -> (menuId >= 100 && menuId <= 139)
                    || (menuId >= 150 && menuId <= 159)
                    || (menuId >= 170 && menuId <= 199)
                    || (menuId >= 310 && menuId <= 319);
            case BOARD -> menuId >= 160 && menuId <= 169;
            case HR -> (menuId >= 200 && menuId <= 249) || (menuId >= 261 && menuId <= 279);
            case ROBOT -> menuId >= 250 && menuId <= 260;
            case TRAIN -> (menuId >= 300 && menuId <= 309) || (menuId >= 320 && menuId <= 349);
            case RESERVED -> false;
        };
    }

    /** 根据已有菜单的 path/perms 推断归属；无法判断时返回 null。 */
    public static Owner inferOwner(String path, String perms) {
        String p = path == null ? "" : path.trim();
        String perm = perms == null ? "" : perms.trim();
        if (p.startsWith("/robot") || p.equals("robot") || p.startsWith("robot/")
                || perm.startsWith("robot:")) {
            return Owner.ROBOT;
        }
        if (p.startsWith("/hr") || p.equals("hr") || p.startsWith("hr/") || perm.startsWith("hr:")) {
            return Owner.HR;
        }
        if (p.startsWith("/train") || p.equals("train") || p.startsWith("train/")
                || perm.startsWith("train:")) {
            return Owner.TRAIN;
        }
        if (p.startsWith("board") || p.startsWith("/board") || perm.startsWith("board:")) {
            return Owner.BOARD;
        }
        if (p.startsWith("geo") || p.startsWith("/geo") || perm.startsWith("geo:")) {
            return Owner.GEO;
        }
        if (perm.startsWith("system:ai:") || perm.startsWith("system:dingtalk")
                || perm.startsWith("system:schedule-rule")
                || p.contains("ai-provider") || p.contains("dingtalk") || p.contains("schedule-rule")) {
            return Owner.SYSTEM_EXT;
        }
        return null;
    }

    /** HR 号段内下一可用建议（含扩展段）；调用方仍须避开已占用 ID。 */
    public static int hrNextHint() {
        return 265;
    }
}
