package com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.BitSet;

/**
 * 书籍缓存进度
 * <p>
 * 加密后持久化为 {@code progress.enc}，用于：
 * <ul>
 *   <li>记录已缓存章节（位图，支持乱序、断点续传）</li>
 *   <li>UI 进度展示</li>
 *   <li>恢复未完成的缓存任务</li>
 * </ul>
 *
 * @author NanCheung
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookCacheProgress {

    /**
     * 书籍 URL（缓存键）
     */
    @JsonProperty("bookUrl")
    private String bookUrl;

    /**
     * 总章节数
     */
    @JsonProperty("totalChapters")
    private int totalChapters;

    /**
     * 已缓存章节数（冗余字段，便于 UI 直接读取）
     */
    @JsonProperty("cachedChapters")
    private int cachedChapters;

    /**
     * 章节缓存位图（Base64，每 bit 对应一章是否已缓存）
     */
    @JsonProperty("cachedBitmap")
    private String cachedBitmap;

    /**
     * 最后缓存时间（时间戳）
     */
    @JsonProperty("lastCacheTime")
    private long lastCacheTime;

    /**
     * 缓存状态：INCOMPLETE / COMPLETE
     */
    @JsonProperty("status")
    private String status;

    public static final String STATUS_INCOMPLETE = "INCOMPLETE";
    public static final String STATUS_COMPLETE = "COMPLETE";

    /**
     * 将 BitSet 序列化为 Base64 字符串
     */
    public static String bitmapToBase64(BitSet bitSet) {
        return java.util.Base64.getEncoder().encodeToString(bitSet.toByteArray());
    }

    /**
     * 从 Base64 字符串反序列化为 BitSet
     */
    public static BitSet base64ToBitmap(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new BitSet();
        }
        return BitSet.valueOf(java.util.Base64.getDecoder().decode(base64));
    }
}
