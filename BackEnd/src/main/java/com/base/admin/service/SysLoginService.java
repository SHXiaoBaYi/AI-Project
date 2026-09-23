package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkLoginDTO;
import com.base.admin.domain.dto.LoginDTO;
import com.base.admin.domain.vo.DingTalkLoginConfigVO;
import com.base.admin.domain.vo.LoginVO;
import com.base.admin.domain.vo.UserInfoVO;

public interface SysLoginService {

    /** 已关闭：本系统仅支持钉钉扫码登录。 */
    LoginVO login(LoginDTO dto, String ip, String userAgent);

    DingTalkLoginConfigVO dingTalkLoginConfig();

    LoginVO loginByDingTalk(DingTalkLoginDTO dto, String ip, String userAgent);

    void logout(Long userId);

    UserInfoVO getUserInfo(Long userId);
}
