-- 周/月/年看板落库 + 日监测锁定 + 月报菜单

CREATE TABLE IF NOT EXISTS geo_board_period_stat (
  id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  period_type         VARCHAR(16)    NOT NULL                COMMENT '周期类型 WEEK/MONTH/YEAR',
  period_key          VARCHAR(96)    NOT NULL                COMMENT '周期唯一键，如 2026-W36 / 2026-09 / 全年@2026-01-01~2026-12-31',
  period_label        VARCHAR(64)    NOT NULL                COMMENT '展示标签',
  period_start        DATE           NOT NULL                COMMENT '周期开始',
  period_end          DATE           NOT NULL                COMMENT '周期结束',
  topic_id            BIGINT         NOT NULL                COMMENT '话题ID',
  topic_name          VARCHAR(128)   NOT NULL DEFAULT ''     COMMENT '话题名称快照',
  platform            VARCHAR(64)    NOT NULL                COMMENT '平台',
  sample_count        INT            NOT NULL DEFAULT 0      COMMENT '样本数',
  mention_rate        DECIMAL(8,2)   NOT NULL DEFAULT 0      COMMENT '提及率%',
  first_mention_rate  DECIMAL(8,2)   NOT NULL DEFAULT 0      COMMENT '首位提及率%',
  recommend_count     INT            NOT NULL DEFAULT 0      COMMENT '推荐次数',
  competitor_top      VARCHAR(500)   DEFAULT ''              COMMENT '竞品TOP',
  cite_platform_top   VARCHAR(500)   DEFAULT ''              COMMENT '引用平台TOP',
  target_rate         DECIMAL(8,2)   DEFAULT NULL            COMMENT '目标%（年度）',
  actual_rate         DECIMAL(8,2)   DEFAULT NULL            COMMENT '实际达成%（年度）',
  achieve_rate        DECIMAL(8,2)   DEFAULT NULL            COMMENT '达成率%（年度）',
  locked_at           DATETIME       NOT NULL                COMMENT '落库时间',
  create_by           VARCHAR(50)    DEFAULT ''              COMMENT '创建者',
  create_time         DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           VARCHAR(50)    DEFAULT ''              COMMENT '更新者',
  update_time         DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active           TINYINT        NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_period_topic_platform (period_type, period_key, topic_id, platform),
  KEY idx_period_range (period_type, period_start, period_end),
  KEY idx_topic_platform (topic_id, platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO周/月/年看板落库快照（支撑同比环比）';

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(118, '月报看板', 100, 6, 'geo/monthly', '', 'C', 'geo:monthly:list', 'BarChartOutlined', 0, 0, '', 1),
(119, '周报落库', 103, 1, '', '', 'F', 'geo:weekly:persist', '#', 0, 0, '', 1),
(120, '月报落库', 118, 1, '', '', 'F', 'geo:monthly:persist', '#', 0, 0, '', 1),
(121, '年报落库', 104, 2, '', '', 'F', 'geo:yearly:persist', '#', 0, 0, '', 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), path = VALUES(path), perms = VALUES(perms), icon = VALUES(icon), is_active = 1;

UPDATE sys_menu SET sort_order = 1 WHERE menu_id = 101;
UPDATE sys_menu SET sort_order = 2 WHERE menu_id = 113;
UPDATE sys_menu SET sort_order = 3 WHERE menu_id = 102;
UPDATE sys_menu SET sort_order = 4 WHERE menu_id = 117;
UPDATE sys_menu SET sort_order = 5 WHERE menu_id = 103;
UPDATE sys_menu SET sort_order = 6 WHERE menu_id = 118;
UPDATE sys_menu SET sort_order = 7 WHERE menu_id = 104;

INSERT INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id BETWEEN 118 AND 121
ON DUPLICATE KEY UPDATE is_active = 1;
