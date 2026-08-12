package com.nancheung.plugins.jetbrains.legadoreader.command.handler;

import com.nancheung.plugins.jetbrains.legadoreader.api.ApiUtil;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.JumpToChapterPayload;
import com.nancheung.plugins.jetbrains.legadoreader.event.EventPublisher;
import com.nancheung.plugins.jetbrains.legadoreader.event.ReadingEvent;
import com.nancheung.plugins.jetbrains.legadoreader.manager.ReadingSessionManager;
import com.nancheung.plugins.jetbrains.legadoreader.model.ReadingSession;
import com.nancheung.plugins.jetbrains.legadoreader.model.ReadingSessionState;
import com.nancheung.plugins.jetbrains.legadoreader.service.ReadingSessionStateMachine;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 跳转章节指令处理器
 * 处理在当前阅读会话内跳转到指定章节的操作
 *
 * @author NanCheung
 */
@Slf4j
public class JumpToChapterHandler implements CommandHandler<JumpToChapterPayload> {

    @Override
    public CommandType supportedType() {
        return CommandType.JUMP_TO_CHAPTER;
    }

    @Override
    public void handle(Command command) {
        ReadingSessionManager sessionManager = ReadingSessionManager.getInstance();
        ReadingSessionStateMachine stateMachine = ReadingSessionStateMachine.getInstance();
        EventPublisher publisher = EventPublisher.getInstance();

        ReadingSession session = sessionManager.getSession();

        // 1. 前置检查：是否有当前阅读会话
        if (session == null) {
            log.warn("没有当前阅读会话，无法跳转章节");
            return;
        }

        // 2. 参数校验
        if (!(command.payload() instanceof JumpToChapterPayload(int targetIndex))) {
            log.warn("跳转章节参数类型错误");
            return;
        }

        List<BookChapterDTO> chapters = session.chapters();
        if (chapters == null || targetIndex < 0 || targetIndex >= chapters.size()) {
            log.warn("跳转章节索引越界: {}，章节总数: {}",
                    targetIndex, chapters == null ? 0 : chapters.size());
            return;
        }

        // 3. 同章不跳
        int currentIndex = sessionManager.getCurrentChapterIndex();
        if (currentIndex == targetIndex) {
            log.debug("已在目标章节，无需跳转: {}", targetIndex);
            return;
        }

        // 4. 防止重复加载
        if (stateMachine.isLoading()) {
            log.warn("当前正在加载中，忽略跳转章节请求");
            return;
        }

        // 5. 状态转换：READING → LOADING
        if (!stateMachine.transition(ReadingSessionState.LOADING)) {
            log.warn("当前状态不允许跳转章节");
            return;
        }

        BookDTO book = session.book();
        BookChapterDTO tempChapter = new BookChapterDTO();
        tempChapter.setIndex(targetIndex);

        log.info("开始跳转章节: {} -> {}", currentIndex, targetIndex);

        // 6. 发布"章节加载开始"事件
        publisher.publish(ReadingEvent.chapterLoading(
                command.id(),
                book,
                tempChapter,
                ReadingEvent.Direction.JUMP
        ));

        // 7. 异步加载章节内容
        int previousIndex = currentIndex;
        CompletableFuture.runAsync(() -> {
            try {
                BookChapterDTO chapter = chapters.get(targetIndex);
                String content = ApiUtil.getBookContent(book.getBookUrl(), targetIndex);

                // 更新会话：跳转到目标章节
                sessionManager.setChapterIndex(targetIndex);
                sessionManager.setContent(content);

                // 状态转换：LOADING → READING
                stateMachine.transition(ReadingSessionState.READING);

                // 发布"章节加载成功"事件
                publisher.publish(ReadingEvent.chapterLoaded(
                        command.id(),
                        book,
                        chapter,
                        content,
                        0,  // 跳转章节默认定位到开头
                        ReadingEvent.Direction.JUMP
                ));

                log.info("跳转章节成功：{}", chapter.getTitle());

                // 异步同步进度到服务器（不等待）
                syncProgressAsync(book, targetIndex, chapter.getTitle(), 0);

            } catch (Exception e) {
                // 失败处理：回滚章节索引
                sessionManager.setChapterIndex(previousIndex);
                stateMachine.transition(ReadingSessionState.READING);

                publisher.publish(ReadingEvent.chapterLoadFailed(
                        command.id(),
                        book,
                        tempChapter,
                        e,
                        ReadingEvent.Direction.JUMP
                ));

                if (Boolean.TRUE.equals(PluginSettingsStorage.getInstance().getState().enableErrorLog)) {
                    log.error("跳转章节失败", e);
                }
            }
        });
    }

    /**
     * 异步同步阅读进度到服务器
     */
    private void syncProgressAsync(BookDTO book, int chapterIndex, String chapterTitle, int position) {
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
        });
    }
}
