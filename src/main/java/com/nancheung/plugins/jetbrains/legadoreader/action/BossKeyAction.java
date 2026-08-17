package com.nancheung.plugins.jetbrains.legadoreader.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.nancheung.plugins.jetbrains.legadoreader.manager.BossKeyManager;
import org.jetbrains.annotations.NotNull;

/**
 * 老板键：一键隐藏/恢复所有阅读痕迹
 * <p>
 * 触发后立即关闭行内阅读模式并隐藏 Reader 工具窗口；
 * 再次触发则恢复进入老板模式前的阅读状态。
 * 默认快捷键：Shift + Alt + B
 */
public class BossKeyAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        BossKeyManager.getInstance().toggle();
    }
}
