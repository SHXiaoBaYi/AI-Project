package com.base.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.admin.domain.entity.GeoMediaAssetTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GeoMediaAssetTopicMapper extends BaseMapper<GeoMediaAssetTopic> {

    @Select("SELECT topic_id FROM geo_media_asset_topic WHERE asset_id = #{assetId}")
    List<Long> listTopicIds(@Param("assetId") Long assetId);
}
