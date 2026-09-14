-- 日报看板菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(117, '日报看板', 100, 4, 'geo/day', '', 'C', 'geo:day:list', 'LineChartOutlined', 0, 0, '', 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), path = VALUES(path), perms = VALUES(perms), icon = VALUES(icon), is_active = 1;

UPDATE sys_menu SET sort_order = 1 WHERE menu_id = 101;
UPDATE sys_menu SET sort_order = 2 WHERE menu_id = 113;
UPDATE sys_menu SET sort_order = 3 WHERE menu_id = 102;
UPDATE sys_menu SET sort_order = 4 WHERE menu_id = 117;
UPDATE sys_menu SET sort_order = 5 WHERE menu_id = 103;
UPDATE sys_menu SET sort_order = 6 WHERE menu_id = 104;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
VALUES (1, 117, 1)
ON DUPLICATE KEY UPDATE is_active = 1;
