-- GEO 文章多人审核（撰写人发起，挂投放主表统一稿）

CREATE TABLE IF NOT EXISTS geo_content_review (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  placement_id      BIGINT        NOT NULL                COMMENT '投放主表ID',
  round_no          INT           NOT NULL DEFAULT 1      COMMENT '第几轮审核',
  status            VARCHAR(32)   NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  submitter_user_id BIGINT        DEFAULT NULL            COMMENT '发起人用户ID',
  submitter_name    VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '发起人',
  submit_remark     VARCHAR(1000) NOT NULL DEFAULT ''     COMMENT '提审说明',
  content_version   BIGINT        NOT NULL DEFAULT 0      COMMENT '提审时正文版本',
  create_by         VARCHAR(64)   DEFAULT ''              COMMENT '创建者',
  create_time       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by         VARCHAR(64)   DEFAULT ''              COMMENT '更新者',
  update_time       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active         TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
  PRIMARY KEY (id),
  KEY idx_review_placement (placement_id),
  KEY idx_review_status (status),
  KEY idx_review_submitter (submitter_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO文章审核轮次';

CREATE TABLE IF NOT EXISTS geo_content_reviewer (
  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  review_id    BIGINT        NOT NULL                COMMENT '审核轮次ID',
  placement_id BIGINT        NOT NULL                COMMENT '投放主表ID',
  user_id      BIGINT        NOT NULL                COMMENT '审核人用户ID',
  user_name    VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '审核人',
  result       VARCHAR(32)   NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  comment_text VARCHAR(2000) NOT NULL DEFAULT ''     COMMENT '审核意见',
  acted_at     DATETIME      DEFAULT NULL            COMMENT '操作时间',
  create_by    VARCHAR(64)   DEFAULT ''              COMMENT '创建者',
  create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(64)   DEFAULT ''              COMMENT '更新者',
  update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
  PRIMARY KEY (id),
  KEY idx_reviewer_review (review_id),
  KEY idx_reviewer_user (user_id, result),
  KEY idx_reviewer_placement (placement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO文章审核人明细';

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
VALUES
(175, '我的撰写记录', 132, 4, 'geo/my-writings', '', 'C', 'geo:write:mine', 'EditOutlined', 0, 0, '撰写人查看自己的文章、发起多人审核、改稿', 1),
(176, '待审核文章', 132, 5, 'geo/pending-review', '', 'C', 'geo:write:review', 'AuditOutlined', 0, 0, '被指定为审核人的待办', 1),
(177, '审核记录', 132, 6, 'geo/review-records', '', 'C', 'geo:write:review', 'HistoryOutlined', 0, 0, '我发起或参与过的审核记录', 1)
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
  is_active = 1,
  visible = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (175, 176, 177);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
SELECT DISTINCT rm.role_id, m.menu_id, 1
FROM sys_role_menu rm
CROSS JOIN (SELECT 175 AS menu_id UNION ALL SELECT 176 UNION ALL SELECT 177) m
WHERE rm.menu_id IN (122, 128, 132) AND IFNULL(rm.is_active, 1) = 1;
