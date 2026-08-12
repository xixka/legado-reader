package com.nancheung.plugins.jetbrains.legadoreader.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.nancheung.plugins.jetbrains.legadoreader.api.ApiUtil;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import com.nancheung.plugins.jetbrains.legadoreader.event.CacheEvent;
import com.nancheung.plugins.jetbrains.legadoreader.event.EventPublisher;
import com.nancheung.plugins.jetbrains.legadoreader.storage.AddressHistoryStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.cache.BookCacheStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.BookCacheMeta;
import com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.BookCacheProgress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 离线缓存服务（Application Service）
 * <p>
 * 职责：
 * <ul>
 *   <li>后台异步缓存整本书的所有章节，AES 加密落盘</li>
 *   <li>每章缓存后发布 {@link CacheEvent} 进度事件</li>
 *   <li>支持取消正在进行的缓存任务</li>
 *   <li>支持断点续传（已缓存章节跳过）</li>
 *   <li>对外提供缓存优先的章节读取接口（用于阅读流程）</li>
 * </ul>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>所有缓存任务在独立的 {@link CompletableFuture} 链中执行，不阻塞 EDT</li>
 *   <li>同一本书不会并发缓存（防止重复请求与文件冲突）</li>
 *   <li>缓存失败不影响阅读流程，调用方应回退到 API</li>
 * </ul>
 *
 * @author NanCheung
 */
@Slf4j
@Service
public final class OfflineCacheService {

    /**
     * 获取单例实例
     */
    public static OfflineCacheService getInstance() {
        return ApplicationManager.getApplication().getService(OfflineCacheService.class);
    }

    /**
     * 每本书的运行状态：key = bookUrl
     */
    private final Map<String, CacheTaskState> runningTasks = new ConcurrentHashMap<>();

    /**
     * 每章缓存后的延迟（毫秒），降低对阅读 APP 服务端的并发压力
     */
    private static final long CHAPTER_DELAY_MS = 80L;

    // ==================== 缓存任务状态 ====================

    /**
     * 单本书的缓存任务运行状态
     */
    private static final class CacheTaskState {
        final AtomicBoolean canceled = new AtomicBoolean(false);
        final AtomicBoolean done = new AtomicBoolean(false);
        final int totalChapters;
        final String bookName;

        CacheTaskState(int totalChapters, String bookName) {
            this.totalChapters = totalChapters;
            this.bookName = bookName;
        }

        boolean isCanceled() {
            return canceled.get();
        }

        boolean isDone() {
            return done.get();
        }
    }

    // ==================== 状态查询 ====================

    /**
     * 是否正在缓存某本书
     *
     * @param bookUrl 书籍 URL
     * @return true 如果正在缓存
     */
    public boolean isCacheRunning(String bookUrl) {
        CacheTaskState state = runningTasks.get(bookUrl);
        return state != null && !state.isDone();
    }

