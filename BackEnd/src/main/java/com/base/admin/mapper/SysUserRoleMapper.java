package com.base.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.admin.domain.entity.SysUserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Update("UPDATE sys_user_role SET is_active = 0 WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Update("""
            <script>
            UPDATE sys_user_role SET is_active = 0 WHERE user_id IN
            <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int deleteByUserIds(@Param("userIds") List<Long> userIds);

    @Insert("""
            INSERT INTO sys_user_role (user_id, role_id, is_active) VALUES (#{userId}, #{roleId}, 1)
            ON DUPLICATE KEY UPDATE is_active = 1
            """)
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
