package com.nancheung.plugins.jetbrains.legadoreader.command.handler;

import com.nancheung.plugins.jetbrains.legadoreader.api.ApiUtil;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.CacheBookPayload;
import com.nancheung.plugins.jetbrains.legadoreader.event.EventPublisher;
import com.nancheung.plugins.jetbrains.legadoreader.event.ReadingEvent;
import com.nancheung.plugins.jetbrains.legadoreader.service.OfflineCacheService;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 离线缓存指令处理器
 * <p>
 * 启动后台任务，异步下载整本书的所有章节并 AES 加密落盘。
 * 缓存任务在独立线程中执行，不影响当前阅读会话。
 * 进度通过 {@link com.nancheung.plugins.jetbrains.legadoreader.event.CacheEvent} 实时发布。
 *
 * @author NanCheung
 */
@Slf4j
public class CacheBookHandler implements CommandHandler<CacheBookPayload> {

    @Override
    public CommandType supportedType() {
        return CommandType.CACHE_BOOK;
    }

    @Override
    public void handle(Command command) {
        // 1. 参数校验
        if (!(command.payload() instanceof CacheBookPayload(BookDTO book, List<BookChapterDTO> chapters))) {
            log.warn("离线缓存参数类型错误");
            return;
        }

        if (book == null) {
            log.warn("离线缓存参数 book 为空");
            return;
        }

        // 2. 检查缓存是否启用
        PluginSettingsStorage.State state = PluginSettingsStorage.getInstance().getState();
        if (!Boolean.TRUE.equals(state.cacheEnabled)) {
            log.info("离线缓存未启用，跳过");
            EventPublisher.getInstance().publish(ReadingEvent.chapterLoadFailed(
                    command.id(), book, new BookChapterDTO(),
                    new IllegalStateException("离线缓存未启用，请在设置中开启"),
                    ReadingEvent.Direction.JUMP
            ));
            return;
        }

        // 3. 异步获取章节列表（如果未传入）
        OfflineCacheService cacheService = OfflineCacheService.getInstance();

        if (chapters != null && !chapters.isEmpty()) {
            log.info("启动离线缓存：book={}, chapters={}", book.getName(), chapters.size());
            cacheService.cacheBookAsync(book, chapters, command.id());
            return;
        }

        // 章节列表为空，先异步获取
        CompletableFuture.runAsync(() -> {
            try {
                List<BookChapterDTO> fetched = ApiUtil.getChapterList(book.getBookUrl());
                if (fetched == null || fetched.isEmpty()) {
                    log.warn("获取章节列表失败，无法缓存：book={}", book.getName());
                    return;
                }
                log.info("启动离线缓存：book={}, chapters={}", book.getName(), fetched.size());
                cacheService.cacheBookAsync(book, fetched, command.id());
            } catch (Exception e) {
                log.error("获取章节列表失败：book={}", book.getName(), e);
            }
        });
    }
}
