package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.nancheung.plugins.jetbrains.legadoreader.api.ApiUtil;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookDTO;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandBus;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.CacheBookPayload;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.SelectBookPayload;
import com.nancheung.plugins.jetbrains.legadoreader.service.OfflineCacheService;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Vector;
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

    // ==================== 数据模型（静态，多窗口共享） ====================
    private static final DefaultTableModel BOOK_SHELF_TABLE_MODEL =
            new DefaultTableModel(null, new String[]{"name", "current", "new", "author"}) {
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

        // 1. 创建地址栏组件，传入加载动作和回调
        addressBarPanel = new AddressBarPanel<>(ApiUtil::getBookshelf, this::handleBooksLoaded, this::handleLoadFailed);
        this.add(addressBarPanel, BorderLayout.NORTH);

        // 2. 中央内容区（使用 CardLayout 切换内容/错误）
        bookshelfContentLayout = new CardLayout();
        bookshelfContentPanel = new JBPanel<>(bookshelfContentLayout);
        bookshelfContentPanel.setOpaque(false);

        // 2.1 内容卡片：书架表格
        bookshelfTable = createBookshelfTable();
        bookshelfTable.setOpaque(false);
        JBScrollPane shelfScrollPane = new JBScrollPane(bookshelfTable);
        shelfScrollPane.setOpaque(false);
        shelfScrollPane.getViewport().setOpaque(false);
        bookshelfContentPanel.add(shelfScrollPane, CARD_CONTENT);

        // 2.2 错误卡片：错误提示
        bookshelfContentPanel.add(wrapCentered(createErrorLabel()), CARD_ERROR);

        this.add(bookshelfContentPanel, BorderLayout.CENTER);

        // 3. 绑定事件监听器
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

    // ==================== 事件绑定方法 ====================

    /**
     * 绑定事件监听器
     */
    private void bindEventListeners() {
        // 表格点击
        bookshelfTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                handleBookSelection(evt);
            }

            @Override
            public void mousePressed(MouseEvent evt) {
                showContextMenuIfNeeded(evt);
            }

            @Override
            public void mouseReleased(MouseEvent evt) {
                showContextMenuIfNeeded(evt);
            }
        });
    }

    /**
     * 在右键点击时弹出上下文菜单
     */
    private void showContextMenuIfNeeded(MouseEvent evt) {
        if (!evt.isPopupTrigger()) {
            return;
        }
        int row = bookshelfTable.rowAtPoint(evt.getPoint());
        if (row < 0) {
            return;
        }
        // 选中该行
        bookshelfTable.setRowSelectionInterval(row, row);

        BookDTO book = getBookAtRow(row);
        if (book == null) {
            return;
        }

        JPopupMenu popup = new JPopupMenu();

        // 离线缓存菜单项
        JMenuItem cacheItem = new JMenuItem("离线缓存本书");
        boolean cacheEnabled = Boolean.TRUE.equals(PluginSettingsStorage.getInstance().getState().cacheEnabled);
        cacheItem.setEnabled(cacheEnabled);
        if (!cacheEnabled) {
            cacheItem.setToolTipText("请在设置中开启离线缓存");
        }
        if (OfflineCacheService.getInstance().isCacheRunning(book.getBookUrl())) {
            cacheItem.setText("正在缓存中...");
            cacheItem.setEnabled(false);
        }
        cacheItem.addActionListener(e -> triggerBookCache(book));
        popup.add(cacheItem);

        // 取消缓存菜单项（仅当该任务运行中时启用）
        JMenuItem cancelItem = new JMenuItem("取消缓存");
        cancelItem.setEnabled(OfflineCacheService.getInstance().isCacheRunning(book.getBookUrl()));
        cancelItem.addActionListener(e -> CommandBus.getInstance().dispatchAsync(
                Command.of(CommandType.CANCEL_CACHE_BOOK)
        ));
        popup.add(cancelItem);

        popup.show(bookshelfTable, evt.getX(), evt.getY());
    }

    /**
     * 触发某本书的离线缓存
     */
    private void triggerBookCache(BookDTO book) {
        // 不预先获取章节列表，由 handler 内部异步获取
        CommandBus.getInstance().dispatchAsync(Command.of(
                CommandType.CACHE_BOOK,
                new CacheBookPayload(book, null)
        ));
        log.info("已请求离线缓存：book={}", book.getName());
    }

    /**
     * 通过表格行号获取书籍
     */
    private BookDTO getBookAtRow(int row) {
        if (row < 0) {
            return null;
        }
        TableModel model = bookshelfTable.getModel();
        String name = String.valueOf(model.getValueAt(row, 0));
        String author = String.valueOf(model.getValueAt(row, 3));
        return getBook(author, name);
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

        // 添加表格数据
        books.stream().map(book -> {
            Vector<String> bookVector = new Vector<>();
            bookVector.add(book.getName());
            bookVector.add(book.getDurChapterTitle());
            bookVector.add(book.getLatestChapterTitle());
            bookVector.add(book.getAuthor());
            return bookVector;
        }).forEach(BOOK_SHELF_TABLE_MODEL::addRow);

        // 显示内容（隐藏错误）
        showContent();
    }

    /**
     * 处理书籍选择
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
