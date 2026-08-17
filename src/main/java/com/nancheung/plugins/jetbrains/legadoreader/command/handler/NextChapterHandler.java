package com.nancheung.plugins.jetbrains.legadoreader.command.handler;

import com.nancheung.plugins.jetbrains.legadoreader.api.ApiUtil;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.CommandPayload;
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
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 下一章指令处理器
 * 演示事件驱动架构的使用方式
 *
 * @author NanCheung
 */
@Slf4j
public class NextChapterHandler implements CommandHandler<CommandPayload> {

    @Override
    public CommandType supportedType() {
        return CommandType.NEXT_CHAPTER;
    }

    @Override
    public void handle(Command command) {
        ReadingSessionManager sessionManager = ReadingSessionManager.getInstance();
        ReadingSessionStateMachine stateMachine = ReadingSessionStateMachine.getInstance();
        EventPublisher publisher = EventPublisher.getInstance();

        ReadingSession session = sessionManager.getSession();

        // 1. 前置检查：是否有当前阅读会话
        if (session == null) {
            log.warn("没有当前阅读会话");
            return;
        }

        // 2. 前置检查：是否已经是最后一章
        int currentIndex = sessionManager.getCurrentChapterIndex();
        int totalChapters = session.chapters().size();

        if (currentIndex >= totalChapters - 1) {
            log.warn("已经是最后一章");
            return;
        }

        // 3. 检查当前状态，防止重复加载
        if (stateMachine.isLoading()) {
            log.warn("当前正在加载中，忽略切换章节请求");
            return;
        }

        // 4. 状态转换：READING → LOADING
        if (!stateMachine.transition(ReadingSessionState.LOADING)) {
            log.warn("当前状态不允许切换章节");
            return;
        }

        // 5. 准备数据
        int nextIndex = currentIndex + 1;
        BookDTO book = session.book();
        BookChapterDTO tempChapter = new BookChapterDTO();
        tempChapter.setIndex(nextIndex);

        log.info("开始切换到下一章: {} -> {}", currentIndex, nextIndex);

        // 6. 优先尝试从缓存读取（缓存命中时跳过加载状态，避免"加载中..."闪烁）
        String cachedContent = OfflineCacheService.getInstance().tryLoadChapterFromCache(book.getBookUrl(), nextIndex);

        // 7. 仅在没有缓存时发布"章节加载开始"事件
        if (cachedContent == null) {
            publisher.publish(ReadingEvent.chapterLoading(
                    command.id(),
                    book,
                    tempChapter,
                    ReadingEvent.Direction.NEXT
            ));
        }

        // 8. 异步加载数据（使用专用 IO 线程池，避免与进度同步等阻塞任务互相排队）
        final String contentFromCache = cachedContent;
        CompletableFuture.runAsync(() -> {
            boolean indexAdvanced = false;
            try {
                // 8.1 获取章节列表和内容
                List<BookChapterDTO> chapters = sessionManager.getChapters();
                BookChapterDTO chapter = chapters.get(nextIndex);
                // 使用缓存内容或从 API 获取
                String content = contentFromCache;
                if (content == null) {
                    // 离线模式下服务器不可达，直接快速失败，不再等待网络超时
                    if (AddressHistoryStorage.getInstance().isOfflineMode()) {
                        throw new IllegalStateException("离线模式：该章节未缓存，无法加载。请联网缓存后再试。");
                    }
                    content = ApiUtil.getBookContent(book.getBookUrl(), nextIndex);
                }

                // 8.2 更新会话
                sessionManager.nextChapter();
                indexAdvanced = true;
                sessionManager.setContent(content);

                // 8.3 状态转换：LOADING → READING
                stateMachine.transition(ReadingSessionState.READING);

                // 8.4 发布"章节加载成功"事件
                publisher.publish(ReadingEvent.chapterLoaded(
                        command.id(),
                        book,
                        chapter,
                        content,
                        0,  // 定位到章节开头
                        ReadingEvent.Direction.NEXT
                ));

                log.info("切换到下一章成功：{}", chapter.getTitle());

                // 8.5 保存本地阅读进度（离线重开也能恢复位置）
                OfflineCacheService.getInstance().saveReadingProgress(book.getBookUrl(), nextIndex, chapter.getTitle(), 0);

                // 8.6 异步同步进度到服务器（不等待）
                syncProgressAsync(book, nextIndex, chapter.getTitle(), 0);

            } catch (Exception e) {
                // 失败处理：回滚状态（仅当索引已前进时才回滚，避免索引漂移）
                if (indexAdvanced) {
                    sessionManager.previousChapter();
                }
                stateMachine.transition(ReadingSessionState.READING);  // 回到阅读状态

                // 发布"章节加载失败"事件
                publisher.publish(ReadingEvent.chapterLoadFailed(
                        command.id(),
                        book,
                        tempChapter,
                        e,
                        ReadingEvent.Direction.NEXT
                ));

                // 记录错误日志（如果启用）
                if (Boolean.TRUE.equals(PluginSettingsStorage.getInstance().getState().enableErrorLog)) {
                    log.error("切换到下一章失败", e);
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
                ApiUtil.saveBookProgress(
                        book.getAuthor(),
                        book.getName(),
                        chapterIndex,
                        chapterTitle,
                        position
                );
                log.debug("同步阅读进度成功：{} - {}", book.getName(), chapterTitle);
            } catch (Exception e) {
                log.warn("同步阅读进度失败", e);
                // 忽略同步失败，不影响阅读体验
            }
        }, PluginExecutors.io());
    }
}
