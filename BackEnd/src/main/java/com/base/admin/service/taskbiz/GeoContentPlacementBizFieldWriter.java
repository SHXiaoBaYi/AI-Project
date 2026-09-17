package com.base.admin.service.taskbiz;

import com.base.admin.common.Constants;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPlacementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
        GeoContentPlacement placement = placementMapper.selectById(bizId);
        if (placement == null) {
            throw new BusinessException("关联投放记录不存在，无法回写");
        }
        String field = assignField.trim();
        if (Constants.TASK_ASSIGN_FIELD_PUBLISHER.equals(field)) {
            placement.setPublisherUserId(userId);
            placement.setPublisherName(displayName);
        } else if (Constants.TASK_ASSIGN_FIELD_WRITER.equals(field)) {
            placement.setOwnerUserId(userId);
            placement.setOwnerName(displayName);
        } else {
            throw new BusinessException("投放业务不支持的回写字段: " + field);
        }
        placementMapper.updateById(placement);
    }
}
