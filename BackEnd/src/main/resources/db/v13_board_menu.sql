-- 统一数据看板：顶级菜单；下线旧露出/文章看板入口

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(160, '数据看板', 0, 2, 'board', 'board/index', 'C', 'board:view', 'FundOutlined', 0, 0,
 '露出/投放/任务统一看板', 1)
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
SELECT 1, 160, 1 FROM DUAL
ON DUPLICATE KEY UPDATE is_active = 1;

-- 原露出/文章看板角色继承到统一看板
INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT DISTINCT role_id, 160, 1 FROM sys_role_menu
WHERE menu_id IN (117, 129) AND IFNULL(is_active, 1) = 1
ON DUPLICATE KEY UPDATE is_active = 1;

-- 下线旧看板菜单（保留记录，侧栏不可见）
UPDATE sys_menu SET is_active = 0, visible = 1
WHERE menu_id IN (117, 129, 103, 118)
   OR path IN ('geo/expose-board', 'geo/article-board', 'geo/weekly', 'geo/monthly', 'geo/yearly', 'geo/day');
