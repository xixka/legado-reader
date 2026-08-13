package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.nancheung.plugins.jetbrains.legadoreader.storage.AddressHistoryStorage;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 地址栏面板组件（通用）
 * 负责管理服务器下拉框、添加按钮、刷新按钮和异步加载逻辑
 * <p>
 * 下拉框第一项固定为 {@link #OFFLINE_CACHE_OPTION}（离线缓存），
 * 选择该项时调用 onOfflineMode 回调而非 loadAction。
 *
 * @param <T> 加载结果的数据类型
 * @author NanCheung
 */
@Slf4j
public class AddressBarPanel<T> extends JBPanel<AddressBarPanel<T>> {

    /**
     * 离线缓存虚拟选项（非真实服务器地址）
     */
    public static final String OFFLINE_CACHE_OPTION = "离线缓存";

    // ==================== UI 组件 ====================
    private ComboBox<String> addressHistoryBox;
    private JButton addButton;
    private JButton refreshButton;

    // ==================== 数据模型（静态，多窗口共享） ====================
    private static final DefaultComboBoxModel<String> ADDRESS_HISTORY_MODEL = new DefaultComboBoxModel<>();

    // ==================== 回调接口 ====================
    private final Supplier<T> loadAction;
    private final Consumer<T> onLoadSucceeded;
    private final Runnable onLoadFailed;
    private final Runnable onOfflineMode;

    /**
     * 标志位：程序设置下拉框选中项时抑制自动加载（避免 refreshHistory 触发 load 循环）
     */
    private boolean suppressAutoLoad = false;

    // ==================== 构造函数 ====================

    /**
     * 创建地址栏面板
     *
     * @param loadAction      在线模式加载动作（在后台线程执行）
     * @param onOfflineMode   选择"离线缓存"时的回调（在 EDT 线程执行）
     * @param onLoadSucceeded 加载成功回调（在 EDT 线程执行）
     * @param onLoadFailed    加载失败回调（在 EDT 线程执行）
     */
    public AddressBarPanel(Supplier<T> loadAction, Runnable onOfflineMode, Consumer<T> onLoadSucceeded, Runnable onLoadFailed) {
        setOpaque(false);
        this.loadAction = loadAction;
        this.onOfflineMode = onOfflineMode;
        this.onLoadSucceeded = onLoadSucceeded;
        this.onLoadFailed = onLoadFailed;
        initializeUI();
        bindEventListeners();
    }

    // ==================== UI 创建方法 ====================

    private void initializeUI() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(JBUI.Borders.empty(4));

        // 服务器下拉框
        addressHistoryBox = new ComboBox<>(ADDRESS_HISTORY_MODEL);
        addressHistoryBox.setName("addressHistoryBox");
        addressHistoryBox.setPreferredSize(JBUI.size(200, -1));
        addressHistoryBox.setMinimumSize(JBUI.size(150, -1));

        // 添加按钮
        addButton = new JButton("添加");
        addButton.setName("addButton");
        addButton.setToolTipText("添加新的阅读服务器地址（不带端口自动补 1122）");

        // 刷新按钮
        refreshButton = new JButton("刷新");
        refreshButton.setName("refreshButton");

        add(addressHistoryBox);
        add(Box.createHorizontalStrut(JBUI.scale(4)));
        add(addButton);
        add(Box.createHorizontalStrut(JBUI.scale(4)));
        add(refreshButton);
    }

    // ==================== 事件绑定方法 ====================

    private void bindEventListeners() {
        refreshButton.addActionListener(e -> load());
        addButton.addActionListener(e -> addServer());

        // 下拉框选择切换：自动加载（程序设置时抑制）
        addressHistoryBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() != null && !suppressAutoLoad) {
                load();
            }
        });
    }

    // ==================== 业务逻辑方法 ====================

    /**
     * 弹出输入对话框添加服务器
     */
    private void addServer() {
        String input = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "请输入阅读服务器地址：\n（不带端口自动补 1122，如 127.0.0.1）",
                "添加服务器",
                JOptionPane.PLAIN_MESSAGE
        );

        if (input == null || input.trim().isEmpty()) {
            return;
        }

        // 规范化地址（补全协议 + 端口）
        String normalized = AddressHistoryStorage.normalizeAddress(input.trim());
        AddressHistoryStorage.getInstance().addAddress(normalized);

        refreshHistory();
        suppressAutoLoad = true;
        addressHistoryBox.setSelectedItem(normalized);
        suppressAutoLoad = false;

        load();
    }

    /**
     * 执行刷新操作
     * 选择"离线缓存"时调用 onOfflineMode，否则走 loadAction
     */
    public void load() {
        String selected = (String) addressHistoryBox.getSelectedItem();

        // 离线缓存模式：不走 API，直接调用回调（loadOfflineBookshelf 内部异步处理）
        if (OFFLINE_CACHE_OPTION.equals(selected)) {
            refreshButton.setEnabled(false);
            onOfflineMode.run();
            refreshButton.setEnabled(true);
            return;
        }

        // 在线模式
        refreshButton.setEnabled(false);

        if (selected == null || selected.trim().isEmpty()) {
            refreshButton.setEnabled(true);
            onLoadFailed.run();
            return;
        }

        // 更新历史排序（当前地址移到最前）
        AddressHistoryStorage.getInstance().addAddress(selected);
        refreshHistory();

        String current = (String) addressHistoryBox.getSelectedItem();
        CompletableFuture.supplyAsync(loadAction)
                .handle((result, throwable) -> {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (throwable == null) {
                            onLoadSucceeded.accept(result);
                            return;
                        }
                        if (Boolean.TRUE.equals(PluginSettingsStorage.getInstance().getState().enableErrorLog)) {
                            log.error("加载失败", throwable.getCause());
                        }
                        onLoadFailed.run();
                    });
                    return null;
                }).whenComplete((aVoid, throwable) -> ApplicationManager.getApplication().invokeLater(() -> refreshButton.setEnabled(true)));
    }

    /**
     * 刷新历史记录
     * 下拉框第一项固定为"离线缓存"，后面是真实服务器地址
     */
    public void refreshHistory() {
        suppressAutoLoad = true;
        try {
            List<String> history = AddressHistoryStorage.getInstance().getAddressList();

            ADDRESS_HISTORY_MODEL.removeAllElements();

            // 第一项固定为"离线缓存"
            ADDRESS_HISTORY_MODEL.addElement(OFFLINE_CACHE_OPTION);

            if (history.isEmpty()) {
                addressHistoryBox.setEnabled(true);
                ADDRESS_HISTORY_MODEL.setSelectedItem(OFFLINE_CACHE_OPTION);
                return;
            }

            ADDRESS_HISTORY_MODEL.addAll(history);
            addressHistoryBox.setEnabled(true);

            // 如果之前选中的是离线缓存，保持选中
            String currentSelection = (String) addressHistoryBox.getSelectedItem();
            if (OFFLINE_CACHE_OPTION.equals(currentSelection)) {
                ADDRESS_HISTORY_MODEL.setSelectedItem(OFFLINE_CACHE_OPTION);
            } else {
                ADDRESS_HISTORY_MODEL.setSelectedItem(history.getFirst());
            }
        } finally {
            suppressAutoLoad = false;
        }
    }
}
