package com.base.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.admin.domain.entity.GeoMonitorDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface GeoMonitorDailyMapper extends BaseMapper<GeoMonitorDaily> {

    @Select("""
            SELECT id, inspect_date, platform, keyword, topic_id, mentioned, rank_no, recommend_status,
                   screenshot_url, third_party_url, negative_content, competitors,
                   create_by, create_time, update_by, update_time, is_active
            FROM geo_monitor_daily
            WHERE inspect_date = #{inspectDate} AND platform = #{platform} AND keyword = #{keyword}
            LIMIT 1
            """)
    GeoMonitorDaily selectUkIncludeDeleted(@Param("inspectDate") LocalDate inspectDate,
                                           @Param("platform") String platform,
                                           @Param("keyword") String keyword);

    @Update("UPDATE geo_monitor_daily SET is_active = 1 WHERE id = #{id}")
    int restoreActive(@Param("id") Long id);
}
