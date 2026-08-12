package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBPanel;
import com.nancheung.plugins.jetbrains.legadoreader.event.PaginationEvent;
import com.nancheung.plugins.jetbrains.legadoreader.event.ReadingEvent;
import com.nancheung.plugins.jetbrains.legadoreader.event.SettingsChangedEvent;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.common.UIEventSubscriber;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.styling.TextBodyStyling;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel.TextBodyPanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel.BookshelfPanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel.ChapterListPanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.handler.MainPanelEventHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

@Slf4j
@Getter
public class MainReaderPanel extends UIEventSubscriber {

    // ==================== 卡片常量 ====================
    private static final String CARD_BOOKSHELF = "BOOKSHELF";
    private static final String CARD_TEXT_BODY = "TEXT_BODY";
    private static final String CARD_CHAPTER_LIST = "CHAPTER_LIST";

    // ==================== 根面板 ====================
    private JBPanel<?> rootPanel;
    private CardLayout mainCardLayout;

    // ==================== 子面板组件 ====================
    private BookshelfPanel bookshelfPanel;
    private TextBodyPanel textBodyPanel;
    private ChapterListPanel chapterListPanel;

    // ==================== 事件处理器 ====================
    private final MainPanelEventHandler eventHandler;

    // ==================== 单例实例 ====================
    private static MainReaderPanel INSTANCE;

    // ==================== 样式管理器 ====================
    private final TextBodyStyling textBodyStyling = new TextBodyStyling();

    // ==================== ToolWindow 标题栏按钮（动态切换） ====================
    /**
     * 当前绑定的 ToolWindow。
     * 由于 MainReaderPanel 为单例，多项目场景下该引用会被后打开的项目覆盖，
     * 与现有"多窗口共享状态"设计保持一致。
     */
    private ToolWindow toolWindow;
    /**
     * 正文阅读工具栏按钮组（返回 / 上下章 / 上下页 / 章节列表）。
     * 仅在正文面板或章节列表面板时挂到 ToolWindow 标题栏右侧；书架面板时清空。
     */
    private List<AnAction> titleActions = Collections.emptyList();
    /**
     * 当前显示的卡片（用来让返回按钮知道跳哪里：章节列表→正文，正文→书架）
     */
    private String currentCard = CARD_BOOKSHELF;
    /**
     * 当前是否处于正文面板或章节列表面板（决定 titleActions 是否挂载）
     */
    private boolean textBodyVisible = false;

    // ==================== 构造函数 ====================
    public MainReaderPanel() {
        super();

        // 创建 UI 组件
        createRootPanel();

        // 创建事件处理器
        this.eventHandler = new MainPanelEventHandler(this, bookshelfPanel, textBodyPanel);

        // 初始加载书架
        // First load is triggered in initAddressHistory() once the address field is ready
    }

    // ==================== 组件创建方法 ====================

    /**
     * 创建根面板
     */
    private void createRootPanel() {
        mainCardLayout = new CardLayout();
        rootPanel = new JBPanel<>(mainCardLayout);
        rootPanel.setOpaque(false);

        // 创建书架面板（传入书籍选择回调）
        bookshelfPanel = new BookshelfPanel();
        rootPanel.add(bookshelfPanel, CARD_BOOKSHELF);

        // 创建正文面板
        textBodyPanel = new TextBodyPanel();
        rootPanel.add(textBodyPanel, CARD_TEXT_BODY);

        // 创建章节列表面板
        chapterListPanel = new ChapterListPanel();
        rootPanel.add(chapterListPanel, CARD_CHAPTER_LIST);

        // 默认显示书架
        mainCardLayout.show(rootPanel, CARD_BOOKSHELF);
    }


    // ==================== 初始化方法 ====================


    /**
     * 初始加载书架
     */
    private void initialLoadBookshelf() {
        bookshelfPanel.refreshBookshelf();
    }

    // ==================== 面板切换方法 ====================

