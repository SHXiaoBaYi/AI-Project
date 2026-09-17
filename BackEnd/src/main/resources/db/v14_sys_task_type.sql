-- 任务类型基础配置；历史「投放」迁移为「文章发布」

CREATE TABLE IF NOT EXISTS sys_task_type (
  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  type_name    VARCHAR(64)   NOT NULL                COMMENT '类型名称',
  sort_order   INT           NOT NULL DEFAULT 0      COMMENT '排序（越小越靠前）',
  remark       VARCHAR(500)  DEFAULT ''              COMMENT '备注',
  create_by    VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_name (type_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务类型基础配置';

INSERT INTO sys_task_type (id, type_name, sort_order, remark, is_active)
VALUES
(1, '日常', 1, '默认日常事务', 1),
(2, '文章发布', 2, '原「投放」已更名', 1),
(3, '文件撰写', 3, '', 1),
(4, '账号', 4, '', 1),
(5, '其它', 99, '', 1)
ON DUPLICATE KEY UPDATE
  type_name = VALUES(type_name),
  sort_order = VALUES(sort_order),
  remark = VALUES(remark),
  is_active = 1;

-- 历史任务类型数据迁移
UPDATE sys_task SET task_type = '文章发布' WHERE task_type = '投放' AND is_active = 1;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(156, '任务类型', 150, 3, 'task/type', 'task/type/index', 'C', 'task:type:list', 'TagsOutlined', 0, 0, '任务类型基础配置', 1),
(157, '任务类型新增', 156, 1, '', '', 'F', 'task:type:add', '#', 0, 0, '', 1),
(158, '任务类型修改', 156, 2, '', '', 'F', 'task:type:edit', '#', 0, 0, '', 1),
(159, '任务类型删除', 156, 3, '', '', 'F', 'task:type:delete', '#', 0, 0, '', 1)
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  sort_order = VALUES(sort_order),
  path = VALUES(path),
  component = VALUES(component),
  perms = VALUES(perms),
  icon = VALUES(icon),
  remark = VALUES(remark),
  is_active = 1;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 156 AND 159
ON DUPLICATE KEY UPDATE is_active = 1;
