-- 数据看板拆为 GEO / 任务 两个子页

UPDATE sys_menu
SET menu_name = '数据看板',
    menu_type = 'M',
    path = 'board',
    component = '',
    perms = '',
    icon = 'FundOutlined',
    visible = 0,
    is_active = 1,
    remark = 'GEO/任务看板父菜单'
WHERE menu_id = 160;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(161, 'GEO看板', 160, 1, 'board/geo', 'board/geo/index', 'C', 'board:view', 'FundOutlined', 0, 0, 'GEO主题/人维图表下钻', 1),
(162, '任务看板', 160, 2, 'board/task', 'board/task/index', 'C', 'board:view', 'ProfileOutlined', 0, 0, '任务主题/人维图表下钻', 1)
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  sort_order = VALUES(sort_order),
  path = VALUES(path),
  component = VALUES(component),
  perms = VALUES(perms),
  icon = VALUES(icon),
  remark = VALUES(remark),
  menu_type = VALUES(menu_type),
  is_active = 1;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, 161, 1 FROM DUAL
ON DUPLICATE KEY UPDATE is_active = 1;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, 162, 1 FROM DUAL
ON DUPLICATE KEY UPDATE is_active = 1;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT DISTINCT role_id, 161, 1 FROM sys_role_menu
WHERE menu_id = 160 AND IFNULL(is_active, 1) = 1
ON DUPLICATE KEY UPDATE is_active = 1;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT DISTINCT role_id, 162, 1 FROM sys_role_menu
WHERE menu_id = 160 AND IFNULL(is_active, 1) = 1
ON DUPLICATE KEY UPDATE is_active = 1;
