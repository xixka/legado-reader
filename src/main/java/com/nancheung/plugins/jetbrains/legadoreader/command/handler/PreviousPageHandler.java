package com.nancheung.plugins.jetbrains.legadoreader.command.handler;

import com.intellij.openapi.application.ApplicationManager;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandBus;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.CommandPayload;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.NavigateChapterPayload;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.MainReaderPanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel.TextBodyPanel;
import lombok.extern.slf4j.Slf4j;

/**
 * 上一页指令处理器
 * 按视口高度逐行向上翻页，到达顶部时触发上一章
 *
 * @author NanCheung
 */
@Slf4j
public class PreviousPageHandler implements CommandHandler<CommandPayload> {

    @Override
    public CommandType supportedType() {
        return CommandType.PREVIOUS_PAGE;
    }

    @Override
    public void handle(Command command) {
        // UI 操作必须在 EDT 线程执行，dispatchAsync 在后台线程池中运行
        ApplicationManager.getApplication().invokeLater(() -> {
            MainReaderPanel mainPanel = MainReaderPanel.getInstance();
            if (mainPanel == null) {
                log.warn("MainReaderPanel 未初始化");
                return;
            }

            TextBodyPanel textBodyPanel = mainPanel.getTextBodyPanel();
            if (textBodyPanel == null || !textBodyPanel.isContentVisible()) {
                log.debug("正文面板不可见，跳过翻页");
                return;
            }

            if (textBodyPanel.canPageUp()) {
                int newPos = textBodyPanel.pageUp();
                if (newPos >= 0) {
                    log.debug("向上翻页完成，跳转到位置 {}", newPos);
                    return;
                }
            }

            // 已经到顶部，触发上一章，定位到末尾（从翻页触发的上一章应从末尾开始）
            log.debug("已到当前章节顶部，切换到上一章（定位到末尾）");
            CommandBus.getInstance().dispatch(Command.of(CommandType.PREVIOUS_CHAPTER, new NavigateChapterPayload(true)));
        });
    }
}