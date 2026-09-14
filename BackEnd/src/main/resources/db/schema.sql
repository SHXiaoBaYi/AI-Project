-- Base Admin Management System Database Schema
CREATE DATABASE IF NOT EXISTS base_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE base_admin;

-- System users
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  user_id       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'User ID',
  username      VARCHAR(50)  NOT NULL                  COMMENT 'Login username',
  password      VARCHAR(200) NOT NULL                  COMMENT 'BCrypt hashed password',
  nickname      VARCHAR(50)  DEFAULT ''                COMMENT 'Display name',
  email         VARCHAR(100) DEFAULT ''                COMMENT 'Email',
  phone         VARCHAR(20)  DEFAULT ''                COMMENT 'Phone number',
  avatar        VARCHAR(500) DEFAULT ''                COMMENT 'Avatar URL',
  gender        TINYINT      DEFAULT 0                 COMMENT 'Gender: 0=unknown, 1=male, 2=female',
  status        TINYINT      DEFAULT 0                 COMMENT 'Status: 0=active, 1=disabled',
  create_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Created by',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  update_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Updated by',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  remark        VARCHAR(500) DEFAULT ''                COMMENT 'Remark',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System users';

-- System roles
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  role_id       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Role ID',
  role_name     VARCHAR(50)  NOT NULL                  COMMENT 'Role name',
  role_key      VARCHAR(100) NOT NULL                  COMMENT 'Role key',
  sort_order    INT          DEFAULT 0                 COMMENT 'Sort order',
  status        TINYINT      DEFAULT 0                 COMMENT 'Status: 0=active, 1=disabled',
  create_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Created by',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  update_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Updated by',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  remark        VARCHAR(500) DEFAULT ''                COMMENT 'Remark',
  PRIMARY KEY (role_id),
  UNIQUE KEY uk_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System roles';

-- System menus and permissions
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
  menu_id       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Menu ID',
  menu_name     VARCHAR(50)  NOT NULL                  COMMENT 'Menu name',
  parent_id     BIGINT       DEFAULT 0                 COMMENT 'Parent menu ID (0=root)',
  sort_order    INT          DEFAULT 0                 COMMENT 'Sort order',
  path          VARCHAR(200) DEFAULT ''                COMMENT 'Route path',
  component     VARCHAR(200) DEFAULT ''                COMMENT 'Frontend component path',
  menu_type     CHAR(1)      NOT NULL                  COMMENT 'Type: M=directory, C=menu, F=button',
  perms         VARCHAR(100) DEFAULT ''                COMMENT 'Permission string',
  icon          VARCHAR(100) DEFAULT '#'               COMMENT 'Menu icon',
  visible       TINYINT      DEFAULT 0                 COMMENT 'Visible: 0=yes, 1=no',
  status        TINYINT      DEFAULT 0                 COMMENT 'Status: 0=active, 1=disabled',
  create_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Created by',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  update_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Updated by',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  remark        VARCHAR(500) DEFAULT ''                COMMENT 'Remark',
  PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System menus and permissions';

-- User-Role association
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  user_id  BIGINT NOT NULL COMMENT 'User ID',
  role_id  BIGINT NOT NULL COMMENT 'Role ID',
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-Role association';

-- Role-Menu association
DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
  role_id  BIGINT NOT NULL COMMENT 'Role ID',
  menu_id  BIGINT NOT NULL COMMENT 'Menu ID',
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Role-Menu association';

-- Login log
DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
  log_id        BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Log ID',
  username      VARCHAR(50)  DEFAULT ''                COMMENT 'Login username',
  ip            VARCHAR(128) DEFAULT ''                COMMENT 'Login IP',
  location      VARCHAR(255) DEFAULT ''                COMMENT 'Login location',
  browser       VARCHAR(50)  DEFAULT ''                COMMENT 'Browser',
  os            VARCHAR(50)  DEFAULT ''                COMMENT 'OS',
  status        TINYINT      DEFAULT 0                 COMMENT 'Status: 0=success, 1=fail',
  msg           VARCHAR(255) DEFAULT ''                COMMENT 'Message',
  login_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Login time',
  PRIMARY KEY (log_id),
  INDEX idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Login log';

-- Operation log
DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
  log_id         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Log ID',
  title          VARCHAR(50)  DEFAULT ''                COMMENT 'Module title',
  business_type  TINYINT      DEFAULT 0                 COMMENT 'Business type: 0=other, 1=add, 2=edit, 3=delete',
  method         VARCHAR(200) DEFAULT ''                COMMENT 'Method name',
  request_method VARCHAR(10)  DEFAULT ''                COMMENT 'HTTP method',
  oper_name      VARCHAR(50)  DEFAULT ''                COMMENT 'Operator name',
  oper_url       VARCHAR(500) DEFAULT ''                COMMENT 'Request URL',
  oper_ip        VARCHAR(128) DEFAULT ''                COMMENT 'Operator IP',
  oper_param     TEXT                                    COMMENT 'Request params',
  json_result    TEXT                                    COMMENT 'Response result',
  status         TINYINT      DEFAULT 0                 COMMENT 'Status: 0=success, 1=fail',
  error_msg      TEXT                                    COMMENT 'Error message',
  oper_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Operation time',
  cost_time      BIGINT       DEFAULT 0                 COMMENT 'Cost time (ms)',
  PRIMARY KEY (log_id),
  INDEX idx_oper_time (oper_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Operation log';

-- =============================================
-- Seed Data
-- =============================================

-- Default admin role
INSERT INTO sys_role (role_id, role_name, role_key, sort_order, status, remark)
VALUES (1, '超级管理员', 'admin', 1, 0, '超级管理员拥有所有权限');

-- Default admin user (password: admin123)
INSERT INTO sys_user (user_id, username, password, nickname, status, remark)
VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 0, '系统管理员');

-- Assign admin role
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- System Management directory
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(1, '系统管理', 0, 1, '/system', '', 'M', '', 'SettingOutlined');

-- Menu items
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(2, '用户管理', 1, 1, 'user', 'system/user/index', 'C', 'system:user:list', 'UserOutlined'),
(3, '角色管理', 1, 2, 'role', 'system/role/index', 'C', 'system:role:list', 'TeamOutlined'),
(4, '菜单管理', 1, 3, 'menu', 'system/menu/index', 'C', 'system:menu:list', 'MenuOutlined');

-- User management buttons
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(5, '用户新增', 2, 1, '', '', 'F', 'system:user:add', '#'),
(6, '用户修改', 2, 2, '', '', 'F', 'system:user:edit', '#'),
(7, '用户删除', 2, 3, '', '', 'F', 'system:user:delete', '#'),
(8, '重置密码', 2, 4, '', '', 'F', 'system:user:resetPwd', '#');

-- Role management buttons
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(9, '角色新增', 3, 1, '', '', 'F', 'system:role:add', '#'),
(10, '角色修改', 3, 2, '', '', 'F', 'system:role:edit', '#'),
(11, '角色删除', 3, 3, '', '', 'F', 'system:role:delete', '#');

-- Menu management buttons
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(12, '菜单新增', 4, 1, '', '', 'F', 'system:menu:add', '#'),
(13, '菜单修改', 4, 2, '', '', 'F', 'system:menu:edit', '#'),
(14, '菜单删除', 4, 3, '', '', 'F', 'system:menu:delete', '#');

-- Assign ALL menus to admin role
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),(1,12),(1,13),(1,14);
