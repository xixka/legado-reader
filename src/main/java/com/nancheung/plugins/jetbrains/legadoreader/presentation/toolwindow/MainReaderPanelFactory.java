package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.nancheung.plugins.jetbrains.legadoreader.common.Constant;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
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

        // 将正文阅读工具栏按钮（返回书架 / 上一章 / 下一章 / 当前阅读信息）挂到 ToolWindow 标题栏右侧
        // 即放在标题 "Reader" 的后面
        ActionManager actionManager = ActionManager.getInstance();
        DefaultActionGroup actionGroup = (DefaultActionGroup) actionManager.getAction(Constant.PLUGIN_TOOL_BAR_ID);
        if (actionGroup != null) {
            AnAction[] children = actionGroup.getChildren(null);
            List<AnAction> titleActions = Arrays.asList(children);
            toolWindow.setTitleActions(titleActions);
        }
    }
}
