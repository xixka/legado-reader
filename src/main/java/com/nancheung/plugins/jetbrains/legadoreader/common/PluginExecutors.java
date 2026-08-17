package com.nancheung.plugins.jetbrains.legadoreader.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 插件专用线程池
 * <p>
 * 历史问题：所有异步任务（指令分发、章节加载、进度同步、离线缓存）原先都跑在
 * {@code ForkJoinPool.commonPool()} 上。一旦服务器不可达，阻塞的网络请求
 * （无超时的进度同步）会占满 commonPool，导致后续切章请求排队，
 * 表现为"点击上一章/下一章卡 2 秒"。
 * <p>
 * 现将所有涉及磁盘 IO / 网络的任务统一放到本专用线程池，
 * 与 commonPool 隔离，阻塞再久也不会影响其他任务调度。
 *
 * @author NanCheung
 */
public final class PluginExecutors {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * IO 线程池（守护线程，不阻止 IDE 退出）
     * 2 个线程：章节加载与进度同步可并行，互不阻塞
     */
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "legado-reader-io-" + SEQ.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 分页专用单线程池
     * <p>
     * 分页任务必须严格串行：快速连续切章时，若两个分页任务并行交错，
     * 后完成者会覆盖先完成者，导致显示的页码与章节错乱。
     * 单线程 + 任务按事件顺序提交（invokeLater FIFO）保证顺序一致。
     */
    private static final ExecutorService PAGINATION_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "legado-reader-pagination");
        thread.setDaemon(true);
        return thread;
    });

    private PluginExecutors() {
    }

    /**
     * 获取 IO 线程池（用于磁盘读写、网络请求等可能阻塞的任务）
     */
    public static ExecutorService io() {
        return IO_EXECUTOR;
    }

    /**
     * 获取分页专用单线程池（任务按提交顺序串行执行）
     */
    public static ExecutorService pagination() {
        return PAGINATION_EXECUTOR;
    }
}
