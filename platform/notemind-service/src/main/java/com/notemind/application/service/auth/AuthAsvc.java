package com.notemind.application.service.auth;

import com.notemind.common.util.DateTimes;
import com.notemind.infrastructure.security.JwtTokenProvider;
import com.notemind.infrastructure.security.LoginRateLimiter;
import com.notemind.interfaces.auth.vo.ChangePasswordRequest;
import com.notemind.interfaces.auth.vo.LoginRequest;
import com.notemind.interfaces.auth.vo.LoginResponse;
import com.notemind.interfaces.auth.vo.ProfileUpdateRequest;
import com.notemind.interfaces.auth.vo.UserProfileVo;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 认证与个人资料应用服务：登录发 JWT、种子用户、资料与改密。
 */

@Service
public class AuthAsvc {

    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;
    private final String adminUsername;
    private final String adminPassword;
    private final String demoUsername;
    private final String demoPassword;

    /**
     * 注入 JWT、JDBC、密码编码器、登录限流及演示账号配置。
     */
    public AuthAsvc(
            JwtTokenProvider jwtTokenProvider,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            LoginRateLimiter loginRateLimiter,
            @Value("${DEMO_ADMIN_USERNAME:admin}") String adminUsername,
            @Value("${DEMO_ADMIN_PASSWORD:changeme}") String adminPassword,
            @Value("${DEMO_USERNAME:demo}") String demoUsername,
            @Value("${DEMO_PASSWORD:changeme}") String demoPassword) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.demoUsername = demoUsername;
        this.demoPassword = demoPassword;
    }

    /**
     * 启动时确保管理员与演示用户存在。
     */

    @PostConstruct
    public void ensureSeedUsers() {
        // 尝试执行
        try {
            upsertSeedUser("u_admin", adminUsername, adminPassword, "管理员", "ADMIN");
            upsertSeedUser("u_demo", demoUsername, demoPassword, "张三", "USER");
        } catch (Exception ignored) {
            /* 启动期库未就绪时跳过，首次登录会再尝试 */
        }
    }

    /**
     * 若不存在则插入种子用户。
     *
     * @param id          用户 ID
     * @param username    用户名
     * @param rawPassword 明文密码
     * @param nickname    昵称
     * @param role        角色
     */
    private void upsertSeedUser(
            String id, String username, String rawPassword, String nickname, String role) {
        // 用户名或密码缺失则跳过
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        String uname = username.trim();
        LocalDateTime now = LocalDateTime.now();
        // 已存在：不改密码、不强制启用（管理员停用后不得被种子逻辑拉起）
        Integer status = null;
        try {
            status = jdbcTemplate.queryForObject(
                    "SELECT status FROM t_user WHERE deleted = 0 AND username = ? LIMIT 1",
                    Integer.class,
                    uname);
        } catch (Exception ignored) {
            status = null;
        }
        if (status != null) {
            return;
        }
        // 插入种子用户（密码 BCrypt）
        jdbcTemplate.update(
                """
                INSERT INTO t_user (
                  id, create_time, update_time, deleted,
                  username, password, nickname, role, status
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, 1)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                uname,
                passwordEncoder.encode(rawPassword),
                nickname,
                role);
    }

    /**
     * 用户名密码登录，签发 JWT。
     *
     * @param req      登录请求
     * @param clientIp 客户端 IP（限流用）
     * @return 含 token 与用户摘要的响应
     */
    public LoginResponse login(LoginRequest req, String clientIp) {
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username/password required");
        }
        ensureSeedUsers();
        String username = req.getUsername().trim();
        String password = req.getPassword();
        loginRateLimiter.checkAllowed(clientIp, username);

        List<UserRow> rows = jdbcTemplate.query(
                """
                SELECT id, username, password, nickname, avatar_url, role, status
                FROM t_user
                WHERE deleted = 0 AND username = ?
                LIMIT 1
                """,
                userRowMapper(),
                username);
        if (rows.isEmpty()) {
            loginRateLimiter.recordFailure(clientIp, username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        UserRow user = rows.get(0);
        if (user.status != null && user.status == 0) {
            // 与错密统一 401，降低账号枚举；仍记失败限流
            loginRateLimiter.recordFailure(clientIp, username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            loginRateLimiter.recordFailure(clientIp, username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }

        loginRateLimiter.recordSuccess(clientIp, username);
        String token = jwtTokenProvider.createToken(
                user.id, user.username, user.role, jwtTokenProvider.passwordFingerprint(user.passwordHash));
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(token);
        resp.setExpiresIn(jwtTokenProvider.getExpireSeconds());
        resp.setUser(Map.of(
                "id", user.id,
                "username", user.username,
                "nickname", user.nickname == null ? user.username : user.nickname,
                "role", user.role,
                "avatarUrl", user.avatarUrl == null ? "" : user.avatarUrl));
        return resp;
    }

    /** 兼容旧调用：无 IP 时按 unknown 限流。 */
    public LoginResponse login(LoginRequest req) {
        return login(req, null);
    }

    /**
     * 获取当前登录用户资料。
     *
     * @return 个人资料 VO
     */
    public UserProfileVo me() {
        return getById(currentUserId());
    }

    /**
     * 更新当前用户昵称/头像。
     *
     * @param req 资料更新请求
     * @return 更新后的资料
     */

    @Transactional
    public UserProfileVo updateProfile(ProfileUpdateRequest req) {
        String userId = currentUserId();
        getById(userId);
        // 请求体不能为空
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        String nickname = req.getNickname() == null ? null : req.getNickname().trim();
        // 昵称传了空串则拒绝
        if (nickname != null && nickname.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称不能为空");
        }
        // 昵称长度校验
        if (nickname != null && nickname.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称最长 64 字");
        }
        String avatar = req.getAvatarUrl() == null ? null : req.getAvatarUrl().trim();
        // 头像 URL 长度校验
        if (avatar != null && avatar.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像地址过长");
        }
        // 至少修改一项
        if (nickname == null && avatar == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少修改一项");
        }
        // 更新昵称
        if (nickname != null) {
            // 写回昵称字段
            jdbcTemplate.update(
                    "UPDATE t_user SET nickname = ?, update_time = ? WHERE id = ? AND deleted = 0",
                    nickname,
                    Timestamp.valueOf(LocalDateTime.now()),
                    userId);
        }
        // 更新头像
        if (avatar != null) {
            // 写回头像 URL（空串清为 null）
            jdbcTemplate.update(
                    "UPDATE t_user SET avatar_url = ?, update_time = ? WHERE id = ? AND deleted = 0",
                    avatar.isEmpty() ? null : avatar,
                    Timestamp.valueOf(LocalDateTime.now()),
                    userId);
        }
        return getById(userId);
    }

    /**
     * 修改当前用户密码（需校验原密码）。
     *
     * @param req 改密请求
     */

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        // 原密码与新密码必填
        if (req == null || req.getOldPassword() == null || req.getNewPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写原密码与新密码");
        }
        String oldPassword = req.getOldPassword();
        String newPassword = req.getNewPassword();
        // 新密码长度校验
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码长度需 6～64 位");
        }
        // 禁止与原密码相同
        if (oldPassword.equals(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }
        String userId = currentUserId();
        // 查询当前用户含密码哈希
        List<UserRow> rows = jdbcTemplate.query(
                """
                SELECT id, username, password, nickname, avatar_url, role, status
                FROM t_user WHERE deleted = 0 AND id = ? LIMIT 1
                """,
                userRowMapper(),
                userId);
        // 用户不存在
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        UserRow user = rows.get(0);
        // 校验原密码
        boolean ok = passwordEncoder.matches(oldPassword, user.passwordHash);
        // 原密码错误
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        // 写入新密码哈希
        jdbcTemplate.update(
                "UPDATE t_user SET password = ?, update_time = ? WHERE id = ? AND deleted = 0",
                // 编码新密码
                passwordEncoder.encode(newPassword),
                Timestamp.valueOf(LocalDateTime.now()),
                userId);
    }

    /**
     * 按 ID 查询用户资料。
     *
     * @param id 用户 ID
     * @return 资料 VO
     */
    private UserProfileVo getById(String id) {
        // 查询用户公开资料字段
        List<UserProfileVo> rows = jdbcTemplate.query(
                """
                SELECT id, username, nickname, avatar_url, role, status, create_time, update_time
                FROM t_user WHERE deleted = 0 AND id = ?
                """,
                profileMapper(),
                id);
        // 不存在则 404
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return rows.get(0);
    }

    /**
     * 从安全上下文解析当前用户 ID。
     *
     * @return 用户 ID
     */
    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // 未认证
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        String p = String.valueOf(auth.getPrincipal());
        // 匿名主体视为未登录
        if (p.isBlank() || "anonymousUser".equals(p)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return p;
    }

    /**
     * 登录校验用的用户行映射。
     *
     * @return RowMapper
     */
    private RowMapper<UserRow> userRowMapper() {
        return (rs, n) -> {
            UserRow u = new UserRow();
            u.id = rs.getString("id");
            u.username = rs.getString("username");
            u.passwordHash = rs.getString("password");
            u.nickname = rs.getString("nickname");
            u.avatarUrl = rs.getString("avatar_url");
            u.role = rs.getString("role");
            u.status = rs.getObject("status") == null ? 1 : rs.getInt("status");
            return u;
        };
    }

    /**
     * 个人资料 VO 行映射。
     *
     * @return RowMapper
     */
    private RowMapper<UserProfileVo> profileMapper() {
        return (rs, n) -> {
            UserProfileVo vo = new UserProfileVo();
            vo.setId(rs.getString("id"));
            vo.setUsername(rs.getString("username"));
            vo.setNickname(rs.getString("nickname"));
            vo.setAvatarUrl(rs.getString("avatar_url"));
            vo.setRole(rs.getString("role"));
            vo.setStatus(rs.getObject("status") == null ? 1 : rs.getInt("status"));
            Timestamp ct = rs.getTimestamp("create_time");
            Timestamp ut = rs.getTimestamp("update_time");
            vo.setCreateTime(ct == null ? null : DateTimes.format(ct.toLocalDateTime()));
            vo.setUpdateTime(ut == null ? null : DateTimes.format(ut.toLocalDateTime()));
            return vo;
        };
    }

    /** 登录查询用的内部用户行。 */
    private static class UserRow {
        String id;
        String username;
        String passwordHash;
        String nickname;
        String avatarUrl;
        String role;
        Integer status;
    }
}

