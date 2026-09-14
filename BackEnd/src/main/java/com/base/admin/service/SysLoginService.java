package com.base.admin.service;

import com.base.admin.domain.dto.LoginDTO;
import com.base.admin.domain.vo.LoginVO;
import com.base.admin.domain.vo.UserInfoVO;

public interface SysLoginService {

    LoginVO login(LoginDTO dto);

    UserInfoVO getUserInfo(Long userId);
}
