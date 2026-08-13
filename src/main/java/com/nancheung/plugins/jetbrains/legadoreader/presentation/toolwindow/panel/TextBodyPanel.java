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
     * 向下翻一页（按视口高度逐行计算，部分可见的行不计入）
     * 新视口顶部 = 当前视口最后一个完整可见行的下一行行首
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

            // 找到 viewport 底部像素位置对应的字符偏移
            int pos = findPositionAtY(viewBottom);

            // 获取该位置所在行的信息
            Rectangle rect = textBodyPane.modelToView2D(pos).getBounds();
            if (rect == null) return -1;
            int lineTop = rect.y;
            int lineHeight = rect.height;

            // 确定"当前视口最后一个完整可见行"的字符位置
            int lastFullLinePos;
            if ((lineTop + lineHeight) > viewBottom) {
                // 底部行不完整，最后一个完整行是上一行
                if (lineTop <= 0) {
                    lastFullLinePos = 0;
                } else {
                    lastFullLinePos = findPositionAtY(lineTop - 1);
                }
            } else {
                // 底部行完整，它就是最后一个完整行
                lastFullLinePos = pos;
            }

            // 新视口顶部 = 最后一个完整行的下一行行首
            int newTop = findNextLineStart(lastFullLinePos, totalLen);

            // 确保至少推进了一行（避免原地不动）
            int currentTopPos = findPositionAtY(viewTop + 1);
            if (newTop <= currentTopPos) {
                newTop = findNextLineStart(currentTopPos, totalLen);
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
     * 新视口顶部对齐到目标行的行首，避免截断导致少翻
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
        float targetY = Math.max(0, viewTop - viewHeight);
        int pos = findPositionAtY(targetY);

        if (pos <= 0) {
            scrollToPosition(0);
            setCaretPosition(0);
            return 0;
        }

        // 对齐到该行行首，避免 scrollToPosition 把行中间推到视口顶部
        Rectangle rect;
        try {
            rect = textBodyPane.modelToView2D(pos).getBounds();
        } catch (BadLocationException e) {
            rect = null;
        }
        if (rect != null) {
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
            pos = lineStart;
        }

        scrollToPosition(pos);
        setCaretPosition(pos);
        return pos;
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
     * 从指定位置开始找下一行的行首
     */
    private int findNextLineStart(int fromPos, int totalLen) {
        int pos = fromPos;
        while (pos < totalLen) {
            try {
                Rectangle rect = textBodyPane.modelToView2D(pos).getBounds();
                if (rect == null) { pos++; continue; }
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
                if (r == null || r.y == currentBaseline) continue;
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