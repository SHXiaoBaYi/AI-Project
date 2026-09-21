package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.dto.SysTaskTypeDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentPlacementItem;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskAssignee;
import com.base.admin.domain.entity.SysTaskFile;
import com.base.admin.domain.entity.SysTaskType;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.mapper.GeoContentPlacementItemMapper;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.SysTaskAssigneeMapper;
import com.base.admin.mapper.SysTaskFileMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.PlacementTaskSyncService;
import com.base.admin.service.SysTaskService;
import com.base.admin.service.SysTaskTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementTaskSyncServiceImpl implements PlacementTaskSyncService {

    private final GeoContentPlacementMapper placementMapper;
    private final GeoContentPlacementItemMapper itemMapper;
    private final SysTaskMapper taskMapper;
    private final SysTaskAssigneeMapper assigneeMapper;
    private final SysTaskFileMapper taskFileMapper;
    private final SysUserMapper userMapper;
    private final SysTaskService taskService;
    private final SysTaskTypeService taskTypeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String syncUnassignedPlacementTasks() {
        ensureTaskTypes();
        Long fallbackUserId = resolveFallbackUserId();
        if (fallbackUserId == null) {
            return "跳过：未找到可用系统用户作为任务负责人";
        }

        List<GeoContentPlacement> placements = placementMapper.selectList(new LambdaQueryWrapper<GeoContentPlacement>()
                .orderByAsc(GeoContentPlacement::getId));

        int publisherCreated = 0;
        int writerCreated = 0;
        for (GeoContentPlacement p : placements) {
            int[] created = ensureTasksInternal(p, fallbackUserId);
            publisherCreated += created[0];
            writerCreated += created[1];
        }
        String summary = "待分配发布人任务 +" + publisherCreated + "，待分配撰写人任务 +" + writerCreated
                + "（投放共 " + placements.size() + " 条）";
        log.info("投放待分配任务刷数完成: {}", summary);
        return summary;
    }

    @Override
    public int ensureTasksForPlacement(Long placementId) {
        if (placementId == null) {
            return 0;
        }
        GeoContentPlacement placement = placementMapper.selectById(placementId);
        return ensureTasksForPlacement(placement);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeArticlePublishTasks(Long placementId) {
        if (placementId == null || !hasPublishRecord(placementId)) {
            return;
        }
        finishOpenPublishTasks(placementId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeArticlePublishTasks() {
        List<SysTask> open = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getTaskType, "文章发布")
                .in(SysTask::getStatus, List.of("未开始", "进行中"))
                .isNotNull(SysTask::getBizId));
        for (Long placementId : open.stream().map(SysTask::getBizId).filter(Objects::nonNull).distinct().toList()) {
            if (hasPublishRecord(placementId)) {
                finishOpenPublishTasks(placementId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTasksForPlacement(Long placementId) {
        if (placementId == null) {
            return;
        }
        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getBizId, placementId));
        for (SysTask task : tasks) {
            if (task.getId() == null) {
                continue;
            }
            assigneeMapper.physicalDeleteByTaskId(task.getId());
            taskFileMapper.delete(new LambdaQueryWrapper<SysTaskFile>()
                    .eq(SysTaskFile::getTaskId, task.getId()));
            taskMapper.deleteById(task.getId());
        }
    }

    private boolean hasPublishRecord(Long placementId) {
        List<GeoContentPlacementItem> items = itemMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, placementId)
                .select(GeoContentPlacementItem::getId, GeoContentPlacementItem::getPublishStatus, GeoContentPlacementItem::getPublishUrl));
        for (GeoContentPlacementItem item : items) {
            if (Constants.CONTENT_PUBLISH_SUCCESS.equals(item.getPublishStatus())) {
                return true;
            }
            String url = item.getPublishUrl() == null ? "" : item.getPublishUrl().trim();
            if (url.matches("(?i)https?://\\S+")) {
                return true;
            }
        }
        return false;
    }

    private void finishOpenPublishTasks(Long placementId) {
        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getBizId, placementId)
                .eq(SysTask::getTaskType, "文章发布")
                .in(SysTask::getStatus, List.of("未开始", "进行中")));
        LocalDateTime now = LocalDateTime.now();
        for (SysTask task : tasks) {
            task.setStatus("已完成");
            task.setProgress(100);
            task.setActualEndTime(now);
            String note = "投放已有发布记录，系统自动完成";
            task.setRemark(StringUtils.hasText(task.getRemark()) ? task.getRemark().trim() + "；" + note : note);
            taskMapper.updateById(task);
            if (task.getId() != null) {
                assigneeMapper.update(null, new LambdaUpdateWrapper<SysTaskAssignee>()
                        .eq(SysTaskAssignee::getTaskId, task.getId())
                        .set(SysTaskAssignee::getDone, 1));
            }
        }
    }

    @Override
    public int ensureTasksForPlacement(GeoContentPlacement placement) {
        if (placement == null || placement.getId() == null) {
            return 0;
        }
        try {
            ensureTaskTypes();
            Long fallbackUserId = resolveFallbackUserId();
            if (fallbackUserId == null) {
                log.warn("自动生成待分配任务跳过：无可用系统用户 placementId={}", placement.getId());
                return 0;
            }
            int[] created = ensureTasksInternal(placement, fallbackUserId);
            return created[0] + created[1];
        } catch (Exception e) {
            log.warn("自动生成待分配任务失败 placementId={}: {}", placement.getId(), e.getMessage());
            return 0;
        }
    }

    /** @return [publisherCreated, writerCreated] — 含待分配任务与执行任务 */
    private int[] ensureTasksInternal(GeoContentPlacement p, Long fallbackUserId) {
        int publisherCreated = 0;
        int writerCreated = 0;
        // 同一话题下待分配任务统一负责人，避免交叉用发布人/撰写人导致负责人不一致
        Long assignOwnerId = fallbackUserId;

        if (isPublisherMissing(p)) {
            if (!taskExists(p.getId(), Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER)) {
                createAssignTask(p, Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER, "发布人", assignOwnerId);
                publisherCreated++;
            }
        } else {
            cancelOpenAssignTasks(p.getId(), Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER);
            if (p.getPublisherUserId() != null && ensureExecTask(p, "文章发布", p.getPublisherUserId())) {
                publisherCreated++;
            }
        }

        if (isWriterMissing(p)) {
            if (!taskExists(p.getId(), Constants.TASK_TYPE_GEO_ASSIGN_WRITER)) {
                createAssignTask(p, Constants.TASK_TYPE_GEO_ASSIGN_WRITER, "撰写人", assignOwnerId);
                writerCreated++;
            }
        } else {
            cancelOpenAssignTasks(p.getId(), Constants.TASK_TYPE_GEO_ASSIGN_WRITER);
            if (p.getOwnerUserId() != null && ensureExecTask(p, "文章撰写", p.getOwnerUserId())) {
                writerCreated++;
            }
        }
        return new int[]{publisherCreated, writerCreated};
    }

    private void ensureTaskTypes() {
        ensureType(Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER, 10, "投放管理：目标问题缺少发布人",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, Constants.TASK_ASSIGN_FIELD_PUBLISHER, "文章发布", false);
        ensureType(Constants.TASK_TYPE_GEO_ASSIGN_WRITER, 11, "投放管理：目标问题缺少撰写人",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, Constants.TASK_ASSIGN_FIELD_WRITER, "文章撰写", false);
        ensureType("文章撰写", 12, "撰写人执行任务",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, "", "", true);
        ensureType("文章发布", 13, "发布人执行任务，有发布记录后自动完成",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, "", "", false);
    }

    private void ensureType(String name, int sort, String remark, String bizType, String assignField,
                            String spawnTaskType, boolean requireProof) {
        SysTaskType existing = taskTypeService.getByTypeName(name);
        if (existing == null) {
            SysTaskTypeDTO dto = new SysTaskTypeDTO();
            dto.setTypeName(name);
            dto.setSortOrder(sort);
            dto.setRemark(remark);
            dto.setBizType(bizType);
            dto.setAssignField(assignField);
            dto.setSpawnTaskType(spawnTaskType);
            dto.setRequireProof(requireProof);
            taskTypeService.create(dto);
            return;
        }
        int wantProof = requireProof ? 1 : 0;
        int gotProof = existing.getRequireProof() == null ? 0 : existing.getRequireProof();
        boolean needFix = !bizType.equals(nz(existing.getBizType()))
                || !assignField.equals(nz(existing.getAssignField()))
                || !spawnTaskType.equals(nz(existing.getSpawnTaskType()))
                || wantProof != gotProof;
        if (needFix) {
            SysTaskTypeDTO dto = new SysTaskTypeDTO();
            dto.setId(existing.getId());
            dto.setTypeName(existing.getTypeName());
            dto.setSortOrder(existing.getSortOrder() != null ? existing.getSortOrder() : sort);
            dto.setRemark(StringUtils.hasText(existing.getRemark()) ? existing.getRemark() : remark);
            dto.setBizType(bizType);
            dto.setAssignField(assignField);
            dto.setSpawnTaskType(spawnTaskType);
            dto.setRequireProof(requireProof);
            taskTypeService.update(dto);
        }
    }

    /** 表格已填写发布人（含未对上系统用户的姓名）则不再生成待分配任务 */
    private boolean isPublisherMissing(GeoContentPlacement p) {
        String name = p.getPublisherName() == null ? "" : p.getPublisherName().trim();
        return !StringUtils.hasText(name) || Constants.CONTENT_UNASSIGNED.equals(name);
    }

    private boolean isWriterMissing(GeoContentPlacement p) {
        String name = p.getOwnerName() == null ? "" : p.getOwnerName().trim();
        return !StringUtils.hasText(name) || Constants.CONTENT_UNASSIGNED.equals(name);
    }

    private boolean taskExists(Long placementId, String taskType) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getBizId, placementId)
                .eq(SysTask::getTaskType, taskType)
                .ne(SysTask::getStatus, "已取消"));
        return count != null && count > 0;
    }

    /** 已补齐人时，关掉仍挂着的待分配任务，避免和执行任务并存 */
    private void cancelOpenAssignTasks(Long placementId, String taskType) {
        List<SysTask> open = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getBizId, placementId)
                .eq(SysTask::getTaskType, taskType)
                .in(SysTask::getStatus, List.of("待分配", "未开始", "进行中")));
        for (SysTask task : open) {
            task.setStatus("已取消");
            taskMapper.updateById(task);
        }
    }

    /** 已指定发布人/撰写人时，补齐对应执行任务（未开始，执行人=该用户） */
    private boolean ensureExecTask(GeoContentPlacement p, String taskType, Long assigneeUserId) {
        if (assigneeUserId == null || taskExists(p.getId(), taskType)) {
            return false;
        }
        String question = StringUtils.hasText(p.getTargetQuestion()) ? p.getTargetQuestion().trim() : ("投放#" + p.getId());
        String title = "【" + taskType + "】" + abbreviate(question, 80);
        String content = "话题：" + nzDash(p.getTopicName()) + "\n目标问题：" + question
                + (StringUtils.hasText(p.getTitle()) ? "\n标题：" + p.getTitle().trim() : "");

        SysTaskType typeCfg = taskTypeService.getByTypeName(taskType);
        String bizType = typeCfg != null && StringUtils.hasText(typeCfg.getBizType())
                ? typeCfg.getBizType().trim()
                : Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT;

        SysTaskDTO dto = new SysTaskDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setTaskType(taskType);
        dto.setPriority(2);
        dto.setStatus("未开始");
        dto.setProgress(0);
        dto.setOwnerUserId(assigneeUserId);
        dto.setAssigneeUserIds(List.of(assigneeUserId));
        dto.setBizType(bizType);
        dto.setBizId(p.getId());
        dto.setBizTitle(abbreviate(question, 180));
        dto.setRemark("由投放管理指定" + ("文章发布".equals(taskType) ? "发布人" : "撰写人") + "后自动生成");
        taskService.create(dto);
        return true;
    }

    private void createAssignTask(GeoContentPlacement p, String taskType, String roleLabel, Long ownerUserId) {
        String question = StringUtils.hasText(p.getTargetQuestion()) ? p.getTargetQuestion().trim() : ("投放#" + p.getId());
        String title = "【待分配" + roleLabel + "】" + abbreviate(question, 80);
        String content = "话题：" + nzDash(p.getTopicName()) + "\n目标问题：" + question
                + (StringUtils.hasText(p.getTitle()) ? "\n标题：" + p.getTitle().trim() : "");

        SysTaskType typeCfg = taskTypeService.getByTypeName(taskType);
        String bizType = typeCfg != null && StringUtils.hasText(typeCfg.getBizType())
                ? typeCfg.getBizType().trim()
                : Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT;

        SysTaskDTO dto = new SysTaskDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setTaskType(taskType);
        dto.setPriority(2);
        dto.setStatus("待分配");
        dto.setProgress(0);
        dto.setOwnerUserId(ownerUserId);
        // 待分配任务不挂执行人，避免进入「我的任务」、也避免被误当成已分配
        dto.setAssigneeUserIds(List.of());
        dto.setBizType(bizType);
        dto.setBizId(p.getId());
        dto.setBizTitle(abbreviate(question, 180));
        dto.setRemark("由投放管理待分配字段自动生成");
        taskService.create(dto);
    }

    private Long resolveFallbackUserId() {
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "admin")
                .last("LIMIT 1"));
        if (admin != null && admin.getUserId() != null) {
            return admin.getUserId();
        }
        SysUser any = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getUserId)
                .last("LIMIT 1"));
        return any == null ? null : any.getUserId();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nzDash(String s) {
        return s == null || s.isBlank() ? "-" : s.trim();
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }
}
