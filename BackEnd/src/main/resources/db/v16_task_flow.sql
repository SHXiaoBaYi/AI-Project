-- 任务流：分配完成后派生子任务类型；状态 待处理→未开始

UPDATE sys_task SET status = '未开始' WHERE status = '待处理' AND is_active = 1;

-- 执行类任务类型
INSERT INTO sys_task_type (type_name, sort_order, remark, biz_type, assign_field, spawn_task_type, is_active)
SELECT '文章撰写', 12, '撰写人执行任务', 'geo_content_placement', '', '', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = '文章撰写');

UPDATE sys_task_type
SET biz_type = 'geo_content_placement', assign_field = '', spawn_task_type = '', remark = '撰写人执行任务', sort_order = 12, is_active = 1
WHERE type_name = '文章撰写';

UPDATE sys_task_type
SET biz_type = 'geo_content_placement', assign_field = '', spawn_task_type = '', remark = '发布人执行任务', sort_order = 13, is_active = 1
WHERE type_name = '文章发布';

INSERT INTO sys_task_type (type_name, sort_order, remark, biz_type, assign_field, spawn_task_type, is_active)
SELECT '文章发布', 13, '发布人执行任务', 'geo_content_placement', '', '', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = '文章发布');

-- 分配类 → 派生子任务
UPDATE sys_task_type
SET biz_type = 'geo_content_placement',
    assign_field = 'publisher',
    spawn_task_type = '文章发布',
    remark = '投放管理：目标问题缺少发布人；分配后生成「文章发布」任务',
    sort_order = 10,
    is_active = 1
WHERE type_name = 'GEO文章待分配发布人';

UPDATE sys_task_type
SET biz_type = 'geo_content_placement',
    assign_field = 'writer',
    spawn_task_type = '文章撰写',
    remark = '投放管理：目标问题缺少撰写人；分配后生成「文章撰写」任务',
    sort_order = 11,
    is_active = 1
WHERE type_name = 'GEO文章待分配撰写人';
