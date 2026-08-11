package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.nancheung.plugins.jetbrains.legadoreader.common.Constant;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.styling.TextBodyStyling;

import javax.swing.*;
import java.awt.*;

/**
 * 正文面板组件
 * 负责显示章节内容、工具栏和错误提示
 *
 * @author NanCheung
 */
public class TextBodyPanel extends JBPanel<TextBodyPanel> {

    // ==================== 卡片常量 ====================
    private static final String CARD_CONTENT = "CONTENT";
    private static final String CARD_ERROR = "ERROR";

    // ==================== 错误提示文本 ====================
    private static final String ERROR_MESSAGE = """
            请求内容失败，请检查web服务是否开启、url是否正确、网络是否正常？

            小提示：可以在File -> Settings -> Tools -> Legado Reader中进行更多设置哦~
            """;

    // ==================== UI 组件 ====================
    private final ActionToolbar actionToolbar;
    private final JTextPane textBodyPane;
    private final JBPanel<?> textBodyContentPanel;
    private final CardLayout textBodyContentLayout;

    // ==================== 样式管理器 ====================
    private final TextBodyStyling textBodyStyling;

    // ==================== 构造函数 ====================
    public TextBodyPanel() {
        super(new BorderLayout());
        this.textBodyStyling = new TextBodyStyling();
        setOpaque(false);

        // 1. 顶部工具栏
        ActionManager actionManager = ActionManager.getInstance();
        DefaultActionGroup actionGroup = (DefaultActionGroup) actionManager.getAction(Constant.PLUGIN_TOOL_BAR_ID);
        this.actionToolbar = actionManager.createActionToolbar(Constant.PLUGIN_TOOL_BAR_ID, actionGroup, true);
        this.actionToolbar.setTargetComponent(this);

        JComponent toolbarComponent = actionToolbar.getComponent();
        toolbarComponent.setBorder(JBUI.Borders.empty());
        this.add(toolbarComponent, BorderLayout.NORTH);

        // 2. 中央内容区（使用 CardLayout）
        textBodyContentLayout = new CardLayout();
        textBodyContentPanel = new JBPanel<>(textBodyContentLayout);
        textBodyContentPanel.setOpaque(false);

        // 2.1 内容卡片：正文
        textBodyPane = new JTextPane();
        textBodyPane.setEditable(false);
        textBodyPane.setOpaque(false);

        JBScrollPane textScrollPane = new JBScrollPane(textBodyPane);
        textScrollPane.setOpaque(false);
        textScrollPane.getViewport().setOpaque(false);
        textBodyContentPanel.add(textScrollPane, CARD_CONTENT);

        // 2.2 错误卡片
        textBodyContentPanel.add(wrapCentered(createErrorLabel()), CARD_ERROR);

        this.add(textBodyContentPanel, BorderLayout.CENTER);

        // 默认显示内容
        showContent();
    }

    // ==================== UI 创建辅助方法 ====================

    /**
     * 创建错误标签
     */
    private JBLabel createErrorLabel() {
        JBLabel label = new JBLabel();
        label.setText("<html><center>" + ERROR_MESSAGE.replace("\n", "<br>") + "</center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setForeground(JBColor.GRAY);
        return label;
    }

    /**
     * 将组件包装在居中面板中
     */
    private JBPanel<?> wrapCentered(JComponent component) {
        JBPanel<?> wrapper = new JBPanel<>(new GridBagLayout());
        wrapper.setOpaque(false);
wrapper.add(component);
        return wrapper;
    }

    // ==================== 状态切换方法 ====================

    /**
     * 显示正文内容（隐藏错误）
     */
    public void showContent() {
        textBodyContentLayout.show(textBodyContentPanel, CARD_CONTENT);
    }

    /**
     * 显示错误提示（隐藏内容）
     */
    public void showError() {
        textBodyContentLayout.show(textBodyContentPanel, CARD_ERROR);
    }

    // ==================== 内容操作方法 ====================

    /**
     * 设置正文文本
     *
     * @param text 文本内容
     */
    public void setText(String text) {
        textBodyPane.setText(text);
    }

    /**
     * 获取正文文本
     *
     * @return 文本内容
     */
    public String getText() {
        return textBodyPane.getText();
    }

    /**
     * 设置光标位置
     *
     * @param position 光标位置
     */
    public void setCaretPosition(int position) {
        textBodyPane.setCaretPosition(position);
    }

    /**
     * 滚动到指定位置
     *
     * @param position 目标位置
     */
    public void scrollToPosition(int position) {
        try {
            Rectangle viewRect = textBodyPane.modelToView2D(position).getBounds();
            textBodyPane.scrollRectToVisible(viewRect);
        } catch (javax.swing.text.BadLocationException e) {
            // 忽略无效位置
        }
    }

    /**
     * 请求焦点
     */
    public void requestTextFocus() {
        textBodyPane.requestFocus();
    }

    // ==================== 样式操作方法 ====================

    /**
     * 应用样式
     *
     * @param fontColor  字体颜色
     * @param font       字体
     * @param lineHeight 行高
     */
    public void applyStyle(JBColor fontColor, Font font, double lineHeight) {
        textBodyStyling.apply(textBodyPane, fontColor, font, lineHeight);
    }

    /**
     * 从设置中应用样式
     */
    public void applyStyleFromSettings() {
        textBodyStyling.applyFromSettings(textBodyPane);
    }

    // ==================== 查询方法 ====================

    /**
     * 检查内容是否可见
     *
     * @return true 如果内容可见
     */
    public boolean isContentVisible() {
        return this.isVisible();
    }

    /**
     * 获取 ActionToolbar
     *
     * @return ActionToolbar 实例
     */
    public ActionToolbar getActionToolbar() {
        return actionToolbar;
    }
}
