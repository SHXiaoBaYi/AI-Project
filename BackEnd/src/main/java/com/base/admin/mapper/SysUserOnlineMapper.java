package com.base.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.admin.domain.entity.SysUserOnline;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserOnlineMapper extends BaseMapper<SysUserOnline> {

    /**
     * 物理删除（会话表不走逻辑删除，避免 soft-delete 后再 insert 主键冲突）
     */
    @Delete("DELETE FROM sys_user_online WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);

    /**
     * 并发安全写入：存在则覆盖当前会话
     */
    @Insert("""
            INSERT INTO sys_user_online
              (user_id, token_id, username, ip, user_agent, login_time, expire_time, is_active)
            VALUES
              (#{userId}, #{tokenId}, #{username}, #{ip}, #{userAgent}, #{loginTime}, #{expireTime}, 1)
            ON DUPLICATE KEY UPDATE
              token_id = VALUES(token_id),
              username = VALUES(username),
              ip = VALUES(ip),
              user_agent = VALUES(user_agent),
              login_time = VALUES(login_time),
              expire_time = VALUES(expire_time),
              is_active = 1
            """)
    int upsert(SysUserOnline online);
}
