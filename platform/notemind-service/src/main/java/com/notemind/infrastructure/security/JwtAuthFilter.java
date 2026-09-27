package com.notemind.infrastructure.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JWT 鉴权过滤器：从 Authorization Bearer 解析 Token，写入 SecurityContext。
 * <p>解析失败仅清空上下文，不直接 401（由后续安全链决定）。
 * <p>额外校验库内 status/role/密码指纹：停用立即失效，改密后旧 Token 失效，角色以降权为准。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, JdbcTemplate jdbcTemplate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwtTokenProvider.parse(token);
                String userId = claims.getSubject();
                String claimPwdFp = claims.get("pwdFp", String.class);
                String role = resolveLiveRole(userId, String.valueOf(claims.get("role")), claimPwdFp);
                if (role == null) {
                    SecurityContextHolder.clearContext();
                } else {
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @return 库内有效角色；用户不存在/已删/停用/改密后旧 Token 返回 null
     */
    private String resolveLiveRole(String userId, String claimRole, String claimPwdFp) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT role, status, password FROM t_user
                    WHERE deleted = 0 AND id = ?
                    LIMIT 1
                    """,
                    userId.trim());
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            Object statusObj = row.get("status");
            int status = statusObj == null ? 1 : ((Number) statusObj).intValue();
            if (status == 0) {
                return null;
            }
            String liveFp = jwtTokenProvider.passwordFingerprint(
                    row.get("password") == null ? null : String.valueOf(row.get("password")));
            // 改密后旧 Token 失效；无指纹的旧 Token 也需重新登录
            if (!liveFp.isEmpty() && (claimPwdFp == null || !claimPwdFp.equals(liveFp))) {
                return null;
            }
            Object roleObj = row.get("role");
            if (roleObj != null && !String.valueOf(roleObj).isBlank()) {
                return String.valueOf(roleObj).trim().toUpperCase();
            }
            return claimRole == null || claimRole.isBlank() || "null".equals(claimRole)
                    ? "USER"
                    : claimRole.trim().toUpperCase();
        } catch (Exception ex) {
            return null;
        }
    }
}
