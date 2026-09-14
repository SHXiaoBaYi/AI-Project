-- 平台主数据 + GEO 菜单补齐 + 工作台置顶
CREATE TABLE IF NOT EXISTS geo_platform (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '平台ID',
  platform_name   VARCHAR(64)  NOT NULL                COMMENT '平台名称',
  sort_order      INT          NOT NULL DEFAULT 0      COMMENT '排序',
  remark          VARCHAR(500) DEFAULT ''              COMMENT '备注',
  create_by       VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_platform_name (platform_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO监测平台基础数据';

INSERT INTO geo_platform (platform_name, sort_order, is_active)
VALUES ('豆包', 1, 1), ('DS', 2, 1), ('小红书', 3, 1)
ON DUPLICATE KEY UPDATE is_active = 1;

UPDATE sys_menu SET sort_order = 0 WHERE menu_name = '工作台' OR path IN ('workbench', '/workbench');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(113, '平台管理', 100, 2, 'geo/platform', '', 'C', 'geo:platform:list', 'AppstoreOutlined', 0, 0, '', 1),
(114, '平台新增', 113, 1, '', '', 'F', 'geo:platform:add', '#', 0, 0, '', 1),
(115, '平台修改', 113, 2, '', '', 'F', 'geo:platform:edit', '#', 0, 0, '', 1),
(116, '平台删除', 113, 3, '', '', 'F', 'geo:platform:delete', '#', 0, 0, '', 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), path = VALUES(path), perms = VALUES(perms), is_active = 1;

UPDATE sys_menu SET sort_order = 1 WHERE menu_id = 101;
UPDATE sys_menu SET sort_order = 2 WHERE menu_id = 113;
UPDATE sys_menu SET sort_order = 3 WHERE menu_id = 102;
UPDATE sys_menu SET sort_order = 4 WHERE menu_id = 103;
UPDATE sys_menu SET sort_order = 5 WHERE menu_id = 104;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 113 AND 116
ON DUPLICATE KEY UPDATE is_active = 1;
