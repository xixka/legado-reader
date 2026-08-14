package com.nancheung.plugins.jetbrains.legadoreader.presentation.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.nancheung.plugins.jetbrains.legadoreader.common.Constant;
import com.nancheung.plugins.jetbrains.legadoreader.event.EventPublisher;
import com.nancheung.plugins.jetbrains.legadoreader.event.SettingsChangedEvent;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.settings.validation.ValidationError;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.settings.validation.ValidationResult;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * IntelliJ Platform 设置页面集成点
 * 遵循 IntelliJ Configurable 最佳实践：
 * - 每次打开创建新面板
 * - 使用 ViewModel 隔离数据
 * - 清晰的生命周期
 */
public class SettingsConfigurable implements SearchableConfigurable {

    private static final String DISPLAY_NAME = "Legado Reader";

    // 非静态字段，每个实例独立
    private SettingsPanel settingsPanel;
    private SettingsViewModel viewModel;

    @Override
    public @NotNull String getId() {
        return Constant.PLUGIN_SETTING_ID;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public @Nullable JComponent createComponent() {
        // 每次打开创建新实例
        viewModel = new SettingsViewModel();
        viewModel.loadFromStorage();

        settingsPanel = new SettingsPanel(viewModel);
        settingsPanel.refresh();  // 刷新 UI

        return settingsPanel.getComponent();
    }

    @Override
    public boolean isModified() {
        return viewModel != null && viewModel.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        if (viewModel == null) {
            return;
        }

        // 统一验证入口
        ValidationResult result = viewModel.validate();
        if (!result.isValid()) {
            // 取第一个错误展示
            ValidationError firstError = result.errors().get(0);
            throw new ConfigurationException(
                firstError.message(),
                "设置验证失败"
            );
        }

        // 保存到存储
        viewModel.saveToStorage();

        // 发布变更事件
        publishSettingsChangedEvent();
    }

    @Override
    public void reset() {
        if (viewModel != null && settingsPanel != null) {
            viewModel.loadFromStorage();
            settingsPanel.refresh();
        }
    }

    @Override
    public void disposeUIResources() {
        // 清理资源
        settingsPanel = null;
        viewModel = null;
    }

    private void publishSettingsChangedEvent() {
        PluginSettingsStorage.State state = PluginSettingsStorage.getInstance().getState();
        if (state == null) {
            return;
        }

        EventPublisher.getInstance().publish(
            SettingsChangedEvent.fontSettings(
                state.textBodyFontColor,
                state.textBodyFont,
                state.textBodyLineHeight
            )
        );

        EventPublisher.getInstance().publish(
            SettingsChangedEvent.hideScrollBarSettings(state.hideScrollBar)
        );
    }
}
