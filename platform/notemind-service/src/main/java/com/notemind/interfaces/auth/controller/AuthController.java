package com.notemind.interfaces.auth.controller;

import com.notemind.application.service.auth.AuthAsvc;
import com.notemind.common.result.Result;
import com.notemind.interfaces.auth.vo.ChangePasswordRequest;
import com.notemind.interfaces.auth.vo.LoginRequest;
import com.notemind.interfaces.auth.vo.LoginResponse;
import com.notemind.interfaces.auth.vo.ProfileUpdateRequest;
import com.notemind.interfaces.auth.vo.UserProfileVo;
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

    /** 认证应用服务，负责校验账号与签发/更新用户资料。 */
    private final AuthAsvc authAsvc;

    /**
     * 构造注入认证服务。
     *
     * @param authAsvc 认证应用服务
     */
    public AuthController(AuthAsvc authAsvc) {
        this.authAsvc = authAsvc;
    }

    /**
     * 用户登录，校验账号密码并返回 JWT。
     *
     * @param request 登录请求（用户名、密码）
     * @return 含 Token 与用户信息的统一响应
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        // 委托认证服务完成登录校验与 Token 签发
        return Result.ok(authAsvc.login(request));
    }

    /**
     * 获取当前登录用户资料。
     *
     * @return 当前用户个人资料
     */
    @GetMapping("/me")
    public Result<UserProfileVo> me() {
        // 从安全上下文解析当前用户并返回资料
        return Result.ok(authAsvc.me());
    }

    /**
     * 更新当前用户个人资料（如昵称）。
     *
     * @param body 资料更新请求
     * @return 更新后的个人资料
     */
    @PutMapping("/profile")
    public Result<UserProfileVo> updateProfile(@RequestBody ProfileUpdateRequest body) {
        // 委托认证服务持久化昵称等资料字段
        return Result.ok(authAsvc.updateProfile(body));
    }

    /**
     * 修改当前用户密码；成功后需重新登录。
     *
     * @param body 改密请求（旧密码、新密码）
     * @return 空成功响应
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest body) {
        // 校验旧密码并用 BCrypt 写入新密码
        authAsvc.changePassword(body);
        return Result.ok(null);
    }
}
