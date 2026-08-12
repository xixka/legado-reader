package com.nancheung.plugins.jetbrains.legadoreader.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.nancheung.plugins.jetbrains.legadoreader.manager.ReadingSessionManager;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.MainReaderPanel;
import org.jetbrains.annotations.NotNull;

/**
 * 显示章节列表
 * 点击后切换到章节列表面板，可选择章节跳转
 *
 * @author NanCheung
 */
public class ShowChapterListAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        MainReaderPanel.getInstance().showChapterListPanel();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // 使用后台线程更新，因为只访问 Service，不访问 UI 组件
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只有存在当前阅读会话时才启用
        e.getPresentation().setEnabledAndVisible(ReadingSessionManager.getInstance().getSession() != null);
    }
}
