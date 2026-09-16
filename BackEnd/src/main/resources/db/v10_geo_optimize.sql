-- GEO v10：日监测唯一键含 term_type；内容投放周期快照；菜单三分域

-- 1) 日监测唯一键：日期×平台×关键字×长短词
-- （由 GeoSchemaMigrator.ensureDailyUniqueKey 兼容执行）

-- 2) 内容发布/收录周期快照
CREATE TABLE IF NOT EXISTS geo_content_period_stat (
  id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  period_type         VARCHAR(16)    NOT NULL                COMMENT '周期类型 WEEK/MONTH',
  period_key          VARCHAR(96)    NOT NULL                COMMENT '周期唯一键',
  period_label        VARCHAR(64)    NOT NULL                COMMENT '展示标签',
  period_start        DATE           NOT NULL                COMMENT '周期开始',
  period_end          DATE           NOT NULL                COMMENT '周期结束',
  publisher_user_id   BIGINT         NOT NULL DEFAULT 0      COMMENT '发布人用户ID，0=未分配',
  publisher_name      VARCHAR(64)    NOT NULL DEFAULT ''     COMMENT '发布人名称快照',
  topic_id            BIGINT         NOT NULL DEFAULT 0      COMMENT '话题ID，0=未关联',
  topic_name          VARCHAR(128)   NOT NULL DEFAULT ''     COMMENT '话题名称快照',
  publish_platform    VARCHAR(64)    NOT NULL DEFAULT '*'    COMMENT '发布平台，*=全平台汇总',
  success_count       INT            NOT NULL DEFAULT 0      COMMENT '成功发布数',
  cited_count         INT            NOT NULL DEFAULT 0      COMMENT '被收录数（有引用的成功发布）',
  cite_hit_count      INT            NOT NULL DEFAULT 0      COMMENT '引用次数',
  cite_rate           DECIMAL(8,2)   NOT NULL DEFAULT 0      COMMENT '收录率%',
  locked_at           DATETIME       NOT NULL                COMMENT '落库时间',
  create_by           VARCHAR(50)    DEFAULT ''              COMMENT '创建者',
  create_time         DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           VARCHAR(50)    DEFAULT ''              COMMENT '更新者',
  update_time         DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active           TINYINT        NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_period (period_type, period_key, publisher_user_id, topic_id, publish_platform),
  KEY idx_content_period_range (period_type, period_start, period_end),
  KEY idx_content_publisher (publisher_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO内容投放周/月看板落库快照';
