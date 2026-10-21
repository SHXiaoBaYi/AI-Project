package com.base.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.admin.domain.entity.GeoMediaAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GeoMediaAssetMapper extends BaseMapper<GeoMediaAsset> {

    /** 生效素材（语义召回候选池） */
    @Select("""
            SELECT a.*
            FROM geo_media_asset a
            WHERE a.is_active = 1 AND a.status = 'active'
            ORDER BY a.id DESC
            LIMIT #{limit}
            """)
    List<GeoMediaAsset> listActive(@Param("limit") int limit);

    /** 已关联指定话题的素材 ID */
    @Select("""
            SELECT asset_id FROM geo_media_asset_topic WHERE topic_id = #{topicId}
            """)
    List<Long> listAssetIdsByTopic(@Param("topicId") Long topicId);
}
