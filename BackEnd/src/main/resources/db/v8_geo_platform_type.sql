-- GEO平台：平台类型（AI平台 / 内容发布平台）
-- 唯一性：平台名称 + 平台类型

ALTER TABLE geo_platform
  ADD COLUMN platform_type VARCHAR(32) NOT NULL DEFAULT 'AI平台'
  COMMENT '平台类型：AI平台/内容发布平台'
  AFTER platform_name;

UPDATE geo_platform SET platform_type = 'AI平台' WHERE platform_type IS NULL OR platform_type = '';

ALTER TABLE geo_platform DROP INDEX uk_platform_name;
ALTER TABLE geo_platform ADD UNIQUE KEY uk_platform_name_type (platform_name, platform_type);

INSERT INTO geo_platform (platform_name, platform_type, sort_order, is_active)
VALUES
  ('搜狐', '内容发布平台', 101, 1),
  ('网易', '内容发布平台', 102, 1),
  ('今日头条', '内容发布平台', 103, 1),
  ('淘江湖', '内容发布平台', 104, 1),
  ('腾讯新闻', '内容发布平台', 105, 1),
  ('携程', '内容发布平台', 106, 1),
  ('bilibili', '内容发布平台', 107, 1),
  ('知乎', '内容发布平台', 108, 1),
  ('trip', '内容发布平台', 109, 1),
  ('官网', '内容发布平台', 110, 1),
  ('公众号', '内容发布平台', 111, 1),
  ('小红书', '内容发布平台', 112, 1)
ON DUPLICATE KEY UPDATE is_active = 1, platform_type = VALUES(platform_type);
