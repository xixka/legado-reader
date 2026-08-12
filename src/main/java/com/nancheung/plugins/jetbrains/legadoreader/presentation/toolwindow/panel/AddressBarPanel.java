package com.nancheung.plugins.jetbrains.legadoreader.presentation.toolwindow.panel;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
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
 * 负责管理地址输入框、历史记录下拉框、刷新按钮和异步加载逻辑
 *
 * @param <T> 加载结果的数据类型
 * @author NanCheung
 */
@Slf4j
public class AddressBarPanel<T> extends JBPanel<AddressBarPanel<T>> {

    // ==================== UI 组件 ====================
    private JBTextField addressTextField;
    private ComboBox<String> addressHistoryBox;
    private JButton refreshButton;

    // ==================== 数据模型（静态，多窗口共享） ====================
    private static final DefaultComboBoxModel<String> ADDRESS_HISTORY_MODEL = new DefaultComboBoxModel<>();

    // ==================== 回调接口 ====================
    private final Supplier<T> loadAction;
    private final Consumer<T> onLoadSucceeded;
    private final Runnable onLoadFailed;

    // ==================== 构造函数 ====================

    /**
     * 创建地址栏面板
     *
     * @param loadAction      加载动作（在后台线程执行）
     * @param onLoadSucceeded 加载成功回调（在 EDT 线程执行）
     * @param onLoadFailed    加载失败回调（在 EDT 线程执行）
     */
    public AddressBarPanel(Supplier<T> loadAction, Consumer<T> onLoadSucceeded, Runnable onLoadFailed) {
        setOpaque(false);
        this.loadAction = loadAction;
        this.onLoadSucceeded = onLoadSucceeded;
        this.onLoadFailed = onLoadFailed;
        initializeUI();
        bindEventListeners();
    }

    // ==================== UI 创建方法 ====================

    /**
     * 初始化 UI 组件
     */
    private void initializeUI() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(JBUI.Borders.empty(4));

        // 地址输入框
        addressTextField = new JBTextField(0);
        addressTextField.setMinimumSize(JBUI.size(150, -1));
        addressTextField.setPreferredSize(JBUI.size(150, -1));
        addressTextField.setName("addressTextField");

        // 历史记录下拉框
        addressHistoryBox = new ComboBox<>(ADDRESS_HISTORY_MODEL);
        addressHistoryBox.setName("addressHistoryBox");

        // 刷新按钮
        refreshButton = new JButton("刷新");
        refreshButton.setName("refreshButton");

        add(addressTextField);
        add(Box.createHorizontalStrut(JBUI.scale(4)));
        add(addressHistoryBox);
        add(Box.createHorizontalStrut(JBUI.scale(4)));
        add(refreshButton);
    }


    /**
     * 绑定事件监听器
     */
    private void bindEventListeners() {
        // 刷新按钮
        refreshButton.addActionListener(e -> load());

        // 历史记录选择
        addressHistoryBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() != null) {
                addressTextField.setText(e.getItem().toString());
            }
        });
    }


    /**
     * 执行刷新操作
     * 在后台线程执行加载动作，在 EDT 线程执行回调
     */
    public void load() {
        refreshButton.setEnabled(false);

        String text = AddressHistoryStorage.normalizeAddress(addressTextField.getText());
        addressTextField.setText(text);
        AddressHistoryStorage.getInstance().addAddress(text);
        refreshHistory();

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
     * 从 AddressHistoryStorage 加载历史并更新 UI
     */
    public void refreshHistory() {
        List<String> history = AddressHistoryStorage.getInstance().getAddressList();
        ADDRESS_HISTORY_MODEL.removeAllElements();
        ADDRESS_HISTORY_MODEL.addAll(history);

        if (history.isEmpty()) {
            addressHistoryBox.setEnabled(false);
            addressTextField.setText("http://127.0.0.1:1122");
            ADDRESS_HISTORY_MODEL.addElement("http://127.0.0.1:1122");
            return;
        }

        addressHistoryBox.setEnabled(true);
        ADDRESS_HISTORY_MODEL.setSelectedItem(history.getFirst());
        addressTextField.setText(history.getFirst());
    }
}
