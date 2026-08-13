package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandBus;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.styling.TextBodyStyling;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;

/**
 * 正文面板组件
 * 负责显示章节内容和错误提示
 * （返回/上下章等操作按钮已移至 ToolWindow 标题栏右侧）
 *
 * @author NanCheung
 */
@Slf4j
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
    private final JTextPane textBodyPane;
    private final JBScrollPane textScrollPane;
    private final JBPanel<?> textBodyContentPanel;
    private final CardLayout textBodyContentLayout;
    private boolean contentVisible = false;

    // ==================== 样式管理器 ====================
    private final TextBodyStyling textBodyStyling;

    // ==================== 构造函数 ====================
    public TextBodyPanel() {
        super(new BorderLayout());
        this.textBodyStyling = new TextBodyStyling();
        setOpaque(false);

        // 1. 中央内容区（使用 CardLayout）
        textBodyContentLayout = new CardLayout();
        textBodyContentPanel = new JBPanel<>(textBodyContentLayout);
        textBodyContentPanel.setOpaque(false);

        // 1.1 内容卡片：正文
        textBodyPane = new JTextPane();
        textBodyPane.setEditable(false);
        // 使文本面板可聚焦，方向键 KeyBindings 才能生效
        textBodyPane.setFocusable(true);
        textBodyPane.getCaret().setVisible(false);

        textScrollPane = new JBScrollPane(textBodyPane);
        textScrollPane.setOpaque(false);
        textScrollPane.getViewport().setOpaque(false);
        // 禁用滚动面板自身的方向键处理，避免与翻页快捷键冲突
        disableScrollPaneArrowKeys(textScrollPane);
        textBodyContentPanel.add(textScrollPane, CARD_CONTENT);

        // 1.2 错误卡片
        textBodyContentPanel.add(wrapCentered(createErrorLabel()), CARD_ERROR);

        this.add(textBodyContentPanel, BorderLayout.CENTER);

        // 默认显示内容
        showContent();

        // 注册方向键快捷键（仅在文本面板有焦点时生效，失去焦点自动失效）
        registerArrowKeyBindings();
    }

    /**
     * 注册方向键快捷键到文本面板
     * ← 上一页  |  → 下一页  |  ↑ 上一章  |  ↓ 下一章
     * 使用 WHEN_FOCUSED，焦点在文本面板时生效，失去焦点自动失效
     */
    private void registerArrowKeyBindings() {
        InputMap inputMap = textBodyPane.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = textBodyPane.getActionMap();

        // ← 上一页
        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "pageUp");
        actionMap.put("pageUp", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                doPageUp();
            }
        });

        // → 下一页
        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "pageDown");
        actionMap.put("pageDown", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                doPageDown();
            }
        });

        // ↑ 上一章
        inputMap.put(KeyStroke.getKeyStroke("UP"), "previousChapter");
        actionMap.put("previousChapter", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                CommandBus.getInstance().dispatchAsync(Command.of(CommandType.PREVIOUS_CHAPTER));
            }
        });

        // ↓ 下一章
        inputMap.put(KeyStroke.getKeyStroke("DOWN"), "nextChapter");
        actionMap.put("nextChapter", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                CommandBus.getInstance().dispatchAsync(Command.of(CommandType.NEXT_CHAPTER));
            }
        });
    }

    /**
     * 禁用 JBScrollPane 自带的方向键滚动行为，避免与翻页快捷键冲突
     */
    private void disableScrollPaneArrowKeys(JScrollPane scrollPane) {
        InputMap im = scrollPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = scrollPane.getActionMap();
        // 注册空动作，将方向键绑定到空操作，防止滚动面板消费方向键事件
        am.put("none", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // no-op
            }
        });
        im.put(KeyStroke.getKeyStroke("UP"), "none");
        im.put(KeyStroke.getKeyStroke("DOWN"), "none");
        im.put(KeyStroke.getKeyStroke("LEFT"), "none");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "none");
    }

    /**
     * 执行上一页：章内翻页，已到顶部则触发上一章
     */
    private void doPageUp() {
        if (canPageUp()) {
            int newPos = pageUp();
            if (newPos >= 0) return;
        }
        // 已到顶部，触发上一章
        CommandBus.getInstance().dispatchAsync(Command.of(CommandType.PREVIOUS_CHAPTER));
    }

    /**
     * 执行下一页：章内翻页，已到底部则触发下一章
     */
    private void doPageDown() {
        if (canPageDown()) {
            int newPos = pageDown();
            if (newPos >= 0) return;
        }
        // 已到底部，触发下一章
        CommandBus.getInstance().dispatchAsync(Command.of(CommandType.NEXT_CHAPTER));
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
     * 将光标设置到文档末尾
     */
    public void setCaretPositionToEnd() {
        int length = textBodyPane.getDocument().getLength();
        textBodyPane.setCaretPosition(length);
    }

    /**
     * 无动画覆盖滑动：直接将目标位置推到视口顶部
     *
     * @param position 目标字符偏移
     */
    public void scrollToPosition(int position) {
        try {
            Rectangle viewRect = textBodyPane.modelToView2D(position).getBounds();
            if (viewRect == null) return;
            JViewport viewport = textScrollPane.getViewport();
            viewport.setViewPosition(new Point(0, viewRect.y));
        } catch (BadLocationException e) {
            // 忽略无效位置
        }
    }

    /**
     * 向下翻一页（按视口高度逐行计算）
     * 新视口顶部 = 当前视口最后一个完整可见行的下一行行首
     * 保证至少推进一行，避免原地不动
     *
     * @return 跳转后的行首字符偏移；已到底则返回 -1
     */
    public int pageDown() {
        JViewport viewport = textScrollPane.getViewport();
        int viewHeight = viewport.getExtentSize().height;
        if (viewHeight <= 0) return -1;
        int viewTop = viewport.getViewPosition().y;
        int viewBottom = viewTop + viewHeight;

        try {
            int totalLen = textBodyPane.getDocument().getLength();
            if (totalLen == 0) return -1;

            // 找到视口内第一行的行首位置（用于确保至少推进一行）
            int firstLineStart = findLineStart(findPositionAtY(viewTop));

            // 从 viewBottom 向上找最后一个【完整可见行】的行首
            // 完整可见行 = 行的底部像素 <= viewBottom
            // 使用 viewBottom - 1 避免落在边界时的取整歧义
            int pos = findPositionAtY(viewBottom - 1);
            int lastFullLineStart = findLineStart(pos);

            // 验证该行是否完整可见（底部 <= viewBottom）
            Rectangle rect = textBodyPane.modelToView2D(pos).getBounds();
            if (rect == null) return -1;
            if (rect.y + rect.height > viewBottom) {
                // 该行不完整，取上一行
                lastFullLineStart = findPreviousLineStart(lastFullLineStart);
            }

            // 新视口顶部 = 最后一个完整可见行的下一行行首
            int newTop = findNextLineStartAfter(lastFullLineStart, totalLen);

            // 确保至少推进了一行（避免因像素误差导致原地不动）
            if (newTop <= firstLineStart) {
                newTop = findNextLineStartAfter(firstLineStart, totalLen);
            }

            if (newTop >= totalLen) return -1;

            scrollToPosition(newTop);
            setCaretPosition(newTop);
            return newTop;
        } catch (BadLocationException e) {
            return -1;
        }
    }

    /**
     * 向上翻一页（按视口高度逐行计算）
     * 新视口顶部 = 从当前顶部向上数一个视口高度处的行首
     * 对齐到行首，避免截断
     *
     * @return 跳转后的行首字符偏移；已到顶则返回 -1
     */
    public int pageUp() {
        JViewport viewport = textScrollPane.getViewport();
        int viewHeight = viewport.getExtentSize().height;
        int viewTop = viewport.getViewPosition().y;
        int totalLen = textBodyPane.getDocument().getLength();

        if (totalLen == 0) return -1;

        // 目标位置：从当前 viewport 顶部往上翻一个 viewport 高度
        int targetY = Math.max(0, viewTop - viewHeight);
        int pos = findPositionAtY(targetY);

        if (pos <= 0) {
            // 已到文档顶部，无法继续上翻，返回 -1 通知 handler 触发上一章
            return -1;
        }

        // 对齐到该行行首
        int lineStart = findLineStart(pos);

        scrollToPosition(lineStart);
        setCaretPosition(lineStart);
        return lineStart;
    }

    /**
     * 检查是否还能继续向下翻页
     */
    public boolean canPageDown() {
        JViewport viewport = textScrollPane.getViewport();
        int viewHeight = viewport.getExtentSize().height;
        if (viewHeight <= 0) return false;
        Rectangle viewRect = viewport.getViewRect();
        int viewBottom = viewRect.y + viewHeight;

        try {
            int totalLen = textBodyPane.getDocument().getLength();
            if (totalLen == 0) return false;
            Rectangle lastRect = textBodyPane.modelToView2D(totalLen - 1).getBounds();
            if (lastRect == null) return false;
            return (lastRect.y + lastRect.height) > viewBottom;
        } catch (BadLocationException e) {
            return false;
        }
    }

    /**
     * 检查是否还能继续向上翻页
     */
    public boolean canPageUp() {
        return textScrollPane.getViewport().getViewPosition().y > 0;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 根据像素 Y 坐标找到对应的字符偏移
     */
    private int findPositionAtY(float y) {
        int pos = textBodyPane.viewToModel2D(new Point(0, (int) y));
        return pos >= 0 ? pos : textBodyPane.getDocument().getLength() - 1;
    }

    /**
     * 找到包含指定位置的那一行的行首字符偏移
     *
     * @param pos 文档中的任意字符偏移
     * @return 该行行首的字符偏移
     */
    private int findLineStart(int pos) {
        if (pos <= 0) return 0;
        try {
            Rectangle rect = textBodyPane.modelToView2D(pos).getBounds();
            if (rect == null) return pos;
            int lineY = rect.y;
            int lineStart = pos;
            while (lineStart > 0) {
                Rectangle prevRect = textBodyPane.modelToView2D(lineStart - 1).getBounds();
                if (prevRect == null || prevRect.y != lineY) break;
                lineStart--;
            }
            return lineStart;
        } catch (BadLocationException e) {
            return pos;
        }
    }

    /**
     * 从当前行首往后找下一行开头
     *
     * @param lineStart 当前行的行首字符偏移
     * @param totalLen  文档总长度
     * @return 下一行的行首字符偏移，若无则返回 totalLen
     */
    private int findNextLineStartAfter(int lineStart, int totalLen) {
        try {
            Rectangle currentRect = textBodyPane.modelToView2D(lineStart).getBounds();
            if (currentRect == null) return totalLen;
            int currentY = currentRect.y;

            for (int i = lineStart + 1; i < totalLen; i++) {
                Rectangle r = textBodyPane.modelToView2D(i).getBounds();
                if (r == null || r.y == currentY) continue;
                if (r.y > currentY) return i;
            }
        } catch (BadLocationException ignored) {}
        return totalLen;
    }

    /**
     * 找到当前行上一行的行首字符偏移
     *
     * @param lineStart 当前行的行首字符偏移
     * @return 上一行的行首字符偏移，若无则返回 0
     */
    private int findPreviousLineStart(int lineStart) {
        if (lineStart <= 0) return 0;
        try {
            // 取当前行行首前一个字符，看它属于哪一行
            Rectangle currentRect = textBodyPane.modelToView2D(lineStart).getBounds();
            if (currentRect == null) return 0;
            int currentY = currentRect.y;

            // 找到第一个 Y 坐标小于 currentY 的字符
            for (int i = lineStart - 1; i >= 0; i--) {
                Rectangle r = textBodyPane.modelToView2D(i).getBounds();
                if (r == null || r.y >= currentY) continue;
                // 找到上一行的某个字符，返回该行行首
                return findLineStart(i);
            }
        } catch (BadLocationException ignored) {}
        return 0;
    }

    /**
     * 请求焦点到文本面板（用于方向键快捷键）
     */
    public void requestTextFocus() {
        if (textBodyPane != null) {
            textBodyPane.requestFocusInWindow();
        }
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
     * 获取当前是否处于可见（正文）状态
     *
     * @return true 如果内容可见
     */
    public boolean isContentVisible() {
        return contentVisible;
    }

    /**
     * 设置面板的可见状态（由 MainReaderPanel 控制）
     */
    public void setContentVisible(boolean visible) {
        this.contentVisible = visible;
    }
}