package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkLoginDTO;
import com.base.admin.domain.dto.LoginDTO;
import com.base.admin.domain.vo.DingTalkLoginConfigVO;
import com.base.admin.domain.vo.LoginOptionsVO;
import com.base.admin.domain.vo.LoginVO;
import com.base.admin.domain.vo.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;

public interface SysLoginService {

    /** 仅本机 localhost / 127.0.0.1 允许账号密码登录。 */
    LoginVO login(LoginDTO dto, String ip, String userAgent, HttpServletRequest request);

    LoginOptionsVO loginOptions(HttpServletRequest request);

    DingTalkLoginConfigVO dingTalkLoginConfig();

    LoginVO loginByDingTalk(DingTalkLoginDTO dto, String ip, String userAgent);

    void logout(Long userId);

    UserInfoVO getUserInfo(Long userId);
}
