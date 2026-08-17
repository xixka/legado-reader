package com.nancheung.plugins.jetbrains.legadoreader.presentation.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.*;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.settings.components.CustomParamTablePanel;
import com.nancheung.plugins.jetbrains.legadoreader.presentation.settings.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.Arrays;
import java.util.Optional;

/**
 * 设置面板 - 纯 UI 组件
 * 职责：渲染 UI、绑定事件、显示验证状态
 * 不持有业务逻辑
 */
@Slf4j
public class SettingsPanel {

    // ==================== 常量 ====================
    private static final String FONT_PREVIEW_TEXT = """
            字体预览 Font Preview
            
            Designed to provide inspiration and improve productivity during the coding process.
            
            Legado Reader是 开源阅读APP 的Jetbrains IDE插件版，旨在随时随地在IDE中进行阅读，为编码过程带来灵感和效率的提升。
            """;

    // ViewModel 引用
    private final SettingsViewModel viewModel;

    // ==================== 根面板 ====================
    private final JBPanel<?> rootPanel;

    // 子组件（不暴露给外部）
    private final CustomParamTablePanel customParamTablePanel;
    private JBCheckBox enableErrorLogCheckBox;
    private JBCheckBox enableInLineModelCheckBox;
    private JBCheckBox enableOfflineCacheCheckBox;
    private JBCheckBox hideScrollBarCheckBox;

    // ==================== 阅读界面设置组件 ====================
    private ColorPanel fontColorButton;
    private ComboBox<String> fontNameComboBox;
    private JSpinner fontSizeSpinner;
    private JSpinner lineHeightSpinner;
    private JTextPane fontPreviewPane;

    public SettingsPanel(SettingsViewModel viewModel) {
        this.viewModel = viewModel;

        // 1. 创建子组件
        this.customParamTablePanel = new CustomParamTablePanel();
        createComponents();

        // 2. 构建布局
        this.rootPanel = createRootPanel();

        // 3. 绑定双向数据
        bindToViewModel();

        // 4. 监听验证结果
        viewModel.addValidationListener(this::onValidationResult);
    }

    public JComponent getComponent() {
        return rootPanel;
    }

    /**
     * 刷新 UI（从 ViewModel 重新读取）
     */
    public void refresh() {
        customParamTablePanel.setItems(viewModel.getCustomParams());
        fontNameComboBox.setSelectedItem(viewModel.getFontName());
        fontSizeSpinner.setValue(viewModel.getFontSize());
        fontColorButton.setSelectedColor(viewModel.getFontColor());
        lineHeightSpinner.setValue(viewModel.getLineHeight());
        enableErrorLogCheckBox.setSelected(viewModel.isEnableErrorLog());
        enableInLineModelCheckBox.setSelected(viewModel.isEnableInLineMode());
        enableOfflineCacheCheckBox.setSelected(viewModel.isCacheEnabled());
        hideScrollBarCheckBox.setSelected(viewModel.isHideScrollBar());
        // 更新预览
        updateFontPreview();
    }

    // ==================== 组件创建方法 ====================

    private void createComponents() {
        createGeneralSettingsComponents();
        createReadingInterfaceComponents();
    }

    private void createGeneralSettingsComponents() {
        enableErrorLogCheckBox = new JBCheckBox("开启异常日志");
        enableErrorLogCheckBox.setToolTipText("报错时给出报错日志");

        enableInLineModelCheckBox = new JBCheckBox("是否开启行内阅读模式");
        enableInLineModelCheckBox.setToolTipText("在代码行后显示章节内容");

        enableOfflineCacheCheckBox = new JBCheckBox("开启离线缓存（AES-256 加密落盘）");
        enableOfflineCacheCheckBox.setToolTipText("缓存书籍章节内容到本地磁盘，支持断点续传、离线阅读");

        hideScrollBarCheckBox = new JBCheckBox("隐藏滚动条");
    }

    private void createReadingInterfaceComponents() {
        // 颜色选择按钮
        fontColorButton = new ColorPanel();

        // 字体选择下拉框
        fontNameComboBox = createFontNameComboBox();

        // 字体大小 Spinner
        fontSizeSpinner = createFontSizeSpinner();

        // 行高 Spinner
        lineHeightSpinner = createLineHeightSpinner();

        // 字体预览面板
        fontPreviewPane = createFontPreviewPane();
    }

