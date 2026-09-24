package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoTopicDTO;
import com.base.admin.domain.dto.GeoTopicQueryDTO;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoMonitorDailyMapper;
import com.base.admin.mapper.GeoTopicMapper;
import com.base.admin.service.DataScopeFilter;
import com.base.admin.service.GeoTopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeoTopicServiceImpl implements GeoTopicService {

    private final GeoTopicMapper topicMapper;
    private final GeoMonitorDailyMapper dailyMapper;
    private final DataScopeFilter dataScopeFilter;

    @Override
    public PageResult<GeoTopic> list(GeoTopicQueryDTO query) {
        LambdaQueryWrapper<GeoTopic> wrapper = new LambdaQueryWrapper<GeoTopic>()
                .like(StringUtils.hasText(query.getTopicName()), GeoTopic::getTopicName, query.getTopicName())
                .orderByDesc(GeoTopic::getId);
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(wrapper, query, GeoTopic::getCreateTime);
        Page<GeoTopic> page = topicMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    public List<GeoTopic> listAll() {
        List<GeoTopic> list = topicMapper.selectList(new LambdaQueryWrapper<GeoTopic>().orderByAsc(GeoTopic::getTopicName));
        return dataScopeFilter.filterGeoTopics(list, GeoTopic::getId);
    }

    @Override
    public GeoTopic getById(Long id) {
        GeoTopic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BusinessException("话题不存在");
        }
        return topic;
    }

    @Override
    public void create(GeoTopicDTO dto) {
        long count = topicMapper.selectCount(
                new LambdaQueryWrapper<GeoTopic>().eq(GeoTopic::getTopicName, dto.getTopicName().trim()));
        if (count > 0) {
            throw new BusinessException("话题名称已存在");
        }
        GeoTopic topic = new GeoTopic();
        topic.setTopicName(dto.getTopicName().trim());
        topic.setOptimizeWeek(dto.getOptimizeWeek());
        topic.setRemark(dto.getRemark());
        topicMapper.insert(topic);
    }

    @Override
    public void update(GeoTopicDTO dto) {
        GeoTopic topic = getById(dto.getId());
        long count = topicMapper.selectCount(new LambdaQueryWrapper<GeoTopic>()
                .eq(GeoTopic::getTopicName, dto.getTopicName().trim())
                .ne(GeoTopic::getId, dto.getId()));
        if (count > 0) {
            throw new BusinessException("话题名称已存在");
        }
        topic.setTopicName(dto.getTopicName().trim());
        topic.setOptimizeWeek(dto.getOptimizeWeek());
        topic.setRemark(dto.getRemark());
        topicMapper.updateById(topic);
    }

    @Override
    public void delete(Long id) {
        long used = dailyMapper.selectCount(new LambdaQueryWrapper<GeoMonitorDaily>().eq(GeoMonitorDaily::getTopicId, id));
        if (used > 0) {
            throw new BusinessException("该话题已被日监测数据引用，无法删除");
        }
        topicMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的话题");
        }
        for (Long id : ids) {
            if (id != null) {
                delete(id);
            }
        }
    }

    @Override
    public GeoTopic getOrCreate(String topicName, String optimizeWeek) {
        String name = topicName == null ? "" : topicName.trim();
        if (!StringUtils.hasText(name)) {
            name = "未分类";
        }
        GeoTopic existing = topicMapper.selectOne(new LambdaQueryWrapper<GeoTopic>().eq(GeoTopic::getTopicName, name));
        if (existing != null) {
            if (StringUtils.hasText(optimizeWeek) && !StringUtils.hasText(existing.getOptimizeWeek())) {
                existing.setOptimizeWeek(optimizeWeek);
                topicMapper.updateById(existing);
            }
            return existing;
        }
        GeoTopic topic = new GeoTopic();
        topic.setTopicName(name);
        topic.setOptimizeWeek(optimizeWeek);
        try {
            topicMapper.insert(topic);
            return topic;
        } catch (Exception e) {
            GeoTopic again = topicMapper.selectOne(new LambdaQueryWrapper<GeoTopic>().eq(GeoTopic::getTopicName, name));
            if (again != null) {
                return again;
            }
            throw e;
        }
    }
}
