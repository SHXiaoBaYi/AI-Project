package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.HrDingTalkBindDTO;
import com.base.admin.domain.dto.UserDTO;
import com.base.admin.domain.dto.UserPageQueryDTO;
import com.base.admin.domain.dto.UserRoleDTO;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.entity.SysUserRole;
import com.base.admin.domain.vo.RoleSimpleVO;
import com.base.admin.domain.vo.UserImportResultVO;
import com.base.admin.domain.vo.UserVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.SysRoleMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.mapper.SysUserRoleMapper;
import com.base.admin.service.HrMasterService;
import com.base.admin.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final JdbcTemplate jdbc;
    private final HrMasterService hrMasterService;

    @Override
    public PageResult<UserVO> list(UserPageQueryDTO query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getNickname()), SysUser::getNickname, query.getNickname())
                .like(StringUtils.hasText(query.getPhone()), SysUser::getPhone, query.getPhone())
                .like(StringUtils.hasText(query.getEmail()), SysUser::getEmail, query.getEmail())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime)
                .orderByDesc(SysUser::getUserId);
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(wrapper, query, SysUser::getCreateTime);

        if (query.getRoleIds() != null && !query.getRoleIds().isEmpty()) {
            List<Long> userIds = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, query.getRoleIds()))
                    .stream().map(SysUserRole::getUserId).distinct().collect(Collectors.toList());
            if (userIds.isEmpty()) {
                return new PageResult<>(0L, List.of());
            }
            wrapper.in(SysUser::getUserId, userIds);
        }

        if (query.getDingtalkBound() != null) {
            List<Long> boundIds = jdbc.query(
                    "SELECT user_id FROM hr_user_dingtalk WHERE is_active = 1",
                    (rs, row) -> rs.getLong(1));
            if (Integer.valueOf(1).equals(query.getDingtalkBound())) {
                if (boundIds.isEmpty()) {
                    return new PageResult<>(0L, List.of());
                }
                wrapper.in(SysUser::getUserId, boundIds);
            } else if (!boundIds.isEmpty()) {
                wrapper.notIn(SysUser::getUserId, boundIds);
            }
        }

        Page<SysUser> page = userMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<UserVO> rows = page.getRecords().stream().map(this::toUserVO).collect(Collectors.toList());
        fillDingTalk(rows);
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public UserVO getById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserVO vo = toUserVO(user);
        fillDingTalk(List.of(vo));
        return vo;
    }

    @Override
    @Transactional
    public void create(UserDTO dto) {
        throw new BusinessException("已关闭手动新增用户，请通过钉钉扫码登录自动注册");
    }

    @Override
    @Transactional
    public void update(UserDTO dto) {
        SysUser user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        String oldPhone = user.getPhone() == null ? "" : user.getPhone().trim();
        String newPhone = dto.getPhone() == null ? "" : dto.getPhone().trim();
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(newPhone.isEmpty() ? null : newPhone);
        user.setGender(dto.getGender());
        user.setStatus(dto.getStatus());
        user.setRemark(dto.getRemark());
        userMapper.updateById(user);

        if (!oldPhone.equals(newPhone)) {
            Integer bound = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM hr_user_dingtalk WHERE user_id = ? AND is_active = 1",
                    Integer.class, user.getUserId());
            if (bound != null && bound > 0) {
                if (newPhone.isEmpty()) {
                    hrMasterService.unbindDingTalk(user.getUserId());
                } else {
                    hrMasterService.bindDingTalk(bindDto(user.getUserId()));
                }
            }
        }

        if (dto.getRoleIds() != null) {
            saveUserRoles(user.getUserId(), dto.getRoleIds());
        }
    }

    @Override
    @Transactional
    public void delete(Long userId) {
        throw new BusinessException("已关闭删除用户，如需停用请在编辑中将账户状态设为停用");
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        throw new BusinessException("已关闭批量删除用户");
    }

    @Override
    public void resetPwd(Long userId, String password) {
        throw new BusinessException("已关闭重置密码，请使用钉钉扫码登录");
    }

    @Override
    public void changeStatus(Long userId, Integer status) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void assignRoles(UserRoleDTO dto) {
        saveUserRoles(dto.getUserId(), dto.getRoleIds());
    }

    @Override
    @Transactional
    public UserImportResultVO importUsers(MultipartFile file) {
        throw new BusinessException("已关闭批量导入用户，请通过钉钉扫码登录自动注册");
    }

    @Override
    public void exportUsers(List<Long> ids, jakarta.servlet.http.HttpServletResponse response) throws IOException {
        throw new BusinessException("已关闭批量导出用户");
    }

    private static HrDingTalkBindDTO bindDto(Long userId) {
        HrDingTalkBindDTO dto = new HrDingTalkBindDTO();
        dto.setUserId(userId);
        return dto;
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                userRoleMapper.insertUserRole(userId, roleId);
            }
        }
    }

    private UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setGender(user.getGender());
        vo.setStatus(user.getStatus());
        vo.setRemark(user.getRemark());
        vo.setCreateTime(user.getCreateTime());

        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getUserId()));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (!roleIds.isEmpty()) {
            List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
            vo.setRoles(roles.stream().map(r -> {
                RoleSimpleVO rvo = new RoleSimpleVO();
                rvo.setRoleId(r.getRoleId());
                rvo.setRoleName(r.getRoleName());
                return rvo;
            }).collect(Collectors.toList()));
        }
        return vo;
    }

    private void fillDingTalk(List<UserVO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        String marks = rows.stream().map(row -> "?").collect(Collectors.joining(","));
        Object[] args = rows.stream().map(UserVO::getUserId).toArray();
        Map<Long, UserVO> byId = new HashMap<>();
        rows.forEach(row -> {
            row.setDingtalkBound(0);
            byId.put(row.getUserId(), row);
        });
        jdbc.query("SELECT user_id, dingtalk_user_id, dingtalk_union_id FROM hr_user_dingtalk WHERE is_active = 1 AND user_id IN ("
                + marks + ")", rs -> {
            UserVO vo = byId.get(rs.getLong("user_id"));
            if (vo != null) {
                vo.setDingtalkBound(1);
                vo.setDingtalkUserId(rs.getString("dingtalk_user_id"));
                vo.setDingtalkUnionId(rs.getString("dingtalk_union_id"));
            }
        }, args);
    }
}
