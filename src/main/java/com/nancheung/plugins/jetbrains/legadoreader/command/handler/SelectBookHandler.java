package com.nancheung.plugins.jetbrains.legadoreader.command.handler;

import com.nancheung.plugins.jetbrains.legadoreader.api.ApiUtil;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.SelectBookPayload;
import com.nancheung.plugins.jetbrains.legadoreader.common.PluginExecutors;
import com.nancheung.plugins.jetbrains.legadoreader.event.EventPublisher;
import com.nancheung.plugins.jetbrains.legadoreader.event.ReadingEvent;
import com.nancheung.plugins.jetbrains.legadoreader.manager.ReadingSessionManager;
import com.nancheung.plugins.jetbrains.legadoreader.model.ReadingSession;
import com.nancheung.plugins.jetbrains.legadoreader.model.ReadingSessionState;
import com.nancheung.plugins.jetbrains.legadoreader.service.OfflineCacheService;
import com.nancheung.plugins.jetbrains.legadoreader.service.ReadingSessionStateMachine;
import com.nancheung.plugins.jetbrains.legadoreader.storage.AddressHistoryStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.LocalReadingProgress;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 选择书籍指令处理器
 * 处理从书架选择书籍、跳转到指定章节的操作
 *
 * @author NanCheung
 */
@Slf4j
public class SelectBookHandler implements CommandHandler<SelectBookPayload> {

    @Override
    public CommandType supportedType() {
        return CommandType.SELECT_BOOK;
    }

