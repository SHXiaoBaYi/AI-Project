-- GEO 任务看板子菜单

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(164, 'GEO任务看板', 160, 4, 'board/geo-work', 'board/geo-work/index', 'C', 'board:view', 'ScheduleOutlined', 0, 0, 'GEO任务按话题/撰写人/任务类型完成情况', 1)
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  sort_order = VALUES(sort_order),
  path = VALUES(path),
  component = VALUES(component),
  perms = VALUES(perms),
  icon = VALUES(icon),
  remark = VALUES(remark),
  menu_type = VALUES(menu_type);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, 164, 1 FROM DUAL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
SELECT DISTINCT role_id, 164, 1 FROM sys_role_menu
WHERE menu_id = 160 AND IFNULL(is_active, 1) = 1;
