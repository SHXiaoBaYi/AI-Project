package com.base.admin.controller;

import com.base.admin.common.Result;
import com.base.admin.domain.dto.DingTalkLoginDTO;
import com.base.admin.domain.dto.LoginDTO;
import com.base.admin.domain.vo.DingTalkLoginConfigVO;
import com.base.admin.domain.vo.LoginOptionsVO;
import com.base.admin.domain.vo.LoginVO;
import com.base.admin.domain.vo.UserInfoVO;
import com.base.admin.security.LoginUser;
import com.base.admin.service.SysLoginService;
import com.base.admin.util.IpUtils;
import com.base.admin.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "登录管理", description = "用户登录与认证相关接口")
@RestController
@RequiredArgsConstructor
public class SysLoginController {

    private final SysLoginService loginService;

    @Operation(summary = "登录页公开选项")
    @GetMapping("/auth/login-options")
    public Result<LoginOptionsVO> loginOptions(HttpServletRequest request) {
        return Result.ok(loginService.loginOptions(request));
    }

    @Operation(summary = "用户名密码登录")
    @PostMapping("/auth/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        LoginVO vo = loginService.login(dto, IpUtils.getClientIp(request), request.getHeader("User-Agent"), request);
        return Result.ok(vo);
    }

    @Operation(summary = "钉钉扫码登录公开配置")
    @GetMapping("/auth/dingtalk/config")
    public Result<DingTalkLoginConfigVO> dingTalkConfig() {
        return Result.ok(loginService.dingTalkLoginConfig());
    }

    @Operation(summary = "钉钉扫码登录")
    @PostMapping("/auth/dingtalk/login")
    public Result<LoginVO> dingTalkLogin(@Valid @RequestBody DingTalkLoginDTO dto, HttpServletRequest request) {
        LoginVO vo = loginService.loginByDingTalk(dto, IpUtils.getClientIp(request), request.getHeader("User-Agent"));
        return Result.ok(vo);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/auth/info")
    public Result<UserInfoVO> getUserInfo() {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        UserInfoVO info = loginService.getUserInfo(currentUser.getUserId());
        return Result.ok(info);
    }

    @Operation(summary = "会话探活（用于被踢检测）")
    @GetMapping("/auth/session")
    public Result<Void> checkSession() {
        return Result.ok();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        loginService.logout(currentUser.getUserId());
        return Result.ok();
    }
}
