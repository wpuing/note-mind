package com.notemind.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 签发与验签：HMAC-SHA，密钥至少 32 字节。
 */
@Component
public class JwtTokenProvider {

    /** HMAC 签名密钥 */
    private final SecretKey key;
    /** 与签名相同的密钥材料，用于密码指纹 */
    private final byte[] secretBytes;
    /** Token 有效期（秒） */
    private final long expireSeconds;

    /**
     * 从配置构建签名密钥；密钥不足 32 字节时拒绝启动。
     *
     * @param secret        JWT 密钥材料
     * @param expireSeconds 过期秒数，默认 86400
     */
    public JwtTokenProvider(
            @Value("${notemind.jwt.secret:local-dev-only-change-me-32chars!!}") String secret,
            @Value("${notemind.jwt.expire-seconds:86400}") long expireSeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥足够长
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.secretBytes = bytes;
        this.expireSeconds = expireSeconds;
    }

    /**
     * 签发访问 Token：subject=userId，claims 含 username 与 role。
     *
     * @param userId   用户 ID
     * @param username 登录名
     * @param role     角色（如 ADMIN）
     * @return 紧凑 JWT 字符串
     */
    public String createToken(String userId, String username, String role) {
        return createToken(userId, username, role, null);
    }

    /**
     * @param passwordFingerprint 密码哈希指纹（改密后旧 Token 失效）；可空则仅依赖过期
     */
    public String createToken(String userId, String username, String role, String passwordFingerprint) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("username", username);
        claims.put("role", role);
        if (passwordFingerprint != null && !passwordFingerprint.isBlank()) {
            claims.put("pwdFp", passwordFingerprint);
        }
        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 密码指纹：HMAC(JWT secret, passwordHash) 截断，避免 JWT 泄露 BCrypt 盐前缀。
     */
    public String passwordFingerprint(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] out = mac.doFinal(passwordHash.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out).substring(0, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("password fingerprint failed", ex);
        }
    }

    /**
     * 验签并解析 Claims；非法或过期抛异常。
     *
     * @param token JWT 字符串
     * @return Claims 载荷
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取配置的过期秒数（供登录响应返回）。
     *
     * @return 过期秒数
     */
    public long getExpireSeconds() {
        return expireSeconds;
    }
}