    /**
     * 显示书架面板
     */
    public void showBookshelfPanel() {
        currentCard = CARD_BOOKSHELF;
        mainCardLayout.show(rootPanel, CARD_BOOKSHELF);
        textBodyVisible = false;
        if (textBodyPanel != null) {
            textBodyPanel.setContentVisible(false);
        }
        updateTitleActions();
    }

    /**
     * 显示正文面板
     */
    public TextBodyPanel getTextBodyPanel() {
        return textBodyPanel;
    }

    public void showTextBodyPanel() {
        currentCard = CARD_TEXT_BODY;
        mainCardLayout.show(rootPanel, CARD_TEXT_BODY);
        textBodyVisible = true;
        if (textBodyPanel != null) {
            textBodyPanel.setContentVisible(true);
        }
        updateTitleActions();
    }

    /**
     * 显示章节列表面板
     * 调用前会刷新章节列表以确保数据最新
     */
    public void showChapterListPanel() {
        currentCard = CARD_CHAPTER_LIST;
        chapterListPanel.refreshChapters();
        mainCardLayout.show(rootPanel, CARD_CHAPTER_LIST);
        textBodyVisible = true;
        if (textBodyPanel != null) {
            textBodyPanel.setContentVisible(false);
        }
        updateTitleActions();
    }

    // ==================== ToolWindow 标题栏按钮管理 ====================

    /**
     * 绑定 ToolWindow（由 MainReaderPanelFactory 在创建 ToolWindow 内容时调用）
     */
    public void setToolWindow(ToolWindow toolWindow) {
        this.toolWindow = toolWindow;
        updateTitleActions();
    }

    /**
     * 设置正文阅读工具栏按钮组
     */
    public void setTitleActions(List<AnAction> titleActions) {
        this.titleActions = titleActions == null ? Collections.emptyList() : titleActions;
        updateTitleActions();
    }

    /**
     * 根据当前卡片动态挂载/卸载 ToolWindow 标题栏按钮
     * - 正文面板：挂载按钮组（放在标题 "Reader" 后面）
     * - 书架面板：清空按钮组
     */
    private void updateTitleActions() {
        if (toolWindow == null) {
            return;
        }
        // ToolWindow 的 setTitleActions 必须在 EDT 调用
        ApplicationManager.getApplication().invokeLater(() -> {
            if (toolWindow == null) {
                return;
            }
            if (textBodyVisible && titleActions != null && !titleActions.isEmpty()) {
                toolWindow.setTitleActions(titleActions);
            } else {
                toolWindow.setTitleActions(Collections.emptyList());
            }
        });
    }

    // ==================== 公共接口方法 ====================

    /**
     * 获取根组件
     */
    public JComponent getComponent() {
        return rootPanel;
    }

    /**
     * 获取当前显示的卡片标识
     */
    public String getCurrentCard() {
        return currentCard;
    }

    /**
     * 获取单例实例
     */
    public static MainReaderPanel getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MainReaderPanel();
        }
        return INSTANCE;
    }

    /**
     * 初始化地址历史记录
     * 在 ToolWindow 首次显示时调用
     */
    public void initAddressHistory() {
        bookshelfPanel.initAddressHistory();
        // Trigger the first load only after the address field has been initialized
        bookshelfPanel.refreshBookshelf();
    }

    // ==================== 事件处理方法 ====================

    /**
     * 重写父类方法：处理阅读事件
     */
    @Override
    protected void onReadingEvent(ReadingEvent event) {
        eventHandler.handleReadingEvent(event);
    }

    /**
     * 重写父类方法：处理分页事件
     */
    @Override
    protected void onPaginationEvent(PaginationEvent event) {
        eventHandler.handlePaginationEvent(event);
    }

    /**
     * 重写父类方法：处理设置变更事件
     */
    @Override
    protected void onSettingsChangedEvent(SettingsChangedEvent event) {
        eventHandler.handleSettingsChangedEvent(event);
    }
}
