-- 任务完成证明附件；类型可配置完成是否必须上传

CREATE TABLE IF NOT EXISTS sys_task_file (
  id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id            BIGINT        NOT NULL                COMMENT '任务ID',
  biz_type           VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '关联业务类型',
  biz_id             BIGINT        DEFAULT NULL            COMMENT '关联业务ID',
  file_name          VARCHAR(255)  NOT NULL DEFAULT ''     COMMENT '原始文件名',
  file_url           VARCHAR(500)  NOT NULL                COMMENT '访问路径',
  file_size          BIGINT        NOT NULL DEFAULT 0      COMMENT '字节大小',
  content_type       VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT 'MIME',
  upload_user_id     BIGINT        DEFAULT NULL            COMMENT '上传人ID',
  upload_user_name   VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '上传人',
  remark             VARCHAR(255)  NOT NULL DEFAULT ''     COMMENT '备注',
  create_by          VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active          TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
  PRIMARY KEY (id),
  KEY idx_task (task_id),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务完成证明/附件';

UPDATE sys_task_type SET require_proof = 1
WHERE type_name IN ('文章撰写', '文章发布');
