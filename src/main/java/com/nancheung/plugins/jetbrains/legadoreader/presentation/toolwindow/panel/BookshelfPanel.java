package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.nancheung.plugins.jetbrains.legadoreader.api.ApiUtil;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandBus;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.CacheBookPayload;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.SelectBookPayload;
import com.nancheung.plugins.jetbrains.legadoreader.service.OfflineCacheService;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.cache.BookCacheStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.BookCacheMeta;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 书架面板组件
 * 负责显示书架列表、地址栏和错误提示
 *
 * @author NanCheung
 */
@Slf4j
public class BookshelfPanel extends JBPanel<BookshelfPanel> {

    // ==================== 卡片常量 ====================
    private static final String CARD_CONTENT = "CONTENT";
    private static final String CARD_ERROR = "ERROR";

    // ==================== 错误提示文本 ====================
    private static final String ERROR_MESSAGE = """
            请求内容失败，请检查web服务是否开启、url是否正确、网络是否正常？
            
            小提示：可以在File -> Settings -> Tools -> Legado Reader中进行更多设置哦~
            也可以在 Keymap -> Plugins -> Legado Reader 中查看所有快捷键并进行自定义设置~
            """;

    // ==================== UI 组件 ====================
    private final AddressBarPanel<List<BookDTO>> addressBarPanel;
    private final JBTable bookshelfTable;
    private final JBPanel<?> bookshelfContentPanel;
    private final CardLayout bookshelfContentLayout;
    private JButton cacheButton;
    private JButton cancelButton;

    // ==================== 数据模型（静态，多窗口共享） ====================
    private static final DefaultTableModel BOOK_SHELF_TABLE_MODEL =
            new DefaultTableModel(null, new String[]{"书名", "当前章节", "最新章节", "作者", "缓存状态"}) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    // ==================== 书架数据 ====================
    private Map<String, BookDTO> bookshelf;
    private static final BiFunction<String, String, String> BOOK_MAP_KEY_FUNC = (author, name) -> author + "#" + name;

    // ==================== 构造函数 ====================
    public BookshelfPanel() {
        super(new BorderLayout());
        setOpaque(false);

        // 1. 创建地址栏组件，传入在线加载动作、离线模式回调、成功/失败回调
        addressBarPanel = new AddressBarPanel<>(ApiUtil::getBookshelf, this::loadOfflineBookshelf, this::handleBooksLoaded, this::handleLoadFailed);
        this.add(addressBarPanel, BorderLayout.NORTH);

        // 2. 中央内容区（使用 CardLayout 切换内容/错误）
        bookshelfContentLayout = new CardLayout();
        bookshelfContentPanel = new JBPanel<>(bookshelfContentLayout);
        bookshelfContentPanel.setOpaque(false);

        // 2.1 内容卡片：书架表格
        bookshelfTable = createBookshelfTable();
        bookshelfTable.setOpaque(false);
        // 设置列宽：缓存状态列收紧
        bookshelfTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        bookshelfTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        bookshelfTable.getColumnModel().getColumn(4).setMaxWidth(100);
        JBScrollPane shelfScrollPane = new JBScrollPane(bookshelfTable);
        shelfScrollPane.setOpaque(false);
        shelfScrollPane.getViewport().setOpaque(false);
        bookshelfContentPanel.add(shelfScrollPane, CARD_CONTENT);

        // 2.2 错误卡片：错误提示
        bookshelfContentPanel.add(wrapCentered(createErrorLabel()), CARD_ERROR);

        this.add(bookshelfContentPanel, BorderLayout.CENTER);

        // 3. 底部缓存操作按钮栏
        this.add(createCacheButtonBar(), BorderLayout.SOUTH);

        // 4. 绑定事件监听器
        bindEventListeners();

        // 默认显示内容
        showContent();
    }

    // ==================== UI 创建方法 ====================

    /**
     * 创建书架表格
     */
    private JBTable createBookshelfTable() {
        JBTable table = new JBTable(BOOK_SHELF_TABLE_MODEL);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return table;
    }

