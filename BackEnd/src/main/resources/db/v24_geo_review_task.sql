-- 审核文章任务类型；下线「待审核文章」菜单（改由我的任务承接）

INSERT INTO sys_task_type (type_name, sort_order, remark, biz_type, assign_field, spawn_task_type, require_proof, is_active)
SELECT '审核文章', 14, '审核人执行：在线审核撰写人提交的文章', 'geo_content_review', '', '', 0, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_task_type WHERE type_name = '审核文章');

UPDATE sys_task_type
SET biz_type = 'geo_content_review',
    assign_field = '',
    spawn_task_type = '',
    require_proof = 0,
    remark = '审核人执行：在线审核撰写人提交的文章',
    sort_order = 14
WHERE type_name = '审核文章' AND is_active = 1;

-- 待审核文章页功能并入「我的任务」审核文章类型
UPDATE sys_menu
SET status = 0,
    visible = 0,
    is_active = 0,
    remark = '已下线：请到「我的任务」处理审核文章任务'
WHERE path = 'geo/pending-review' OR menu_id = 176;
