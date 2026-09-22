package com.notemind.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 模型 API Key 落库加密（AES-GCM）。
 * 密文格式：{@code enc:v1:<base64(iv || ciphertext+tag)>}；无前缀视为历史明文，读取时原样返回。
 */
@Component
public class ApiKeyCrypto {

    /** 密文版本前缀 */
    public static final String PREFIX = "enc:v1:";

    /** GCM IV 长度（字节） */
    private static final int IV_LEN = 12;
    /** GCM 认证标签位数 */
    private static final int TAG_BITS = 128;

    /** 派生得到的 AES 密钥 */
    private final SecretKey secretKey;
    /** 安全随机数，生成 IV */
    private final SecureRandom random = new SecureRandom();

    /**
     * 用配置材料派生 AES-256 密钥（SHA-256 摘要）。
     *
     * @param secretMaterial {@code notemind.api-key-secret}，缺省回落 JWT secret
     */
    public ApiKeyCrypto(
            @Value("${notemind.api-key-secret:${notemind.jwt.secret}}") String secretMaterial) {
        this.secretKey = deriveKey(secretMaterial == null ? "" : secretMaterial);
    }

    /**
     * 写入库：空白原样 null；已是密文不再二次加密；明文则 AES-GCM 加密并加前缀。
     *
     * @param plainOrNull 明文或已加密串（可空）
     * @return 可落库字符串，或 null
     */
    public String encryptForStorage(String plainOrNull) {
        // null 直接返回
        if (plainOrNull == null) {
            return null;
        }
        String plain = plainOrNull.trim();
        // 空白视为无密钥
        if (plain.isEmpty()) {
            return null;
        }
        // 已加密则幂等返回
        if (plain.startsWith(PREFIX)) {
            return plain;
        }
        // AES-GCM 加密
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buf.put(iv);
            buf.put(cipherBytes);
            return PREFIX + Base64.getEncoder().encodeToString(buf.array());
        // 加密失败包装
        } catch (Exception e) {
            throw new IllegalStateException("encrypt api key failed", e);
        }
    }

    /**
     * 读取调用：密文解密；历史明文原样返回。
     *
     * @param storedOrNull 库中字符串（可空）
     * @return 明文 API Key，或 null
     */
    public String decryptFromStorage(String storedOrNull) {
        // null 直接返回
        if (storedOrNull == null) {
            return null;
        }
        String stored = storedOrNull.trim();
        // 空白视为无密钥
        if (stored.isEmpty()) {
            return null;
        }
        // 无前缀视为历史明文
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        // AES-GCM 解密
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            // 长度必须大于 IV
            if (all.length <= IV_LEN) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            byte[] cipherBytes = new byte[all.length - IV_LEN];
            System.arraycopy(all, IV_LEN, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        // 解密失败包装
        } catch (Exception e) {
            throw new IllegalStateException("decrypt api key failed", e);
        }
    }

    /**
     * 粗判是否已是本组件密文格式。
     *
     * @param stored 库中字符串
     * @return true 表示以 enc:v1: 开头
     */
    public boolean looksEncrypted(String stored) {
        return stored != null && stored.trim().startsWith(PREFIX);
    }

    /**
     * 将配置材料经 SHA-256 派生为 AES 密钥。
     *
     * @param material 密钥材料
     * @return SecretKey
     */
    private static SecretKey deriveKey(String material) {
        // SHA-256 派生
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        // 算法不可用等
        } catch (Exception e) {
            throw new IllegalStateException("derive api-key secret failed", e);
        }
    }
}
