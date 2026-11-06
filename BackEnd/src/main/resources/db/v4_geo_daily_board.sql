-- 日报看板菜单
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(117, '日报看板', 100, 4, 'geo/day', '', 'C', 'geo:day:list', 'LineChartOutlined', 0, 0, '', 1)
ON DUPLICATE KEY UPDATE path = VALUES(path), perms = VALUES(perms), icon = VALUES(icon);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
VALUES (1, 117, 1);
