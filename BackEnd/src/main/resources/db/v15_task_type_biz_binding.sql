-- 任务类型业务回写绑定：确保 GEO 待分配类型存在并绑定字段

INSERT INTO sys_task_type (type_name, sort_order, remark, biz_type, assign_field, is_active)
SELECT 'GEO文章待分配发布人', 10, '投放管理：目标问题缺少发布人', 'geo_content_placement', 'publisher', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = 'GEO文章待分配发布人');

INSERT INTO sys_task_type (type_name, sort_order, remark, biz_type, assign_field, is_active)
SELECT 'GEO文章待分配撰写人', 11, '投放管理：目标问题缺少撰写人', 'geo_content_placement', 'writer', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = 'GEO文章待分配撰写人');

-- 只修补仍有效的类型配置，不把用户已删除的类型复活
UPDATE sys_task_type
SET biz_type = 'geo_content_placement',
    assign_field = 'publisher',
    remark = '投放管理：目标问题缺少发布人',
    sort_order = 10
WHERE type_name = 'GEO文章待分配发布人' AND is_active = 1;

UPDATE sys_task_type
SET biz_type = 'geo_content_placement',
    assign_field = 'writer',
    remark = '投放管理：目标问题缺少撰写人',
    sort_order = 11
WHERE type_name = 'GEO文章待分配撰写人' AND is_active = 1;
