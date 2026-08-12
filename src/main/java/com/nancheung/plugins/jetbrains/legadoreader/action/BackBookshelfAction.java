package com.nancheung.plugins.jetbrains.legadoreader.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.MainReaderPanel;
import org.jetbrains.annotations.NotNull;

public class BackBookshelfAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        MainReaderPanel mainPanel = MainReaderPanel.getInstance();
        String currentCard = mainPanel.getCurrentCard();

        // 章节列表 → 返回正文；正文 → 返回书架
        if ("CHAPTER_LIST".equals(currentCard)) {
            mainPanel.showTextBodyPanel();
        } else {
            mainPanel.getBookshelfPanel().refreshBookshelf();
            mainPanel.showBookshelfPanel();
        }
    }
}
