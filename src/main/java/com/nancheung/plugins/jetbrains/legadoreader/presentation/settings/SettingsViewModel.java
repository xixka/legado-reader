package com.nancheung.plugins.jetbrains.legadoreader.presentation.settings;

import com.intellij.ui.JBColor;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.settings.validation.SettingsValidator;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.settings.validation.ValidationResult;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import lombok.Data;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 设置视图模型
 * 隔离 UI 层和存储层，提供数据转换和验证协调
 */
@Data
public final class SettingsViewModel {

    /**
     * 自定义参数条目 Record
     */
    public record CustomParamEntry(String name, String value) {
    }

    // 可编辑数据（与 Storage.State 解耦的副本）
    private String fontName;
    private int fontSize;
    private Color fontColor;
    private double lineHeight;
    private boolean enableErrorLog;
    private boolean enableInLineMode;
    private boolean cacheEnabled;
    private final List<CustomParamEntry> customParams = new ArrayList<>();

    // 验证器
    private final SettingsValidator validator = new SettingsValidator();

    // 变更监听器（用于通知 UI 更新）
    private final List<Consumer<ValidationResult>> validationListeners = new ArrayList<>();

    /**
     * 从存储加载数据（创建独立副本）
     */
    public void loadFromStorage() {
        PluginSettingsStorage.State state = PluginSettingsStorage.getInstance().getState();
        if (state == null) {
            return;
        }

        this.fontName = state.textBodyFont.getFontName();
        this.fontSize = state.textBodyFont.getSize();
        this.fontColor = state.textBodyFontColor;
        this.lineHeight = state.textBodyLineHeight;
        this.enableErrorLog = Boolean.TRUE.equals(state.enableErrorLog);
        this.enableInLineMode = Boolean.TRUE.equals(state.enableShowBodyInLine);
        this.cacheEnabled = Boolean.TRUE.equals(state.cacheEnabled);

        // 深拷贝参数列表
        this.customParams.clear();
        state.apiCustomParams.stream()
                .map(p -> new CustomParamEntry(p.name, p.value))
                .forEach(customParams::add);
    }

    /**
     * 保存到存储
     */
    public void saveToStorage() {
        PluginSettingsStorage.State state = PluginSettingsStorage.getInstance().getState();
        if (state == null) {
            return;
        }

        state.textBodyFont = new Font(fontName, Font.PLAIN, fontSize);
        state.textBodyFontColor = new JBColor(fontColor, fontColor);
        state.textBodyLineHeight = lineHeight;
        state.enableErrorLog = enableErrorLog;
        state.enableShowBodyInLine = enableInLineMode;
        state.cacheEnabled = cacheEnabled;

        state.apiCustomParams = customParams.stream()
                .map(e -> new PluginSettingsStorage.CustomParam(e.name(), e.value()))
                .collect(Collectors.toList());
    }

    /**
     * 执行完整验证
     */
    public ValidationResult validate() {
        return validator.validateAll(customParams, fontSize);
    }

    /**
     * 检查是否有变更
     */
    public boolean isModified() {
        PluginSettingsStorage.State state = PluginSettingsStorage.getInstance().getState();
        if (state == null) {
            return false;
        }

        return !Objects.equals(fontName, state.textBodyFont.getFontName())
                || fontSize != state.textBodyFont.getSize()
                || !Objects.equals(fontColor, state.textBodyFontColor)
                || !Objects.equals(lineHeight, state.textBodyLineHeight)
                || enableErrorLog != Boolean.TRUE.equals(state.enableErrorLog)
                || enableInLineMode != Boolean.TRUE.equals(state.enableShowBodyInLine)
                || cacheEnabled != Boolean.TRUE.equals(state.cacheEnabled)
                || !customParamsEquals(state.apiCustomParams);
    }

    /**
     * 比较自定义参数列表是否相等
     */
    private boolean customParamsEquals(List<PluginSettingsStorage.CustomParam> storageParams) {
        if (customParams.size() != storageParams.size()) {
            return false;
        }

        for (int i = 0; i < customParams.size(); i++) {
            CustomParamEntry entry = customParams.get(i);
            PluginSettingsStorage.CustomParam storageParam = storageParams.get(i);
            if (!Objects.equals(entry.name(), storageParam.name) ||
                    !Objects.equals(entry.value(), storageParam.value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 添加验证监听器
     */
    public void addValidationListener(Consumer<ValidationResult> listener) {
        validationListeners.add(listener);
    }

    /**
     * 通知验证监听器
     */
    private void notifyValidationListeners(ValidationResult result) {
        validationListeners.forEach(l -> l.accept(result));
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
        notifyValidationListeners(validator.validateFontSize(fontSize));
    }

    public void setCustomParams(List<CustomParamEntry> customParams) {
        this.customParams.clear();
        this.customParams.addAll(customParams);
        notifyValidationListeners(validator.validateCustomParams(customParams));
    }
}
