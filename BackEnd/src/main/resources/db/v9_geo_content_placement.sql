-- 内容投放管理：主表 + 平台投放子表 + AI引用子表 + 菜单

CREATE TABLE IF NOT EXISTS geo_content_placement (
  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  publisher_name   VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '发布人展示名',
  publisher_user_id BIGINT       DEFAULT NULL            COMMENT '发布人用户ID',
  owner_name       VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '归属人展示名',
  owner_user_id    BIGINT        DEFAULT NULL            COMMENT '归属人用户ID',
  topic_id         BIGINT        DEFAULT NULL            COMMENT '话题ID',
  topic_name       VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '话题名称',
  target_question  VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '目标问题',
  title            VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '标题',
  source           VARCHAR(32)   NOT NULL DEFAULT '手动新增' COMMENT '来源：导入/手动新增/AI生成',
  source_placement_id BIGINT     DEFAULT NULL            COMMENT 'AI生成参照的内容投放ID',
  placement_progress VARCHAR(32) NOT NULL DEFAULT '未投放' COMMENT '投放进度：投放完成/部分投放/未投放',
  remark           VARCHAR(1000) DEFAULT ''              COMMENT '备注',
  create_by        VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active        TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  KEY idx_publisher (publisher_name),
  KEY idx_publisher_user (publisher_user_id),
  KEY idx_owner (owner_name),
  KEY idx_owner_user (owner_user_id),
  KEY idx_topic (topic_name),
  KEY idx_topic_id (topic_id),
  KEY idx_title (title(191)),
  KEY idx_placement_progress (placement_progress)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO内容投放主表';

CREATE TABLE IF NOT EXISTS geo_content_placement_item (
  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  placement_id     BIGINT        NOT NULL                COMMENT '投放主表ID',
  title            VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '标题（归属发布详情）',
  platform_name    VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '发布平台',
  content_form     VARCHAR(16)   NOT NULL DEFAULT '图文'  COMMENT '内容形态：图文/视频',
  publish_status   VARCHAR(32)   NOT NULL DEFAULT '未投放' COMMENT '投放状态：投放成功/审核未通过/未投放',
  publish_url      VARCHAR(1000) DEFAULT ''              COMMENT '投放链接',
  publish_time     DATE          DEFAULT NULL            COMMENT '发布时间',
  sort_order       INT           NOT NULL DEFAULT 0      COMMENT '排序',
  remark           VARCHAR(500)  DEFAULT ''              COMMENT '备注',
  create_by        VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active        TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  KEY idx_placement (placement_id),
  KEY idx_platform (platform_name),
  KEY idx_status (publish_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO内容投放-发布平台明细';

CREATE TABLE IF NOT EXISTS geo_content_placement_cite (
  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  placement_id     BIGINT        NOT NULL                COMMENT '投放主表ID',
  item_id          BIGINT        DEFAULT NULL            COMMENT '关联平台投放明细ID',
  ask_question     VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '提问问题',
  ai_platform      VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT 'AI平台名称',
  cite_url         VARCHAR(1000) DEFAULT ''              COMMENT '引用链接',
  sort_order       INT           NOT NULL DEFAULT 0      COMMENT '排序',
  remark           VARCHAR(500)  DEFAULT ''              COMMENT '备注',
  create_by        VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active        TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  KEY idx_placement (placement_id),
  KEY idx_item (item_id),
  KEY idx_ai_platform (ai_platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO内容投放-AI平台引用情况';

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(122, '投放管理', 100, 8, 'geo/content-placement-manage', '', 'C', 'geo:content:list', 'SendOutlined', 0, 0, '管理视角：目标问题生成、分配发布人、投放进度', 1),
(123, '内容投放删除', 122, 1, '', '', 'F', 'geo:content:delete', '#', 0, 0, '', 1),
(124, '内容投放导入', 122, 2, '', '', 'F', 'geo:content:import', '#', 0, 0, '', 1),
(125, '内容投放新增', 122, 3, '', '', 'F', 'geo:content:add', '#', 0, 0, '', 1),
(126, '内容投放修改', 122, 4, '', '', 'F', 'geo:content:edit', '#', 0, 0, '', 1),
(127, '生成相似问题', 122, 5, '', '', 'F', 'geo:content:generate', '#', 0, 0, '', 1),
(128, '投放执行', 100, 9, 'geo/content-placement-work', '', 'C', 'geo:content:work', 'FormOutlined', 0, 0, '一线视角：维护本人话题/目标问题的平台投放与引用详情', 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), path = VALUES(path), perms = VALUES(perms), icon = VALUES(icon), remark = VALUES(remark), is_active = 1;

UPDATE sys_menu SET sort_order = 8 WHERE menu_id = 122;
UPDATE sys_menu SET sort_order = 9 WHERE menu_id = 128;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 122 AND 128
ON DUPLICATE KEY UPDATE is_active = 1;
