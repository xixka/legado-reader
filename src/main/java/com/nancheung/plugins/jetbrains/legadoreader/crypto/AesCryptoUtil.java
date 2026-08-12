package com.nancheung.plugins.jetbrains.legadoreader.crypto;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * AES 加解密工具
 * <p>
 * 算法：AES/CBC/PKCS5Padding，密钥长度 256 位（32 字节），IV 长度 16 字节。
 * <p>
 * 存储格式：
 * <ul>
 *   <li>二进制模式：{@code [16字节 IV][密文]}</li>
 *   <li>文本模式：Base64(IV + 密文)</li>
 * </ul>
 * IV 每次加密随机生成，避免相同明文产生相同密文。
 *
 * @author NanCheung
 */
@Slf4j
@UtilityClass
public class AesCryptoUtil {

    /**
     * 算法/模式/填充
     */
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    /**
     * 密钥算法
     */
    private static final String ALGORITHM = "AES";

    /**
     * IV 长度（字节）
     */
    private static final int IV_LENGTH = 16;

    /**
     * 密钥长度（字节），AES-256
     */
    public static final int KEY_LENGTH = 32;

    /**
     * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ==================== 密钥工具 ====================

    /**
     * 生成随机 256 位 AES 密钥
     *
     * @return 32 字节密钥
     */
    public static byte[] generateKey() {
        byte[] key = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    /**
     * 将密钥字节数组编码为 Base64 字符串（便于持久化）
     *
     * @param key 密钥字节数组
     * @return Base64 字符串
     */
    public static String encodeKey(byte[] key) {
        Objects.requireNonNull(key, "密钥不能为空");
        if (key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("密钥长度必须为 " + KEY_LENGTH + " 字节，当前：" + key.length);
        }
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 将 Base64 字符串密钥解码为字节数组
     *
     * @param keyStr Base64 字符串
     * @return 密钥字节数组
     */
    public static byte[] decodeKey(String keyStr) {
        Objects.requireNonNull(keyStr, "密钥不能为空");
        byte[] key = Base64.getDecoder().decode(keyStr);
        if (key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("密钥长度必须为 " + KEY_LENGTH + " 字节，当前：" + key.length);
        }
        return key;
    }

    // ==================== 二进制加解密 ====================

    /**
     * 加密：返回 {@code [16字节 IV][密文]}
     *
     * @param data 明文
     * @param key  密钥
     * @return IV + 密文
     */
    public static byte[] encrypt(byte[] data, byte[] key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new IvParameterSpec(iv));

            byte[] cipherText = cipher.doFinal(data);

            // 拼接 IV + 密文
            byte[] output = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, output, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, output, IV_LENGTH, cipherText.length);

            return output;
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * 解密：输入为 {@code [16字节 IV][密文]}
     *
     * @param encrypted IV + 密文
     * @param key      密钥
     * @return 明文
     */
    public static byte[] decrypt(byte[] encrypted, byte[] key) {
        if (encrypted == null || encrypted.length <= IV_LENGTH) {
            throw new IllegalArgumentException("密文数据无效：长度必须大于 " + IV_LENGTH);
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[encrypted.length - IV_LENGTH];
            System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH);
            System.arraycopy(encrypted, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new IvParameterSpec(iv));

            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    // ==================== 字符串便捷方法 ====================

    /**
     * 加密字符串（UTF-8）为二进制
     *
     * @param plainText 明文
     * @param key       密钥
     * @return IV + 密文
     */
    public static byte[] encryptString(String plainText, byte[] key) {
        Objects.requireNonNull(plainText, "明文不能为空");
        return encrypt(plainText.getBytes(StandardCharsets.UTF_8), key);
    }

    /**
     * 解密二进制为字符串（UTF-8）
     *
     * @param encrypted IV + 密文
     * @param key       密钥
     * @return 明文字符串
     */
    public static String decryptToString(byte[] encrypted, byte[] key) {
        return new String(decrypt(encrypted, key), StandardCharsets.UTF_8);
    }

    /**
     * 加密字符串为 Base64 字符串
     *
     * @param plainText 明文
     * @param key       密钥
     * @return Base64(IV + 密文)
     */
    public static String encryptStringToBase64(String plainText, byte[] key) {
        return Base64.getEncoder().encodeToString(encryptString(plainText, key));
    }

    /**
     * 从 Base64 字符串解密
     *
     * @param base64 Base64(IV + 密文)
     * @param key    密钥
     * @return 明文字符串
     */
    public static String decryptBase64ToString(String base64, byte[] key) {
        Objects.requireNonNull(base64, "密文不能为空");
        return decryptToString(Base64.getDecoder().decode(base64), key);
    }
}
