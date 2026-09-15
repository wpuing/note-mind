package com.notemind.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 生产/非 local 环境禁止沿用内置默认 JWT / AI Engine Token。
 * <p>启动时校验：prod/production/staging 下若仍为默认值则直接失败；开发环境仅打 warn。
 */
@Component
public class DevSecretGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSecretGuard.class);

    /** 仓库内置默认 JWT（仅本地） */
    private static final String DEFAULT_JWT = "local-dev-only-change-me-32chars!!";
    /** 仓库内置默认 AI Engine Token（仅本地） */
    private static final String DEFAULT_AI_TOKEN = "local-dev-ai-engine-token";

    /** Spring 环境，用于读取 active profiles */
    private final Environment environment;
    /** JWT 密钥配置值 */
    private final String jwtSecret;
    /** AI 引擎 Token 配置值 */
    private final String aiEngineToken;

    /**
     * 注入环境与密钥相关配置。
     *
     * @param environment   Spring Environment
     * @param jwtSecret     {@code notemind.jwt.secret}
     * @param aiEngineToken {@code notemind.ai-engine.token}
     */
    public DevSecretGuard(
            Environment environment,
            @Value("${notemind.jwt.secret:}") String jwtSecret,
            @Value("${notemind.ai-engine.token:}") String aiEngineToken) {
        this.environment = environment;
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret;
        this.aiEngineToken = aiEngineToken == null ? "" : aiEngineToken;
    }

    /**
     * 启动后检查密钥：生产类 profile 强制拒绝默认值；其它环境警告。
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        boolean prodLike = false;
        // 遍历激活的 profile，识别生产/预发
        for (String p : environment.getActiveProfiles()) {
            String n = p == null ? "" : p.trim().toLowerCase();
            // prod / production / staging 视为生产类
            if ("prod".equals(n) || "production".equals(n) || "staging".equals(n)) {
                prodLike = true;
                break;
            }
        }
        boolean badJwt = jwtSecret.isBlank()
                || jwtSecret.equals(DEFAULT_JWT)
                || jwtSecret.length() < 32;
        boolean badAi = aiEngineToken.isBlank() || aiEngineToken.equals(DEFAULT_AI_TOKEN);

        // 生产类环境发现默认密钥则拒绝启动
        if (prodLike && (badJwt || badAi)) {
            throw new IllegalStateException(
                    "生产/预发环境必须设置 JWT_SECRET（≥32）与 AI_ENGINE_TOKEN，禁止使用内置默认值");
        }
        // 开发环境仅告警
        if (!prodLike && (badJwt || badAi)) {
            log.warn(
                    "当前使用本地默认 JWT/AI_ENGINE_TOKEN；仅限开发。上线前请设置 JWT_SECRET 与 AI_ENGINE_TOKEN，"
                            + "并启用 spring.profiles.active=prod");
        }
    }
}
