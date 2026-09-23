package com.base.admin.security;

import com.base.admin.common.Constants;
import com.base.admin.common.Result;
import com.base.admin.service.OnlineSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final OnlineSessionService onlineSessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            Long userId = jwtUtils.getUserIdFromToken(token);
            String tokenId = jwtUtils.getTokenId(token);

            // 旧 token 无 jti、已超时、或会话已被替换：强制重新登录
            OnlineSessionService.TokenStatus status = onlineSessionService.checkToken(userId, tokenId);
            if (status != OnlineSessionService.TokenStatus.ACTIVE) {
                writeSessionInvalid(response, status);
                return;
            }

            String username = jwtUtils.parseToken(token).get("username", String.class);
            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(userId);
            loginUser.setUsername(username);
            loginUser.setPermissions(Set.of(Constants.ADMIN_PERM));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private void writeSessionInvalid(HttpServletResponse response, OnlineSessionService.TokenStatus status)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        int code;
        String msg;
        if (status == OnlineSessionService.TokenStatus.EXPIRED) {
            code = Constants.CODE_SESSION_EXPIRED;
            msg = "登录已超过12小时，请重新扫码登录";
        } else if (status == OnlineSessionService.TokenStatus.REPLACED) {
            code = Constants.CODE_SESSION_KICKED;
            msg = "该账号已在其他设备登录，您已被强制下线";
        } else {
            code = Constants.CODE_SESSION_EXPIRED;
            msg = "登录已失效，请重新扫码登录";
        }
        objectMapper.writeValue(response.getWriter(), Result.fail(code, msg));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(Constants.TOKEN_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(Constants.TOKEN_PREFIX)) {
            return header.substring(Constants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