    /**
     * 获取当前正在缓存的书籍 URL 集合
     */
    public Set<String> getRunningBookUrls() {
        return runningTasks.entrySet().stream()
                .filter(e -> !e.getValue().isDone())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * 缓存功能是否启用
     */
    public boolean isCacheEnabled() {
        return BookCacheStorage.getInstance().isCacheEnabled();
    }

    // ==================== 缓存启动与取消 ====================

    /**
     * 异步缓存整本书
     * <p>
     * 若已在缓存该书，直接返回；若缓存被禁用，返回 null。
     * 支持断点续传：已缓存章节跳过。
     *
     * @param book     书籍信息
     * @param chapters 章节列表
     * @param commandId 关联的指令 ID（用于事件追踪）
     * @return 缓存任务 future；null 表示未启动（已禁用或已在运行）
     */
    public CompletableFuture<Void> cacheBookAsync(BookDTO book, List<BookChapterDTO> chapters, @Nullable String commandId) {
        Objects.requireNonNull(book, "书籍信息不能为空");
        Objects.requireNonNull(chapters, "章节列表不能为空");

        if (!isCacheEnabled()) {
            log.debug("缓存未启用，跳过：book={}", book.getName());
            return null;
        }

        String bookUrl = book.getBookUrl();
        if (bookUrl == null || bookUrl.isEmpty()) {
            log.warn("书籍 URL 为空，无法缓存：{}", book.getName());
            return null;
        }

        // 防止并发缓存同一本书
        if (isCacheRunning(bookUrl)) {
            log.info("已在缓存中，忽略重复请求：book={}", book.getName());
            return null;
        }

        int total = chapters.size();
        if (total == 0) {
            log.warn("章节列表为空，跳过缓存：book={}", book.getName());
            return null;
        }

        EventPublisher publisher = EventPublisher.getInstance();
        BookCacheStorage storage = BookCacheStorage.getInstance();

        // 1. 保存元数据
        BookCacheMeta meta = new BookCacheMeta();
        meta.setBookUrl(bookUrl);
        meta.setAuthor(book.getAuthor());
        meta.setName(book.getName());
        meta.setTotalChapters(total);
        meta.setCachedAt(System.currentTimeMillis());
        meta.setChapters(chapters);
        meta.setBookSnapshot(book);
        meta.setSourceAddress(AddressHistoryStorage.getInstance().getMostRecent());
        storage.saveMeta(meta);

        // 2. 加载或创建进度位图
        BookCacheProgress progress = storage.loadProgress(bookUrl);
        BitSet bitmap;
        if (progress != null && progress.getTotalChapters() == total) {
            bitmap = BookCacheProgress.base64ToBitmap(progress.getCachedBitmap());
        } else {
            bitmap = new BitSet(total);
            progress = new BookCacheProgress();
            progress.setBookUrl(bookUrl);
            progress.setTotalChapters(total);
            progress.setCachedChapters(0);
            progress.setCachedBitmap(BookCacheProgress.bitmapToBase64(bitmap));
            progress.setLastCacheTime(System.currentTimeMillis());
            progress.setStatus(BookCacheProgress.STATUS_INCOMPLETE);
        }

        // 3. 发布开始事件
        publisher.publish(CacheEvent.started(commandId, bookUrl, book.getName(), total));

        // 4. 预创建任务状态
        CacheTaskState state = new CacheTaskState(total, book.getName());
        runningTasks.put(bookUrl, state);

        // 5. 启动异步任务
        return CompletableFuture.runAsync(() -> {
            try {
                AtomicInteger cachedCount = new AtomicInteger(bitmap.cardinality());

                for (int i = 0; i < total; i++) {
                    // 取消检查
                    if (state.isCanceled() || Thread.currentThread().isInterrupted()) {
                        log.info("缓存被取消：book={}, progress={}/{}", book.getName(), cachedCount.get(), total);
                        publisher.publish(CacheEvent.canceled(commandId, bookUrl, book.getName(), total, cachedCount.get()));
                        saveProgress(storage, progress, bitmap, cachedCount.get(), BookCacheProgress.STATUS_INCOMPLETE);
                        return;
                    }

                    // 跳过已缓存章节（断点续传）
                    if (bitmap.get(i)) {
                        continue;
                    }

                    // 缓存单章
                    try {
                        BookChapterDTO chapter = chapters.get(i);
                        String content = ApiUtil.getBookContent(bookUrl, i);

                        if (content == null) {
                            log.warn("章节内容为空，跳过：book={}, index={}", book.getName(), i);
                            continue;
                        }

                        // chapter 仅用于日志，避免 IDE 提示未使用
                        log.debug("正在缓存：book={}, index={}, title={}",
                                book.getName(), i, chapter.getTitle());

                        storage.saveChapter(bookUrl, i, content);
                        bitmap.set(i);
                        cachedCount.getAndIncrement();

                        // 持久化进度（每章保存一次，避免崩溃丢失）
                        saveProgress(storage, progress, bitmap, cachedCount.get(), BookCacheProgress.STATUS_INCOMPLETE);

                        // 发布进度事件
                        publisher.publish(CacheEvent.progress(commandId, bookUrl, book.getName(), total, cachedCount.get()));

                        // 节流：降低服务端压力
                        if (i < total - 1) {
                            Thread.sleep(CHAPTER_DELAY_MS);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.info("缓存被中断：book={}, progress={}/{}", book.getName(), cachedCount.get(), total);
                        publisher.publish(CacheEvent.canceled(commandId, bookUrl, book.getName(), total, cachedCount.get()));
                        saveProgress(storage, progress, bitmap, cachedCount.get(), BookCacheProgress.STATUS_INCOMPLETE);
                        return;
                    } catch (Exception e) {
                        log.error("缓存章节失败：book={}, index={}", book.getName(), i, e);
                        publisher.publish(CacheEvent.failed(commandId, bookUrl, book.getName(), total, cachedCount.get(), e.getMessage()));
                        saveProgress(storage, progress, bitmap, cachedCount.get(), BookCacheProgress.STATUS_INCOMPLETE);
                        return;
                    }
                }

                // 完成
                saveProgress(storage, progress, bitmap, total, BookCacheProgress.STATUS_COMPLETE);
                publisher.publish(CacheEvent.completed(commandId, bookUrl, book.getName(), total));
                log.info("缓存完成：book={}, chapters={}", book.getName(), total);

            } finally {
                state.done.set(true);
                runningTasks.remove(bookUrl);
            }
        });
    }

    /**
     * 保存进度到存储
     */
    private void saveProgress(BookCacheStorage storage, BookCacheProgress progress,
                              BitSet bitmap, int cachedCount, String status) {
        progress.setCachedChapters(cachedCount);
        progress.setCachedBitmap(BookCacheProgress.bitmapToBase64(bitmap));
        progress.setLastCacheTime(System.currentTimeMillis());
        progress.setStatus(status);
        storage.saveProgress(progress);
    }

    /**
     * 取消某本书的缓存任务
     *
     * @param bookUrl 书籍 URL
     * @return true 如果成功取消（任务存在且未完成）
     */
    public boolean cancelCache(String bookUrl) {
        CacheTaskState state = runningTasks.get(bookUrl);
        if (state == null || state.isDone()) {
            return false;
        }
        state.canceled.set(true);
        log.info("已请求取消缓存：bookKey={}", BookCacheStorage.bookKey(bookUrl));
        return true;
    }

    /**
     * 删除某本书的全部缓存
     *
     * @param bookUrl 书籍 URL
     */
    public void deleteBookCache(String bookUrl) {
        cancelCache(bookUrl);
        BookCacheStorage.getInstance().deleteBookCache(bookUrl);
    }

    // ==================== 缓存优先读取 ====================

    /**
     * 尝试从缓存读取章节内容
     * <p>
     * 缓存未启用或未命中时返回 null，调用方应回退到 API。
     *
     * @param bookUrl 书籍 URL
     * @param index   章节索引
     * @return 章节内容；未命中返回 null
     */
    public String tryLoadChapterFromCache(String bookUrl, int index) {
        if (!isCacheEnabled() || bookUrl == null) {
            return null;
        }
        return BookCacheStorage.getInstance().loadChapter(bookUrl, index);
    }

    /**
     * 是否已缓存某章节
     */
    public boolean hasChapter(String bookUrl, int index) {
        if (!isCacheEnabled() || bookUrl == null) {
            return false;
        }
        return BookCacheStorage.getInstance().hasChapter(bookUrl, index);
    }

    /**
     * 获取某本书的缓存进度
     *
     * @param bookUrl 书籍 URL
     * @return 缓存进度；不存在返回 null
     */
    public BookCacheProgress getProgress(String bookUrl) {
        return BookCacheStorage.getInstance().loadProgress(bookUrl);
    }

    /**
     * 获取某本书的缓存元数据
     *
     * @param bookUrl 书籍 URL
     * @return 缓存元数据；不存在返回 null
     */
    public BookCacheMeta getMeta(String bookUrl) {
        return BookCacheStorage.getInstance().loadMeta(bookUrl);
    }

    /**
     * 是否已完整缓存某本书
     *
     * @param bookUrl       书籍 URL
     * @param totalChapters 总章节数
     * @return true 如果全部章节已缓存
     */
    public boolean isBookComplete(String bookUrl, int totalChapters) {
        return BookCacheStorage.getInstance().isBookComplete(bookUrl, totalChapters);
    }
}
