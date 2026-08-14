package com.nancheung.plugins.jetbrains.legadoreader.command.handler;

import com.intellij.openapi.application.ApplicationManager;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandBus;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.CommandPayload;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.MainReaderPanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel.TextBodyPanel;
import lombok.extern.slf4j.Slf4j;

/**
 * 下一页指令处理器
 * 按视口高度逐行翻页（仅完整可见行计入），到达底部时触发下一章
 *
 * @author NanCheung
 */
@Slf4j
public class NextPageHandler implements CommandHandler<CommandPayload> {

    @Override
    public CommandType supportedType() {
        return CommandType.NEXT_PAGE;
    }

    @Override
    public void handle(Command command) {
        // UI 操作必须在 EDT 线程执行，dispatchAsync 在后台线程池中运行
        ApplicationManager.getApplication().invokeLater(() -> {
            MainReaderPanel mainPanel = MainReaderPanel.getInstance();
            if (mainPanel == null) {
                System.out.println("[DBG-PageDown] mainPanel is null, abort");
                return;
            }

            TextBodyPanel textBodyPanel = mainPanel.getTextBodyPanel();
            if (textBodyPanel == null) {
                System.out.println("[DBG-PageDown] textBodyPanel is null, abort");
                return;
            }
            if (!textBodyPanel.isContentVisible()) {
                System.out.println("[DBG-PageDown] contentVisible=false, skip paging");
                return;
            }

            boolean canPD = textBodyPanel.canPageDown();
            System.out.println("[DBG-PageDown] canPageDown=" + canPD);
            if (canPD) {
                int newPos = textBodyPanel.pageDown();
                System.out.println("[DBG-PageDown] pageDown returned " + newPos);
                if (newPos >= 0) {
                    return;
                }
            }

            // 已经到底部，触发下一章
            System.out.println("[DBG-PageDown] dispatching NEXT_CHAPTER");
            CommandBus.getInstance().dispatch(Command.of(CommandType.NEXT_CHAPTER));
        });
    }
}