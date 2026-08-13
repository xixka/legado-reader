package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.nancheung.plugins.jetbrains.legadoreader.api.dto.BookChapterDTO;
import com.nancheung.plugins.jetbrains.legadoreader.command.Command;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandBus;
import com.nancheung.plugins.jetbrains.legadoreader.command.CommandType;
import com.nancheung.plugins.jetbrains.legadoreader.command.payload.JumpToChapterPayload;
import com.nancheung.plugins.jetbrains.legadoreader.manager.ReadingSessionManager;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 章节列表面板组件
 * 负责显示当前书籍的全部章节，并支持点击跳转
 *
 * @author NanCheung
 */
@Slf4j
public class ChapterListPanel extends JBPanel<ChapterListPanel> {

    // ==================== 表头列名 ====================
    private static final String[] COLUMN_NAMES = {"序号", "章节标题"};

    // ==================== UI 组件 ====================
    private final JBTable chapterTable;
    private final DefaultTableModel tableModel;

    // ==================== 构造函数 ====================
    public ChapterListPanel() {
        super(new BorderLayout());

        // 1. 中央章节表格
        tableModel = new DefaultTableModel(null, COLUMN_NAMES) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        chapterTable = new JBTable(tableModel);
        chapterTable.setFillsViewportHeight(true);
        chapterTable.setRowSelectionAllowed(true);
        chapterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapterTable.getTableHeader().setReorderingAllowed(false);
        chapterTable.setOpaque(false);

        // 列宽：序号列收紧，标题列自动填充剩余空间
        chapterTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        chapterTable.getColumnModel().getColumn(0).setMaxWidth(80);
        chapterTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // 当前行（正在阅读的章节）高亮渲染
        chapterTable.setDefaultRenderer(Object.class, new CurrentChapterRenderer());

        JBScrollPane scrollPane = new JBScrollPane(chapterTable);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        this.add(scrollPane, BorderLayout.CENTER);

        // 2. 绑定事件
        bindEventListeners();
    }

    // ==================== 事件绑定方法 ====================

    /**
     * 绑定表格双击事件：跳转到对应章节
     */
    private void bindEventListeners() {
        chapterTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                // 双击触发跳转
                if (evt.getClickCount() < 2) {
                    return;
                }
                handleChapterSelection(evt);
            }
        });
    }

    // ==================== 业务逻辑方法 ====================

    /**
     * 处理章节选择：派发 JUMP_TO_CHAPTER 指令
     */
    private void handleChapterSelection(MouseEvent evt) {
        int row = chapterTable.rowAtPoint(evt.getPoint());
        if (row < 0) {
            return;
        }

        TableModel model = ((JTable) evt.getSource()).getModel();
        try {
            int chapterIndex = Integer.parseInt(model.getValueAt(row, 0).toString());
            CommandBus.getInstance().dispatchAsync(Command.of(
                    CommandType.JUMP_TO_CHAPTER,
                    new JumpToChapterPayload(chapterIndex)
            ));
            log.debug("从章节列表跳转到第 {} 章", chapterIndex);
        } catch (NumberFormatException ex) {
            log.warn("章节序号解析失败: {}", model.getValueAt(row, 0));
        }
    }

    // ==================== 公共接口方法 ====================

    /**
     * 刷新章节列表
     * 从当前阅读会话中获取章节并填充到表格，同时滚动到当前章节
     */
    public void refreshChapters() {
        List<BookChapterDTO> chapters = ReadingSessionManager.getInstance().getChapters();
        int currentIndex = ReadingSessionManager.getInstance().getCurrentChapterIndex();

        tableModel.getDataVector().clear();
        if (chapters == null || chapters.isEmpty()) {
            log.debug("章节列表为空");
            tableModel.addRow(new Object[]{"-", "暂无章节"});
            return;
        }

        for (int i = 0; i < chapters.size(); i++) {
            BookChapterDTO chapter = chapters.get(i);
            String title = chapter.getTitle() != null ? chapter.getTitle() : ("第" + (i + 1) + " 章");
            tableModel.addRow(new Object[]{i, title});
        }

        // 滚动到当前章节并选中
        selectCurrentChapter(currentIndex);
    }

    /**
     * 选中并滚动到当前章节
     */
    private void selectCurrentChapter(int chapterIndex) {
        int rowCount = tableModel.getRowCount();
        if (chapterIndex < 0 || chapterIndex >= rowCount) {
            return;
        }
        chapterTable.setRowSelectionInterval(chapterIndex, chapterIndex);
        scrollRowToVisible(chapterIndex);
    }

    /**
     * 滚动到指定行
     */
    private void scrollRowToVisible(int row) {
        Rectangle rect = chapterTable.getCellRect(row, 0, true);
        chapterTable.scrollRectToVisible(rect);
    }

    // ==================== 内部渲染器 ====================

    /**
     * 当前行（正在阅读的章节）高亮渲染器
     */
    private static class CurrentChapterRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            int currentChapterIndex = ReadingSessionManager.getInstance().getCurrentChapterIndex();
            if (row == currentChapterIndex) {
                // 当前章节：使用主题强调色
                component.setForeground(JBColor.BLUE);
                component.setFont(component.getFont().deriveFont(Font.BOLD));
            } else if (!isSelected) {
                // 非当前章节且未选中：恢复表格默认前景色，避免复用渲染器时残留蓝色
                component.setForeground(table.getForeground());
                component.setFont(component.getFont().deriveFont(Font.PLAIN));
            }
            return component;
        }
    }
}
