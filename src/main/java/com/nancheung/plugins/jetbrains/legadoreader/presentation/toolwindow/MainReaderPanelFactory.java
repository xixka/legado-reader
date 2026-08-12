package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.nancheung.plugins.jetbrains.legadoreader.common.Constant;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainReaderPanelFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        //获取内容工厂的实例
        ContentFactory contentFactory = ContentFactory.getInstance();

        // 获取 MainReaderPanel 实例（懒加载）
        MainReaderPanel mainReaderPanel = MainReaderPanel.getInstance();

        // 初始化地址历史记录（延迟访问 Service）
        mainReaderPanel.initAddressHistory();

        // 获取用于 toolWindow显示的内容
        Content content = contentFactory.createContent(mainReaderPanel.getComponent(), "", false);
        // 给 toolWindow设置内容
        toolWindow.getContentManager().addContent(content);

        // 准备正文阅读工具栏按钮组（返回书架 / 上下章 / 上下页 / 当前阅读信息）
        // 该按钮组仅在显示正文面板时挂到 ToolWindow 标题栏右侧（标题 "Reader" 后面），
        // 书架面板时由 MainReaderPanel 动态清空
        List<AnAction> titleActions = resolveTitleActions();
        mainReaderPanel.setTitleActions(titleActions);
        mainReaderPanel.setToolWindow(toolWindow);
    }

    /**
     * 从 ActionManager 获取正文工具栏按钮组并转为 List
     */
    private List<AnAction> resolveTitleActions() {
        ActionManager actionManager = ActionManager.getInstance();
        DefaultActionGroup actionGroup = (DefaultActionGroup) actionManager.getAction(Constant.PLUGIN_TOOL_BAR_ID);
        if (actionGroup == null) {
            return Collections.emptyList();
        }
        AnAction[] children = actionGroup.getChildren((AnActionEvent) null);
        return Arrays.asList(children);
    }
}
