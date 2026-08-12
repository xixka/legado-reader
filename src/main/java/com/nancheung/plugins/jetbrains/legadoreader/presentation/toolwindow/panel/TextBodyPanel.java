package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.styling.TextBodyStyling;

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
        textBodyPane.setFocusable(false);
        textBodyPane.getCaret().setVisible(false);

        textScrollPane = new JBScrollPane(textBodyPane);
        textScrollPane.setOpaque(false);
        textScrollPane.getViewport().setOpaque(false);
        textBodyContentPanel.add(textScrollPane, CARD_CONTENT);

        // 1.2 错误卡片
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
     * 无动画覆盖滑动：直接将目标位置推到视口顶部
     *
     * @param position 目标字符偏移
     */
    public void scrollToPosition(int position) {
        try {
            Rectangle viewRect = textBodyPane.modelToView2D(position).getBounds();
            JViewport viewport = textScrollPane.getViewport();
            viewport.setViewPosition(new Point(0, viewRect.y));
        } catch (BadLocationException e) {
            // 忽略无效位置
        }
    }

    /**
     * 向下翻一页（按视口高度逐行计算，部分可见的行不计入）
     *
     * @return 跳转后的行首字符偏移；已到底则返回 -1
     */
    public int pageDown() {
        JViewport viewport = textScrollPane.getViewport();
        int viewHeight = viewport.getExtentSize().height;
        int viewTop = viewport.getViewPosition().y;
        int viewBottom = viewTop + viewHeight;

        try {
            int totalLen = textBodyPane.getDocument().getLength();
            if (totalLen == 0) return -1;

            // 找到 viewport 底部像素位置对应的字符偏移（可能在一行中间）
            float pixelY = viewBottom;
            int pos = findPositionAtY(pixelY);

            // 获取该位置所在行的 baseline
            Rectangle rect = textBodyPane.modelToView2D(pos).getBounds();
            int lastVisibleLineBaseline = rect.y;
            int lastVisibleLineHeight = rect.height;

            // 判断此行是否完整可见（行底部 ≤ viewBottom）
            if ((lastVisibleLineBaseline + lastVisibleLineHeight) > viewBottom) {
                // 行不完整，从上一行的行首开始
                int prevLineEnd = findPositionAtY(lastVisibleLineBaseline - 1);
                pos = prevLineEnd;
            }

            // 确保跳过了当前 viewport 内的内容（至少推进一行）
            if (pos <= findPositionAtY(viewTop + 1)) {
                // 找下一行开头
                pos = findNextLineStart(pos, totalLen);
            }

            if (pos >= totalLen) return -1;

            scrollToPosition(pos);
            setCaretPosition(pos);
            return pos;
        } catch (BadLocationException e) {
            return -1;
        }
    }

    /**
     * 向上翻一页（按视口高度逐行计算）
     *
     * @return 跳转后的行首字符偏移；已到顶则返回 -1
     */
    public int pageUp() {
        JViewport viewport = textScrollPane.getViewport();
        int viewHeight = viewport.getExtentSize().height;
        int viewTop = viewport.getViewPosition().y;

        try {
            int totalLen = textBodyPane.getDocument().getLength();
            if (totalLen == 0) return -1;

            // 目标位置：从当前 viewport 顶部往上翻一个 viewport 高度
            float targetY = Math.max(0, viewTop - viewHeight);
            int pos = findPositionAtY(targetY);

            if (pos <= 0) {
                scrollToPosition(0);
                setCaretPosition(0);
                return 0;
            }

            scrollToPosition(pos);
            setCaretPosition(pos);
            return pos;
        } catch (BadLocationException e) {
            return -1;
        }
    }

    /**
     * 检查是否还能继续向下翻页
     */
    public boolean canPageDown() {
        JViewport viewport = textScrollPane.getViewport();
        int viewHeight = viewport.getExtentSize().height;
        Rectangle viewRect = viewport.getViewRect();
        int viewBottom = viewRect.y + viewHeight;

        try {
            int totalLen = textBodyPane.getDocument().getLength();
            Rectangle lastRect = textBodyPane.modelToView2D(totalLen - 1).getBounds();
            if (lastRect == null) return false;
            // 文档底部的 Y + 行高 <= viewport 底部 → 到底了
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
        try {
            return textBodyPane.viewToModel2D(new Point(0, (int) y));
        } catch (BadLocationException e) {
            return textBodyPane.getDocument().getLength() - 1;
        }
    }

    /**
     * 从指定位置开始找下一行的行首
     */
    private int findNextLineStart(int fromPos, int totalLen) {
        int pos = fromPos;
        while (pos < totalLen) {
            try {
                Rectangle rect = textBodyPane.modelToView2D(pos).getBounds();
                int lineBaseline = rect.y;
                // 往前扫到这一行的真正开头
                int lineStart = pos;
                while (lineStart > 0) {
                    try {
                        Rectangle prevRect = textBodyPane.modelToView2D(lineStart - 1).getBounds();
                        if (prevRect == null || prevRect.y != lineBaseline) break;
                        lineStart--;
                    } catch (BadLocationException e) {
                        break;
                    }
                }
                // 再往后找下一行的第一个字符
                return findNextLineStartAfter(lineStart, totalLen);
            } catch (BadLocationException e) {
                pos++;
            }
        }
        return totalLen;
    }

    /**
     * 从当前行首往后找下一行开头
     */
    private int findNextLineStartAfter(int lineStart, int totalLen) {
        try {
            Rectangle currentRect = textBodyPane.modelToView2D(lineStart).getBounds();
            if (currentRect == null) return totalLen;
            int currentBaseline = currentRect.y;

            for (int i = lineStart + 1; i < totalLen; i++) {
                Rectangle r = textBodyPane.modelToView2D(i).getBounds();
                if (r == null) continue;
                if (r.y > currentBaseline) return i;
            }
        } catch (BadLocationException ignored) {}
        return totalLen;
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
}