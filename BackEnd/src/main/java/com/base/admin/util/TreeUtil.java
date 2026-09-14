package com.base.admin.util;

import com.base.admin.domain.entity.SysMenu;
import com.base.admin.domain.vo.MenuVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TreeUtil {

    private TreeUtil() {}

    public static List<MenuVO> buildMenuTree(List<SysMenu> menus) {
        Map<Long, MenuVO> voMap = menus.stream()
                .collect(Collectors.toMap(SysMenu::getMenuId, m -> MenuVO.builder()
                        .menuId(m.getMenuId())
                        .menuName(m.getMenuName())
                        .path(m.getPath())
                        .component(m.getComponent())
                        .icon(m.getIcon())
                        .sortOrder(m.getSortOrder())
                        .status(m.getStatus())
                        .children(new ArrayList<>())
                        .build()));

        List<MenuVO> tree = new ArrayList<>();
        for (SysMenu menu : menus) {
            MenuVO vo = voMap.get(menu.getMenuId());
            if (menu.getParentId() == null || menu.getParentId() == 0L) {
                tree.add(vo);
            } else {
                MenuVO parent = voMap.get(menu.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    tree.add(vo);
                }
            }
        }
        tree.sort((a, b) -> {
            int pin = Boolean.compare(isWorkbench(b), isWorkbench(a));
            if (pin != 0) {
                return pin;
            }
            return Integer.compare(sortOf(a), sortOf(b));
        });
        return tree;
    }

    private static boolean isWorkbench(MenuVO menu) {
        if (menu == null) {
            return false;
        }
        String path = menu.getPath() == null ? "" : menu.getPath().replace("/", "");
        return "工作台".equals(menu.getMenuName()) || "workbench".equalsIgnoreCase(path);
    }

    private static int sortOf(MenuVO menu) {
        return menu.getSortOrder() == null ? 0 : menu.getSortOrder();
    }
}
