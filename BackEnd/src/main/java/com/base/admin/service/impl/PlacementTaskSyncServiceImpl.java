package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.dto.SysTaskTypeDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentPlacementItem;
import com.base.admin.domain.entity.GeoContentReview;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskAssignee;
import com.base.admin.domain.entity.SysTaskFile;
import com.base.admin.domain.entity.SysTaskType;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.mapper.GeoContentPlacementItemMapper;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.GeoContentReviewMapper;
import com.base.admin.mapper.SysTaskAssigneeMapper;
import com.base.admin.mapper.SysTaskFileMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.PlacementTaskSyncService;
import com.base.admin.service.SysTaskService;
import com.base.admin.service.SysTaskTypeService;
import com.base.admin.util.RobotSecurity;
import com.base.admin.util.SecurityUtils;
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
    private final GeoContentReviewMapper reviewMapper;
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
        int repaired = repairSystemCreatedAutoTasks();
        Long fallbackUserId = resolveFallbackUserId();
        if (fallbackUserId == null) {
            return repaired > 0
                    ? "跳过新建：未找到可用系统用户作为任务负责人；已纠正创建人 " + repaired + " 条"
                    : "跳过：未找到可用系统用户作为任务负责人";
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
        if (repaired > 0) {
            summary += "；纠正创建人 system " + repaired + " 条";
        }
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
        finishOpenTasks(placementId, Constants.TASK_TYPE_GEO_PUBLISH, "投放已有发布记录，系统自动完成");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeArticlePublishTasks() {
        List<SysTask> open = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getTaskType, Constants.TASK_TYPE_GEO_PUBLISH)
                .in(SysTask::getStatus, List.of("未开始", "进行中"))
                .isNotNull(SysTask::getBizId));
        for (Long placementId : open.stream().map(SysTask::getBizId).filter(Objects::nonNull).distinct().toList()) {
            if (hasPublishRecord(placementId)) {
                finishOpenTasks(placementId, Constants.TASK_TYPE_GEO_PUBLISH, "投放已有发布记录，系统自动完成");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeVideoTasks(Long placementId) {
        if (placementId == null) {
            return;
        }
        List<GeoContentPlacementItem> items = itemMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, placementId)
                .select(GeoContentPlacementItem::getId,
                        GeoContentPlacementItem::getContentForm,
                        GeoContentPlacementItem::getPublishStatus,
                        GeoContentPlacementItem::getPublishUrl));
        if (hasVideoEditDone(items)) {
            finishOpenTasks(placementId, Constants.TASK_TYPE_GEO_VIDEO_EDIT,
                    "文章列表已新增待投放视频稿，系统自动完成");
        }
        if (hasVideoPublishDone(items)) {
            finishOpenTasks(placementId, Constants.TASK_TYPE_GEO_VIDEO_PUBLISH,
                    "视频稿已投放成功且有投放链接，系统自动完成");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeVideoTasks() {
        List<SysTask> open = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .in(SysTask::getTaskType, List.of(
                        Constants.TASK_TYPE_GEO_VIDEO_EDIT,
                        Constants.TASK_TYPE_GEO_VIDEO_PUBLISH))
                .in(SysTask::getStatus, List.of("未开始", "进行中"))
                .isNotNull(SysTask::getBizId));
        for (Long placementId : open.stream().map(SysTask::getBizId).filter(Objects::nonNull).distinct().toList()) {
            completeVideoTasks(placementId);
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

    /** 剪辑完成：已有内容形态=视频，且状态不是「未投放」（至少到待投放） */
    private static boolean hasVideoEditDone(List<GeoContentPlacementItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (GeoContentPlacementItem item : items) {
            if (!isVideoForm(item)) {
                continue;
            }
            String status = item.getPublishStatus() == null ? "" : item.getPublishStatus().trim();
            if (!Constants.CONTENT_PUBLISH_NONE.equals(status) && StringUtils.hasText(status)) {
                return true;
            }
        }
        return false;
    }

    /** 视频发布完成：视频形态 + 投放成功 + 有投放链接 */
    private static boolean hasVideoPublishDone(List<GeoContentPlacementItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (GeoContentPlacementItem item : items) {
            if (!isVideoForm(item)) {
                continue;
            }
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(item.getPublishStatus())) {
                continue;
            }
            String url = item.getPublishUrl() == null ? "" : item.getPublishUrl().trim();
            if (url.matches("(?i)https?://\\S+")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVideoForm(GeoContentPlacementItem item) {
        String form = item.getContentForm() == null ? "" : item.getContentForm().trim();
        return Constants.CONTENT_FORM_VIDEO.equals(form);
    }

    private void finishOpenTasks(Long placementId, String taskType, String note) {
        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getBizId, placementId)
                .eq(SysTask::getTaskType, taskType)
                .in(SysTask::getStatus, List.of("未开始", "进行中")));
        LocalDateTime now = LocalDateTime.now();
        for (SysTask task : tasks) {
            task.setStatus("已完成");
            task.setProgress(100);
            task.setActualEndTime(now);
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

        boolean video = isHasVideoTask(p);

        if (isPublisherMissing(p)) {
            if (!taskExists(p.getId(), Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER)) {
                createAssignTask(p, Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER, "发布人", assignOwnerId);
                publisherCreated++;
            }
        } else {
            cancelOpenAssignTasks(p.getId(), Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER);
            if (!video && p.getPublisherUserId() != null
                    && ensureExecTask(p, Constants.TASK_TYPE_GEO_PUBLISH, p.getPublisherUserId())) {
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
            if (!video && p.getOwnerUserId() != null
                    && ensureExecTask(p, Constants.TASK_TYPE_GEO_WRITE, p.getOwnerUserId())) {
                writerCreated++;
            }
        }

        // 有视频任务：只保留剪辑/视频发布，取消未完成的文章撰写、文章发布。
        // 关掉视频：取消未完成的视频任务，并按撰写人/发布人补回文章任务。
        if (video) {
            cancelOpenAssignTasks(p.getId(), Constants.TASK_TYPE_GEO_WRITE);
            cancelOpenAssignTasks(p.getId(), Constants.TASK_TYPE_GEO_PUBLISH);
            if (p.getOwnerUserId() != null
                    && ensureExecTask(p, Constants.TASK_TYPE_GEO_VIDEO_EDIT, p.getOwnerUserId())) {
                writerCreated++;
            }
            if (p.getPublisherUserId() != null
                    && ensureExecTask(p, Constants.TASK_TYPE_GEO_VIDEO_PUBLISH, p.getPublisherUserId())) {
                publisherCreated++;
            }
            // 已具备完成条件时立即关掉（例如勾选前已有视频稿）
            completeVideoTasks(p.getId());
        } else {
            cancelOpenAssignTasks(p.getId(), Constants.TASK_TYPE_GEO_VIDEO_EDIT);
            cancelOpenAssignTasks(p.getId(), Constants.TASK_TYPE_GEO_VIDEO_PUBLISH);
        }
        syncPlacementRemark(p);
        syncPlacementQuestionTitles(p);
        return new int[]{publisherCreated, writerCreated};
    }

    private static boolean isHasVideoTask(GeoContentPlacement p) {
        return p.getHasVideoTask() != null && p.getHasVideoTask() == 1;
    }

    private void ensureTaskTypes() {
        ensureType(Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER, 10, "投放管理：目标问题缺少发布人",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, Constants.TASK_ASSIGN_FIELD_PUBLISHER,
                Constants.TASK_TYPE_GEO_PUBLISH, false);
        ensureType(Constants.TASK_TYPE_GEO_ASSIGN_WRITER, 11, "投放管理：目标问题缺少撰写人",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, Constants.TASK_ASSIGN_FIELD_WRITER,
                Constants.TASK_TYPE_GEO_WRITE, false);
        ensureType(Constants.TASK_TYPE_GEO_WRITE, 12, "撰写人执行任务",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, "", "", true);
        ensureType(Constants.TASK_TYPE_GEO_PUBLISH, 13, "发布人执行任务，有发布记录后自动完成",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, "", "", false);
        ensureType(Constants.TASK_TYPE_GEO_VIDEO_EDIT, 14,
                "撰写人执行：在文章列表新增待投放视频稿后自动完成",
                Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT, "", "", false);
        ensureType(Constants.TASK_TYPE_GEO_VIDEO_PUBLISH, 15,
                "发布人执行：视频稿投放成功且有投放链接后自动完成",
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
        String question = questionText(p);
        String title = "【" + taskType + "】" + abbreviate(question, 80);
        String content = taskContent(p);

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
        dto.setRemark("由投放管理指定" + resolveRoleLabel(taskType) + "后自动生成");
        if (!persistTaskAsOperator(p, dto)) {
            log.warn("跳过自动生成执行任务：无可用操作人 placementId={} type={}", p.getId(), taskType);
            return false;
        }
        return true;
    }

    private static String resolveRoleLabel(String taskType) {
        if (Constants.TASK_TYPE_GEO_PUBLISH.equals(taskType)
                || Constants.TASK_TYPE_GEO_VIDEO_PUBLISH.equals(taskType)) {
            return "发布人";
        }
        if (Constants.TASK_TYPE_GEO_WRITE.equals(taskType)
                || Constants.TASK_TYPE_GEO_VIDEO_EDIT.equals(taskType)) {
            return "撰写人";
        }
        return "执行人";
    }

    private void createAssignTask(GeoContentPlacement p, String taskType, String roleLabel, Long ownerUserId) {
        String question = questionText(p);
        String title = "【待分配" + roleLabel + "】" + abbreviate(question, 80);
        String content = taskContent(p);

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
        if (!persistTaskAsOperator(p, dto)) {
            // 启动刷待分配缺口时可能没有登录用户，仍需建任务
            taskService.create(dto);
        }
    }

    /**
     * 以当前登录用户创建任务；无登录上下文时回退到投放记录的 updateBy/createBy
     *（指定撰写人/发布人的操作人就写在投放表上）。
     */
    private boolean persistTaskAsOperator(GeoContentPlacement p, SysTaskDTO dto) {
        SysUser operator = resolveOperatorUser(p);
        if (operator == null || operator.getUserId() == null) {
            return false;
        }
        String username = StringUtils.hasText(operator.getUsername())
                ? operator.getUsername() : userDisplayName(operator);
        RobotSecurity.runAs(operator.getUserId(), username, () -> {
            taskService.create(dto);
            return null;
        });
        return true;
    }

    private SysUser resolveOperatorUser(GeoContentPlacement p) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid != null) {
            SysUser current = userMapper.selectById(uid);
            if (current != null) {
                return current;
            }
        }
        String loginName = SecurityUtils.getCurrentUsername();
        SysUser byLogin = findUserByLoginName(loginName);
        if (byLogin != null) {
            return byLogin;
        }
        if (p == null) {
            return null;
        }
        SysUser byUpdate = findUserByLoginName(p.getUpdateBy());
        if (byUpdate != null) {
            return byUpdate;
        }
        return findUserByLoginName(p.getCreateBy());
    }

    private SysUser findUserByLoginName(String name) {
        if (!StringUtils.hasText(name) || "system".equals(name.trim())) {
            return null;
        }
        String key = name.trim();
        SysUser byUsername = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, key)
                .last("LIMIT 1"));
        if (byUsername != null) {
            return byUsername;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getNickname, key)
                .last("LIMIT 1"));
    }

    /** 历史：启动刷数把「指定撰写人/发布人后自动生成」的任务写成了 system */
    private int repairSystemCreatedAutoTasks() {
        List<SysTask> rows = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getCreatorName, "system")
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .in(SysTask::getTaskType, List.of("文章撰写", "文章发布"))
                .like(SysTask::getRemark, "由投放管理指定")
                .isNotNull(SysTask::getBizId));
        int fixed = 0;
        for (SysTask task : rows) {
            GeoContentPlacement placement = placementMapper.selectById(task.getBizId());
            SysUser operator = resolveOperatorUser(placement);
            if (operator == null || operator.getUserId() == null) {
                continue;
            }
            String display = userDisplayName(operator);
            String username = StringUtils.hasText(operator.getUsername())
                    ? operator.getUsername() : display;
            RobotSecurity.runAs(operator.getUserId(), username, () -> {
                taskMapper.update(null, new LambdaUpdateWrapper<SysTask>()
                        .eq(SysTask::getId, task.getId())
                        .set(SysTask::getCreatorUserId, operator.getUserId())
                        .set(SysTask::getCreatorName, display)
                        .set(SysTask::getCreateBy, username));
                return null;
            });
            fixed++;
        }
        if (fixed > 0) {
            log.info("已纠正投放自动任务创建人 system → 实际操作人 {} 条", fixed);
        }
        return fixed;
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

    /**
     * 目标问题改了之后，把已关联任务的标题和业务标题改成新问题。
     * 投放任务说明里的「目标问题：」一行一并换掉；审核任务只改标题。
     */
    private void syncPlacementQuestionTitles(GeoContentPlacement p) {
        if (p.getId() == null) {
            return;
        }
        String question = questionText(p);
        String shortQuestion = abbreviate(question, 80);
        String bizTitle = abbreviate(question, 180);
        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getBizId, p.getId())
                .select(SysTask::getId, SysTask::getTitle, SysTask::getBizTitle,
                        SysTask::getContent, SysTask::getTaskType));
        for (SysTask task : tasks) {
            applyQuestionTitle(task, shortQuestion, bizTitle, question, true);
        }
        List<Long> reviewIds = reviewMapper.selectList(new LambdaQueryWrapper<GeoContentReview>()
                        .eq(GeoContentReview::getPlacementId, p.getId())
                        .select(GeoContentReview::getId))
                .stream()
                .map(GeoContentReview::getId)
                .filter(Objects::nonNull)
                .toList();
        if (reviewIds.isEmpty()) {
            return;
        }
        List<SysTask> reviewTasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_REVIEW)
                .in(SysTask::getBizId, reviewIds)
                .select(SysTask::getId, SysTask::getTitle, SysTask::getBizTitle, SysTask::getTaskType));
        for (SysTask task : reviewTasks) {
            applyQuestionTitle(task, shortQuestion, bizTitle, question, false);
        }
    }

    private void applyQuestionTitle(SysTask task, String shortQuestion, String bizTitle,
                                    String question, boolean patchContent) {
        if (task.getId() == null) {
            return;
        }
        String nextTitle = retitle(task.getTitle(), task.getTaskType(), shortQuestion);
        String currentContent = task.getContent() == null ? "" : task.getContent();
        String nextContent = patchContent ? replaceTargetQuestionLine(currentContent, question) : currentContent;
        boolean titleChanged = !Objects.equals(nz(task.getTitle()), nextTitle);
        boolean bizChanged = !Objects.equals(nz(task.getBizTitle()), bizTitle);
        boolean contentChanged = patchContent && !Objects.equals(currentContent, nextContent);
        if (!titleChanged && !bizChanged && !contentChanged) {
            return;
        }
        LambdaUpdateWrapper<SysTask> update = new LambdaUpdateWrapper<SysTask>()
                .eq(SysTask::getId, task.getId());
        if (titleChanged) {
            update.set(SysTask::getTitle, nextTitle);
        }
        if (bizChanged) {
            update.set(SysTask::getBizTitle, bizTitle);
        }
        if (contentChanged) {
            update.set(SysTask::getContent, nextContent);
        }
        taskMapper.update(null, update);
    }

    /** 保留「【类型】」前缀，只替换后面的目标问题 */
    static String retitle(String current, String taskType, String shortQuestion) {
        String prefix = titlePrefix(current);
        if (prefix == null) {
            prefix = defaultTitlePrefix(taskType);
        }
        return prefix + (shortQuestion == null ? "" : shortQuestion);
    }

    static String titlePrefix(String title) {
        if (title == null) {
            return null;
        }
        String text = title.trim();
        if (!text.startsWith("【")) {
            return null;
        }
        int end = text.indexOf('】');
        if (end <= 0) {
            return null;
        }
        return text.substring(0, end + 1);
    }

    static String defaultTitlePrefix(String taskType) {
        if (Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER.equals(taskType)) {
            return "【待分配发布人】";
        }
        if (Constants.TASK_TYPE_GEO_ASSIGN_WRITER.equals(taskType)) {
            return "【待分配撰写人】";
        }
        if (Constants.TASK_TYPE_GEO_REVIEW.equals(taskType)) {
            return "【审核文章·一审】";
        }
        String type = StringUtils.hasText(taskType) ? taskType.trim() : "任务";
        return "【" + type + "】";
    }

    /** 只替换说明里的「目标问题：」那一行；没有这一行则原样返回 */
    static String replaceTargetQuestionLine(String content, String question) {
        if (content == null || content.isEmpty()) {
            return content == null ? "" : content;
        }
        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        boolean changed = false;
        String nextLine = "目标问题：" + (question == null ? "" : question);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("目标问题：")) {
                if (!lines[i].equals(nextLine)) {
                    lines[i] = nextLine;
                    changed = true;
                }
                break;
            }
        }
        if (!changed) {
            return content;
        }
        return String.join("\n", lines);
    }

    /** 把目标问题备注写进已有关联任务的说明，改备注后任务上能跟着看到 */
    private void syncPlacementRemark(GeoContentPlacement p) {
        if (p.getId() == null) {
            return;
        }
        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTask::getBizId, p.getId())
                .select(SysTask::getId, SysTask::getContent));
        String remark = p.getRemark();
        for (SysTask task : tasks) {
            String next = applyRemarkBlock(task.getContent(), remark);
            if (Objects.equals(task.getContent() == null ? "" : task.getContent(), next)) {
                continue;
            }
            taskMapper.update(null, new LambdaUpdateWrapper<SysTask>()
                    .eq(SysTask::getId, task.getId())
                    .set(SysTask::getContent, next));
        }
        syncReviewTaskRemark(p, remark);
    }

    /** 审核任务说明保留 placementId=，并把目标问题备注附在后面 */
    private void syncReviewTaskRemark(GeoContentPlacement p, String remark) {
        List<Long> reviewIds = reviewMapper.selectList(new LambdaQueryWrapper<GeoContentReview>()
                        .eq(GeoContentReview::getPlacementId, p.getId())
                        .select(GeoContentReview::getId))
                .stream()
                .map(GeoContentReview::getId)
                .filter(Objects::nonNull)
                .toList();
        if (reviewIds.isEmpty()) {
            return;
        }
        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_REVIEW)
                .in(SysTask::getBizId, reviewIds)
                .select(SysTask::getId, SysTask::getContent));
        for (SysTask task : tasks) {
            String raw = task.getContent() == null ? "" : task.getContent().replace("\r\n", "\n");
            if (p.getId() != null && !raw.contains("placementId=")) {
                raw = raw.isBlank() ? ("placementId=" + p.getId()) : ("placementId=" + p.getId() + "\n" + raw.trim());
            }
            String next = applyRemarkBlock(raw, remark);
            if (Objects.equals(task.getContent() == null ? "" : task.getContent(), next)) {
                continue;
            }
            taskMapper.update(null, new LambdaUpdateWrapper<SysTask>()
                    .eq(SysTask::getId, task.getId())
                    .set(SysTask::getContent, next));
        }
    }

    static String reviewTaskContent(Long placementId, String remark) {
        String prefix = placementId == null ? "" : ("placementId=" + placementId);
        return applyRemarkBlock(prefix, remark);
    }

    private static String questionText(GeoContentPlacement p) {
        return StringUtils.hasText(p.getTargetQuestion()) ? p.getTargetQuestion().trim() : ("投放#" + p.getId());
    }

    private static String taskContent(GeoContentPlacement p) {
        String body = "话题：" + nzDash(p.getTopicName()) + "\n目标问题：" + questionText(p)
                + (StringUtils.hasText(p.getTitle()) ? "\n标题：" + p.getTitle().trim() : "");
        return applyRemarkBlock(body, p.getRemark());
    }

    /** 说明末尾维护「备注：」整段（可多行）。备注之前的正文保留。 */
    static String applyRemarkBlock(String content, String remark) {
        String base = content == null ? "" : content.replace("\r\n", "\n");
        int idx = base.lastIndexOf("\n备注：");
        if (base.startsWith("备注：")) {
            idx = 0;
        } else if (idx >= 0) {
            idx = idx + 1;
        }
        String stripped = idx >= 0 ? base.substring(0, idx).trim() : base.trim();
        if (!StringUtils.hasText(remark)) {
            return stripped;
        }
        String block = "备注：" + remark.trim();
        return stripped.isEmpty() ? block : stripped + "\n" + block;
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
