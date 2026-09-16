-- 平台账号管理：归属平台 + 管理人/持有人/开通人关联系统用户

CREATE TABLE IF NOT EXISTS geo_platform_account (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  platform_id       BIGINT        NOT NULL                COMMENT '平台ID',
  platform_name     VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '平台名称（冗余）',
  account           VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '登录账号',
  password          VARCHAR(256)  NOT NULL DEFAULT ''     COMMENT '登录密码',
  account_nickname  VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '平台昵称/展示名',
  manager_user_id   BIGINT        DEFAULT NULL            COMMENT '管理人用户ID',
  manager_name      VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '管理人展示名',
  holder_user_id    BIGINT        DEFAULT NULL            COMMENT '当前持有人用户ID',
  holder_name       VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '当前持有人展示名',
  opener_user_id    BIGINT        DEFAULT NULL            COMMENT '开通人用户ID',
  opener_name       VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '开通人展示名',
  recharged         TINYINT       NOT NULL DEFAULT 0      COMMENT '是否充值 1=是 0=否',
  open_time         DATETIME      DEFAULT NULL            COMMENT '开通时间',
  expire_time       DATETIME      DEFAULT NULL            COMMENT '到期时间',
  login_method      VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '平台登录方式',
  verified          TINYINT       NOT NULL DEFAULT 0      COMMENT '是否在平台做了验证 1=是 0=否',
  verify_method     VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '验证方式',
  bind_phone        VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '绑定手机',
  bind_email        VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '绑定邮箱',
  account_status    VARCHAR(16)   NOT NULL DEFAULT '正常'  COMMENT '账号状态：正常/停用/过期',
  last_login_time   DATETIME      DEFAULT NULL            COMMENT '最近登录时间',
  sort_order        INT           NOT NULL DEFAULT 0      COMMENT '排序',
  remark            VARCHAR(1000) DEFAULT ''              COMMENT '备注',
  create_by         VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by         VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active         TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  KEY idx_platform (platform_id),
  KEY idx_account (account),
  KEY idx_manager (manager_user_id),
  KEY idx_holder (holder_user_id),
  KEY idx_opener (opener_user_id),
  KEY idx_expire (expire_time),
  KEY idx_status (account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO平台账号';

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(133, '平台账号管理', 130, 3, 'geo/platform-account', '', 'C', 'geo:platformAccount:list', 'IdcardOutlined', 0, 0,
 '平台侧运营账号与持有人管理', 1),
(134, '平台账号新增', 133, 1, '', '', 'F', 'geo:platformAccount:add', '#', 0, 0, '', 1),
(135, '平台账号修改', 133, 2, '', '', 'F', 'geo:platformAccount:edit', '#', 0, 0, '', 1),
(136, '平台账号删除', 133, 3, '', '', 'F', 'geo:platformAccount:delete', '#', 0, 0, '', 1)
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
SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 133 AND 136
ON DUPLICATE KEY UPDATE is_active = 1;
