package com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto;

import lombok.Data;

/**
 * 本地阅读进度（AES 加密落盘，每本书一个独立小文件）
 * <p>
 * 用于服务器离线（"离线"模式）时保存/恢复阅读位置：
 * 离线模式下进度无法同步到 legado 服务器，若不落地本地，
 * 重开书籍会退回缓存快照中的旧章节。
 *
 * @author NanCheung
 */
@Data
public class LocalReadingProgress {

    /**
     * 书籍 URL
     */
    private String bookUrl;

    /**
     * 当前章节索引
     */
    private Integer durChapterIndex;

    /**
     * 当前章节标题
     */
    private String durChapterTitle;

    /**
     * 章节内位置（光标偏移）
     */
    private Integer durChapterPos;

    /**
     * 更新时间戳
     */
    private Long durChapterTime;
}