    /**
     * 创建错误标签
     */
    private JBLabel createErrorLabel() {
        JBLabel label = new JBLabel();
        label.setText("<html><div style='text-align:center;'>" + ERROR_MESSAGE.replace("\n", "<br>") + "</div></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setForeground(JBColor.GRAY);
        // 确保错误提示有足够宽度完整显示
        label.setMinimumSize(JBUI.size(350, 120));
        label.setPreferredSize(JBUI.size(450, 140));
        return label;
    }

    /**
     * 将组件包装在居中面板中（横向填充，确保内容不被压缩）
     */
    private JBPanel<?> wrapCentered(JComponent component) {
        JBPanel<?> wrapper = new JBPanel<>(new GridBagLayout());
        wrapper.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        wrapper.add(component, gbc);
        return wrapper;
    }

    // ==================== 事件绑定方法 ====================

    /**
     * 绑定事件监听器
     */
    private void bindEventListeners() {
        // 表格点击：双击进入阅读
        bookshelfTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() >= 2) {
                    handleBookSelection(evt);
                }
            }
        });

        // 选中行变化时更新按钮启用状态
        bookshelfTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateCacheButtonState();
            }
        });
    }

    /**
     * 创建底部缓存按钮栏
     */
    private JComponent createCacheButtonBar() {
        JBPanel<?> bar = new JBPanel<>();
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
        bar.setOpaque(false);

        cacheButton = new JButton("离线缓存");
        cacheButton.setToolTipText("将选中的书籍后台缓存到本地（AES-256 加密）");
        cacheButton.setEnabled(false);
        cacheButton.addActionListener(e -> triggerCacheForSelectedBook());

        cancelButton = new JButton("取消缓存");
        cancelButton.setToolTipText("取消正在进行的缓存任务");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> CommandBus.getInstance().dispatchAsync(
                Command.of(CommandType.CANCEL_CACHE_BOOK)
        ));

        bar.add(cacheButton);
        bar.add(cancelButton);
        return bar;
    }

    /**
     * 触发当前选中书籍的离线缓存
     */
    private void triggerCacheForSelectedBook() {
        BookDTO book = getSelectedBook();
        if (book == null) {
            return;
        }
        if (OfflineCacheService.getInstance().isCacheRunning(book.getBookUrl())) {
            log.info("该书正在缓存中，忽略重复请求");
            return;
        }
        CommandBus.getInstance().dispatchAsync(Command.of(
                CommandType.CACHE_BOOK,
                new CacheBookPayload(book, null)
        ));
        log.info("已请求离线缓存：book={}", book.getName());
    }

    /**
     * 获取当前选中的书籍
     */
    private BookDTO getSelectedBook() {
        int row = bookshelfTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        TableModel model = bookshelfTable.getModel();
        String name = String.valueOf(model.getValueAt(row, 0));
        String author = String.valueOf(model.getValueAt(row, 3));
        return getBook(author, name);
    }

    /**
     * 根据缓存设置与运行状态更新按钮启用状态
     */
    private void updateCacheButtonState() {
        BookDTO book = getSelectedBook();
        boolean cacheEnabled = Boolean.TRUE.equals(
                PluginSettingsStorage.getInstance().getState().cacheEnabled);
        boolean running = book != null
                && OfflineCacheService.getInstance().isCacheRunning(book.getBookUrl());

        cacheButton.setEnabled(cacheEnabled && book != null && !running);
        cancelButton.setEnabled(running);

        if (!cacheEnabled) {
            cacheButton.setToolTipText("请在 设置 → Tools → Legado Reader 中开启离线缓存");
        } else if (book == null) {
            cacheButton.setToolTipText("请先在表格中选中一本书");
        } else if (running) {
            cacheButton.setToolTipText("正在缓存中...");
        } else {
            cacheButton.setToolTipText("将选中的书籍后台缓存到本地（AES-256 加密）");
        }
    }

    /**
     * 刷新所有书籍的缓存状态列
     */
    public void refreshCacheStatus() {
        int rowCount = BOOK_SHELF_TABLE_MODEL.getRowCount();
        if (rowCount == 0 || bookshelf == null) {
            return;
        }
        for (int row = 0; row < rowCount; row++) {
            String name = String.valueOf(BOOK_SHELF_TABLE_MODEL.getValueAt(row, 0));
            String author = String.valueOf(BOOK_SHELF_TABLE_MODEL.getValueAt(row, 3));
            BookDTO book = getBook(author, name);
            String statusText = computeCacheStatusText(book);
            BOOK_SHELF_TABLE_MODEL.setValueAt(statusText, row, 4);
        }
        updateCacheButtonState();
    }

    /**
     * 计算某本书的缓存状态展示文本
     */
    private String computeCacheStatusText(BookDTO book) {
        if (book == null) {
            return "-";
        }
        String bookUrl = book.getBookUrl();
        if (OfflineCacheService.getInstance().isCacheRunning(bookUrl)) {
            com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.BookCacheProgress progress =
                    OfflineCacheService.getInstance().getProgress(bookUrl);
            int cached = progress != null ? progress.getCachedChapters() : 0;
            int total = progress != null ? progress.getTotalChapters() : 0;
            return total > 0 ? "缓存中 " + (cached * 100 / total) + "%" : "缓存中";
        }
        com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.BookCacheMeta meta =
                OfflineCacheService.getInstance().getMeta(bookUrl);
        if (meta == null) {
            return "未缓存";
        }
        com.nancheung.plugins.jetbrains.legadoreader.storage.cache.dto.BookCacheProgress progress =
                OfflineCacheService.getInstance().getProgress(bookUrl);
        if (progress != null && "COMPLETE".equals(progress.getStatus())) {
            return "已缓存";
        }
        if (progress != null && progress.getTotalChapters() > 0) {
            int percent = progress.getCachedChapters() * 100 / progress.getTotalChapters();
            return "已缓存 " + percent + "%";
        }
        return "部分缓存";
    }

    // ==================== 状态切换方法 ====================

    /**
     * 显示书架内容（隐藏错误）
     */
    public void showContent() {
        bookshelfContentLayout.show(bookshelfContentPanel, CARD_CONTENT);
    }

    /**
     * 显示书架错误（隐藏内容）
     */
    public void showError() {
        bookshelfContentLayout.show(bookshelfContentPanel, CARD_ERROR);
    }

    // ==================== 回调处理方法 ====================

    /**
     * 加载离线缓存书架
     * 异步从本地缓存读取所有已缓存的书籍，在 EDT 更新书架表格
     * 断网时也能查看已缓存的书籍
     */
    private void loadOfflineBookshelf() {
        CompletableFuture.supplyAsync(() -> BookCacheStorage.getInstance().listCachedMetas())
                .thenAccept(metas -> ApplicationManager.getApplication().invokeLater(() -> {
                    List<BookDTO> books = metas.stream()
                            .map(BookCacheMeta::getBookSnapshot)
                            .filter(Objects::nonNull)
                            .toList();

                    if (books.isEmpty()) {
                        BOOK_SHELF_TABLE_MODEL.getDataVector().clear();
                        this.bookshelf = null;
                        showContent();
                        updateCacheButtonState();
                        return;
                    }

                    handleBooksLoaded(books);
                    log.info("已加载离线缓存书架：{} 本", books.size());
                }));
    }

    /**
     * 处理书籍加载成功
     * 由 AddressBarPanel 回调触发
     *
     * @param books 书籍列表
     */
    private void handleBooksLoaded(List<BookDTO> books) {
        // 保存书架目录信息
        this.bookshelf = books.stream()
                .collect(Collectors.toMap(
                        book -> BOOK_MAP_KEY_FUNC.apply(book.getAuthor(), book.getName()),
                        Function.identity()
                ));
        // 设置书架目录 UI
        setBookshelfUI(books);
    }

    /**
     * 处理加载失败
     * 由 AddressBarPanel 回调触发
     */
    private void handleLoadFailed() {
        showError();
    }

    // ==================== 业务逻辑方法 ====================

    /**
     * 设置书架 UI
     */
    private void setBookshelfUI(List<BookDTO> books) {
        // 清空表格
        BOOK_SHELF_TABLE_MODEL.getDataVector().clear();

        // 添加表格数据（含缓存状态列）
        books.forEach(book -> {
            Vector<String> bookVector = new Vector<>();
            bookVector.add(book.getName());
            bookVector.add(book.getDurChapterTitle());
            bookVector.add(book.getLatestChapterTitle());
            bookVector.add(book.getAuthor());
            bookVector.add(computeCacheStatusText(book));
            BOOK_SHELF_TABLE_MODEL.addRow(bookVector);
        });

        // 显示内容（隐藏错误）
        showContent();

        // 刷新按钮状态
        updateCacheButtonState();
    }

    /**
     * 处理书籍选择（双击进入阅读）
     */
    private void handleBookSelection(MouseEvent evt) {
        int row = bookshelfTable.rowAtPoint(evt.getPoint());
        int col = bookshelfTable.columnAtPoint(evt.getPoint());

        if (row < 0 || col < 0) {
            return;
        }

        // 获取当前点击的书籍信息
        TableModel model = ((JTable) evt.getSource()).getModel();
        String name = model.getValueAt(row, 0).toString();
        String author = model.getValueAt(row, 3).toString();

        // 获取书籍信息
        BookDTO book = getBook(author, name);

        // 发送选择书籍事件
        if (book != null) {
            CommandBus.getInstance().dispatchAsync(Command.of(
                    CommandType.SELECT_BOOK,
                    new SelectBookPayload(book, book.getDurChapterIndex())
            ));
        }
    }

    /**
     * 获取书籍
     */
    private BookDTO getBook(String author, String name) {
        if (bookshelf == null) {
            return null;
        }
        return bookshelf.get(BOOK_MAP_KEY_FUNC.apply(author, name));
    }

    // ==================== 公共接口方法 ====================

    /**
     * 初始化地址历史记录
     * 在 ToolWindow 首次显示时调用
     */
    public void initAddressHistory() {
        addressBarPanel.refreshHistory();
    }

    /**
     * 刷新书架
     */
    public void refreshBookshelf(){
        addressBarPanel.load();
    }
}
