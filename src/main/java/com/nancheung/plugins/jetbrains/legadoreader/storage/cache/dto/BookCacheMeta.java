package com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 书籍缓存元数据
 * <p>
 * 加密后持久化为 {@code meta.enc}，用于：
 * <ul>
 *   <li>恢复书架（无需重新请求 API）</li>
 *   <li>校验缓存完整性</li>
 *   <li>提供章节列表</li>
 * </ul>
 *
 * @author NanCheung
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookCacheMeta {

    /**
     * 书籍 URL（缓存键，与 BookDTO.bookUrl 一致）
     */
    @JsonProperty("bookUrl")
    private String bookUrl;

    /**
     * 作者
     */
    @JsonProperty("author")
    private String author;

    /**
     * 书名
     */
    @JsonProperty("name")
    private String name;

    /**
     * 总章节数
     */
    @JsonProperty("totalChapters")
    private int totalChapters;

    /**
     * 缓存创建时间（时间戳）
     */
    @JsonProperty("cachedAt")
    private long cachedAt;

    /**
     * 章节目录列表
     */
    @JsonProperty("chapters")
    private List<BookChapterDTO> chapters;

    /**
     * 缓存时使用的书籍信息快照
     */
    @JsonProperty("bookSnapshot")
    private BookDTO bookSnapshot;

    /**
     * 缓存服务端地址（用于校验缓存可用性，离线时可不校验）
     */
    @JsonProperty("sourceAddress")
    private String sourceAddress;
}
