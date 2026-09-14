package com.base.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.admin.domain.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    @Update("UPDATE sys_role_menu SET is_active = 0 WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId} AND is_active = 1")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO sys_role_menu (role_id, menu_id, is_active) VALUES (#{roleId}, #{menuId}, 1)
            ON DUPLICATE KEY UPDATE is_active = 1
            """)
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    @Update("UPDATE sys_role_menu SET is_active = 0 WHERE menu_id = #{menuId}")
    int deleteByMenuId(@Param("menuId") Long menuId);
}
