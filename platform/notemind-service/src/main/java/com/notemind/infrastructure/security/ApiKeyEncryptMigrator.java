package com.notemind.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 启动时把历史明文 api_key_enc 升级为 AES 密文（已是 enc:v1: 的跳过）。
 */
@Component
public class ApiKeyEncryptMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyEncryptMigrator.class);

    /** 查询/更新模型配置表 */
    private final JdbcTemplate jdbcTemplate;
    /** API Key 加解密组件 */
    private final ApiKeyCrypto apiKeyCrypto;

    /**
     * 注入 JDBC 与加密组件。
     *
     * @param jdbcTemplate JDBC 模板
     * @param apiKeyCrypto 密钥加解密
     */
    public ApiKeyEncryptMigrator(JdbcTemplate jdbcTemplate, ApiKeyCrypto apiKeyCrypto) {
        this.jdbcTemplate = jdbcTemplate;
        this.apiKeyCrypto = apiKeyCrypto;
    }

    /**
     * 扫描 {@code t_ai_model_config} 中非空 api_key_enc，明文则加密回写。
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        // 整体 try：迁移失败只 warn，不阻断启动
        try {
            // 通过 JdbcTemplate 拉取待检查行
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT id, api_key_enc FROM t_ai_model_config
                    WHERE deleted = 0 AND api_key_enc IS NOT NULL AND api_key_enc <> ''
                    """);
            int n = 0;
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            // 逐行判断是否需加密
            for (Map<String, Object> row : rows) {
                String id = String.valueOf(row.get("id"));
                String stored = row.get("api_key_enc") == null ? null : String.valueOf(row.get("api_key_enc"));
                // 空值或已是密文则跳过
                if (stored == null || stored.isBlank() || apiKeyCrypto.looksEncrypted(stored)) {
                    continue;
                }
                // 依赖 ApiKeyCrypto 加密明文
                String enc = apiKeyCrypto.encryptForStorage(stored);
                // 通过 JdbcTemplate 回写密文
                jdbcTemplate.update(
                        "UPDATE t_ai_model_config SET api_key_enc = ?, update_time = ? WHERE id = ? AND deleted = 0",
                        enc,
                        now,
                        id);
                n++;
            }
            // 有迁移才打 info
            if (n > 0) {
                log.info("migrated {} model api keys to AES-GCM ciphertext", n);
            }
        // 表不存在或 SQL 异常时跳过
        } catch (Exception e) {
            log.warn("api key encrypt migration skipped: {}", e.getMessage());
        }
    }
}
