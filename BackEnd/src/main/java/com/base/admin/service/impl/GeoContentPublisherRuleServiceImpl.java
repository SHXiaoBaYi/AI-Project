package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentPublisherRuleDTO;
import com.base.admin.domain.dto.GeoContentPublisherRuleQueryDTO;
import com.base.admin.domain.entity.GeoContentPublisherRule;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPublisherRuleMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.GeoContentPublisherRuleService;
import com.base.admin.util.QueryWrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GeoContentPublisherRuleServiceImpl implements GeoContentPublisherRuleService {

    private final GeoContentPublisherRuleMapper ruleMapper;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<GeoContentPublisherRule> list(GeoContentPublisherRuleQueryDTO query) {
        LambdaQueryWrapper<GeoContentPublisherRule> wrapper = new LambdaQueryWrapper<GeoContentPublisherRule>()
                .eq(query.getPublisherUserId() != null, GeoContentPublisherRule::getPublisherUserId, query.getPublisherUserId())
                .eq(query.getIsDefault() != null, GeoContentPublisherRule::getIsDefault, query.getIsDefault())
                .orderByDesc(GeoContentPublisherRule::getIsDefault)
                .orderByDesc(GeoContentPublisherRule::getId);
        QueryWrappers.applyCreateTimeRange(wrapper, query, GeoContentPublisherRule::getCreateTime);
        Page<GeoContentPublisherRule> page = ruleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(GeoContentPublisherRuleDTO dto) {
        GeoContentPublisherRule rule = new GeoContentPublisherRule();
        apply(rule, dto);
        if (isDefaultFlag(rule.getIsDefault())) {
            clearOtherDefaults(null);
        } else if (resolveDefault().isEmpty()) {
            rule.setIsDefault(1);
        }
        ruleMapper.insert(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(GeoContentPublisherRuleDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("规则ID不能为空");
        }
        GeoContentPublisherRule rule = ruleMapper.selectById(dto.getId());
        if (rule == null) {
            throw new BusinessException("发布人规则不存在");
        }
        boolean wasDefault = isDefaultFlag(rule.getIsDefault());
        apply(rule, dto);
        if (isDefaultFlag(rule.getIsDefault())) {
            clearOtherDefaults(dto.getId());
        } else if (wasDefault) {
            throw new BusinessException("请先指定其他默认发布人，再取消当前默认");
        }
        ruleMapper.updateById(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException("规则ID不能为空");
        }
        GeoContentPublisherRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException("发布人规则不存在");
        }
        if (isDefaultFlag(rule.getIsDefault())) {
            long others = ruleMapper.selectCount(new LambdaQueryWrapper<GeoContentPublisherRule>()
                    .ne(GeoContentPublisherRule::getId, id));
            if (others > 0) {
                throw new BusinessException("请先将其他规则设为默认，再删除当前默认规则");
            }
        }
        ruleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的规则");
        }
        for (Long id : ids) {
            if (id != null) {
                delete(id);
            }
        }
    }

    @Override
    public Optional<GeoContentPublisherRule> resolveDefault() {
        GeoContentPublisherRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<GeoContentPublisherRule>()
                .eq(GeoContentPublisherRule::getIsDefault, 1)
                .orderByDesc(GeoContentPublisherRule::getId)
                .last("LIMIT 1"));
        return Optional.ofNullable(rule);
    }

    private void clearOtherDefaults(Long excludeId) {
        ruleMapper.update(null, new LambdaUpdateWrapper<GeoContentPublisherRule>()
                .set(GeoContentPublisherRule::getIsDefault, 0)
                .eq(GeoContentPublisherRule::getIsDefault, 1)
                .ne(excludeId != null, GeoContentPublisherRule::getId, excludeId));
    }

    private void apply(GeoContentPublisherRule rule, GeoContentPublisherRuleDTO dto) {
        SysUser user = userMapper.selectById(dto.getPublisherUserId());
        if (user == null) {
            throw new BusinessException("发布人不存在");
        }
        rule.setPublisherUserId(dto.getPublisherUserId());
        rule.setPublisherName(displayName(user));
        rule.setIsDefault(isDefaultFlag(dto.getIsDefault()) ? 1 : 0);
        rule.setRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
    }

    private static boolean isDefaultFlag(Integer v) {
        return v != null && v != 0;
    }

    private static String displayName(SysUser user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : String.valueOf(user.getUserId());
    }
}
