package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.UserDTO;
import com.base.admin.domain.dto.UserExcelRowDTO;
import com.base.admin.domain.dto.UserExportRowDTO;
import com.base.admin.domain.dto.UserPageQueryDTO;
import com.base.admin.domain.dto.UserRoleDTO;
import com.base.admin.domain.entity.SysRole;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.entity.SysUserRole;
import com.base.admin.domain.vo.RoleSimpleVO;
import com.base.admin.domain.vo.UserImportResultVO;
import com.base.admin.domain.vo.UserVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.listener.UserExcelListener;
import com.base.admin.mapper.SysRoleMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.mapper.SysUserRoleMapper;
import com.base.admin.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

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

        if (query.getRoleIds() != null && !query.getRoleIds().isEmpty()) {
            List<Long> userIds = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, query.getRoleIds()))
                    .stream().map(SysUserRole::getUserId).distinct().collect(Collectors.toList());
            if (userIds.isEmpty()) {
                return new PageResult<>(0L, List.of());
            }
            wrapper.in(SysUser::getUserId, userIds);
        }

        Page<SysUser> page = userMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<UserVO> rows = page.getRecords().stream().map(this::toUserVO).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public UserVO getById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toUserVO(user);
    }

    @Override
    @Transactional
    public void create(UserDTO dto) {
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名不能重复");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setGender(dto.getGender() != null ? dto.getGender() : 0);
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
        user.setRemark(dto.getRemark());
        userMapper.insert(user);

        saveUserRoles(user.getUserId(), dto.getRoleIds());
    }

    @Override
    @Transactional
    public void update(UserDTO dto) {
        SysUser user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setGender(dto.getGender());
        user.setStatus(dto.getStatus());
        user.setRemark(dto.getRemark());
        userMapper.updateById(user);

        if (dto.getRoleIds() != null) {
            saveUserRoles(user.getUserId(), dto.getRoleIds());
        }
    }

    @Override
    @Transactional
    public void delete(Long userId) {
        userMapper.deleteById(userId);
        userRoleMapper.deleteByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的用户");
        }
        userMapper.deleteBatchIds(ids);
        userRoleMapper.deleteByUserIds(ids);
    }

    @Override
    public void resetPwd(Long userId, String password) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
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
        UserExcelListener listener = new UserExcelListener(userMapper, passwordEncoder);
        try {
            com.alibaba.excel.EasyExcel.read(file.getInputStream(), UserExcelRowDTO.class, listener)
                    .sheet().doRead();
        } catch (Exception e) {
            throw new BusinessException("Excel解析失败: " + e.getMessage());
        }

        List<SysUser> validUsers = listener.getValidUsers();
        for (SysUser user : validUsers) {
            userMapper.insert(user);
        }

        UserImportResultVO result = new UserImportResultVO();
        result.setTotalCount(listener.getTotalCount());
        result.setSuccessCount(validUsers.size());
        result.setFailureCount(listener.getErrors().size());
        result.setErrors(listener.getErrors());
        return result;
    }

    @Override
    public void exportUsers(List<Long> ids, jakarta.servlet.http.HttpServletResponse response) throws IOException {
        List<SysUser> users = userMapper.selectBatchIds(ids);
        List<UserExportRowDTO> rows = users.stream().map(u -> {
            UserExportRowDTO dto = new UserExportRowDTO();
            dto.setUserId(u.getUserId());
            dto.setUsername(u.getUsername());
            dto.setNickname(u.getNickname());
            dto.setEmail(u.getEmail());
            dto.setPhone(u.getPhone());
            dto.setGender(toGenderText(u.getGender()));
            dto.setStatus(toStatusText(u.getStatus()));
            // 加载角色
            List<SysUserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, u.getUserId()));
            if (!userRoles.isEmpty()) {
                List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
                List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
                dto.setRoles(roles.stream().map(SysRole::getRoleName).collect(Collectors.joining("、")));
            }
            if (u.getCreateTime() != null) {
                dto.setCreateTime(u.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            return dto;
        }).collect(Collectors.toList());

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = java.net.URLEncoder.encode("用户导出数据.xlsx", java.nio.charset.StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), UserExportRowDTO.class)
                .sheet("用户数据").doWrite(rows);
    }

    private static final Map<Integer, String> GENDER_MAP = Map.of(0, "未知", 1, "男", 2, "女");
    private static final Map<Integer, String> STATUS_MAP = Map.of(0, "启用", 1, "停用");

    private static String toGenderText(Integer gender) {
        return gender == null ? "" : GENDER_MAP.getOrDefault(gender, "");
    }

    private static String toStatusText(Integer status) {
        return status == null ? "" : STATUS_MAP.getOrDefault(status, "");
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
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
}
