package com.nancheung.plugins.jetbrains.legadoreader.manager;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.nancheung.plugins.jetbrains.legadoreader.common.Constant;
import com.nancheung.plugins.jetbrains.legadoreader.storage.PluginSettingsStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

/**
 * 老板键管理器
 * <p>
 * 老板键是一键隐藏/恢复所有阅读痕迹的开关：
 * 进入老板模式时，记录当前行内阅读模式状态并关闭行内展示，同时隐藏所有已打开项目的 Reader 工具窗口；
 * 退出老板模式时，恢复进入老板模式前的阅读状态（行内阅读模式与工具窗口可见性）。
 * <p>
 * 老板模式状态仅保存在内存中，重启 IDE 后自动回到正常阅读状态，避免误持久化。
 */
@Slf4j
public final class BossKeyManager {

    /**
     * 单例实例
     */
    private static final BossKeyManager INSTANCE = new BossKeyManager();

    /**
     * 当前是否处于老板模式
     */
    private boolean bossModeActive = false;

    /**
     * 进入老板模式前，行内阅读模式是否启用（用于退出时恢复）
     */
    private boolean previousInlineModeEnabled = false;

    /**
     * 被老板模式隐藏过工具窗口的项目名集合（用于退出时精确恢复，避免恢复用户本来就隐藏的窗口）
     */
    private final Set<String> hiddenToolWindowProjectNames = new HashSet<>();

    private BossKeyManager() {
    }

    /**
     * 获取管理器实例
     *
     * @return 管理器单例
     */
    public static BossKeyManager getInstance() {
        return INSTANCE;
    }

    /**
     * 当前是否处于老板模式
     *
     * @return true=老板模式已激活（阅读内容处于隐藏状态）
     */
    public boolean isBossModeActive() {
        return bossModeActive;
    }

    /**
     * 切换老板模式状态
     *
     * @return 切换后的状态（true=已进入老板模式，false=已退出老板模式）
     */
    public boolean toggle() {
        return bossModeActive ? exitBossMode() : enterBossMode();
    }

    /**
     * 进入老板模式：立即隐藏所有阅读痕迹
     *
     * @return 恒为 true
     */
    private boolean enterBossMode() {
        PluginSettingsStorage storage = PluginSettingsStorage.getInstance();

        // 记录行内阅读模式状态后关闭行内展示，防止正文继续显示在代码行后
        previousInlineModeEnabled = Boolean.TRUE.equals(storage.getState().enableShowBodyInLine);
        storage.getState().enableShowBodyInLine = false;

        bossModeActive = true;
        log.info("老板模式已激活：行内阅读已隐藏");

        // 隐藏工具窗口与刷新编辑器必须在 EDT 中执行
        ApplicationManager.getApplication().invokeLater(this::hideReadingUi);
        return true;
    }

    /**
     * 退出老板模式：恢复进入前的阅读状态
     *
     * @return 恒为 false
     */
    private boolean exitBossMode() {
        PluginSettingsStorage storage = PluginSettingsStorage.getInstance();

        // 恢复进入老板模式前的行内阅读模式状态
        storage.getState().enableShowBodyInLine = previousInlineModeEnabled;

        bossModeActive = false;
        log.info("老板模式已退出：阅读状态已恢复");

        // 恢复工具窗口与刷新编辑器必须在 EDT 中执行
        ApplicationManager.getApplication().invokeLater(this::restoreReadingUi);
        return false;
    }

    /**
     * 隐藏所有项目中可见的阅读界面
     * 在 EDT 中执行：隐藏 Reader 工具窗口，并刷新编辑器以清除行内正文
     */
    private void hideReadingUi() {
        hiddenToolWindowProjectNames.clear();

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project == null || project.isDisposed()) {
                continue;
            }

            ToolWindow readerToolWindow = ToolWindowManager.getInstance(project).getToolWindow(Constant.PLUGIN_TOOL_WINDOW_ID);
            // 仅隐藏当前可见的窗口，并记录项目名，退出老板模式时才能精确恢复
            if (readerToolWindow != null && readerToolWindow.isAvailable() && readerToolWindow.isVisible()) {
                readerToolWindow.hide(null);
                hiddenToolWindowProjectNames.add(project.getName());
            }

            refreshEditor(project);
        }
    }

    /**
     * 恢复进入老板模式前可见的阅读界面
     * 在 EDT 中执行：恢复被老板模式隐藏的工具窗口，并刷新编辑器以重绘行内正文
     */
    private void restoreReadingUi() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project == null || project.isDisposed()) {
                continue;
            }

            if (hiddenToolWindowProjectNames.contains(project.getName())) {
                ToolWindow readerToolWindow = ToolWindowManager.getInstance(project).getToolWindow(Constant.PLUGIN_TOOL_WINDOW_ID);
                if (readerToolWindow != null && readerToolWindow.isAvailable()) {
                    readerToolWindow.show(null);
                }
            }

            refreshEditor(project);
        }
        hiddenToolWindowProjectNames.clear();
    }

    /**
     * 刷新项目当前编辑器
     * 触发行内内容重绘，使老板模式切换立即生效
     */
    private void refreshEditor(Project project) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null && !editor.isDisposed()) {
            editor.getContentComponent().repaint();
        }
    }
}
