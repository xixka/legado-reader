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
import java.awt.event.KeyEvent;
import javax.swing.ScrollPaneConstants;

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
        // 重写 getScrollableTracksViewportHeight() 始终返回 false：
        // JTextPane 默认在「首选高度 ≤ 视口高度」时会让 JScrollPane 把视图拉伸到视口高度，
        // 导致 setViewPosition 被锁死在 (0,0)、章内翻页失效。
        // 隐藏滚动条后视口变宽 → 文本重新换行 → 首选高度减小，极易触发此条件。
        // 固定返回 false 可确保视图始终保持自然高度，setViewPosition 永远可用。
        textBodyPane = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportHeight() {
                return false;
            }
        };
        textBodyPane.setEditable(false);
        textBodyPane.setFocusable(false); // 不可聚焦 → 不显示光标

        textScrollPane = new JBScrollPane(textBodyPane);
        textScrollPane.setOpaque(false);
        textScrollPane.getViewport().setOpaque(false);
        textBodyContentPanel.add(textScrollPane, CARD_CONTENT);

        // 1.2 错误卡片
        textBodyContentPanel.add(wrapCentered(createErrorLabel()), CARD_ERROR);

        this.add(textBodyContentPanel, BorderLayout.CENTER);

        // 面板自身可聚焦（用于方向键快捷键），textBodyPane 不聚焦（避免光标）
        setFocusable(true);

        // 默认显示内容
        showContent();

        // 注册方向键快捷键（通过 KeyEventDispatcher，焦点在面板内时生效）
        registerArrowKeyDispatcher();
    }

    /**
     * 注册方向键全局监听器，仅在焦点位于本面板内时响应
     * ↑ 上一页  |  ↓ 下一页  |  ← 上一章  |  → 下一章
     */
    private void registerArrowKeyDispatcher() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            // 焦点不在本面板内则不处理（失去焦点自动失效）
            if (!isFocusInsidePanel()) return false;

            switch (e.getKeyCode()) {
                case KeyEvent.VK_UP:
                    doPageUp();
                    return true;
                case KeyEvent.VK_DOWN:
                    doPageDown();
                    return true;
                case KeyEvent.VK_LEFT:
                    CommandBus.getInstance().dispatchAsync(Command.of(CommandType.PREVIOUS_CHAPTER));
                    return true;
                case KeyEvent.VK_RIGHT:
                    CommandBus.getInstance().dispatchAsync(Command.of(CommandType.NEXT_CHAPTER));
                    return true;
                default:
                    return false;
            }
        });
    }

    /**
     * 检查当前焦点是否在本面板内
     */
    private boolean isFocusInsidePanel() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) return false;
        // focusOwner 是本面板或本面板的子孙 → true
        return SwingUtilities.isDescendingFrom(focusOwner, this);
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
        log.info("doPageDown: 方向键触发翻页, canPageDown={}", canPageDown());
        if (canPageDown()) {
            int newPos = pageDown();
            log.info("doPageDown: pageDown 返回 {}", newPos);
            if (newPos >= 0) return;
        }
        // 已到底部，触发下一章
        log.info("doPageDown: 触发下一章");
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
        JViewport viewport = textScrollPane.getViewport();
        // 确保 viewSize 反映文本实际渲染高度，避免 setViewPosition 被 clamp 到 0
        ensureViewSizeAccurate(viewport);
        if (position <= 0) {
            // 滚动到文档最顶部，直接设为 (0, 0)，避免 margin 影响
            viewport.setViewPosition(new Point(0, 0));
            return;
        }
        try {
            int totalLen = textBodyPane.getDocument().getLength();
            // 滚动到文档末尾：将视口底部对齐到文档末尾
            if (position >= totalLen - 1) {
                Rectangle endRect = textBodyPane.modelToView2D(totalLen - 1).getBounds();
                if (endRect != null) {
                    int viewHeight = viewport.getExtentSize().height;
                    int y = Math.max(0, endRect.y + endRect.height - viewHeight);
                    viewport.setViewPosition(new Point(0, y));
                }
                return;
            }
            Rectangle viewRect = textBodyPane.modelToView2D(position).getBounds();
            if (viewRect == null) return;
            viewport.setViewPosition(new Point(0, viewRect.y));
        } catch (BadLocationException e) {
            // 忽略无效位置
        }
    }

    /**
     * 确保 viewport 的 viewSize 反映文本实际渲染高度
     * <p>
     * JTextPane 的 getPreferredSize()（由 TextUI 基于 View 首选宽度计算）
     * 可能与按视口宽度换行后的实际布局高度不一致——preferredSize 偏小时，
     * ScrollPaneLayout 设置的 viewSize.height ≤ extentSize.height，
     * 导致 setViewPosition 被 clamp 到 0，视口纹丝不动、翻页失效。
     * 此方法用 modelToView2D 计算文本实际底部，修正 viewSize。
     */
    private void ensureViewSizeAccurate(JViewport viewport) {
        int totalLen = textBodyPane.getDocument().getLength();
        if (totalLen <= 0) return;
        try {
            Rectangle lastRect = textBodyPane.modelToView2D(totalLen - 1).getBounds();
            if (lastRect == null) return;
            int docBottom = lastRect.y + lastRect.height;
            Dimension viewSize = viewport.getViewSize();
            if (viewSize.height < docBottom) {
                viewport.setViewSize(new Dimension(viewSize.width, docBottom));
            }
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

        log.info("pageDown: viewSize={}, extentSize={}, viewPosition.y={}, viewBottom={}",
                viewport.getViewSize(), viewport.getExtentSize(), viewTop, viewBottom);

        try {
            int totalLen = textBodyPane.getDocument().getLength();
            if (totalLen == 0) return -1;

            // 找到 viewBottom 对应的字符，确定"完全不可见的下一行"
            int pos = findPositionAtY(viewBottom);
            Rectangle rect = textBodyPane.modelToView2D(pos).getBounds();
            if (rect == null) return -1;

            int newTop;
            if (rect.y >= viewBottom) {
                // 该行行首已在视口底部之下（完全不可见），作为新视口顶部
                newTop = findLineStart(pos);
            } else {
                // 该行部分可见，新视口从它的下一行开始（零重叠）
                int lineStart = findLineStart(pos);
                newTop = findNextLineStartAfter(lineStart, totalLen);
            }

            // newTop 已超出文档末尾 → 到底
            if (newTop >= totalLen) {
                log.info("pageDown: newTop={} >= totalLen={}, 返回 -1", newTop, totalLen);
                return -1;
            }

            // 关键：newTop 所在行必须在当前视口中完全不可见
            // 如果可见（剩余内容不足一页），不滚动，返回 -1 触发下一章
            Rectangle newTopRect = textBodyPane.modelToView2D(newTop).getBounds();
            if (newTopRect == null || newTopRect.y < viewBottom) {
                log.info("pageDown: newTopRect.y={} < viewBottom={}, 返回 -1",
                        newTopRect == null ? "null" : newTopRect.y, viewBottom);
                return -1;
            }

            log.info("pageDown: 准备滚动到 newTop={}, newTopRect.y={}", newTop, newTopRect.y);
            scrollToPosition(newTop);
            int newViewTop = viewport.getViewPosition().y;
            log.info("pageDown: scrollToPosition 后 viewPosition.y={} (期望={})", newViewTop, newTopRect.y);
            setCaretPosition(newTop);
            return newTop;
        } catch (BadLocationException e) {
            log.warn("pageDown: BadLocationException", e);
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
            // 已到文档顶部，滚动到文档开头
            // 返回 0 表示成功（不触发上一章），上一章由 canPageUp() 为 false 时触发
            scrollToPosition(0);
            setCaretPosition(0);
            return 0;
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
        if (pos >= 0) return pos;
        // viewToModel2D 返回 -1 时，y 在文档范围之外
        // y <= 0 说明在文档上方，返回 0（首字符）
        return y <= 0 ? 0 : textBodyPane.getDocument().getLength() - 1;
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
     * 设置滚动条显隐
     *
     * @param hide true 隐藏滚动条，false 显示滚动条
     */
    public void setScrollBarVisible(boolean hide) {
        textScrollPane.setVerticalScrollBarPolicy(
                hide ? ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
                     : ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        );
    }

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