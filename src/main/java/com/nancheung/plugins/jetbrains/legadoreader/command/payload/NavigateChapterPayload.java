package com.nancheung.plugins.jetbrains.legadoreader.command.payload;

/**
 * 章节导航参数
 * 用于 PREVIOUS_CHAPTER / NEXT_CHAPTER 指令，指示是否在章节末尾定位光标
 *
 * @param positionAtEnd true 表示光标定位到章节末尾，false 表示定位到章节开头
 * @author NanCheung
 */
public record NavigateChapterPayload(boolean positionAtEnd) implements CommandPayload {
}
