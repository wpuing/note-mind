package com.notemind.infrastructure.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器：从 Authorization Bearer 解析 Token，写入 SecurityContext。
 * <p>解析失败仅清空上下文，不直接 401（由后续安全链决定）。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** JWT 签发与解析组件 */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 注入 Token 提供者。
     *
     * @param jwtTokenProvider JWT 工具
     */
    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 若存在 Bearer Token 则解析并设置认证；最后放行过滤器链。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        // 仅处理 Bearer 方案
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            // 解析 Token 并写入上下文
            try {
                // 依赖 JwtTokenProvider 验签解析
                Claims claims = jwtTokenProvider.parse(token);
                String userId = claims.getSubject();
                String role = String.valueOf(claims.get("role"));
                var auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            // Token 非法/过期则清空认证
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
