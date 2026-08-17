package com.nancheung.plugins.jetbrains.legadoreader.common;

public interface Constant {
    /**
     * 插件包名
     */
    String PLUGIN_PACKAGE = "com.nancheung.plugins.jetbrains.legado-reader";
    /**
     * 插件id前缀
     */
    String PLUGIN_ID_PREFIX = "com.nancheung.legado-reader";
    /**
     * 插件设置id
     */
    String PLUGIN_SETTING_ID = PLUGIN_ID_PREFIX + ".setting";
    
    /**
     * action id前缀
     */
    String PLUGIN_ACTION_ID_PREFIX = PLUGIN_ID_PREFIX + ".action";
    
    /**
     * action 上一章id
     */
    String PLUGIN_ACTION_PREVIOUS_CHAPTER_ID = PLUGIN_ACTION_ID_PREFIX + ".previousChapter";
    /**
     * action 下一章id
     */
    String PLUGIN_ACTION_NEXT_CHAPTER_ID = PLUGIN_ACTION_ID_PREFIX + ".nextChapter";

    /**
     * action 老板键id
     */
    String PLUGIN_ACTION_BOSS_KEY_ID = PLUGIN_ACTION_ID_PREFIX + ".bossKey";

    /**
     * 正文阅读tool bar
     */
    String PLUGIN_TOOL_BAR_ID = PLUGIN_ID_PREFIX + ".bar.textBodyToolbar";

    /**
     * 阅读工具窗口id（对应 plugin.xml 中 toolWindow 的 id）
     */
    String PLUGIN_TOOL_WINDOW_ID = "Reader";
    
    
    /**
     * 持久化数据
     */
    String PLUGIN__PERSISTENCE_DATA = PLUGIN_ID_PREFIX + ".persistence.data";
}
