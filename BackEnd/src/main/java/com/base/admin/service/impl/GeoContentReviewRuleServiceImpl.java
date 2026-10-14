package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentReviewRuleDTO;
import com.base.admin.domain.dto.GeoContentReviewRuleQueryDTO;
import com.base.admin.domain.entity.GeoContentReviewRule;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentReviewRuleMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.GeoContentReviewRuleService;
import com.base.admin.util.QueryWrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeoContentReviewRuleServiceImpl implements GeoContentReviewRuleService {

    private final GeoContentReviewRuleMapper ruleMapper;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<GeoContentReviewRule> list(GeoContentReviewRuleQueryDTO query) {
        LambdaQueryWrapper<GeoContentReviewRule> wrapper = new LambdaQueryWrapper<GeoContentReviewRule>()
                .eq(query.getWriterUserId() != null, GeoContentReviewRule::getWriterUserId, query.getWriterUserId())
                .eq(query.getSkipStage1() != null, GeoContentReviewRule::getSkipStage1, query.getSkipStage1())
                .orderByDesc(GeoContentReviewRule::getId);
        QueryWrappers.applyCreateTimeRange(wrapper, query, GeoContentReviewRule::getCreateTime);
        Page<GeoContentReviewRule> page = ruleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    public void create(GeoContentReviewRuleDTO dto) {
        assertWriterUnique(dto.getWriterUserId(), null);
        GeoContentReviewRule rule = new GeoContentReviewRule();
        apply(rule, dto);
        ruleMapper.insert(rule);
    }

    @Override
    public void update(GeoContentReviewRuleDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("规则ID不能为空");
        }
        GeoContentReviewRule rule = ruleMapper.selectById(dto.getId());
        if (rule == null) {
            throw new BusinessException("审核规则不存在");
        }
        assertWriterUnique(dto.getWriterUserId(), dto.getId());
        apply(rule, dto);
        ruleMapper.updateById(rule);
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException("规则ID不能为空");
        }
        GeoContentReviewRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException("审核规则不存在");
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
    public Set<Long> writerIdsSkippingStage1(Collection<Long> writerUserIds) {
        if (writerUserIds == null || writerUserIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = writerUserIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return ruleMapper.selectList(new LambdaQueryWrapper<GeoContentReviewRule>()
                        .in(GeoContentReviewRule::getWriterUserId, ids)
                        .eq(GeoContentReviewRule::getSkipStage1, 1)
                        .select(GeoContentReviewRule::getWriterUserId))
                .stream()
                .map(GeoContentReviewRule::getWriterUserId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean skipStage1(Long writerUserId) {
        if (writerUserId == null) {
            return false;
        }
        return !writerIdsSkippingStage1(List.of(writerUserId)).isEmpty();
    }

    private void assertWriterUnique(Long writerUserId, Long excludeId) {
        long count = ruleMapper.selectCount(new LambdaQueryWrapper<GeoContentReviewRule>()
                .eq(GeoContentReviewRule::getWriterUserId, writerUserId)
                .ne(excludeId != null, GeoContentReviewRule::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("该撰写人已配置审核规则");
        }
    }

    private void apply(GeoContentReviewRule rule, GeoContentReviewRuleDTO dto) {
        SysUser user = userMapper.selectById(dto.getWriterUserId());
        if (user == null) {
            throw new BusinessException("撰写人不存在");
        }
        rule.setWriterUserId(dto.getWriterUserId());
        rule.setWriterName(displayName(user));
        rule.setSkipStage1(dto.getSkipStage1() != null && dto.getSkipStage1() != 0 ? 1 : 0);
        rule.setRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
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