    @NotNull
    private ComboBox<String> createFontNameComboBox() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = Arrays.stream(ge.getAllFonts()).map(Font::getFontName).toArray(String[]::new);
        Arrays.sort(fontNames);

        ComboBox<String> comboBox = new ComboBox<>(fontNames);
        Dimension size = JBUI.size(200, 25);
        comboBox.setPreferredSize(size);
        comboBox.setMinimumSize(size);

        // 展示字体自身样式
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String fontName) {
                    setFont(new Font(fontName, Font.PLAIN, getFont().getSize()));
                }
                return this;
            }
        });

        return comboBox;
    }

    @NotNull
    private JSpinner createFontSizeSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        Dimension size = JBUI.size(80, 25);
        spinner.setPreferredSize(size);
        spinner.setMinimumSize(size);

        configureSpinnerFormatter(spinner);

        return spinner;
    }

    @NotNull
    private JSpinner createLineHeightSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1.5, 0.5, 3.0, 0.1));
        Dimension size = JBUI.size(80, 25);
        spinner.setPreferredSize(size);
        spinner.setMinimumSize(size);

        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.0");
        spinner.setEditor(editor);

        configureSpinnerFormatter(spinner);

        return spinner;
    }

    private void configureSpinnerFormatter(JSpinner spinner) {
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        if (editor.getTextField().getFormatter() instanceof DefaultFormatter df) {
            df.setAllowsInvalid(false);
            df.setCommitsOnValidEdit(true);
        }
    }

    @NotNull
    private JTextPane createFontPreviewPane() {
        JTextPane pane = new JTextPane();
        pane.setText(FONT_PREVIEW_TEXT);
        pane.setEditable(false);
        return pane;
    }

    // ==================== 布局构建方法 ====================

    @NotNull
    private JBPanel<?> createRootPanel() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        // 内容面板（垂直排列三个设置组）
        JBPanel<?> contentPanel = new JBPanel<>();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // 1. 自定义参数面板
        contentPanel.add(customParamTablePanel);
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(15)));

        // 2. 常规设置面板
        JPanel generalPanel = createGeneralSettingsPanel();
        contentPanel.add(generalPanel);
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(10)));

        // 3. 阅读界面设置面板
        JPanel readingPanel = createReadingInterfacePanel();
        contentPanel.add(readingPanel);

        // 填充剩余空间
        contentPanel.add(Box.createVerticalGlue());

        panel.add(new JBScrollPane(contentPanel), BorderLayout.CENTER);

        return panel;
    }

    @NotNull
    private JPanel createGeneralSettingsPanel() {
        // 离线缓存说明标签
        JBLabel cacheHintLabel = new JBLabel(
                "启用后，在书架右键或章节列表底部可触发缓存；阅读时优先读取本地缓存。"
        );
        cacheHintLabel.setForeground(JBColor.GRAY);

        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(enableErrorLogCheckBox, JBUI.scale(5))
                .addComponent(enableInLineModelCheckBox, JBUI.scale(5))
                .addComponent(enableOfflineCacheCheckBox, JBUI.scale(5))
                .addComponent(cacheHintLabel, JBUI.scale(2))
                .addComponent(hideScrollBarCheckBox, JBUI.scale(5))
                .getPanel();

        panel.setBorder(IdeBorderFactory.createTitledBorder("常规设置"));

        return panel;
    }

    @NotNull
    private JPanel createReadingInterfacePanel() {
        // 颜色行
        JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0));
        colorRow.add(fontColorButton);

        // 字体大小行：Spinner + 提示
        JPanel fontSizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0));
        fontSizeRow.add(fontSizeSpinner);
        JBLabel hintLabel = new JBLabel("(0=使用编辑器默认)");
        hintLabel.setForeground(JBColor.GRAY);
        fontSizeRow.add(hintLabel);

        // 左侧表单
        JPanel formPanel = FormBuilder.createFormBuilder()
                .setVerticalGap(JBUI.scale(5))
                .addLabeledComponent(new JBLabel("正文字体颜色:"), colorRow, false)
                .addLabeledComponent(new JBLabel("正文字体:"), fontNameComboBox, false)
                .addLabeledComponent(new JBLabel("正文字体大小:"), fontSizeRow, false)
                .addLabeledComponent(new JBLabel("正文字体行高:"), lineHeightSpinner, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        // 右侧预览面板
        JBScrollPane previewScrollPane = new JBScrollPane(fontPreviewPane);
        previewScrollPane.setPreferredSize(JBUI.size(300, 150));
        previewScrollPane.setBorder(IdeBorderFactory.createTitledBorder("字体效果预览"));

        // 组合左右面板
        JBPanel<?> contentPanel = new JBPanel<>(new BorderLayout(JBUI.scale(10), 0));
        contentPanel.add(formPanel, BorderLayout.WEST);
        contentPanel.add(previewScrollPane, BorderLayout.CENTER);

        contentPanel.setBorder(IdeBorderFactory.createTitledBorder("阅读界面设置"));

        return contentPanel;
    }

    // ==================== 双向数据绑定 ====================

    private void bindToViewModel() {
        // 双向绑定：UI 变化 → ViewModel 更新
        enableErrorLogCheckBox.addActionListener(e ->
                viewModel.setEnableErrorLog(enableErrorLogCheckBox.isSelected())
        );

        enableInLineModelCheckBox.addActionListener(e ->
                viewModel.setEnableInLineMode(enableInLineModelCheckBox.isSelected())
        );

        enableOfflineCacheCheckBox.addActionListener(e ->
                viewModel.setCacheEnabled(enableOfflineCacheCheckBox.isSelected())
        );

        hideScrollBarCheckBox.addActionListener(e ->
                viewModel.setHideScrollBar(hideScrollBarCheckBox.isSelected())
        );

        // 颜色选择
        fontColorButton.addActionListener(e -> syncFontColor());

        // 字体设置变化
        fontNameComboBox.addActionListener(e -> {
            viewModel.setFontName((String) fontNameComboBox.getSelectedItem());
            updateFontPreview();
        });

        fontSizeSpinner.addChangeListener(e -> {
            viewModel.setFontSize((int) fontSizeSpinner.getValue());
            updateFontPreview();
        });

        lineHeightSpinner.addChangeListener(e -> {
            viewModel.setLineHeight((double) lineHeightSpinner.getValue());
            updateFontPreview();
        });

        // 自定义参数变化
        customParamTablePanel.addChangeListener(viewModel::setCustomParams);
    }

    /**
     * 同步字体颜色
     */
    private void syncFontColor() {
        Color newColor = fontColorButton.getSelectedColor();
        if (newColor == null) {
            return;
        }

        updateFontPreview(null,newColor,null);
        viewModel.setFontColor(newColor);
    }

    private void updateFontPreview() {
        try {
            String fontName = (String) fontNameComboBox.getSelectedItem();

            int fontSize = (int) fontSizeSpinner.getValue();
            if (fontSize == 0) {
                fontSize = fontSizeSpinner.getFont().getSize();
            }

            double lineHeight = (double) lineHeightSpinner.getValue();

            updateFontPreview(new Font(fontName, Font.PLAIN, fontSize),
                    resolvePreviewColor(),
                    lineHeight);
        } catch (Exception e) {
            log.error("更新字体预览时出错", e);
        }
    }

    /**
     * 解析预览用的字体颜色
     * <p>
     * refresh() 中字体下拉框 setSelectedItem 会先于颜色按钮 setSelectedColor 触发
     * updateFontPreview，此时 getSelectedColor() 返回 null；
     * 若直接用于构造 JBColor 会抛出 IllegalArgumentException。
     * 因此回退到 ViewModel 中的颜色，仍未取到时使用与默认设置一致的绿色。
     */
    private Color resolvePreviewColor() {
        Color color = fontColorButton.getSelectedColor();
        if (color == null) {
            color = viewModel.getFontColor();
        }
        return color != null ? color : JBColor.green;
    }

    private void updateFontPreview(Font font, Color textBodyFontColor, Double lineHeight) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Optional.ofNullable(font).ifPresent(fontPreviewPane::setFont);
            Optional.ofNullable(textBodyFontColor).ifPresent(fontPreviewPane::setForeground);
            Optional.ofNullable(lineHeight).ifPresent(this::applyLineHeight);
        });
    }

    private void applyLineHeight(double lineHeight) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        float lineSpacing = (float) (lineHeight - 1.0);
        StyleConstants.setLineSpacing(attrs, lineSpacing);

        StyledDocument doc = fontPreviewPane.getStyledDocument();
        doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
    }

    // ==================== 验证结果处理 ====================

    private void onValidationResult(ValidationResult result) {
        // 更新 UI 显示验证状态
        customParamTablePanel.showValidationErrors(result);
    }
}
