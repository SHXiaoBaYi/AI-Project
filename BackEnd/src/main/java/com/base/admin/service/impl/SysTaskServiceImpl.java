package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.Constants;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.SysTaskAssignDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.dto.SysTaskQueryDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskAssignee;
import com.base.admin.domain.entity.SysTaskType;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.vo.SysTaskAssigneeVO;
import com.base.admin.domain.vo.SysTaskVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.SysTaskAssigneeMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.SysTaskService;
import com.base.admin.service.SysTaskTypeService;
import com.base.admin.service.taskbiz.TaskBizFieldWriteDispatcher;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysTaskServiceImpl implements SysTaskService {

    private static final Set<String> STATUSES = Set.of("待分配", "未开始", "进行中", "已完成", "已取消");
    /** 我的任务可见状态 */
    private static final Set<String> MINE_STATUSES = Set.of("未开始", "进行中", "已完成", "已取消");

    private final SysTaskMapper taskMapper;
    private final SysTaskAssigneeMapper assigneeMapper;
    private final SysUserMapper userMapper;
    private final GeoContentPlacementMapper placementMapper;
    private final SysTaskTypeService taskTypeService;
    private final TaskBizFieldWriteDispatcher bizFieldWriteDispatcher;

    @Override
    public PageResult<SysTaskVO> list(SysTaskQueryDTO query) {
        Long mineUserId = null;
        if (Boolean.TRUE.equals(query.getMineOnly())) {
            mineUserId = SecurityUtils.getCurrentUserId();
            if (mineUserId == null) {
                return new PageResult<>(0, List.of());
            }
        }

        Set<Long> taskIdsByAssignee = null;
        if (query.getAssigneeUserId() != null || mineUserId != null) {
            Long filterUser = query.getAssigneeUserId() != null ? query.getAssigneeUserId() : mineUserId;
            LambdaQueryWrapper<SysTaskAssignee> aw = new LambdaQueryWrapper<SysTaskAssignee>()
                    .select(SysTaskAssignee::getTaskId)
                    .eq(SysTaskAssignee::getUserId, filterUser);
            taskIdsByAssignee = assigneeMapper.selectList(aw).stream()
                    .map(SysTaskAssignee::getTaskId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Long finalMineUserId = mineUserId;
        Set<Long> assigneeTaskIds = taskIdsByAssignee;
        LambdaQueryWrapper<SysTask> wrapper = new LambdaQueryWrapper<SysTask>()
                .like(StringUtils.hasText(query.getTitle()), SysTask::getTitle, query.getTitle())
                .eq(StringUtils.hasText(query.getTaskType()), SysTask::getTaskType, query.getTaskType())
                .eq(query.getPriority() != null, SysTask::getPriority, query.getPriority())
                .eq(query.getOwnerUserId() != null, SysTask::getOwnerUserId, query.getOwnerUserId())
                .eq(query.getCreatorUserId() != null, SysTask::getCreatorUserId, query.getCreatorUserId());

        List<String> statusFilters = query.getStatuses() == null ? List.of() : query.getStatuses().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (!statusFilters.isEmpty()) {
            wrapper.in(SysTask::getStatus, statusFilters);
        } else if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SysTask::getStatus, query.getStatus().trim());
        }

        if (finalMineUserId != null) {
            // 我的任务：仅看「分给我执行」的任务，且排除待分配类
            if (assigneeTaskIds == null || assigneeTaskIds.isEmpty()) {
                return new PageResult<>(0, List.of());
            }
            wrapper.in(SysTask::getId, assigneeTaskIds)
                    .in(SysTask::getStatus, MINE_STATUSES);
        } else if (query.getAssigneeUserId() != null) {
            if (assigneeTaskIds == null || assigneeTaskIds.isEmpty()) {
                return new PageResult<>(0, List.of());
            }
            wrapper.in(SysTask::getId, assigneeTaskIds);
        }

        if (Boolean.TRUE.equals(query.getOverdueOnly())) {
            wrapper.notIn(SysTask::getStatus, List.of("已完成", "已取消"))
                    .isNotNull(SysTask::getPlanEndTime)
                    .lt(SysTask::getPlanEndTime, LocalDateTime.now());
        }

        wrapper.orderByDesc(SysTask::getId);
        Page<SysTask> page = taskMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<SysTaskVO> rows = toVoList(page.getRecords());
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public SysTaskVO getById(Long id) {
        return toVo(require(id), loadAssignees(List.of(id)).getOrDefault(id, List.of()));
    }

    @Override
    @Transactional
    public Long create(SysTaskDTO dto) {
        boolean pendingAssign = "待分配".equals(normalizeStatus(dto.getStatus()));
        List<Long> assigneeIds = normalizeAssigneeIds(dto.getAssigneeUserIds(), pendingAssign);
        SysUser owner = requireUser(dto.getOwnerUserId(), "负责人");
        SysTask task = new SysTask();
        fillTask(task, dto, owner);
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid != null) {
            SysUser creator = userMapper.selectById(uid);
            task.setCreatorUserId(uid);
            task.setCreatorName(userDisplayName(creator));
        } else {
            task.setCreatorUserId(null);
            task.setCreatorName(SecurityUtils.getCurrentUsername());
        }
        if (!StringUtils.hasText(task.getStatus())) {
            task.setStatus("未开始");
        }
        taskMapper.insert(task);
        replaceAssignees(task.getId(), assigneeIds);
        return task.getId();
    }

    @Override
    @Transactional
    public void update(SysTaskDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("任务ID不能为空");
        }
        SysTask task = require(dto.getId());
        List<Long> assigneeIds = normalizeAssigneeIds(dto.getAssigneeUserIds());
        SysUser owner = requireUser(dto.getOwnerUserId(), "负责人");
        String oldStatus = task.getStatus();
        fillTask(task, dto, owner);
        applyStatusSideEffects(task, oldStatus);
        taskMapper.updateById(task);
        replaceAssignees(task.getId(), assigneeIds);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysTask task = require(id);
        if (!isDeletable(task)) {
            throw new BusinessException("外源生成的任务不允许删除");
        }
        assigneeMapper.physicalDeleteByTaskId(id);
        taskMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void assign(Long id, SysTaskAssignDTO dto) {
        SysTask task = require(id);
        List<Long> assigneeIds = normalizeAssigneeIds(dto.getAssigneeUserIds());
        Long ownerId = dto.getOwnerUserId() != null ? dto.getOwnerUserId() : assigneeIds.getFirst();
        SysUser owner = requireUser(ownerId, "负责人");

        String oldStatus = task.getStatus();
        task.setOwnerUserId(owner.getUserId());
        task.setOwnerName(userDisplayName(owner));
        applyBizAssignSideEffects(task, assigneeIds.getFirst());
        // 无业务回写时：普通待分配 → 未开始
        if (!"已完成".equals(task.getStatus())
                && ("待分配".equals(oldStatus) || !StringUtils.hasText(task.getStatus()))) {
            task.setStatus("未开始");
        }
        applyStatusSideEffects(task, oldStatus);
        taskMapper.updateById(task);
        replaceAssignees(task.getId(), assigneeIds);
        spawnFollowUpTaskIfNeeded(task, assigneeIds.getFirst());
        // 已派生子任务：执行人转到子任务，父任务不再出现在「我的任务」
        if ("已完成".equals(task.getStatus()) && hasSpawnConfig(task.getTaskType())) {
            assigneeMapper.physicalDeleteByTaskId(task.getId());
        }
    }

    @Override
    @Transactional
    public String rollbackMistakenSpawnBackfill() {
        SysUser thh = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "thh")
                .last("LIMIT 1"));
        if (thh == null) {
            return "跳过回滚：未找到用户 thh";
        }

        List<SysTask> autoSpawned = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .in(SysTask::getTaskType, List.of("文章撰写", "文章发布"))
                .like(SysTask::getRemark, "分配后自动生成")
                .orderByAsc(SysTask::getId));
        if (autoSpawned.size() <= 1) {
            return "无需回滚：自动派生子任务仅 " + autoSpawned.size() + " 条";
        }

        SysTask keepChild = null;
        for (SysTask t : autoSpawned) {
            if (!"文章撰写".equals(t.getTaskType())) {
                continue;
            }
            if (Objects.equals(t.getOwnerUserId(), thh.getUserId()) || isUserAssignee(t.getId(), thh.getUserId())) {
                keepChild = t;
                break;
            }
        }

        SysTask keepParent = null;
        if (keepChild != null && keepChild.getParentId() != null) {
            keepParent = taskMapper.selectById(keepChild.getParentId());
        }
        if (keepParent == null) {
            keepParent = taskMapper.selectOne(new LambdaQueryWrapper<SysTask>()
                    .eq(SysTask::getTaskType, Constants.TASK_TYPE_GEO_ASSIGN_WRITER)
                    .eq(SysTask::getStatus, "已完成")
                    .eq(SysTask::getOwnerUserId, thh.getUserId())
                    .orderByDesc(SysTask::getId)
                    .last("LIMIT 1"));
        }
        Long keepParentId = keepParent == null ? null : keepParent.getId();
        Long keepChildId = keepChild == null ? null : keepChild.getId();

        int deletedChildren = 0;
        for (SysTask c : autoSpawned) {
            if (keepChildId != null && Objects.equals(c.getId(), keepChildId)) {
                continue;
            }
            assigneeMapper.physicalDeleteByTaskId(c.getId());
            taskMapper.deleteById(c.getId());
            deletedChildren++;
        }

        int reverted = 0;
        for (String assignType : List.of(
                Constants.TASK_TYPE_GEO_ASSIGN_WRITER,
                Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER)) {
            List<SysTask> parents = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                    .eq(SysTask::getTaskType, assignType)
                    .eq(SysTask::getStatus, "已完成"));
            for (SysTask p : parents) {
                if (keepParentId != null && Objects.equals(p.getId(), keepParentId)) {
                    assigneeMapper.physicalDeleteByTaskId(p.getId());
                    continue;
                }
                clearPlacementAssignField(p);
                taskMapper.update(null, new LambdaUpdateWrapper<SysTask>()
                        .eq(SysTask::getId, p.getId())
                        .set(SysTask::getStatus, "待分配")
                        .set(SysTask::getProgress, 0)
                        .set(SysTask::getActualStartTime, null)
                        .set(SysTask::getActualEndTime, null));
                assigneeMapper.physicalDeleteByTaskId(p.getId());
                reverted++;
            }
        }

        // 待分配任务不应挂执行人（历史同步曾把负责人写成执行人）
        int clearedAssignees = 0;
        for (String assignType : List.of(
                Constants.TASK_TYPE_GEO_ASSIGN_WRITER,
                Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER)) {
            List<SysTask> pending = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                    .eq(SysTask::getTaskType, assignType)
                    .eq(SysTask::getStatus, "待分配"));
            for (SysTask p : pending) {
                assigneeMapper.physicalDeleteByTaskId(p.getId());
                clearedAssignees++;
            }
        }

        int respawned = 0;
        if (keepParentId != null) {
            SysTask parent = taskMapper.selectById(keepParentId);
            if (parent != null && "已完成".equals(parent.getStatus())
                    && !hasActiveSpawnChild(parent, "文章撰写")) {
                spawnFollowUpTaskIfNeeded(parent, thh.getUserId());
                respawned++;
            }
            if (parent != null) {
                assigneeMapper.physicalDeleteByTaskId(parent.getId());
            }
        }

        return "回滚完成：恢复待分配 " + reverted + " 条，删除多余子任务 " + deletedChildren
                + " 条，清理待分配执行人 " + clearedAssignees + " 条，补生成 " + respawned
                + " 条；保留 parentId=" + keepParentId + " childId=" + keepChildId;
    }

    private boolean isUserAssignee(Long taskId, Long userId) {
        if (taskId == null || userId == null) {
            return false;
        }
        return loadAssignees(List.of(taskId)).getOrDefault(taskId, List.of()).stream()
                .anyMatch(a -> Objects.equals(a.getUserId(), userId));
    }

    private void clearPlacementAssignField(SysTask assignTask) {
        if (assignTask.getBizId() == null) {
            return;
        }
        SysTaskType typeCfg = taskTypeService.getByTypeName(assignTask.getTaskType());
        String field = typeCfg == null ? "" : nz(typeCfg.getAssignField());
        if (Constants.TASK_ASSIGN_FIELD_WRITER.equals(field)) {
            placementMapper.update(null, new LambdaUpdateWrapper<GeoContentPlacement>()
                    .eq(GeoContentPlacement::getId, assignTask.getBizId())
                    .set(GeoContentPlacement::getOwnerUserId, null)
                    .set(GeoContentPlacement::getOwnerName, Constants.CONTENT_UNASSIGNED));
        } else if (Constants.TASK_ASSIGN_FIELD_PUBLISHER.equals(field)) {
            placementMapper.update(null, new LambdaUpdateWrapper<GeoContentPlacement>()
                    .eq(GeoContentPlacement::getId, assignTask.getBizId())
                    .set(GeoContentPlacement::getPublisherUserId, null)
                    .set(GeoContentPlacement::getPublisherName, Constants.CONTENT_UNASSIGNED));
        }
    }

    /** 按任务类型配置的 bizType + assignField 回写主业务，并将任务置为已完成 */
    private void applyBizAssignSideEffects(SysTask task, Long assignedUserId) {
        SysTaskType typeCfg = taskTypeService.getByTypeName(task.getTaskType());
        String bizType = StringUtils.hasText(task.getBizType())
                ? task.getBizType().trim()
                : (typeCfg == null ? "" : nz(typeCfg.getBizType()));
        String assignField = typeCfg == null ? "" : nz(typeCfg.getAssignField());
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(assignField) || task.getBizId() == null) {
            return;
        }
        if (!bizFieldWriteDispatcher.supports(bizType)) {
            throw new BusinessException("任务类型已绑定业务「" + bizType + "」，但未注册回写处理器");
        }
        SysUser user = requireUser(assignedUserId, "被分配人");
        bizFieldWriteDispatcher.write(bizType, task.getBizId(), assignField, user.getUserId(), userDisplayName(user));
        if (!StringUtils.hasText(task.getBizType())) {
            task.setBizType(bizType);
        }
        task.setStatus("已完成");
        task.setProgress(100);
    }

    /** 分配类任务完成后，按 spawnTaskType 给被分配人创建执行任务 */
    private void spawnFollowUpTaskIfNeeded(SysTask source, Long assignedUserId) {
        if (!"已完成".equals(source.getStatus())) {
            return;
        }
        SysTaskType typeCfg = taskTypeService.getByTypeName(source.getTaskType());
        if (typeCfg == null || !StringUtils.hasText(typeCfg.getSpawnTaskType())) {
            return;
        }
        String spawnType = typeCfg.getSpawnTaskType().trim();
        taskTypeService.resolveTypeName(spawnType);
        if (hasActiveSpawnChild(source, spawnType)) {
            return;
        }
        SysUser user = requireUser(assignedUserId, "被分配人");
        String bizTitle = StringUtils.hasText(source.getBizTitle()) ? source.getBizTitle() : source.getTitle();
        SysTaskDTO dto = new SysTaskDTO();
        dto.setTitle("【" + spawnType + "】" + abbreviate(bizTitle, 80));
        dto.setContent(source.getContent());
        dto.setTaskType(spawnType);
        dto.setPriority(source.getPriority() == null ? 2 : source.getPriority());
        dto.setStatus("未开始");
        dto.setProgress(0);
        dto.setOwnerUserId(user.getUserId());
        dto.setAssigneeUserIds(List.of(user.getUserId()));
        dto.setBizType(source.getBizType());
        dto.setBizId(source.getBizId());
        dto.setBizTitle(source.getBizTitle());
        dto.setRemark("由任务「" + source.getTaskType() + "」#" + source.getId() + " 分配后自动生成");
        Long newId = create(dto);
        SysTask spawned = taskMapper.selectById(newId);
        if (spawned != null) {
            spawned.setParentId(source.getId());
            taskMapper.updateById(spawned);
        }
    }

    private boolean hasSpawnConfig(String taskType) {
        SysTaskType typeCfg = taskTypeService.getByTypeName(taskType);
        return typeCfg != null && StringUtils.hasText(typeCfg.getSpawnTaskType());
    }

    private boolean hasActiveSpawnChild(SysTask source, String spawnType) {
        if (source.getBizId() != null && StringUtils.hasText(source.getBizType())) {
            Long exists = taskMapper.selectCount(new LambdaQueryWrapper<SysTask>()
                    .eq(SysTask::getBizType, source.getBizType())
                    .eq(SysTask::getBizId, source.getBizId())
                    .eq(SysTask::getTaskType, spawnType)
                    .ne(SysTask::getStatus, "已取消"));
            return exists != null && exists > 0;
        }
        Long exists = taskMapper.selectCount(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getParentId, source.getId())
                .eq(SysTask::getTaskType, spawnType)
                .ne(SysTask::getStatus, "已取消"));
        return exists != null && exists > 0;
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

    private void fillTask(SysTask task, SysTaskDTO dto, SysUser owner) {
        task.setTitle(dto.getTitle().trim());
        task.setContent(dto.getContent());
        task.setTaskType(taskTypeService.resolveTypeName(dto.getTaskType()));
        int priority = dto.getPriority() == null ? 2 : dto.getPriority();
        if (priority < 1 || priority > 4) {
            throw new BusinessException("优先级取值应为 1~4");
        }
        task.setPriority(priority);
        task.setStatus(normalizeStatus(dto.getStatus()));
        int progress = dto.getProgress() == null ? 0 : dto.getProgress();
        if (progress < 0 || progress > 100) {
            throw new BusinessException("进度应在 0~100");
        }
        task.setProgress(progress);
        task.setOwnerUserId(owner.getUserId());
        task.setOwnerName(userDisplayName(owner));
        task.setPlanStartTime(dto.getPlanStartTime());
        task.setPlanEndTime(dto.getPlanEndTime());
        if (StringUtils.hasText(dto.getBizType())) {
            task.setBizType(dto.getBizType().trim());
            task.setBizId(dto.getBizId());
            task.setBizTitle(nz(dto.getBizTitle()).trim());
        } else if (task.getId() == null) {
            task.setBizType("");
            task.setBizId(null);
            task.setBizTitle("");
        }
        task.setRemark(dto.getRemark());
        if (task.getSortOrder() == null) {
            task.setSortOrder(0);
        }
    }

    private void applyStatusSideEffects(SysTask task, String oldStatus) {
        String status = task.getStatus();
        if ("进行中".equals(status) && task.getActualStartTime() == null) {
            task.setActualStartTime(LocalDateTime.now());
        }
        if ("已完成".equals(status)) {
            if (task.getActualEndTime() == null) {
                task.setActualEndTime(LocalDateTime.now());
            }
            if (task.getProgress() == null || task.getProgress() < 100) {
                task.setProgress(100);
            }
        } else if ("已完成".equals(oldStatus) && !"已完成".equals(status)) {
            task.setActualEndTime(null);
        }
    }

    private void replaceAssignees(Long taskId, List<Long> assigneeIds) {
        assigneeMapper.physicalDeleteByTaskId(taskId);
        for (Long userId : assigneeIds) {
            SysUser user = requireUser(userId, "执行人");
            SysTaskAssignee row = new SysTaskAssignee();
            row.setTaskId(taskId);
            row.setUserId(user.getUserId());
            row.setUserName(userDisplayName(user));
            row.setRoleLabel("执行");
            row.setDone(0);
            assigneeMapper.insert(row);
        }
    }

    private List<Long> normalizeAssigneeIds(List<Long> raw) {
        return normalizeAssigneeIds(raw, false);
    }

    private List<Long> normalizeAssigneeIds(List<Long> raw, boolean allowEmpty) {
        if (raw == null || raw.isEmpty()) {
            if (allowEmpty) {
                return List.of();
            }
            throw new BusinessException("至少指定一名执行人");
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null) {
                set.add(id);
            }
        }
        if (set.isEmpty()) {
            if (allowEmpty) {
                return List.of();
            }
            throw new BusinessException("至少指定一名执行人");
        }
        return new ArrayList<>(set);
    }

    private SysTask require(Long id) {
        SysTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    private SysUser requireUser(Long userId, String label) {
        if (userId == null) {
            throw new BusinessException(label + "不能为空");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(label + "用户不存在");
        }
        return user;
    }

    private Map<Long, List<SysTaskAssignee>> loadAssignees(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        return assigneeMapper.selectList(new LambdaQueryWrapper<SysTaskAssignee>()
                        .in(SysTaskAssignee::getTaskId, taskIds)
                        .orderByAsc(SysTaskAssignee::getId))
                .stream()
                .collect(Collectors.groupingBy(SysTaskAssignee::getTaskId));
    }

    private List<SysTaskVO> toVoList(List<SysTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SysTaskAssignee>> map = loadAssignees(tasks.stream().map(SysTask::getId).toList());
        return tasks.stream().map(t -> toVo(t, map.getOrDefault(t.getId(), List.of()))).toList();
    }

    private SysTaskVO toVo(SysTask task, List<SysTaskAssignee> assignees) {
        SysTaskVO vo = new SysTaskVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setContent(task.getContent());
        vo.setTaskType(task.getTaskType());
        vo.setPriority(task.getPriority());
        vo.setStatus(task.getStatus());
        vo.setProgress(task.getProgress());
        vo.setCreatorUserId(task.getCreatorUserId());
        vo.setCreatorName(task.getCreatorName());
        vo.setOwnerUserId(task.getOwnerUserId());
        vo.setOwnerName(task.getOwnerName());
        vo.setPlanStartTime(task.getPlanStartTime());
        vo.setPlanEndTime(task.getPlanEndTime());
        vo.setActualStartTime(task.getActualStartTime());
        vo.setActualEndTime(task.getActualEndTime());
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setBizTitle(task.getBizTitle());
        vo.setRemark(task.getRemark());
        vo.setCreateTime(task.getCreateTime());
        vo.setOverdue(isOverdue(task));
        vo.setDeletable(isDeletable(task));
        vo.setAssignable(isAssignable(task));

        List<SysTaskAssigneeVO> list = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (SysTaskAssignee a : assignees) {
            SysTaskAssigneeVO av = new SysTaskAssigneeVO();
            av.setUserId(a.getUserId());
            av.setUserName(a.getUserName());
            av.setRoleLabel(a.getRoleLabel());
            av.setDone(a.getDone());
            list.add(av);
            ids.add(a.getUserId());
            names.add(a.getUserName());
        }
        vo.setAssignees(list);
        vo.setAssigneeUserIds(ids);
        vo.setAssigneeNames(String.join("、", names));
        return vo;
    }

    private boolean isAssignable(SysTask task) {
        if ("已完成".equals(task.getStatus()) || "已取消".equals(task.getStatus())) {
            return false;
        }
        if ("待分配".equals(task.getStatus())) {
            return true;
        }
        SysTaskType typeCfg = taskTypeService.getByTypeName(task.getTaskType());
        return typeCfg != null && StringUtils.hasText(typeCfg.getAssignField());
    }

    private static boolean isDeletable(SysTask task) {
        return !StringUtils.hasText(task.getBizType());
    }

    private static boolean isOverdue(SysTask task) {
        if (task.getPlanEndTime() == null) {
            return false;
        }
        if ("已完成".equals(task.getStatus()) || "已取消".equals(task.getStatus())) {
            return false;
        }
        return task.getPlanEndTime().isBefore(LocalDateTime.now());
    }

    private static String normalizeStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未开始";
        }
        String s = raw.trim();
        if ("待处理".equals(s)) {
            s = "未开始";
        }
        if (!STATUSES.contains(s)) {
            throw new BusinessException("不支持的任务状态: " + s);
        }
        return s;
    }

    private static String userDisplayName(SysUser user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return user.getUsername() == null ? "" : user.getUsername().trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
