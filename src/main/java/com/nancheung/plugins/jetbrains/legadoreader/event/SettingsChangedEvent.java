package com.nancheung.plugins.jetbrains.legadoreader.event;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.UUID;

/**
 * 设置变更事件（不可变）
 * 当用户在设置页面保存设置时发布
 *
 * @param eventId           事件唯一 ID
 * @param timestamp         事件时间戳
 * @param commandId         关联的指令 ID
 * @param type              设置变更类型
 * @param fontColor         字体颜色（可能为 null）
 * @param font              字体（可能为 null）
 * @param lineHeight        行高（可能为 null）
 * @param enableShowBodyInLine 是否启用行内阅读（可能为 null）
 * @param hideScrollBar     是否隐藏滚动条（可能为 null）
 * @author NanCheung
 */
public record SettingsChangedEvent(
        String eventId,
        long timestamp,
        @Nullable String commandId,
        SettingsChangedType type,
        @Nullable JBColor fontColor,
        @Nullable Font font,
        @Nullable Double lineHeight,
        @Nullable Boolean enableShowBodyInLine,
        @Nullable Boolean hideScrollBar
) implements ReaderEvent {

    /**
     * 设置变更类型
     */
    public enum SettingsChangedType {
        /**
         * 字体设置变更（颜色、字体、行高）
         */
        FONT_SETTINGS,

        /**
         * 显示设置变更（行内阅读开关等）
         */
        DISPLAY_SETTINGS,

        /**
         * 所有设置变更
         */
        ALL_SETTINGS
    }

    /**
     * 创建字体设置变更事件
     */
    public static SettingsChangedEvent fontSettings(JBColor fontColor, Font font, double lineHeight) {
        return new SettingsChangedEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                null,
                SettingsChangedType.FONT_SETTINGS,
                fontColor,
                font,
                lineHeight,
                null,
                null
        );
    }

    /**
     * 创建显示设置变更事件
     */
    public static SettingsChangedEvent displaySettings(boolean enableShowBodyInLine) {
        return new SettingsChangedEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                null,
                SettingsChangedType.DISPLAY_SETTINGS,
                null,
                null,
                null,
                enableShowBodyInLine,
                null
        );
    }

    /**
     * 创建所有设置变更事件
     */
    public static SettingsChangedEvent allSettings(
            JBColor fontColor,
            Font font,
            double lineHeight,
            boolean enableShowBodyInLine) {
        return new SettingsChangedEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                null,
                SettingsChangedType.ALL_SETTINGS,
                fontColor,
                font,
                lineHeight,
                enableShowBodyInLine,
                null
        );
    }

    /**
     * 创建设置变更事件（包含滚动条显隐）
     */
    public static SettingsChangedEvent hideScrollBarSettings(boolean hideScrollBar) {
        return new SettingsChangedEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                null,
                SettingsChangedType.ALL_SETTINGS,
                null,
                null,
                null,
                null,
                hideScrollBar
        );
    }
}
