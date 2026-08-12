package com.nancheung.plugins.jetbrains.legadoreader.event;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 离线缓存事件（不可变）
 * <p>
 * 用于通知 UI 缓存任务的进度与状态变更：
 * <ul>
 *   <li>{@link CacheEventType#STARTED}：缓存任务已启动</li>
 *   <li>{@link CacheEventType#PROGRESS}：单章缓存完成，进度更新</li>
 *   <li>{@link CacheEventType#COMPLETED}：整本书缓存完成</li>
 *   <li>{@link CacheEventType#FAILED}：缓存任务失败（携带错误信息）</li>
 *   <li>{@link CacheEventType#CANCELED}：缓存任务被取消</li>
 * </ul>
 *
 * @param eventId         事件唯一 ID
 * @param timestamp       事件时间戳
 * @param commandId       关联的指令 ID
 * @param type            事件类型
 * @param bookUrl         书籍 URL
 * @param bookName        书名（用于 UI 展示）
 * @param totalChapters   总章节数
 * @param cachedChapters  已缓存章节数
 * @param message         附加消息（如错误描述）
 * @author NanCheung
 */
public record CacheEvent(
        String eventId,
        long timestamp,
        @Nullable String commandId,
        CacheEventType type,
        @Nullable String bookUrl,
        @Nullable String bookName,
        int totalChapters,
        int cachedChapters,
        @Nullable String message
) implements ReaderEvent {

    /**
     * 缓存事件类型
     */
    public enum CacheEventType {
        /**
         * 缓存任务已启动
         */
        STARTED,

        /**
         * 进度更新（单章缓存完成）
         */
        PROGRESS,

        /**
         * 整本书缓存完成
         */
        COMPLETED,

        /**
         * 缓存任务失败
         */
        FAILED,

        /**
         * 缓存任务被取消
         */
        CANCELED
    }

    /**
     * 创建"缓存开始"事件
     */
    public static CacheEvent started(@Nullable String commandId, String bookUrl, String bookName, int totalChapters) {
        return new CacheEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                commandId,
                CacheEventType.STARTED,
                bookUrl,
                bookName,
                totalChapters,
                0,
                null
        );
    }

    /**
     * 创建"进度更新"事件
     */
    public static CacheEvent progress(@Nullable String commandId, String bookUrl, String bookName,
                                     int totalChapters, int cachedChapters) {
        return new CacheEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                commandId,
                CacheEventType.PROGRESS,
                bookUrl,
                bookName,
                totalChapters,
                cachedChapters,
                null
        );
    }

    /**
     * 创建"缓存完成"事件
     */
    public static CacheEvent completed(@Nullable String commandId, String bookUrl, String bookName, int totalChapters) {
        return new CacheEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                commandId,
                CacheEventType.COMPLETED,
                bookUrl,
                bookName,
                totalChapters,
                totalChapters,
                null
        );
    }

    /**
     * 创建"缓存失败"事件
     */
    public static CacheEvent failed(@Nullable String commandId, String bookUrl, String bookName,
                                    int totalChapters, int cachedChapters, String message) {
        return new CacheEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                commandId,
                CacheEventType.FAILED,
                bookUrl,
                bookName,
                totalChapters,
                cachedChapters,
                message
        );
    }

    /**
     * 创建"缓存取消"事件
     */
    public static CacheEvent canceled(@Nullable String commandId, String bookUrl, String bookName,
                                      int totalChapters, int cachedChapters) {
        return new CacheEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                commandId,
                CacheEventType.CANCELED,
                bookUrl,
                bookName,
                totalChapters,
                cachedChapters,
                null
        );
    }

    /**
     * 计算进度百分比（0-100）
     */
    public int progressPercent() {
        if (totalChapters <= 0) {
            return 0;
        }
        return Math.min(100, (int) ((cachedChapters * 100L) / totalChapters));
    }
}
