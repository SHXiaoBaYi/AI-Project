package com.base.admin.controller;

import com.base.admin.common.Result;
import com.base.admin.domain.dto.LoginDTO;
import com.base.admin.domain.vo.LoginVO;
import com.base.admin.domain.vo.UserInfoVO;
import com.base.admin.security.LoginUser;
import com.base.admin.service.SysLoginService;
import com.base.admin.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "登录管理", description = "用户登录与认证相关接口")
@RestController
@RequiredArgsConstructor
public class SysLoginController {

    private final SysLoginService loginService;

    @Operation(summary = "用户登录")
    @PostMapping("/auth/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO vo = loginService.login(dto);
        return Result.ok(vo);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/auth/info")
    public Result<UserInfoVO> getUserInfo() {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        UserInfoVO info = loginService.getUserInfo(currentUser.getUserId());
        return Result.ok(info);
    }
}
