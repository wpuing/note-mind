package com.notemind.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * 启动时校验 JWT / AI Engine Token / AI Engine URL：默认值仅在 NOTEMIND_ALLOW_WEAK_DEFAULTS=true 时允许。
 */
@Component
public class DevSecretGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSecretGuard.class);

    private static final String DEFAULT_JWT = "local-dev-only-change-me-32chars!!";
    private static final String DEFAULT_AI_TOKEN = "local-dev-ai-engine-token";

    private final Environment environment;
    private final String jwtSecret;
    private final String aiEngineToken;
    private final String aiEngineBaseUrl;
    private final String apiKeySecret;
    private final boolean allowWeakDefaults;

    public DevSecretGuard(
            Environment environment,
            @Value("${notemind.jwt.secret:}") String jwtSecret,
            @Value("${notemind.ai-engine.token:}") String aiEngineToken,
            @Value("${notemind.ai-engine.base-url:}") String aiEngineBaseUrl,
            @Value("${notemind.api-key-secret:}") String apiKeySecret,
            @Value("${NOTEMIND_ALLOW_WEAK_DEFAULTS:false}") boolean allowWeakDefaults) {
        this.environment = environment;
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret;
        this.aiEngineToken = aiEngineToken == null ? "" : aiEngineToken;
        this.aiEngineBaseUrl = aiEngineBaseUrl == null ? "" : aiEngineBaseUrl;
        this.apiKeySecret = apiKeySecret == null ? "" : apiKeySecret;
        this.allowWeakDefaults = allowWeakDefaults;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prodLike = false;
        for (String p : environment.getActiveProfiles()) {
            String n = p == null ? "" : p.trim().toLowerCase();
            if ("prod".equals(n) || "production".equals(n) || "staging".equals(n)) {
                prodLike = true;
                break;
            }
        }
        boolean badJwt = jwtSecret.isBlank()
                || jwtSecret.equals(DEFAULT_JWT)
                || jwtSecret.length() < 32;
        boolean badAi = aiEngineToken.isBlank() || aiEngineToken.equals(DEFAULT_AI_TOKEN);
        boolean apiKeyIsDefault =
                apiKeySecret.isBlank() || apiKeySecret.equals(DEFAULT_JWT);
        String adminPwd = environment.getProperty("DEMO_ADMIN_PASSWORD", "changeme");
        String demoPwd = environment.getProperty("DEMO_PASSWORD", "changeme");
        boolean weakSeedPwd = "changeme".equals(adminPwd) || "changeme".equals(demoPwd);
        String dbPassword = environment.getProperty(
                "spring.datasource.password",
                environment.getProperty("DB_PASSWORD", "root"));
        boolean weakDbPwd = dbPassword == null
                || dbPassword.isBlank()
                || "root".equals(dbPassword)
                || "changeme".equals(dbPassword);

        if (prodLike && allowWeakDefaults) {
            throw new IllegalStateException(
                    "生产/预发禁止 NOTEMIND_ALLOW_WEAK_DEFAULTS=true");
        }
        if ((badJwt || badAi) && (prodLike || !allowWeakDefaults)) {
            throw new IllegalStateException(
                    "必须设置 JWT_SECRET（≥32，非默认）与 AI_ENGINE_TOKEN（非默认）。"
                            + "本地开发请显式设置 NOTEMIND_ALLOW_WEAK_DEFAULTS=true");
        }
        if (weakSeedPwd && (prodLike || !allowWeakDefaults)) {
            throw new IllegalStateException(
                    "必须修改 DEMO_ADMIN_PASSWORD / DEMO_PASSWORD，禁止使用内置弱口令。"
                            + "本地开发请显式设置 NOTEMIND_ALLOW_WEAK_DEFAULTS=true");
        }
        if (weakDbPwd && (prodLike || !allowWeakDefaults)) {
            throw new IllegalStateException(
                    "必须设置非默认数据库口令（禁止 root/changeme）。"
                            + "本地开发请显式设置 NOTEMIND_ALLOW_WEAK_DEFAULTS=true");
        }
        validateAiEngineBaseUrl();
        if (!prodLike && allowWeakDefaults && (badJwt || badAi || weakSeedPwd)) {
            log.warn(
                    "NOTEMIND_ALLOW_WEAK_DEFAULTS=true：正在使用本地默认密钥/演示口令，切勿用于公网");
        }
        if (apiKeyIsDefault) {
            if (prodLike || !allowWeakDefaults) {
                throw new IllegalStateException(
                        "请设置 NOTEMIND_API_KEY_SECRET（≥32，非内置默认），用于加密模型 API Key");
            }
            log.warn("模型 API Key 加密材料为默认值；生产请设置独立的 NOTEMIND_API_KEY_SECRET");
        }
        if (weakDbPwd && allowWeakDefaults) {
            log.warn("数据库口令为弱默认（root/changeme）；切勿用于公网");
        }
    }

    /**
     * AI Engine 基址：本机可用 http；非本机必须 https，避免 Token/api_key 明文出站。
     */
    private void validateAiEngineBaseUrl() {
        String raw = aiEngineBaseUrl.isBlank() ? "http://127.0.0.1:8000" : aiEngineBaseUrl.trim();
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("AI_ENGINE_URL 非法: " + raw);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank()) {
            throw new IllegalStateException("AI_ENGINE_URL 缺少主机名");
        }
        boolean local = "127.0.0.1".equals(host)
                || "localhost".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
        if ("https".equals(scheme)) {
            return;
        }
        if ("http".equals(scheme) && local) {
            return;
        }
        throw new IllegalStateException(
                "AI_ENGINE_URL 非本机时必须使用 https（禁止明文 http 传输 Token/API Key）: " + raw);
    }
}
