/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : localhost:3306
 Source Schema         : base_admin

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 17/06/2026 17:42:00
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_login_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log`  (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Log ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Login username',
  `ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Login IP',
  `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Login location',
  `browser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Browser',
  `os` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'OS',
  `status` tinyint NULL DEFAULT 0 COMMENT 'Status: 0=success, 1=fail',
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Message',
  `login_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Login time',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_login_time`(`login_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Login log' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_login_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Menu ID',
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Menu name',
  `parent_id` bigint NULL DEFAULT 0 COMMENT 'Parent menu ID (0=root)',
  `sort_order` int NULL DEFAULT 0 COMMENT 'Sort order',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Route path',
  `component` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Frontend component path',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Type: M=directory, C=menu, F=button',
  `perms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Permission string',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '#' COMMENT 'Menu icon',
  `visible` tinyint NULL DEFAULT 0 COMMENT 'Visible: 0=yes, 1=no',
  `status` tinyint NULL DEFAULT 0 COMMENT 'Status: 0=active, 1=disabled',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Created by',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Updated by',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Remark',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 43 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'System menus and permissions' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, '系统管理', 0, 3, '/system', '', 'M', '', 'SettingOutlined', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '系统管理-主菜单');
INSERT INTO `sys_menu` VALUES (2, '用户管理', 1, 0, 'user', 'system/user/index', 'C', 'system:user:list', 'MenuOutlined', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (3, '角色管理', 1, 2, 'role', 'system/role/index', 'C', 'system:role:list', 'MenuOutlined', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (4, '菜单管理', 1, 3, 'menu', 'system/menu/index', 'C', 'system:menu:list', 'MenuOutlined', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (5, '用户新增', 2, 0, '', '', 'F', 'system:user:add', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (6, '用户修改', 2, 1, '', '', 'F', 'system:user:edit', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (7, '用户删除', 2, 3, '', '', 'F', 'system:user:delete', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (8, '重置密码', 2, 4, '', '', 'F', 'system:user:resetPwd', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (9, '角色新增', 3, 1, '', '', 'F', 'system:role:add', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (10, '角色修改', 3, 2, '', '', 'F', 'system:role:edit', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (11, '角色删除', 3, 3, '', '', 'F', 'system:role:delete', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (12, '菜单新增', 4, 1, '', '', 'F', 'system:menu:add', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (13, '菜单修改', 4, 2, '', '', 'F', 'system:menu:edit', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (14, '菜单删除', 4, 3, '', '', 'F', 'system:menu:delete', '#', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '');
INSERT INTO `sys_menu` VALUES (15, '资产管理', 0, 9, 'product', '', 'M', '', 'DatabaseOutlined', 0, 1, 'admin', '2026-06-11 13:59:50', 'admin', '2026-06-11 13:59:50', '资产管理菜单-主菜单');
INSERT INTO `sys_menu` VALUES (18, '工作台', 0, 2, 'workbench', 'workbench', 'M', 'workbench', 'DesktopOutlined', 0, 0, 'admin', '2026-06-16 09:30:06', 'admin', '2026-06-16 09:30:06', '工作台菜单');
INSERT INTO `sys_menu` VALUES (19, '表单组件', 0, 5, '/form', '', 'M', '', 'ProfileOutlined', 0, 0, 'admin', '2026-06-16 16:19:05', 'admin', '2026-06-16 16:19:05', '');
INSERT INTO `sys_menu` VALUES (20, '基础表单', 19, 0, 'form/base', '', 'C', '', '#', 0, 0, 'admin', '2026-06-16 16:24:01', 'admin', '2026-06-16 16:24:01', '基础表单示例');
INSERT INTO `sys_menu` VALUES (21, '高级表单', 19, 1, 'form/pro', '', 'C', '', '#', 0, 0, 'admin', '2026-06-16 17:10:51', 'admin', '2026-06-16 17:10:51', '');
INSERT INTO `sys_menu` VALUES (22, 'JSON表单', 19, 2, 'form/schema', '', 'C', '', '#', 0, 0, 'admin', '2026-06-16 17:27:26', 'admin', '2026-06-16 17:27:26', '');
INSERT INTO `sys_menu` VALUES (26, '筛选表单', 19, 3, 'form/query', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 00:57:02', 'admin', '2026-06-17 00:57:02', '');
INSERT INTO `sys_menu` VALUES (27, '表格组件', 0, 4, 'table', '', 'M', '', 'TableOutlined', 0, 0, 'admin', '2026-06-17 10:48:21', 'admin', '2026-06-17 10:48:21', '各种表格组件的示例');
INSERT INTO `sys_menu` VALUES (28, '基础表格', 27, 0, 'table/base', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 10:49:27', 'admin', '2026-06-17 10:49:27', '基础表格代码示例');
INSERT INTO `sys_menu` VALUES (29, '高级表格', 27, 1, 'table/pro', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 10:49:45', 'admin', '2026-06-17 10:49:45', '高级表格代码示例');
INSERT INTO `sys_menu` VALUES (30, '可编辑表格', 27, 2, 'table/edit-table', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 10:51:03', 'admin', '2026-06-17 10:51:03', '可编辑表格示例代码');
INSERT INTO `sys_menu` VALUES (31, '拖动排序表格', 27, 3, 'table/drag-sort', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 10:53:46', 'admin', '2026-06-17 10:53:46', '拖动排序表格代码示例');
INSERT INTO `sys_menu` VALUES (32, '列表组件', 0, 6, 'list', '', 'M', '', 'MenuOutlined', 0, 1, 'admin', '2026-06-17 11:24:33', 'admin', '2026-06-17 11:24:33', '列表组件主菜单');
INSERT INTO `sys_menu` VALUES (33, '基础列表', 32, 0, 'list/base', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 11:25:08', 'admin', '2026-06-17 11:25:08', '基础列表代码示例');
INSERT INTO `sys_menu` VALUES (34, '高级列表', 32, 1, 'list/pro', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 11:25:27', 'admin', '2026-06-17 11:25:27', '高级列表代码示例');
INSERT INTO `sys_menu` VALUES (35, '描述组件', 0, 7, 'description', '', 'M', '', 'ContainerOutlined', 0, 0, 'admin', '2026-06-17 11:40:37', 'admin', '2026-06-17 11:40:37', '描述列表菜单');
INSERT INTO `sys_menu` VALUES (36, '基础描述', 35, 0, 'description/base', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 11:43:35', 'admin', '2026-06-17 11:43:35', '基础描述菜单');
INSERT INTO `sys_menu` VALUES (37, '高级描述', 35, 1, 'description/pro', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 11:43:52', 'admin', '2026-06-17 11:43:52', '高级描述组件');
INSERT INTO `sys_menu` VALUES (42, '资产管理子菜单', 15, 0, 'product/sub-product', '', 'C', '', '#', 0, 0, 'admin', '2026-06-17 17:34:18', 'admin', '2026-06-17 17:34:18', '资产管理子菜单测试');

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log`  (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Log ID',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Module title',
  `business_type` tinyint NULL DEFAULT 0 COMMENT 'Business type: 0=other, 1=add, 2=edit, 3=delete',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Method name',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'HTTP method',
  `oper_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Operator name',
  `oper_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Request URL',
  `oper_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Operator IP',
  `oper_param` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Request params',
  `json_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Response result',
  `status` tinyint NULL DEFAULT 0 COMMENT 'Status: 0=success, 1=fail',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Error message',
  `oper_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Operation time',
  `cost_time` bigint NULL DEFAULT 0 COMMENT 'Cost time (ms)',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_oper_time`(`oper_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 192 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Operation log' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------
INSERT INTO `sys_oper_log` VALUES (1, '角色管理', 1, 'SysRoleController.create(..)', 'POST', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-10 14:44:32', 33);
INSERT INTO `sys_oper_log` VALUES (2, '角色管理', 2, 'SysRoleController.update(..)', 'PUT', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-10 14:44:54', 8);
INSERT INTO `sys_oper_log` VALUES (3, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-10 14:45:38', 8);
INSERT INTO `sys_oper_log` VALUES (4, '角色管理', 2, 'SysRoleController.update(..)', 'PUT', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-10 15:52:29', 7);
INSERT INTO `sys_oper_log` VALUES (5, '角色管理', 2, 'SysRoleController.update(..)', 'PUT', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-10 16:17:19', 12);
INSERT INTO `sys_oper_log` VALUES (6, '角色管理', 2, 'SysRoleController.update(..)', 'PUT', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-10 16:17:26', 10);
INSERT INTO `sys_oper_log` VALUES (7, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-10 16:36:09', 13);
INSERT INTO `sys_oper_log` VALUES (8, '角色管理', 2, 'SysRoleController.update(..)', 'PUT', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-10 17:26:01', 12);
INSERT INTO `sys_oper_log` VALUES (9, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 10:41:02', 50);
INSERT INTO `sys_oper_log` VALUES (10, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 10:41:27', 15);
INSERT INTO `sys_oper_log` VALUES (11, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 13:59:50', 16);
INSERT INTO `sys_oper_log` VALUES (12, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:00:15', 8);
INSERT INTO `sys_oper_log` VALUES (13, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:00:21', 9);
INSERT INTO `sys_oper_log` VALUES (14, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:00:45', 0);
INSERT INTO `sys_oper_log` VALUES (15, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:00:47', 12);
INSERT INTO `sys_oper_log` VALUES (16, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:01:04', 11);
INSERT INTO `sys_oper_log` VALUES (17, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:03:50', 11);
INSERT INTO `sys_oper_log` VALUES (18, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:03:53', 0);
INSERT INTO `sys_oper_log` VALUES (19, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:03:55', 12);
INSERT INTO `sys_oper_log` VALUES (20, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:04:00', 5);
INSERT INTO `sys_oper_log` VALUES (21, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:04:05', 9);
INSERT INTO `sys_oper_log` VALUES (22, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:04:33', 11);
INSERT INTO `sys_oper_log` VALUES (23, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:04:37', 19);
INSERT INTO `sys_oper_log` VALUES (24, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:10:21', 9);
INSERT INTO `sys_oper_log` VALUES (25, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:10:49', 13);
INSERT INTO `sys_oper_log` VALUES (26, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:11:06', 10);
INSERT INTO `sys_oper_log` VALUES (27, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:38:46', 0);
INSERT INTO `sys_oper_log` VALUES (28, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:38:48', 5);
INSERT INTO `sys_oper_log` VALUES (29, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:38:52', 3);
INSERT INTO `sys_oper_log` VALUES (30, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/16', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 14:39:10', 23);
INSERT INTO `sys_oper_log` VALUES (31, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 16:27:08', 16);
INSERT INTO `sys_oper_log` VALUES (32, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 16:27:40', 5);
INSERT INTO `sys_oper_log` VALUES (33, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 16:27:47', 11);
INSERT INTO `sys_oper_log` VALUES (34, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 16:28:45', 9);
INSERT INTO `sys_oper_log` VALUES (35, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 17:16:43', 18);
INSERT INTO `sys_oper_log` VALUES (36, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 17:20:49', 15);
INSERT INTO `sys_oper_log` VALUES (37, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-11 17:20:53', 11);
INSERT INTO `sys_oper_log` VALUES (38, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 08:36:55', 0);
INSERT INTO `sys_oper_log` VALUES (39, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 08:37:13', 20);
INSERT INTO `sys_oper_log` VALUES (40, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 08:37:16', 11);
INSERT INTO `sys_oper_log` VALUES (41, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 08:37:36', 13);
INSERT INTO `sys_oper_log` VALUES (42, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 08:37:38', 5);
INSERT INTO `sys_oper_log` VALUES (43, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 08:37:42', 7);
INSERT INTO `sys_oper_log` VALUES (44, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 08:37:55', 11);
INSERT INTO `sys_oper_log` VALUES (45, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 09:47:57', 21);
INSERT INTO `sys_oper_log` VALUES (46, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 09:48:14', 12);
INSERT INTO `sys_oper_log` VALUES (47, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-12 10:24:58', 6);
INSERT INTO `sys_oper_log` VALUES (48, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 08:58:57', 71);
INSERT INTO `sys_oper_log` VALUES (49, '用户管理', 1, 'SysUserController.create(..)', 'POST', 'admin', '/api/system/user', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 08:59:58', 130);
INSERT INTO `sys_oper_log` VALUES (50, '用户管理', 2, 'SysUserController.update(..)', 'PUT', 'admin', '/api/system/user', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 13:53:55', 39);
INSERT INTO `sys_oper_log` VALUES (51, '角色管理', 1, 'SysRoleController.create(..)', 'POST', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 14:41:03', 55);
INSERT INTO `sys_oper_log` VALUES (52, '角色管理', 2, 'SysRoleController.update(..)', 'PUT', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 14:42:28', 17);
INSERT INTO `sys_oper_log` VALUES (53, '角色管理', 2, 'SysRoleController.update(..)', 'PUT', 'admin', '/api/system/role', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 14:53:13', 11);
INSERT INTO `sys_oper_log` VALUES (54, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:02:47', 17);
INSERT INTO `sys_oper_log` VALUES (55, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:03:49', 21);
INSERT INTO `sys_oper_log` VALUES (56, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/17', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:04:28', 13);
INSERT INTO `sys_oper_log` VALUES (57, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:04:40', 12);
INSERT INTO `sys_oper_log` VALUES (58, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:07:10', 14);
INSERT INTO `sys_oper_log` VALUES (59, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:07:30', 17);
INSERT INTO `sys_oper_log` VALUES (60, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:07:43', 16);
INSERT INTO `sys_oper_log` VALUES (61, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:14:59', 8);
INSERT INTO `sys_oper_log` VALUES (62, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:16:14', 18);
INSERT INTO `sys_oper_log` VALUES (63, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:16:59', 24);
INSERT INTO `sys_oper_log` VALUES (64, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '\r\n### Error updating database.  Cause: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry \'1-1\' for key \'sys_role_menu.PRIMARY\'\r\n### The error may exist in com/base/admin/mapper/SysRoleMenuMapper.java (best guess)\r\n### The error may involve com.base.admin.mapper.SysRoleMenuMapper.insertRoleMenu-Inline\r\n### The error occurred while setting parameters\r\n### SQL: INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)\r\n### Cause: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry \'1-1\' for key \'sys_role_menu.PRIMARY\'\n; Duplicate entry \'1-1\' for key \'sys_role_menu.PRIMARY\'', '2026-06-15 15:35:37', 933);
INSERT INTO `sys_oper_log` VALUES (65, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '\r\n### Error updating database.  Cause: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry \'1-1\' for key \'sys_role_menu.PRIMARY\'\r\n### The error may exist in com/base/admin/mapper/SysRoleMenuMapper.java (best guess)\r\n### The error may involve com.base.admin.mapper.SysRoleMenuMapper.insertRoleMenu-Inline\r\n### The error occurred while setting parameters\r\n### SQL: INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)\r\n### Cause: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry \'1-1\' for key \'sys_role_menu.PRIMARY\'\n; Duplicate entry \'1-1\' for key \'sys_role_menu.PRIMARY\'', '2026-06-15 15:35:48', 16);
INSERT INTO `sys_oper_log` VALUES (66, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:40:17', 16);
INSERT INTO `sys_oper_log` VALUES (67, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:40:44', 22);
INSERT INTO `sys_oper_log` VALUES (68, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 15:41:06', 19);
INSERT INTO `sys_oper_log` VALUES (69, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 16:06:56', 9);
INSERT INTO `sys_oper_log` VALUES (70, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 16:07:04', 12);
INSERT INTO `sys_oper_log` VALUES (71, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 16:42:41', 17);
INSERT INTO `sys_oper_log` VALUES (72, '用户管理', 1, 'SysUserController.create(..)', 'POST', 'admin', '/api/system/user', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 16:43:30', 108);
INSERT INTO `sys_oper_log` VALUES (73, '用户管理-分配角色', 2, 'SysUserController.assignRoles(..)', 'PUT', 'admin', '/api/system/user/assignRoles', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 16:43:42', 0);
INSERT INTO `sys_oper_log` VALUES (74, '用户管理-分配角色', 2, 'SysUserController.assignRoles(..)', 'PUT', 'admin', '/api/system/user/assignRoles', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-15 16:43:53', 11);
INSERT INTO `sys_oper_log` VALUES (75, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 08:43:47', 17);
INSERT INTO `sys_oper_log` VALUES (76, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 08:44:10', 16);
INSERT INTO `sys_oper_log` VALUES (77, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 09:11:08', 16);
INSERT INTO `sys_oper_log` VALUES (78, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 09:11:15', 14);
INSERT INTO `sys_oper_log` VALUES (79, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 09:30:06', 28);
INSERT INTO `sys_oper_log` VALUES (80, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 09:30:52', 15);
INSERT INTO `sys_oper_log` VALUES (81, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 09:32:20', 6);
INSERT INTO `sys_oper_log` VALUES (82, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 14:25:53', 39);
INSERT INTO `sys_oper_log` VALUES (83, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 14:41:56', 16);
INSERT INTO `sys_oper_log` VALUES (84, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 14:45:02', 12);
INSERT INTO `sys_oper_log` VALUES (85, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 14:46:07', 22);
INSERT INTO `sys_oper_log` VALUES (86, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 14:47:28', 0);
INSERT INTO `sys_oper_log` VALUES (87, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 14:55:06', 10);
INSERT INTO `sys_oper_log` VALUES (88, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 14:56:57', 10);
INSERT INTO `sys_oper_log` VALUES (89, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 14:58:18', 6);
INSERT INTO `sys_oper_log` VALUES (90, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 16:19:05', 45);
INSERT INTO `sys_oper_log` VALUES (91, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 16:19:14', 14);
INSERT INTO `sys_oper_log` VALUES (92, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 16:24:01', 6);
INSERT INTO `sys_oper_log` VALUES (93, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 17:10:51', 31);
INSERT INTO `sys_oper_log` VALUES (94, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 17:27:26', 23);
INSERT INTO `sys_oper_log` VALUES (95, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:40:28', 54);
INSERT INTO `sys_oper_log` VALUES (96, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:58:21', 10);
INSERT INTO `sys_oper_log` VALUES (97, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:58:31', 11);
INSERT INTO `sys_oper_log` VALUES (98, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:58:36', 6);
INSERT INTO `sys_oper_log` VALUES (99, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:58:38', 4);
INSERT INTO `sys_oper_log` VALUES (100, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:58:46', 6);
INSERT INTO `sys_oper_log` VALUES (101, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:58:57', 5);
INSERT INTO `sys_oper_log` VALUES (102, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:58:59', 4);
INSERT INTO `sys_oper_log` VALUES (103, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:59:01', 3);
INSERT INTO `sys_oper_log` VALUES (104, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:59:03', 4);
INSERT INTO `sys_oper_log` VALUES (105, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:59:19', 6);
INSERT INTO `sys_oper_log` VALUES (106, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:59:34', 5);
INSERT INTO `sys_oper_log` VALUES (107, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-16 23:59:39', 4);
INSERT INTO `sys_oper_log` VALUES (108, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:00:38', 4);
INSERT INTO `sys_oper_log` VALUES (109, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:01:34', 4);
INSERT INTO `sys_oper_log` VALUES (110, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:01:40', 5);
INSERT INTO `sys_oper_log` VALUES (111, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:01:52', 5);
INSERT INTO `sys_oper_log` VALUES (112, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:01:58', 6);
INSERT INTO `sys_oper_log` VALUES (113, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:02:04', 6);
INSERT INTO `sys_oper_log` VALUES (114, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:02:10', 5);
INSERT INTO `sys_oper_log` VALUES (115, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:02:16', 5);
INSERT INTO `sys_oper_log` VALUES (116, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:02:21', 6);
INSERT INTO `sys_oper_log` VALUES (117, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:02:47', 21);
INSERT INTO `sys_oper_log` VALUES (118, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/23', '0:0:0:0:0:0:0:1', NULL, NULL, 1, 'Cannot delete menu with children', '2026-06-17 00:55:30', 10);
INSERT INTO `sys_oper_log` VALUES (119, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/23', '0:0:0:0:0:0:0:1', NULL, NULL, 1, 'Cannot delete menu with children', '2026-06-17 00:55:40', 2);
INSERT INTO `sys_oper_log` VALUES (120, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/25', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:55:52', 11);
INSERT INTO `sys_oper_log` VALUES (121, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/24', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:55:54', 6);
INSERT INTO `sys_oper_log` VALUES (122, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/23', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:55:59', 6);
INSERT INTO `sys_oper_log` VALUES (123, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/19', '0:0:0:0:0:0:0:1', NULL, NULL, 1, 'Cannot delete menu with children', '2026-06-17 00:56:09', 2);
INSERT INTO `sys_oper_log` VALUES (124, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:57:02', 4);
INSERT INTO `sys_oper_log` VALUES (125, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 00:57:26', 18);
INSERT INTO `sys_oper_log` VALUES (126, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 01:00:21', 5);
INSERT INTO `sys_oper_log` VALUES (127, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:23:41', 33);
INSERT INTO `sys_oper_log` VALUES (128, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:23:50', 11);
INSERT INTO `sys_oper_log` VALUES (129, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:23:55', 9);
INSERT INTO `sys_oper_log` VALUES (130, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:23:59', 11);
INSERT INTO `sys_oper_log` VALUES (131, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:24:57', 0);
INSERT INTO `sys_oper_log` VALUES (132, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:25:01', 0);
INSERT INTO `sys_oper_log` VALUES (133, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:25:05', 14);
INSERT INTO `sys_oper_log` VALUES (134, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:26:08', 8);
INSERT INTO `sys_oper_log` VALUES (135, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:48:21', 7);
INSERT INTO `sys_oper_log` VALUES (136, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:48:26', 5);
INSERT INTO `sys_oper_log` VALUES (137, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:49:27', 12);
INSERT INTO `sys_oper_log` VALUES (138, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:49:45', 8);
INSERT INTO `sys_oper_log` VALUES (139, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:51:03', 5);
INSERT INTO `sys_oper_log` VALUES (140, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:51:15', 6);
INSERT INTO `sys_oper_log` VALUES (141, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:51:25', 5);
INSERT INTO `sys_oper_log` VALUES (142, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 10:53:46', 5);
INSERT INTO `sys_oper_log` VALUES (143, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 11:24:33', 9);
INSERT INTO `sys_oper_log` VALUES (144, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 11:24:40', 0);
INSERT INTO `sys_oper_log` VALUES (145, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 11:25:08', 2);
INSERT INTO `sys_oper_log` VALUES (146, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 11:25:27', 9);
INSERT INTO `sys_oper_log` VALUES (147, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 11:40:37', 14);
INSERT INTO `sys_oper_log` VALUES (148, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 11:41:10', 5);
INSERT INTO `sys_oper_log` VALUES (149, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 11:43:35', 8);
INSERT INTO `sys_oper_log` VALUES (150, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 11:43:52', 2);
INSERT INTO `sys_oper_log` VALUES (151, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 14:50:55', 10);
INSERT INTO `sys_oper_log` VALUES (152, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 14:56:36', 54);
INSERT INTO `sys_oper_log` VALUES (153, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/38', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 14:56:49', 18);
INSERT INTO `sys_oper_log` VALUES (154, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/39', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 14:56:49', 4);
INSERT INTO `sys_oper_log` VALUES (155, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 15:04:33', 13);
INSERT INTO `sys_oper_log` VALUES (156, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 15:20:49', 18);
INSERT INTO `sys_oper_log` VALUES (157, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 15:23:54', 12);
INSERT INTO `sys_oper_log` VALUES (158, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 15:57:48', 18);
INSERT INTO `sys_oper_log` VALUES (159, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/40', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 16:15:34', 11);
INSERT INTO `sys_oper_log` VALUES (160, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/32', '0:0:0:0:0:0:0:1', NULL, NULL, 1, 'Cannot delete menu with children', '2026-06-17 16:17:14', 10);
INSERT INTO `sys_oper_log` VALUES (161, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/32', '0:0:0:0:0:0:0:1', NULL, NULL, 1, 'Cannot delete menu with children', '2026-06-17 16:17:23', 3);
INSERT INTO `sys_oper_log` VALUES (162, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 16:20:44', 13);
INSERT INTO `sys_oper_log` VALUES (163, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 16:29:17', 38);
INSERT INTO `sys_oper_log` VALUES (164, '菜单管理', 3, 'SysMenuController.delete(..)', 'DELETE', 'admin', '/api/system/menu/41', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 16:29:33', 14);
INSERT INTO `sys_oper_log` VALUES (165, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 16:35:19', 27);
INSERT INTO `sys_oper_log` VALUES (166, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:16:25', 15);
INSERT INTO `sys_oper_log` VALUES (167, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:16:55', 91);
INSERT INTO `sys_oper_log` VALUES (168, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理', '2026-06-17 17:22:34', 33);
INSERT INTO `sys_oper_log` VALUES (169, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:22:34', 42);
INSERT INTO `sys_oper_log` VALUES (170, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理', '2026-06-17 17:22:40', 9);
INSERT INTO `sys_oper_log` VALUES (171, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:22:40', 5);
INSERT INTO `sys_oper_log` VALUES (172, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：基础列表', '2026-06-17 17:27:42', 12);
INSERT INTO `sys_oper_log` VALUES (173, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理、基础列表', '2026-06-17 17:27:59', 1);
INSERT INTO `sys_oper_log` VALUES (174, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:28:06', 54);
INSERT INTO `sys_oper_log` VALUES (175, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理、基础列表', '2026-06-17 17:28:16', 8);
INSERT INTO `sys_oper_log` VALUES (176, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理、基础列表', '2026-06-17 17:28:33', 7);
INSERT INTO `sys_oper_log` VALUES (177, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理、基础列表', '2026-06-17 17:28:37', 10);
INSERT INTO `sys_oper_log` VALUES (178, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理、基础列表', '2026-06-17 17:29:55', 11);
INSERT INTO `sys_oper_log` VALUES (179, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:30:00', 53);
INSERT INTO `sys_oper_log` VALUES (180, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理、基础列表', '2026-06-17 17:30:05', 8);
INSERT INTO `sys_oper_log` VALUES (181, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:30:40', 17);
INSERT INTO `sys_oper_log` VALUES (182, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：列表组件', '2026-06-17 17:30:48', 6);
INSERT INTO `sys_oper_log` VALUES (183, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：列表组件、基础列表', '2026-06-17 17:30:54', 7);
INSERT INTO `sys_oper_log` VALUES (184, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：列表组件、基础列表', '2026-06-17 17:31:00', 5);
INSERT INTO `sys_oper_log` VALUES (185, '菜单管理', 2, 'SysMenuController.update(..)', 'PUT', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:31:16', 5);
INSERT INTO `sys_oper_log` VALUES (186, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：列表组件', '2026-06-17 17:31:36', 6);
INSERT INTO `sys_oper_log` VALUES (187, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：列表组件', '2026-06-17 17:31:40', 5);
INSERT INTO `sys_oper_log` VALUES (188, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：列表组件', '2026-06-17 17:31:44', 5);
INSERT INTO `sys_oper_log` VALUES (189, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：列表组件', '2026-06-17 17:31:49', 3);
INSERT INTO `sys_oper_log` VALUES (190, '菜单管理', 1, 'SysMenuController.create(..)', 'POST', 'admin', '/api/system/menu', '0:0:0:0:0:0:0:1', NULL, NULL, 0, NULL, '2026-06-17 17:34:18', 12);
INSERT INTO `sys_oper_log` VALUES (191, '角色管理-分配菜单', 2, 'SysRoleController.assignMenus(..)', 'PUT', 'admin', '/api/system/role/assignMenus', '0:0:0:0:0:0:0:1', NULL, NULL, 1, '以下菜单已被禁用，无法分配权限：资产管理', '2026-06-17 17:34:39', 5);

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Role ID',
  `role_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Role name',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Role key',
  `sort_order` int NULL DEFAULT 0 COMMENT 'Sort order',
  `status` tinyint NULL DEFAULT 0 COMMENT 'Status: 0=active, 1=disabled',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Created by',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Updated by',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Remark',
  PRIMARY KEY (`role_id`) USING BTREE,
  UNIQUE INDEX `uk_role_key`(`role_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'System roles' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, '超级管理员', 'admin', 0, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '超级管理员super admin');
INSERT INTO `sys_role` VALUES (2, '开发人员', 'develop', 2, 0, 'admin', '2026-06-10 14:44:32', 'admin', '2026-06-10 14:44:32', '开发人员，可以配置菜单等操作。开发人员，可以配置菜单等操作。开发人员，可以配置菜单等操作。');
INSERT INTO `sys_role` VALUES (3, '测试人员', 'test', 0, 0, 'admin', '2026-06-15 14:41:03', 'admin', '2026-06-15 14:41:03', '提供给测试人员的角色');

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `role_id` bigint NOT NULL COMMENT 'Role ID',
  `menu_id` bigint NOT NULL COMMENT 'Menu ID',
  PRIMARY KEY (`role_id`, `menu_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Role-Menu association' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES (1, 1);
INSERT INTO `sys_role_menu` VALUES (1, 2);
INSERT INTO `sys_role_menu` VALUES (1, 3);
INSERT INTO `sys_role_menu` VALUES (1, 4);
INSERT INTO `sys_role_menu` VALUES (1, 5);
INSERT INTO `sys_role_menu` VALUES (1, 6);
INSERT INTO `sys_role_menu` VALUES (1, 7);
INSERT INTO `sys_role_menu` VALUES (1, 8);
INSERT INTO `sys_role_menu` VALUES (1, 9);
INSERT INTO `sys_role_menu` VALUES (1, 10);
INSERT INTO `sys_role_menu` VALUES (1, 11);
INSERT INTO `sys_role_menu` VALUES (1, 12);
INSERT INTO `sys_role_menu` VALUES (1, 13);
INSERT INTO `sys_role_menu` VALUES (1, 14);
INSERT INTO `sys_role_menu` VALUES (1, 18);
INSERT INTO `sys_role_menu` VALUES (1, 19);
INSERT INTO `sys_role_menu` VALUES (1, 20);
INSERT INTO `sys_role_menu` VALUES (1, 21);
INSERT INTO `sys_role_menu` VALUES (1, 22);
INSERT INTO `sys_role_menu` VALUES (1, 26);
INSERT INTO `sys_role_menu` VALUES (1, 27);
INSERT INTO `sys_role_menu` VALUES (1, 28);
INSERT INTO `sys_role_menu` VALUES (1, 29);
INSERT INTO `sys_role_menu` VALUES (1, 30);
INSERT INTO `sys_role_menu` VALUES (1, 31);
INSERT INTO `sys_role_menu` VALUES (1, 32);
INSERT INTO `sys_role_menu` VALUES (1, 34);
INSERT INTO `sys_role_menu` VALUES (1, 35);
INSERT INTO `sys_role_menu` VALUES (1, 36);
INSERT INTO `sys_role_menu` VALUES (1, 37);
INSERT INTO `sys_role_menu` VALUES (1, 42);
INSERT INTO `sys_role_menu` VALUES (3, 1);
INSERT INTO `sys_role_menu` VALUES (3, 2);
INSERT INTO `sys_role_menu` VALUES (3, 5);
INSERT INTO `sys_role_menu` VALUES (3, 18);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Login username',
  `password` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'BCrypt hashed password',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Display name',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Email',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Phone number',
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Avatar URL',
  `gender` tinyint NULL DEFAULT 0 COMMENT 'Gender: 0=unknown, 1=male, 2=female',
  `status` tinyint NULL DEFAULT 0 COMMENT 'Status: 0=active, 1=disabled',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Created by',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Updated by',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'Remark',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'System users' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', '', '', '', 1, 0, '', '2026-06-09 16:23:14', '', '2026-06-09 16:23:14', '系统管理员');
INSERT INTO `sys_user` VALUES (2, 'thh', '$2a$10$vK0A0pVOwnrsBaZJNCJIcusu121eN06ulHmv4i2MR9VMS3Tnrv.qG', '桃嘿嘿', 'thh@qq.com', '15532547546', '', 1, 0, 'admin', '2026-06-15 08:59:58', 'admin', '2026-06-15 08:59:58', '');
INSERT INTO `sys_user` VALUES (3, 'tbb', '$2a$10$JSaUDTvFhL9BTdm/w8vFk.knYTEfct2eRpnDp1Mk7H71t/MK5IrAS', '桃白白', 'tao.baibai@qq.com', '13658377857', '', 1, 0, 'admin', '2026-06-15 16:43:30', 'admin', '2026-06-15 16:43:30', '');

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `role_id` bigint NOT NULL COMMENT 'Role ID',
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'User-Role association' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1);
INSERT INTO `sys_user_role` VALUES (2, 2);
INSERT INTO `sys_user_role` VALUES (3, 3);

SET FOREIGN_KEY_CHECKS = 1;
