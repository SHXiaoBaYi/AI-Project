package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.SysTaskTypeDTO;
import com.base.admin.domain.dto.SysTaskTypeQueryDTO;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskType;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysTaskTypeMapper;
import com.base.admin.service.SysTaskTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysTaskTypeServiceImpl implements SysTaskTypeService {

    private final SysTaskTypeMapper taskTypeMapper;
    private final SysTaskMapper taskMapper;

    @Override
    public PageResult<SysTaskType> list(SysTaskTypeQueryDTO query) {
        LambdaQueryWrapper<SysTaskType> wrapper = new LambdaQueryWrapper<SysTaskType>()
                .like(StringUtils.hasText(query.getTypeName()), SysTaskType::getTypeName, query.getTypeName())
                .orderByAsc(SysTaskType::getSortOrder)
                .orderByAsc(SysTaskType::getId);
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(wrapper, query, SysTaskType::getCreateTime);
        Page<SysTaskType> page = taskTypeMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    public List<SysTaskType> listOptions() {
        return taskTypeMapper.selectList(new LambdaQueryWrapper<SysTaskType>()
                .orderByAsc(SysTaskType::getSortOrder)
                .orderByAsc(SysTaskType::getId));
    }

    @Override
    public void create(SysTaskTypeDTO dto) {
        String name = dto.getTypeName().trim();
        assertNameUnique(name, null);
        String spawn = nz(dto.getSpawnTaskType());
        assertSpawnValid(name, spawn);
        SysTaskType row = new SysTaskType();
        row.setTypeName(name);
        row.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        row.setRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
        row.setBizType(nz(dto.getBizType()));
        row.setAssignField(nz(dto.getAssignField()));
        row.setSpawnTaskType(spawn);
        row.setRequireProof(Boolean.TRUE.equals(dto.getRequireProof()) ? 1 : 0);
        taskTypeMapper.insert(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysTaskTypeDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("类型ID不能为空");
        }
        SysTaskType row = taskTypeMapper.selectById(dto.getId());
        if (row == null) {
            throw new BusinessException("任务类型不存在");
        }
        String name = dto.getTypeName().trim();
        assertNameUnique(name, dto.getId());
        String spawn = nz(dto.getSpawnTaskType());
        assertSpawnValid(name, spawn);
        String oldName = row.getTypeName();
        row.setTypeName(name);
        row.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        row.setRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
        row.setBizType(nz(dto.getBizType()));
        row.setAssignField(nz(dto.getAssignField()));
        row.setSpawnTaskType(spawn);
        row.setRequireProof(Boolean.TRUE.equals(dto.getRequireProof()) ? 1 : 0);
        taskTypeMapper.updateById(row);
        if (!oldName.equals(name)) {
            taskMapper.update(null, new LambdaUpdateWrapper<SysTask>()
                    .eq(SysTask::getTaskType, oldName)
                    .set(SysTask::getTaskType, name));
        }
    }

    @Override
    public void delete(Long id) {
        SysTaskType row = taskTypeMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("任务类型不存在");
        }
        long used = taskMapper.selectCount(new LambdaQueryWrapper<SysTask>().eq(SysTask::getTaskType, row.getTypeName()));
        if (used > 0) {
            throw new BusinessException("该类型已被任务引用，无法删除");
        }
        taskTypeMapper.deleteById(id);
    }

    @Override
    public String resolveTypeName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return defaultTypeName();
        }
        String name = raw.trim();
        if (getByTypeName(name) == null) {
            throw new BusinessException("不支持的任务类型: " + name);
        }
        return name;
    }

    @Override
    public SysTaskType getByTypeName(String typeName) {
        if (!StringUtils.hasText(typeName)) {
            return null;
        }
        return taskTypeMapper.selectOne(new LambdaQueryWrapper<SysTaskType>()
                .eq(SysTaskType::getTypeName, typeName.trim())
                .last("LIMIT 1"));
    }

    private String defaultTypeName() {
        List<SysTaskType> list = listOptions();
        if (list.isEmpty()) {
            return "日常";
        }
        return list.stream()
                .filter(t -> "日常".equals(t.getTypeName()))
                .map(SysTaskType::getTypeName)
                .findFirst()
                .orElse(list.getFirst().getTypeName());
    }

    private void assertNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<SysTaskType> wrapper = new LambdaQueryWrapper<SysTaskType>()
                .eq(SysTaskType::getTypeName, name)
                .ne(excludeId != null, SysTaskType::getId, excludeId);
        if (taskTypeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("任务类型已存在: " + name);
        }
    }

    private void assertSpawnValid(String typeName, String spawnTaskType) {
        if (!StringUtils.hasText(spawnTaskType)) {
            return;
        }
        if (spawnTaskType.equals(typeName)) {
            throw new BusinessException("派发下一任务不能选择自身类型");
        }
        if (getByTypeName(spawnTaskType) == null) {
            throw new BusinessException("派发下一任务类型不存在: " + spawnTaskType);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
