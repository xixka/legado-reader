package com.nancheung.plugins.jetbrains.legadoreader.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.ui.JBColor;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.nancheung.plugins.jetbrains.legadoreader.storage.converter.FontConverter;
import com.nancheung.plugins.jetbrains.legadoreader.storage.converter.JBColorConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 插件设置存储服务（Application Service）
 * 使用 IntelliJ Platform 的 PersistentStateComponent 进行持久化
 *
 * @author NanCheung
 */
@Service
@State(name = "LegadoReaderSettings", storages = @Storage("nancheung-legadoReader-settings.xml"))
public final class PluginSettingsStorage implements PersistentStateComponent<PluginSettingsStorage.State> {

    /**
     * 自定义 API 参数数据类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomParam {
        public String name = "";
        public String value = "";

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomParam that = (CustomParam) o;
            return Objects.equals(name, that.name) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }

    /**
     * 内部状态类，用于 XML 序列化
     * PersistentStateComponent 框架会自动检测字段变化并持久化
     */
    public static class State {
        /**
         * 正文字体颜色
         */
        @OptionTag(converter = JBColorConverter.class)
        public JBColor textBodyFontColor = JBColor.green;

        /**
         * 正文字体（包含字体名称、样式、大小）
         */
        @OptionTag(converter = FontConverter.class)
        public Font textBodyFont = EditorColorsManager
                .getInstance()
                .getGlobalScheme()
                .getFont(EditorFontType.PLAIN);

        /**
         * 正文字体行高倍数
         */
        public Double textBodyLineHeight = 1.5;

        /**
         * API 自定义参数列表
         */
        public List<CustomParam> apiCustomParams = new ArrayList<>(List.of(
                new CustomParam("source", "legado-reader"),
                new CustomParam("accessToken", "nanchueng")
        ));

        /**
         * 是否启用错误日志
         */
        public Boolean enableErrorLog = false;

        /**
         * 是否启用行内模式
         */
        public Boolean enableShowBodyInLine = false;

        /**
         * 是否启用离线缓存
         */
        public Boolean cacheEnabled = false;

        /**
         * 离线缓存 AES 密钥（Base64 编码，AES-256 需要 32 字节）
         * 首次启用缓存时自动生成，对用户透明
         */
        public String cacheKey = "";

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            return Objects.equals(textBodyFontColor, state.textBodyFontColor) &&
                    Objects.equals(textBodyFont, state.textBodyFont) &&
                    Objects.equals(textBodyLineHeight, state.textBodyLineHeight) &&
                    Objects.equals(apiCustomParams, state.apiCustomParams) &&
                    Objects.equals(enableErrorLog, state.enableErrorLog) &&
                    Objects.equals(enableShowBodyInLine, state.enableShowBodyInLine) &&
                    Objects.equals(cacheEnabled, state.cacheEnabled) &&
                    Objects.equals(cacheKey, state.cacheKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(textBodyFontColor, textBodyFont, textBodyLineHeight,
                    apiCustomParams, enableErrorLog, enableShowBodyInLine,
                    cacheEnabled, cacheKey);
        }
    }

    private State state = new State();

    /**
     * 获取服务实例
     *
     * @return 服务实例
     */
    public static PluginSettingsStorage getInstance() {
        return ApplicationManager.getApplication().getService(PluginSettingsStorage.class);
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    /**
     * 切换阅读模式显示/隐藏
     * 全局开关，同时影响 ToolWindow 和 EditorLine 两种阅读模式
     *
     * @return 切换后的状态（true=启用，false=禁用）
     */
    public boolean toggleReadingMode() {
        State currentState = getState();
        boolean newState = !Boolean.TRUE.equals(currentState.enableShowBodyInLine);

        // 直接修改 State 字段，框架会自动持久化
        currentState.enableShowBodyInLine = newState;

        return newState;
    }
}
