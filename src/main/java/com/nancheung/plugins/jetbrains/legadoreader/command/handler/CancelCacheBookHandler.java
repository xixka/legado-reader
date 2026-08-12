package com.nancheung.plugins.jetbrains.legadoreader.command.handler;

import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.CommandPayload;
import com.nancheung.plugins.jetbrains.legadoreader.service.OfflineCacheService;
import lombok.extern.slf4j.Slf4j;

/**
 * 取消离线缓存指令处理器
 * <p>
 * 取消正在进行的缓存任务。可通过参数指定 bookUrl，不指定时取消全部运行中任务。
 *
 * @author NanCheung
 */
@Slf4j
public class CancelCacheBookHandler implements CommandHandler<CommandPayload> {

    @Override
    public CommandType supportedType() {
        return CommandType.CANCEL_CACHE_BOOK;
    }

    @Override
    public void handle(Command command) {
        OfflineCacheService cacheService = OfflineCacheService.getInstance();

        java.util.Set<String> runningBooks = cacheService.getRunningBookUrls();
        if (runningBooks.isEmpty()) {
            log.info("没有正在进行的缓存任务");
            return;
        }

        // 取消所有运行中的缓存任务
        for (String bookUrl : runningBooks) {
            cacheService.cancelCache(bookUrl);
        }
        log.info("已取消 {} 个缓存任务", runningBooks.size());
    }
}
