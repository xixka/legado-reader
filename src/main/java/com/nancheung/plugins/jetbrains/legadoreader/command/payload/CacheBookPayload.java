package com.nancheung.plugins.jetbrains.legadoreader.command.payload;

import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 离线缓存书籍参数
 *
 * @param book     要缓存的书籍
 * @param chapters 章节列表（可空，空时由 handler 内部获取）
 * @author NanCheung
 */
public record CacheBookPayload(
        BookDTO book,
        @Nullable List<BookChapterDTO> chapters
) implements CommandPayload {
}
