package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.handler;

import com.intellij.ui.JBColor;
import com.nancheung.plugins.jetbrains.legadoreader.event.CacheEvent;
import com.nancheung.plugins.jetbrains.legadoreader.event.PaginationEvent;
import com.nancheung.plugins.jetbrains.legadoreader.event.ReadingEvent;
import com.nancheung.plugins.jetbrains.legadoreader.event.SettingsChangedEvent;
import com.nancheung.plugins.jetbrains.legadoreader.manager.ReadingSessionManager;
import com.nancheung.plugins.jetbrains.legadoreader.model.ReadingSession;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.MainReaderPanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel.BookshelfPanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel.CacheProgressPanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel.TextBodyPanel;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;

/**
 * 主面板事件处理器
 * 负责处理所有 UI 事件的业务逻辑
 *
 * @author NanCheung
 */
@Slf4j
public class MainPanelEventHandler {

    // ==================== UI 状态枚举 ====================
    private enum UIState {
        INITIALIZED,
        LOADING,
        SUCCESS,
        FAILED
    }

    private UIState currentState = UIState.INITIALIZED;

    // ==================== 面板引用 ====================
    private final MainReaderPanel mainPanel;
    private final BookshelfPanel bookshelfPanel;
    private final TextBodyPanel textBodyPanel;
    private CacheProgressPanel cacheProgressPanel;

    // ==================== 构造函数 ====================
    public MainPanelEventHandler(
            MainReaderPanel mainPanel,
            BookshelfPanel bookshelfPanel,
            TextBodyPanel textBodyPanel) {
        this.mainPanel = mainPanel;
        this.bookshelfPanel = bookshelfPanel;
        this.textBodyPanel = textBodyPanel;
    }

    /**
     * 设置缓存进度面板引用（由 MainReaderPanel 在创建 CacheProgressPanel 后调用）
     */
    public void setCacheProgressPanel(CacheProgressPanel cacheProgressPanel) {
        this.cacheProgressPanel = cacheProgressPanel;
    }

    // ==================== 事件处理方法 ====================

    /**
     * 处理阅读事件
     */
    public void handleReadingEvent(ReadingEvent event) {
        switch (event.type()) {
            case CHAPTER_LOADING -> handleLoadingStarted(event);
            case CHAPTER_LOADED -> handleLoadingSuccess(event);
            case CHAPTER_LOAD_FAILED -> handleLoadingFailed(event);
            case SESSION_ENDED -> handleSessionEnded();
        }
    }

    /**
     * 处理分页事件
     * <p>
     * 翻页已改为按视口高度直接跳转（TextBodyPanel.pageDown/pageUp），
     * 不再依赖 PaginationManager 的预切页。
     * PaginationEvent 仅由 EditorLine 模式使用，ToolWindow 模式无需处理。
     */
    public void handlePaginationEvent(PaginationEvent event) {
        // 翻页已由 TextBodyPanel.pageDown()/pageUp() 直接处理
        // PaginationEvent 仅保留给 EditorLine 模式
    }

    /**
     * 处理设置变更事件
     * 当用户在设置页面保存字体设置后，立即刷新 UI
     */
    public void handleSettingsChangedEvent(SettingsChangedEvent event) {
        // 只处理字体设置变更
        if (event.type() != SettingsChangedEvent.SettingsChangedType.FONT_SETTINGS
                && event.type() != SettingsChangedEvent.SettingsChangedType.ALL_SETTINGS) {
            return;
        }

        log.info("收到设置变更事件，刷新正文面板字体样式");

        // 只有在正文面板可见且有内容时才更新
        if (!textBodyPanel.isContentVisible()) {
            log.debug("正文面板不可见，跳过字体刷新");
            return;
        }

        ReadingSession session = ReadingSessionManager.getInstance().getSession();
        if (session == null || session.currentContent() == null) {
            log.debug("无当前阅读会话，跳过字体刷新");
            return;
        }

        // 从事件中获取新设置（优先使用事件中的值，回退到存储）
        PluginSettingsStorage.State state = PluginSettingsStorage.getInstance().getState();
        JBColor fontColor = event.fontColor() != null ? event.fontColor() : state.textBodyFontColor;
        Font font = event.font() != null ? event.font() : state.textBodyFont;
        double lineHeight = event.lineHeight() != null ? event.lineHeight() : state.textBodyLineHeight;

        // 更新正文面板的字体样式
        textBodyPanel.applyStyle(fontColor, font, lineHeight);

        log.debug("字体样式已更新：color={}, font={}, lineHeight={}",
                fontColor, font.getFamily() + "/" + font.getSize(), lineHeight);
    }

    /**
     * 处理离线缓存事件
     * 委托给 CacheProgressPanel 更新进度展示，并同步刷新书架表格的缓存状态列
     */
    public void handleCacheEvent(CacheEvent event) {
        if (cacheProgressPanel != null) {
            cacheProgressPanel.handleCacheEvent(event);
        }
        // 同步刷新书架表格的缓存状态列（STARTED/PROGRESS/COMPLETED/FAILED/CANCELED 都要更新）
        if (bookshelfPanel != null) {
            bookshelfPanel.refreshCacheStatus();
        }
    }

    // ==================== 私有处理方法 ====================

    /**
     * 处理"开始加载"事件
     */
    private void handleLoadingStarted(ReadingEvent event) {
        currentState = UIState.LOADING;

        log.info("UI 进入加载状态: book={}, chapterIndex={}",
                event.book().getName(), event.chapter().getIndex());

        // 切换到正文面板并显示内容区
        mainPanel.showTextBodyPanel();
        textBodyPanel.showContent();

        // 设置加载提示
        textBodyPanel.setText("加载中...");

        // 请求焦点，使方向键快捷键可用
        textBodyPanel.requestFocusInWindow();
    }

    /**
     * 处理"加载成功"事件
     */
    private void handleLoadingSuccess(ReadingEvent event) {
        currentState = UIState.SUCCESS;

        log.info("UI 加载成功: book={}, chapter={}",
                event.book().getName(), event.chapter().getTitle());

        // 设置正文内容
        String title = event.chapter().getTitle();
        String content = event.content();
        textBodyPanel.setText(title + "\n" + content);

        // 应用样式（在 setText 之后）
        textBodyPanel.applyStyleFromSettings();

        // 设置光标位置：始终回到章节顶部
        textBodyPanel.setCaretPosition(event.chapterPosition());

        // 请求焦点到面板，使方向键快捷键立即生效
        textBodyPanel.requestFocusInWindow();
    }

    /**
     * 处理"加载失败"事件
     */
    private void handleLoadingFailed(ReadingEvent event) {
        currentState = UIState.FAILED;

        log.warn("UI 加载失败: book={}, chapterIndex={}, error={}",
                event.book().getName(),
                event.chapter().getIndex(),
                event.error() != null ? event.error().getMessage() : "Unknown");

        // 显示错误提示
        textBodyPanel.showError();

        // 记录详细错误日志
        if (Boolean.TRUE.equals(PluginSettingsStorage.getInstance().getState().enableErrorLog)) {
            log.error("章节加载失败", event.error());
        }
    }

    /**
     * 处理"会话结束"事件
     */
    private void handleSessionEnded() {
        currentState = UIState.INITIALIZED;
        log.info("会话结束，返回书架");

        // 显示书架面板
        mainPanel.showBookshelfPanel();

        // 清空正文内容
        textBodyPanel.setText("");
    }
}