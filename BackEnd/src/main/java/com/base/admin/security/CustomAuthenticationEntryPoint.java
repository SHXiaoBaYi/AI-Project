package com.base.admin.security;

import com.base.admin.common.Constants;
import com.base.admin.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String header = request.getHeader(Constants.TOKEN_HEADER);
        boolean hadToken = StringUtils.hasText(header) && header.startsWith(Constants.TOKEN_PREFIX);
        if (hadToken) {
            // 带了 token 但仍未认证：通常是 JWT 过期或无效
            objectMapper.writeValue(response.getWriter(),
                    Result.fail(Constants.CODE_SESSION_EXPIRED, "登录已超过12小时，请重新扫码登录"));
            return;
        }
        objectMapper.writeValue(response.getWriter(), Result.fail(401, "未授权"));
    }
}
