package com.nancheung.plugins.jetbrains.legadoreader.command;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.nancheung.plugins.jetbrains.legadoreader.command.handler.CommandHandler;
import com.nancheung.plugins.jetbrains.legadoreader.common.PluginExecutors;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * 指令总线（Application Service）
 * 接收指令并路由到对应的处理器
 * 负责发布指令生命周期事件
 *
 * @author NanCheung
 */
@Slf4j
@Service
public final class CommandBus {

    private final CommandHandlerRegistry registry;

    /**
     * 获取单例实例
     */
    public static CommandBus getInstance() {
        return ApplicationManager.getApplication().getService(CommandBus.class);
    }

    /**
     * 构造函数（由 IntelliJ Platform 调用）
     */
    public CommandBus() {
        this.registry = CommandHandlerRegistry.getInstance();
    }

    /**
     * 分发指令（同步）
     * 注意：实际处理可能是异步的（由处理器决定）
     *
     * @param command 指令对象
     */
    public void dispatch(Command command) {
        log.info("收到指令: type={}, id={}", command.type(), command.id());

        registry.getHandler(command.type()).ifPresentOrElse(
                handler -> executeHandler(command, handler),
                () -> {
                    log.error("未找到指令处理器: {}", command.type());
                }
        );
    }

    /**
     * 异步分发指令
     * 指令分发本身在后台线程执行
     * （处理链路可能涉及缓存磁盘读取，统一走专用 IO 线程池，避免占用 ForkJoinPool.commonPool）
     *
     * @param command 指令对象
     */
    public void dispatchAsync(Command command) {
        CompletableFuture.runAsync(() -> dispatch(command), PluginExecutors.io());
    }

    /**
     * 执行处理器
     */
    private void executeHandler(Command command, CommandHandler<?> handler) {
        // 1. 前置检查
        if (!handler.canHandle(command)) {
            log.warn("处理器拒绝处理指令: type={}, handler={}", command.type(), handler.getClass().getSimpleName());
            return;
        }

        try {
            // 3. 执行处理器
            // 注意：成功/失败事件由具体处理器内部发布（因为可能是异步的）
            handler.handle(command);

        } catch (Exception e) {
            // 4. 如果处理器直接抛出异常，记录错误日志
            log.error("指令执行失败: type={}", command.type(), e);
        }
    }
}
