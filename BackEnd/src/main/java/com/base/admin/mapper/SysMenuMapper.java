package com.base.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.admin.domain.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("""
            WITH RECURSIVE active_tree AS (
                SELECT * FROM sys_menu WHERE parent_id = 0 AND status = 0
                UNION ALL
                SELECT m.* FROM sys_menu m
                INNER JOIN active_tree t ON m.parent_id = t.menu_id
                WHERE m.status = 0
            )
            SELECT DISTINCT m.perms FROM active_tree m
            LEFT JOIN sys_role_menu rm ON m.menu_id = rm.menu_id
            LEFT JOIN sys_user_role ur ON rm.role_id = ur.role_id
            WHERE ur.user_id = #{userId} AND m.perms != ''
            """)
    Set<String> selectPermsByUserId(@Param("userId") Long userId);

    @Select("""
            WITH RECURSIVE active_tree AS (
                SELECT * FROM sys_menu WHERE parent_id = 0 AND status = 0
                UNION ALL
                SELECT m.* FROM sys_menu m
                INNER JOIN active_tree t ON m.parent_id = t.menu_id
                WHERE m.status = 0
            )
            SELECT DISTINCT m.menu_id, m.menu_name, m.parent_id, m.sort_order, m.path, m.component,
                   m.menu_type, m.perms, m.icon, m.visible, m.status
            FROM active_tree m
            LEFT JOIN sys_role_menu rm ON m.menu_id = rm.menu_id
            LEFT JOIN sys_user_role ur ON rm.role_id = ur.role_id
            WHERE ur.user_id = #{userId} AND m.menu_type IN ('M', 'C')
            ORDER BY m.sort_order
            """)
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    @Select("""
            WITH RECURSIVE active_tree AS (
                SELECT * FROM sys_menu WHERE parent_id = 0 AND status = 0
                UNION ALL
                SELECT m.* FROM sys_menu m
                INNER JOIN active_tree t ON m.parent_id = t.menu_id
                WHERE m.status = 0
            )
            SELECT * FROM active_tree WHERE menu_type IN ('M', 'C')
            ORDER BY sort_order
            """)
    List<SysMenu> selectAllMenus();

    @Select("""
            WITH RECURSIVE active_tree AS (
                SELECT * FROM sys_menu WHERE parent_id = 0 AND status = 0
                UNION ALL
                SELECT m.* FROM sys_menu m
                INNER JOIN active_tree t ON m.parent_id = t.menu_id
                WHERE m.status = 0
            )
            SELECT menu_id FROM active_tree
            """)
    List<Long> selectAllActiveMenuIds();
}
