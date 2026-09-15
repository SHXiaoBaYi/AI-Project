-- GEO日监测：长短词 + 负责人用户ID
-- 若启动迁移未生效，可手动执行；列已存在时会报错可忽略

ALTER TABLE geo_monitor_daily
  ADD COLUMN term_type VARCHAR(16) NOT NULL DEFAULT '日巡查' COMMENT '长短词：日巡查/周巡查' AFTER inspect_date;

ALTER TABLE geo_monitor_daily
  ADD COLUMN owner_user_id BIGINT NULL COMMENT '负责人用户ID（关联 sys_user）' AFTER keyword;