    @Override
    public void handle(Command command) {
        EventPublisher publisher = EventPublisher.getInstance();
        ReadingSessionStateMachine stateMachine = ReadingSessionStateMachine.getInstance();

        // 1. 获取参数
        if (!(command.payload() instanceof SelectBookPayload(BookDTO book, int chapterIndex))) {
            log.warn("参数类型错误");
            return;
        }

        log.info("加载章节: book={}, chapterIndex={}", book.getName(), chapterIndex);

        // 2. 检查当前状态，防止重复加载
        if (stateMachine.isLoading()) {
            log.warn("当前正在加载中，忽略新的加载请求");
            return;
        }

        // 3. 状态转换到加载中
        if (!stateMachine.transition(ReadingSessionState.LOADING)) {
            log.warn("当前状态不允许加载章节");
            return;
        }

        // 4. 创建临时章节对象，发布加载开始事件
        BookChapterDTO tempChapter = new BookChapterDTO();
        tempChapter.setIndex(chapterIndex);
        publisher.publish(ReadingEvent.chapterLoading(
                command.id(),
                book,
                tempChapter,
                ReadingEvent.Direction.JUMP
        ));

        // 5. 异步获取章节列表和内容（使用专用 IO 线程池，避免与进度同步等阻塞任务互相排队）
        CompletableFuture.runAsync(() -> {
            try {
                boolean offlineMode = AddressHistoryStorage.getInstance().isOfflineMode();

                // 获取章节列表：优先从离线缓存读取（断网时也能打开已缓存的书），未命中再走 API
                OfflineCacheService cacheService = OfflineCacheService.getInstance();
                List<BookChapterDTO> chapters = cacheService.tryLoadChaptersFromCache(book.getBookUrl());
                if (chapters == null) {
                    if (offlineMode) {
                        throw new IllegalStateException("离线模式：未找到本书缓存，无法打开。请联网后再试。");
                    }
                    chapters = ApiUtil.getChapterList(book.getBookUrl());
                }

                // 目标章节：入参索引优先，越界时回退服务器进度，再回退第一章
                Integer serverIndex = book.getDurChapterIndex();
                int targetIndex = chapterIndex;
                if (targetIndex < 0 || targetIndex >= chapters.size()) {
                    targetIndex = (serverIndex != null && serverIndex >= 0 && serverIndex < chapters.size())
                            ? serverIndex : 0;
                }

                // 离线模式：优先恢复本地保存的阅读进度（服务器进度已过期且无法同步）
                LocalReadingProgress localProgress = cacheService.getReadingProgress(book.getBookUrl());
                boolean resumedFromLocal = false;
                if (offlineMode && localProgress != null
                        && localProgress.getDurChapterIndex() != null
                        && localProgress.getDurChapterIndex() >= 0
                        && localProgress.getDurChapterIndex() < chapters.size()) {
                    targetIndex = localProgress.getDurChapterIndex();
                    resumedFromLocal = true;
                }

                BookChapterDTO chapter = chapters.get(targetIndex);

                // 获取章节内容：优先从离线缓存读取，未命中再走 API
                String content = cacheService.tryLoadChapterFromCache(book.getBookUrl(), targetIndex);
                if (content == null) {
                    if (offlineMode) {
                        throw new IllegalStateException("离线模式：该章节未缓存，无法加载。请联网缓存后再试。");
                    }
                    content = ApiUtil.getBookContent(book.getBookUrl(), targetIndex);
                }

                // 创建并设置会话
                ReadingSession session = new ReadingSession(book, chapters, targetIndex, content);
                ReadingSessionManager.getInstance().setSession(session);

                // 状态转换到阅读中
                stateMachine.transition(ReadingSessionState.READING);

                // 定位光标：本地进度恢复用保存的位置；服务器章节用服务器位置；否则章节开头
                int position;
                if (resumedFromLocal && localProgress.getDurChapterPos() != null) {
                    position = localProgress.getDurChapterPos();
                } else if (serverIndex != null && targetIndex == serverIndex && book.getDurChapterPos() != null) {
                    position = book.getDurChapterPos();
                } else {
                    position = 0;
                }

                // 发布加载成功事件
                publisher.publish(ReadingEvent.chapterLoaded(
                        command.id(),
                        book,
                        chapter,
                        content,
                        position,
                        ReadingEvent.Direction.JUMP
                ));

                log.info("章节加载成功: {} (来源: {})", chapter.getTitle(), cacheService.tryLoadChaptersFromCache(book.getBookUrl()) != null ? "本地缓存" : "服务器");

                // 保存本地阅读进度（离线重开也能恢复位置）
                cacheService.saveReadingProgress(book.getBookUrl(), targetIndex, chapter.getTitle(), position);

                // 异步同步进度到服务器（失败静默忽略，不影响阅读体验）
                syncProgressAsync(book, targetIndex, chapter.getTitle(), position);

            } catch (Exception e) {
                // 状态转换到错误
                stateMachine.transition(ReadingSessionState.ERROR);

                // 发布加载失败事件
                BookChapterDTO failedChapter = new BookChapterDTO();
                failedChapter.setIndex(chapterIndex);
                publisher.publish(ReadingEvent.chapterLoadFailed(
                        command.id(),
                        book,
                        failedChapter,
                        e,
                        ReadingEvent.Direction.JUMP
                ));

                if (Boolean.TRUE.equals(PluginSettingsStorage.getInstance().getState().enableErrorLog)) {
                    log.error("章节加载失败", e);
                }
            }
        }, PluginExecutors.io());
    }

    /**
     * 异步同步阅读进度到服务器
     * 离线模式下直接跳过（服务器不可达，请求只会阻塞线程约 2 秒后失败）
     */
    private void syncProgressAsync(BookDTO book, int chapterIndex, String chapterTitle, int position) {
        if (AddressHistoryStorage.getInstance().isOfflineMode()) {
            log.debug("离线模式，跳过服务器进度同步：{} - {}", book.getName(), chapterTitle);
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                ApiUtil.saveBookProgress(book.getAuthor(), book.getName(), chapterIndex, chapterTitle, position);
                log.debug("同步阅读进度成功：{} - {}", book.getName(), chapterTitle);
            } catch (Exception e) {
                log.warn("同步阅读进度失败", e);
            }
        }, PluginExecutors.io());
    }
}
