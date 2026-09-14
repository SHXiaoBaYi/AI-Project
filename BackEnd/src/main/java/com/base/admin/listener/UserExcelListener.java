package com.base.admin.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.base.admin.domain.dto.UserExcelRowDTO;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.vo.UserImportResultVO;
import com.base.admin.mapper.SysUserMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.regex.Pattern;

/**
 * EasyExcel 逐行解析监听器 —— 校验 + 构建实体，批量插入由 Service 层执行。
 */
@Slf4j
public class UserExcelListener extends AnalysisEventListener<UserExcelRowDTO> {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Map<String, Integer> GENDER_MAP = Map.of("男", 1, "女", 2, "1", 1, "2", 2);
    private static final Map<String, Integer> STATUS_MAP = Map.of("启用", 0, "停用", 1, "0", 0, "1", 1);

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Getter
    private final List<SysUser> validUsers = new ArrayList<>();
    @Getter
    private final List<UserImportResultVO.UserImportErrorVO> errors = new ArrayList<>();
    @Getter
    private int totalCount = 0;
    private final Set<String> batchUsernames = new HashSet<>();

    public UserExcelListener(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void invoke(UserExcelRowDTO row, AnalysisContext context) {
        totalCount++;
        Integer rowIndex = context.readRowHolder().getRowIndex() + 1; // 1-based

        String username = trimToNull(row.getUsername());
        String password = trimToNull(row.getPassword());

        // 必填校验
        if (username == null) {
            errors.add(err(rowIndex, "username", "用户名不能为空"));
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            errors.add(err(rowIndex, "username", "用户名只允许字母、数字、下划线，不能以数字开头"));
            return;
        }
        if (password == null) {
            errors.add(err(rowIndex, "password", "密码不能为空"));
            return;
        }
        if (password.length() < 6) {
            errors.add(err(rowIndex, "password", "密码长度不能少于6位"));
            return;
        }

        // 邮箱格式（非空时校验）
        String email = trimToNull(row.getEmail());
        if (email != null && !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            errors.add(err(rowIndex, "email", "邮箱格式不正确"));
            return;
        }

        // 手机号格式（非空时校验）
        String phone = trimToNull(row.getPhone());
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            errors.add(err(rowIndex, "phone", "手机号格式不正确"));
            return;
        }

        // 性别转换
        Integer gender = 0;
        String genderStr = trimToNull(row.getGender());
        if (genderStr != null) {
            gender = GENDER_MAP.get(genderStr);
            if (gender == null) {
                errors.add(err(rowIndex, "gender", "性别请填写'男'或'女'"));
                return;
            }
        }

        // 状态转换
        Integer status = 0;
        String statusStr = trimToNull(row.getStatus());
        if (statusStr != null) {
            Integer val = STATUS_MAP.get(statusStr);
            if (val == null) {
                errors.add(err(rowIndex, "status", "账户状态请填写'启用'或'停用'"));
                return;
            }
            status = val;
        }

        // 批次内去重
        if (!batchUsernames.add(username)) {
            errors.add(err(rowIndex, "username", "用户名 '" + username + "' 与本批次其他行重复"));
            return;
        }

        // 数据库去重
        Long count = userMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
        if (count > 0) {
            errors.add(err(rowIndex, "username", "用户名 '" + username + "' 已存在"));
            return;
        }

        // 构建实体
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(trimToNull(row.getNickname()));
        user.setEmail(email);
        user.setPhone(phone);
        user.setGender(gender);
        user.setStatus(status);
        validUsers.add(user);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("Excel 解析完成: 总行数={}, 成功={}, 失败={}", totalCount, validUsers.size(), errors.size());
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static UserImportResultVO.UserImportErrorVO err(int rowIndex, String field, String message) {
        return new UserImportResultVO.UserImportErrorVO(rowIndex, field, message);
    }
}
