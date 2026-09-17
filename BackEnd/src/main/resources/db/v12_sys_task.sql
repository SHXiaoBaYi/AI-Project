-- 任务模块：主表 + 执行人

CREATE TABLE IF NOT EXISTS sys_task (
  id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  title              VARCHAR(200)  NOT NULL                COMMENT '任务标题',
  content            TEXT                                   COMMENT '任务说明',
  task_type          VARCHAR(32)   NOT NULL DEFAULT '日常'  COMMENT '任务类型（关联 sys_task_type.type_name）',
  priority           TINYINT       NOT NULL DEFAULT 2      COMMENT '优先级 1低2中3高4紧急',
  status             VARCHAR(16)   NOT NULL DEFAULT '待处理' COMMENT '状态：待分配/待处理/进行中/已完成/已取消',
  progress           TINYINT       NOT NULL DEFAULT 0      COMMENT '进度0-100',
  creator_user_id    BIGINT        DEFAULT NULL            COMMENT '创建人用户ID',
  creator_name       VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '创建人展示名',
  owner_user_id      BIGINT        NOT NULL                COMMENT '负责人用户ID',
  owner_name         VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '负责人展示名',
  plan_start_time    DATETIME      DEFAULT NULL            COMMENT '计划开始',
  plan_end_time      DATETIME      DEFAULT NULL            COMMENT '计划截止',
  actual_start_time  DATETIME      DEFAULT NULL            COMMENT '实际开始',
  actual_end_time    DATETIME      DEFAULT NULL            COMMENT '实际完成',
  biz_type           VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '关联业务类型',
  biz_id             BIGINT        DEFAULT NULL            COMMENT '关联业务ID',
  biz_title          VARCHAR(200)  NOT NULL DEFAULT ''     COMMENT '关联业务摘要',
  parent_id          BIGINT        DEFAULT NULL            COMMENT '父任务ID',
  sort_order         INT           NOT NULL DEFAULT 0      COMMENT '排序',
  remark             VARCHAR(1000) DEFAULT ''              COMMENT '备注',
  create_by          VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active          TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  KEY idx_status_end (status, plan_end_time),
  KEY idx_owner (owner_user_id, status),
  KEY idx_creator (creator_user_id),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统任务';

CREATE TABLE IF NOT EXISTS sys_task_assignee (
  id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id            BIGINT        NOT NULL                COMMENT '任务ID',
  user_id            BIGINT        NOT NULL                COMMENT '执行人用户ID',
  user_name          VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '执行人展示名',
  role_label         VARCHAR(16)   NOT NULL DEFAULT '执行' COMMENT '角色：执行/协作/抄送',
  done               TINYINT       NOT NULL DEFAULT 0      COMMENT '个人是否完成 1=是 0=否',
  create_by          VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active          TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_user (task_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行人';

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(150, '任务', 0, 4, '/task', '', 'M', '', 'CheckSquareOutlined', 0, 0, '任务中心', 1),
(151, '任务管理', 150, 1, 'task/list', 'task/list/index', 'C', 'task:list', 'ProfileOutlined', 0, 0, '全部任务增删改查', 1),
(152, '我的任务', 150, 2, 'task/mine', 'task/mine/index', 'C', 'task:mine', 'ScheduleOutlined', 0, 0, '我负责或我执行的任务', 1),
(153, '任务新增', 151, 1, '', '', 'F', 'task:add', '#', 0, 0, '', 1),
(154, '任务修改', 151, 2, '', '', 'F', 'task:edit', '#', 0, 0, '', 1),
(155, '任务删除', 151, 3, '', '', 'F', 'task:delete', '#', 0, 0, '', 1)
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
SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 150 AND 155
ON DUPLICATE KEY UPDATE is_active = 1;
