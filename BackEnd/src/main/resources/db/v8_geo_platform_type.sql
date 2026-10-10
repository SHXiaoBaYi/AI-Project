-- GEO平台：平台类型（AI平台 / 内容发布平台）
-- 唯一性：平台名称 + 平台类型

ALTER TABLE geo_platform
  ADD COLUMN platform_type VARCHAR(32) NOT NULL DEFAULT 'AI平台'
  COMMENT '平台类型：AI平台/内容发布平台'
  AFTER platform_name;

UPDATE geo_platform SET platform_type = 'AI平台' WHERE platform_type IS NULL OR platform_type = '';

ALTER TABLE geo_platform DROP INDEX uk_platform_name;
ALTER TABLE geo_platform ADD UNIQUE KEY uk_platform_name_type (platform_name, platform_type);

-- 仅补缺失；已逻辑删除的平台不复活
INSERT INTO geo_platform (platform_name, platform_type, sort_order, is_active)
SELECT v.platform_name, v.platform_type, v.sort_order, 1
FROM (
  SELECT '搜狐' AS platform_name, '内容发布平台' AS platform_type, 101 AS sort_order UNION ALL
  SELECT '网易', '内容发布平台', 102 UNION ALL
  SELECT '今日头条', '内容发布平台', 103 UNION ALL
  SELECT '淘江湖', '内容发布平台', 104 UNION ALL
  SELECT '腾讯新闻', '内容发布平台', 105 UNION ALL
  SELECT '携程', '内容发布平台', 106 UNION ALL
  SELECT 'bilibili', '内容发布平台', 107 UNION ALL
  SELECT '知乎', '内容发布平台', 108 UNION ALL
  SELECT 'trip', '内容发布平台', 109 UNION ALL
  SELECT '官网', '内容发布平台', 110 UNION ALL
  SELECT '公众号', '内容发布平台', 111 UNION ALL
  SELECT '小红书', '内容发布平台', 112
) v
WHERE NOT EXISTS (
  SELECT 1 FROM geo_platform p
  WHERE p.platform_name = v.platform_name AND p.platform_type = v.platform_type
);
