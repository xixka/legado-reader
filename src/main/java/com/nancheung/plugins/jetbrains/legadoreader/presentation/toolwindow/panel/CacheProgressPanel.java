package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.nancheung.plugins.jetbrains.legadoreader.event.CacheEvent;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandBus;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;

/**
 * 离线缓存进度面板
 * <p>
 * 平时隐藏，当收到 {@link CacheEvent#type() == STARTED} 时显示，
 * 收到 {@link CacheEvent#type() == PROGRESS} 时更新进度条，
 * 收到 {@link CacheEvent#type() == COMPLETED/FAILED/CANCELED} 时收尾。
 *
 * @author NanCheung
 */
@Slf4j
public class CacheProgressPanel extends JBPanel<CacheProgressPanel> {

    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JButton cancelButton;

    /**
     * 当前正在展示的 bookUrl（用于校验事件归属）
     */
    private String currentBookUrl;

    public CacheProgressPanel() {
        super(new BorderLayout(0, 0));
        setOpaque(false);
        setBorder(JBUI.Borders.empty(4, 8));

        // 状态标签
        statusLabel = new JBLabel("离线缓存：等待中...");
        statusLabel.setForeground(JBColor.GRAY);

        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setPreferredSize(JBUI.size(120, 16));

        // 取消按钮
        cancelButton = new JButton("取消");
        cancelButton.setToolTipText("取消当前缓存任务");
        cancelButton.addActionListener(e -> {
            if (currentBookUrl != null) {
                CommandBus.getInstance().dispatchAsync(
                        Command.of(CommandType.CANCEL_CACHE_BOOK)
                );
            }
        });

        // 左侧：状态文字
        add(statusLabel, BorderLayout.WEST);

        // 中部：进度条
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(progressBar, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // 右侧：取消按钮
        add(cancelButton, BorderLayout.EAST);

        // 默认隐藏
        setVisible(false);
    }

    /**
     * 处理缓存事件（由 MainPanelEventHandler 在 EDT 中调用）
     */
    public void handleCacheEvent(CacheEvent event) {
        switch (event.type()) {
            case STARTED -> showStarted(event);
            case PROGRESS -> showProgress(event);
            case COMPLETED -> showCompleted(event);
            case FAILED -> showFailed(event);
            case CANCELED -> showCanceled(event);
        }
    }

    private void showStarted(CacheEvent event) {
        this.currentBookUrl = event.bookUrl();
        setVisible(true);
        cancelButton.setEnabled(true);
        statusLabel.setText(String.format("离线缓存《%s》：准备中 (%d 章)",
                safeName(event.bookName()), event.totalChapters()));
        progressBar.setValue(0);
        progressBar.setString("0%");
        revalidate();
        repaint();
    }

    private void showProgress(CacheEvent event) {
        if (!matchesCurrent(event)) {
            return;
        }
        setVisible(true);
        int percent = event.progressPercent();
        progressBar.setValue(percent);
        progressBar.setString(percent + "%");
        statusLabel.setText(String.format("离线缓存《%s》：%d / %d 章",
                safeName(event.bookName()), event.cachedChapters(), event.totalChapters()));
    }

    private void showCompleted(CacheEvent event) {
        if (!matchesCurrent(event)) {
            return;
        }
        progressBar.setValue(100);
        progressBar.setString("完成");
        statusLabel.setText(String.format("《%s》缓存完成（共 %d 章）",
                safeName(event.bookName()), event.totalChapters()));
        cancelButton.setEnabled(false);
        // 2 秒后自动隐藏
        Timer timer = new Timer(2000, e -> hidePanel());
        timer.setRepeats(false);
        timer.start();
    }

    private void showFailed(CacheEvent event) {
        if (!matchesCurrent(event)) {
            return;
        }
        int percent = event.progressPercent();
        progressBar.setValue(percent);
        progressBar.setString("失败");
        statusLabel.setForeground(JBColor.RED);
        String reason = event.message() != null ? event.message() : "未知错误";
        statusLabel.setText(String.format("《%s》缓存失败：%s（已缓存 %d/%d 章）",
                safeName(event.bookName()), reason, event.cachedChapters(), event.totalChapters()));
        cancelButton.setEnabled(false);
        // 5 秒后自动隐藏，并恢复颜色
        Timer timer = new Timer(5000, e -> {
            statusLabel.setForeground(JBColor.GRAY);
            hidePanel();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void showCanceled(CacheEvent event) {
        if (!matchesCurrent(event)) {
            return;
        }
        int percent = event.progressPercent();
        progressBar.setValue(percent);
        progressBar.setString("已取消");
        statusLabel.setText(String.format("《%s》缓存已取消（已缓存 %d/%d 章）",
                safeName(event.bookName()), event.cachedChapters(), event.totalChapters()));
        cancelButton.setEnabled(false);
        // 3 秒后自动隐藏
        Timer timer = new Timer(3000, e -> hidePanel());
        timer.setRepeats(false);
        timer.start();
    }

    private void hidePanel() {
        setVisible(false);
        currentBookUrl = null;
        statusLabel.setForeground(JBColor.GRAY);
    }

    private boolean matchesCurrent(CacheEvent event) {
        // 没有当前任务时显示任意事件；有当前任务时只显示对应 book 的事件
        return currentBookUrl == null
                || event.bookUrl() == null
                || currentBookUrl.equals(event.bookUrl());
    }

    private String safeName(String name) {
        return name == null ? "未知书籍" : name;
    }
}
