package com.base.admin.service.taskbiz;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPlacementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeoContentPlacementBizFieldWriter implements TaskBizFieldWriter {

    private final GeoContentPlacementMapper placementMapper;

    @Override
    public String bizType() {
        return Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT;
    }

    @Override
    public void writeAssignedUser(Long bizId, String assignField, Long userId, String displayName) {
        if (bizId == null) {
            throw new BusinessException("业务ID不能为空");
        }
        if (!StringUtils.hasText(assignField)) {
            throw new BusinessException("未配置分配回写字段");
        }
        if (userId == null) {
            throw new BusinessException("回写用户ID不能为空");
        }
        if (!StringUtils.hasText(displayName)) {
            throw new BusinessException("回写用户名称不能为空");
        }
        GeoContentPlacement placement = placementMapper.selectById(bizId);
        if (placement == null) {
            throw new BusinessException("关联投放记录不存在，无法回写");
        }
        String field = assignField.trim();
        String name = displayName.trim();
        LambdaUpdateWrapper<GeoContentPlacement> uw = new LambdaUpdateWrapper<GeoContentPlacement>()
                .eq(GeoContentPlacement::getId, bizId);
        if (Constants.TASK_ASSIGN_FIELD_PUBLISHER.equals(field)) {
            uw.set(GeoContentPlacement::getPublisherUserId, userId)
                    .set(GeoContentPlacement::getPublisherName, name);
        } else if (Constants.TASK_ASSIGN_FIELD_WRITER.equals(field)) {
            uw.set(GeoContentPlacement::getOwnerUserId, userId)
                    .set(GeoContentPlacement::getOwnerName, name);
        } else {
            throw new BusinessException("投放业务不支持的回写字段: " + field);
        }
        int rows = placementMapper.update(null, uw);
        if (rows <= 0) {
            throw new BusinessException("回写投放记录失败，请重试");
        }
        log.info("投放#{} 回写 {} → {}({})", bizId, field, name, userId);
    }
}
