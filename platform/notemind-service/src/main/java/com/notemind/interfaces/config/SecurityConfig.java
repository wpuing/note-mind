package com.notemind.interfaces.config;

import com.notemind.infrastructure.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置：无状态 JWT、放行登录/探活、管理写接口需 ADMIN。
 */
@Configuration
public class SecurityConfig {

    /** 未登录或 Token 无效时返回的 JSON 体。 */
    private static final String UNAUTH_JSON =
            "{\"code\":401,\"message\":\"未登录或 Token 无效，请重新登录\",\"data\":null}";
    /** 已登录但非管理员访问受限资源时返回的 JSON 体。 */
    private static final String FORBIDDEN_JSON =
            "{\"code\":403,\"message\":\"无权限执行此操作（需要管理员）\",\"data\":null}";

    /**
     * 提供 BCrypt 密码编码器，供登录与改密使用。
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 构建安全过滤链：禁用 CSRF/会话、配置路径权限并挂载 JWT 过滤器。
     *
     * @param http          HttpSecurity 构建器
     * @param jwtAuthFilter JWT 认证过滤器
     * @return 配置完成的 SecurityFilterChain
     * @throws Exception 配置过程异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/ping").permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/doc.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 用户端：本人资料 + 问答会话；管理写接口仅 ADMIN
                        .requestMatchers(
                                "/api/v1/auth/me",
                                "/api/v1/auth/profile",
                                "/api/v1/auth/password").authenticated()
                        .requestMatchers("/api/v1/chat/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/qa-apps", "/api/v1/qa-apps/*")
                        .authenticated()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTH_JSON))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN_JSON)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 向客户端写入统一 JSON 错误响应。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码
     * @param body     JSON 字符串
     * @throws java.io.IOException 写出失败时抛出
     */
    private static void writeJson(HttpServletResponse response, int status, String body) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }
}
