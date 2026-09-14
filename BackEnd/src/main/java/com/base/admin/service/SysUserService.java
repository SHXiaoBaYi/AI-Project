package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.UserDTO;
import com.base.admin.domain.dto.UserPageQueryDTO;
import com.base.admin.domain.dto.UserRoleDTO;
import com.base.admin.domain.vo.UserImportResultVO;
import com.base.admin.domain.vo.UserVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface SysUserService {

    PageResult<UserVO> list(UserPageQueryDTO query);

    UserVO getById(Long userId);

    void create(UserDTO dto);

    void update(UserDTO dto);

    void delete(Long userId);

    void deleteBatch(List<Long> ids);

    void resetPwd(Long userId, String password);

    void changeStatus(Long userId, Integer status);

    void assignRoles(UserRoleDTO dto);

    UserImportResultVO importUsers(MultipartFile file);

    void exportUsers(List<Long> ids, HttpServletResponse response) throws IOException;
}
