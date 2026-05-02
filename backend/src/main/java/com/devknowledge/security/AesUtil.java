package com.devknowledge.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 工具类，用于加密/解密用户的 API Key
 * 使用 256 位密钥 + 96 位 IV + 128 位 Tag
 */
public class AesUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final SecretKeySpec keySpec;

    /**
     * @param secret 密钥字符串（任意长度，内部通过 SHA-256 派生出 32 字节密钥）
     */
    public AesUtil(String secret) {
        try {
            // SHA-256 输出正好 32 字节，满足 AES-256 要求
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.keySpec = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new RuntimeException("初始化 AES 密钥失败", e);
        }
    }

    /**
     * 加密明文，返回 Base64 编码的密文（IV + 密文拼接）
     *
     * @param plaintext 明文
     * @return Base64 编码的密文
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV 拼接密文后 Base64 编码
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("API Key 加密失败", e);
        }
    }

    /**
     * 解密 Base64 编码的密文
     *
     * @param ciphertext Base64 编码的密文（IV + 密文）
     * @return 解密后的明文
     */
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            byte[] encrypted = new byte[combined.length - IV_LENGTH_BYTE];
            System.arraycopy(combined, IV_LENGTH_BYTE, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("API Key 解密失败", e);
        }
    }

    /**
     * 脱敏 API Key，只显示前 4 位和后 4 位
     * 示例：sk-1234567890abcdef → sk-12****cdef
     *
     * @param apiKey 明文 API Key
     * @return 脱敏后的字符串
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
