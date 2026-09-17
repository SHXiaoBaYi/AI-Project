package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.dto.SysTaskTypeDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskType;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.mapper.GeoContentPlacementMapper;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementTaskSyncServiceImpl implements PlacementTaskSyncService {

    private final GeoContentPlacementMapper placementMapper;
    private final SysTaskMapper taskMapper;
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

    /** @return [publisherCreated, writerCreated] */
    private int[] ensureTasksInternal(GeoContentPlacement p, Long fallbackUserId) {
        int publisherCreated = 0;
        int writerCreated = 0;
        if (isPublisherMissing(p) && !taskExists(p.getId(), Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER)) {
            createTask(p, Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER, "发布人",
                    pickOwner(p.getOwnerUserId(), fallbackUserId));
            publisherCreated++;
        }
        if (isWriterMissing(p) && !taskExists(p.getId(), Constants.TASK_TYPE_GEO_ASSIGN_WRITER)) {
            createTask(p, Constants.TASK_TYPE_GEO_ASSIGN_WRITER, "撰写人",
                    pickOwner(p.getPublisherUserId(), fallbackUserId));
            writerCreated++;
        }
        return new int[]{publisherCreated, writerCreated};
    }

    private void ensureTaskTypes() {
        ensureType(Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER, 10, "投放管理：目标问题缺少发布人",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, Constants.TASK_ASSIGN_FIELD_PUBLISHER, "文章发布");
        ensureType(Constants.TASK_TYPE_GEO_ASSIGN_WRITER, 11, "投放管理：目标问题缺少撰写人",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, Constants.TASK_ASSIGN_FIELD_WRITER, "文章撰写");
        ensureType("文章撰写", 12, "撰写人执行任务",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, "", "");
        ensureType("文章发布", 13, "发布人执行任务",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, "", "");
    }

    private void ensureType(String name, int sort, String remark, String bizType, String assignField, String spawnTaskType) {
        SysTaskType existing = taskTypeService.getByTypeName(name);
        if (existing == null) {
            SysTaskTypeDTO dto = new SysTaskTypeDTO();
            dto.setTypeName(name);
            dto.setSortOrder(sort);
            dto.setRemark(remark);
            dto.setBizType(bizType);
            dto.setAssignField(assignField);
            dto.setSpawnTaskType(spawnTaskType);
            taskTypeService.create(dto);
            return;
        }
        boolean needFix = !bizType.equals(nz(existing.getBizType()))
                || !assignField.equals(nz(existing.getAssignField()))
                || !spawnTaskType.equals(nz(existing.getSpawnTaskType()));
        if (needFix) {
            SysTaskTypeDTO dto = new SysTaskTypeDTO();
            dto.setId(existing.getId());
            dto.setTypeName(existing.getTypeName());
            dto.setSortOrder(existing.getSortOrder() != null ? existing.getSortOrder() : sort);
            dto.setRemark(StringUtils.hasText(existing.getRemark()) ? existing.getRemark() : remark);
            dto.setBizType(bizType);
            dto.setAssignField(assignField);
            dto.setSpawnTaskType(spawnTaskType);
            taskTypeService.update(dto);
        }
    }

    private boolean isPublisherMissing(GeoContentPlacement p) {
        return p.getPublisherUserId() == null
                || !StringUtils.hasText(p.getPublisherName())
                || Constants.CONTENT_UNASSIGNED.equals(p.getPublisherName().trim());
    }

    private boolean isWriterMissing(GeoContentPlacement p) {
        return p.getOwnerUserId() == null
                || !StringUtils.hasText(p.getOwnerName())
                || Constants.CONTENT_UNASSIGNED.equals(p.getOwnerName().trim());
    }

    private boolean taskExists(Long placementId, String taskType) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getBizId, placementId)
                .eq(SysTask::getTaskType, taskType));
        return count != null && count > 0;
    }

    private void createTask(GeoContentPlacement p, String taskType, String roleLabel, Long ownerUserId) {
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

    private Long pickOwner(Long preferred, Long fallback) {
        return preferred != null ? preferred : fallback;
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
