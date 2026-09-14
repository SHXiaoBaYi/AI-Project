-- 全表增加 is_active（1=有效 0=假删除），并新增 GEO 表与菜单
-- 可重复执行前请确认列是否已存在

ALTER TABLE sys_user
  ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效 1=有效 0=已删除';
ALTER TABLE sys_role
  ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效 1=有效 0=已删除';
ALTER TABLE sys_menu
  ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效 1=有效 0=已删除';
ALTER TABLE sys_user_role
  ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效 1=有效 0=已删除';
ALTER TABLE sys_role_menu
  ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效 1=有效 0=已删除';
ALTER TABLE sys_login_log
  ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效 1=有效 0=已删除';
ALTER TABLE sys_oper_log
  ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效 1=有效 0=已删除';
ALTER TABLE sys_user_online
  ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效 1=有效 0=已删除';

CREATE TABLE IF NOT EXISTS geo_topic (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '话题ID',
  topic_name      VARCHAR(128) NOT NULL                COMMENT '话题名称',
  optimize_week   VARCHAR(64)  DEFAULT ''              COMMENT '开始优化时间，如8月第2周',
  remark          VARCHAR(500) DEFAULT ''              COMMENT '备注',
  create_by       VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_topic_name (topic_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO话题基础数据';

CREATE TABLE IF NOT EXISTS geo_monitor_daily (
  id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  inspect_date       DATE          NOT NULL                COMMENT '巡查日期',
  platform           VARCHAR(32)   NOT NULL                COMMENT '平台：豆包/DS/小红书等',
  keyword            VARCHAR(255)  NOT NULL                COMMENT '提问问题/关键字',
  topic_id           BIGINT        NOT NULL                COMMENT '话题ID',
  mentioned          TINYINT       NOT NULL DEFAULT 0      COMMENT '是否提及 1=是 0=否',
  rank_no            INT           DEFAULT NULL            COMMENT '排名，未出现为空',
  recommend_status   VARCHAR(32)   DEFAULT ''              COMMENT '未出现/出现且推荐/出现未推荐',
  screenshot_url     VARCHAR(512)  DEFAULT ''              COMMENT '截图访问路径',
  third_party_url    VARCHAR(1024) DEFAULT ''              COMMENT '第三方分享链接',
  negative_content   VARCHAR(500)  DEFAULT ''              COMMENT '负面/错误内容',
  competitors        VARCHAR(500)  DEFAULT ''              COMMENT '出现的竞品',
  create_by          VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active          TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_date_platform_keyword (inspect_date, platform, keyword),
  KEY idx_inspect_date (inspect_date),
  KEY idx_topic_platform (topic_id, platform),
  KEY idx_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO日监测数据';

CREATE TABLE IF NOT EXISTS geo_year_target (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  period_label  VARCHAR(64)   NOT NULL                COMMENT '时间段展示名，如全年、7-8月',
  period_start  DATE          DEFAULT NULL            COMMENT '区间开始，全年可空',
  period_end    DATE          DEFAULT NULL            COMMENT '区间结束，全年可空',
  topic_id      BIGINT        NOT NULL                COMMENT '话题ID',
  target_rate   DECIMAL(5,2)  NOT NULL DEFAULT 80.00  COMMENT '目标达成率百分比',
  sort_order    INT           NOT NULL DEFAULT 0      COMMENT '排序',
  remark        VARCHAR(500)  DEFAULT ''              COMMENT '备注',
  create_by     VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active     TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_period_topic (period_label, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO全年目标配置（看板实时计算，不存快照）';

-- GEO 菜单（ID 从 100 起，避免与示例菜单冲突）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(100, 'GEO监测', 0, 1, 'geo', '', 'M', '', 'RadarChartOutlined', 0, 0, 'GEO监测目录', 1),
(101, '话题管理', 100, 1, 'geo/topic', '', 'C', 'geo:topic:list', 'TagsOutlined', 0, 0, '', 1),
(102, '日监测数据', 100, 2, 'geo/daily', '', 'C', 'geo:daily:list', 'CalendarOutlined', 0, 0, '', 1),
(103, '周报看板', 100, 3, 'geo/weekly', '', 'C', 'geo:weekly:list', 'FundOutlined', 0, 0, '', 1),
(104, '全年目标看板', 100, 4, 'geo/yearly', '', 'C', 'geo:yearly:list', 'DashboardOutlined', 0, 0, '', 1),
(105, '话题新增', 101, 1, '', '', 'F', 'geo:topic:add', '#', 0, 0, '', 1),
(106, '话题修改', 101, 2, '', '', 'F', 'geo:topic:edit', '#', 0, 0, '', 1),
(107, '话题删除', 101, 3, '', '', 'F', 'geo:topic:delete', '#', 0, 0, '', 1),
(108, '日监测新增', 102, 1, '', '', 'F', 'geo:daily:add', '#', 0, 0, '', 1),
(109, '日监测修改', 102, 2, '', '', 'F', 'geo:daily:edit', '#', 0, 0, '', 1),
(110, '日监测删除', 102, 3, '', '', 'F', 'geo:daily:delete', '#', 0, 0, '', 1),
(111, '日监测导入', 102, 4, '', '', 'F', 'geo:daily:import', '#', 0, 0, '', 1),
(112, '目标配置', 104, 1, '', '', 'F', 'geo:yearly:target', '#', 0, 0, '', 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), path = VALUES(path), perms = VALUES(perms), is_active = 1;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 100 AND 112
ON DUPLICATE KEY UPDATE is_active = 1;
