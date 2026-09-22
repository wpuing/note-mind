package com.notemind.interfaces.auth.controller;

import com.notemind.application.service.auth.AuthAsvc;
import com.notemind.common.result.Result;
import com.notemind.interfaces.auth.vo.ChangePasswordRequest;
import com.notemind.interfaces.auth.vo.LoginRequest;
import com.notemind.interfaces.auth.vo.LoginResponse;
import com.notemind.interfaces.auth.vo.ProfileUpdateRequest;
import com.notemind.interfaces.auth.vo.UserProfileVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与个人资料接口：登录、当前用户、改昵称、改密码。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAsvc authAsvc;

    public AuthController(AuthAsvc authAsvc) {
        this.authAsvc = authAsvc;
    }

    /**
     * 用户登录，校验账号密码并返回 JWT（含 IP/用户名限流）。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return Result.ok(authAsvc.login(request, clientIp(http)));
    }

    @GetMapping("/me")
    public Result<UserProfileVo> me() {
        return Result.ok(authAsvc.me());
    }

    @PutMapping("/profile")
    public Result<UserProfileVo> updateProfile(@RequestBody ProfileUpdateRequest body) {
        return Result.ok(authAsvc.updateProfile(body));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest body) {
        authAsvc.changePassword(body);
        return Result.ok(null);
    }

    /**
     * 客户端 IP：优先信任网关写入的 X-Real-IP；本机直连用 remoteAddr。
     * 不信任客户端自带的 X-Forwarded-For（Gateway 已剥离并由过滤器重写）。
     */
    static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remote = request.getRemoteAddr();
        boolean fromLocalProxy = isLoopback(remote);
        if (fromLocalProxy) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                String t = realIp.trim();
                if (!t.isEmpty() && t.length() <= 64 && !isLoopback(t)) {
                    return t;
                }
                if (!t.isEmpty() && t.length() <= 64) {
                    return t;
                }
            }
        }
        return remote == null || remote.isBlank() ? "unknown" : remote.trim();
    }

    private static boolean isLoopback(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        String t = ip.trim();
        return "127.0.0.1".equals(t)
                || "localhost".equalsIgnoreCase(t)
                || "::1".equals(t)
                || "0:0:0:0:0:0:0:1".equals(t)
                || t.startsWith("::ffff:127.");
    }
}
