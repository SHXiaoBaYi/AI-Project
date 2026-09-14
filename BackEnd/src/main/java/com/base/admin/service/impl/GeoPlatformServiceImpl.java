package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoPlatformDTO;
import com.base.admin.domain.dto.GeoPlatformQueryDTO;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoMonitorDailyMapper;
import com.base.admin.mapper.GeoPlatformMapper;
import com.base.admin.service.GeoPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeoPlatformServiceImpl implements GeoPlatformService {

    private final GeoPlatformMapper platformMapper;
    private final GeoMonitorDailyMapper dailyMapper;

    @Override
    public PageResult<GeoPlatform> list(GeoPlatformQueryDTO query) {
        LambdaQueryWrapper<GeoPlatform> wrapper = new LambdaQueryWrapper<GeoPlatform>()
                .like(StringUtils.hasText(query.getPlatformName()), GeoPlatform::getPlatformName, query.getPlatformName())
                .orderByAsc(GeoPlatform::getSortOrder)
                .orderByAsc(GeoPlatform::getId);
        Page<GeoPlatform> page = platformMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    public List<GeoPlatform> listAll() {
        return platformMapper.selectList(new LambdaQueryWrapper<GeoPlatform>()
                .orderByAsc(GeoPlatform::getSortOrder)
                .orderByAsc(GeoPlatform::getId));
    }

    @Override
    public GeoPlatform getById(Long id) {
        GeoPlatform platform = platformMapper.selectById(id);
        if (platform == null) {
            throw new BusinessException("平台不存在");
        }
        return platform;
    }

    @Override
    public void create(GeoPlatformDTO dto) {
        long count = platformMapper.selectCount(
                new LambdaQueryWrapper<GeoPlatform>().eq(GeoPlatform::getPlatformName, dto.getPlatformName().trim()));
        if (count > 0) {
            throw new BusinessException("平台名称已存在");
        }
        GeoPlatform platform = new GeoPlatform();
        platform.setPlatformName(dto.getPlatformName().trim());
        platform.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        platform.setRemark(dto.getRemark());
        platformMapper.insert(platform);
    }

    @Override
    public void update(GeoPlatformDTO dto) {
        GeoPlatform platform = getById(dto.getId());
        String name = dto.getPlatformName().trim();
        long count = platformMapper.selectCount(new LambdaQueryWrapper<GeoPlatform>()
                .eq(GeoPlatform::getPlatformName, name)
                .ne(GeoPlatform::getId, dto.getId()));
        if (count > 0) {
            throw new BusinessException("平台名称已存在");
        }
        String oldName = platform.getPlatformName();
        platform.setPlatformName(name);
        platform.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        platform.setRemark(dto.getRemark());
        platformMapper.updateById(platform);
        if (!oldName.equals(name)) {
            GeoMonitorDaily patch = new GeoMonitorDaily();
            patch.setPlatform(name);
            dailyMapper.update(patch, new LambdaQueryWrapper<GeoMonitorDaily>().eq(GeoMonitorDaily::getPlatform, oldName));
        }
    }

    @Override
    public void delete(Long id) {
        GeoPlatform platform = getById(id);
        long used = dailyMapper.selectCount(
                new LambdaQueryWrapper<GeoMonitorDaily>().eq(GeoMonitorDaily::getPlatform, platform.getPlatformName()));
        if (used > 0) {
            throw new BusinessException("该平台已被日监测数据引用，无法删除");
        }
        platformMapper.deleteById(id);
    }

    @Override
    public GeoPlatform getOrCreate(String platformName) {
        String name = platformName == null ? "" : platformName.trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("平台不能为空");
        }
        GeoPlatform existing = platformMapper.selectOne(
                new LambdaQueryWrapper<GeoPlatform>().eq(GeoPlatform::getPlatformName, name));
        if (existing != null) {
            return existing;
        }
        GeoPlatform platform = new GeoPlatform();
        platform.setPlatformName(name);
        platform.setSortOrder(0);
        try {
            platformMapper.insert(platform);
            return platform;
        } catch (Exception e) {
            GeoPlatform again = platformMapper.selectOne(
                    new LambdaQueryWrapper<GeoPlatform>().eq(GeoPlatform::getPlatformName, name));
            if (again != null) {
                return again;
            }
            throw e;
        }
    }
}
