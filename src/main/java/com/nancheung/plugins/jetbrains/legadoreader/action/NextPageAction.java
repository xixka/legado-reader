package com.nancheung.plugins.jetbrains.legadoreader.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandBus;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.manager.ReadingSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * 下一页
 * 支持 ToolWindow 和 EditorLine 双模式
 */
@Slf4j
public class NextPageAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        CommandBus.getInstance().dispatchAsync(Command.of(CommandType.NEXT_PAGE));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // 使用后台线程更新，因为只访问 Service，不访问 UI 组件
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只要有当前阅读会话就启用，不再依赖行内模式开关
        // 这样 ToolWindow 工具栏与 EditorLine 行内模式均可点击
        e.getPresentation().setEnabledAndVisible(hasReadingSession());
    }

    private boolean hasReadingSession() {
        return ReadingSessionManager.getInstance().getSession() != null;
    }
}
