-- GEO日监测：负责人（与关键字绑定）
-- 若启动迁移未生效，可手动执行；列已存在时会报错可忽略

ALTER TABLE geo_monitor_daily
  ADD COLUMN owner_name VARCHAR(64) NULL COMMENT '负责人（与关键字绑定）' AFTER keyword;
