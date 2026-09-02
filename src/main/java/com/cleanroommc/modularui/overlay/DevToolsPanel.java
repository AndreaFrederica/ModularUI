package com.cleanroommc.modularui.overlay;

import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.debug.MuiDiagnostics;
import com.cleanroommc.modularui.api.dom.NodeHandle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.style.MuiComputedStyle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.CodeEditorWidget;
import com.cleanroommc.modularui.widget.ParentWidget;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Browser-like, client-local inspector for an existing MUI screen. */
public final class DevToolsPanel extends ModularPanel {

    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int PANEL_BACKGROUND = 0xf010161d;
    private static final int SUB_BACKGROUND = 0xf016222b;
    private static final int SURFACE = 0xf01d2a35;
    private static final int SURFACE_HOVER = 0xf0263947;
    private static final int EDITOR_BACKGROUND = 0xff0b1117;

    private final ModularScreen inspected;
    private final com.cleanroommc.modularui.screen.dom.MuiDevToolsSession tools;
    private final ListWidget<IWidget, ?> elementList = new ListWidget<>();
    private final TextWidget<?> elementDetails;
    private final ListWidget<IWidget, ?> sourceList = new ListWidget<>();
    private final CodeEditorWidget sourceEditor = new CodeEditorWidget();
    private final ButtonWidget<?> sourceApplyButton;
    private final TextWidget<?> logText;

    public DevToolsPanel(IMuiScreen screen) {
        super("mui_devtools");
        this.inspected = screen.getScreen();
        this.tools = this.inspected.openDevTools();
        this.sourceApplyButton = chromeButton(
                IKey.dynamic(() -> selectedSource != null && tools.isSourceReadOnly(selectedSource)
                        ? "Read only" : "Apply"), 52);
        this.elementDetails = new TextWidget<>(IKey.dynamic(this::formatSelected))
                .textAlign(Alignment.TopLeft).color(0xffc9d5df).scale(0.72f);
        this.logText = new TextWidget<>(IKey.dynamic(this::formatLog))
                .textAlign(Alignment.TopLeft).color(0xffc9d5df).scale(0.68f);
        size(PANEL_WIDTH, PANEL_HEIGHT)
                .background(new Rectangle().color(PANEL_BACKGROUND))
                .padding(4);
        buildContents();
    }

    private void buildContents() {
        PagedWidget.Controller tabs = new PagedWidget.Controller();
        PagedWidget<?> pages = new PagedWidget<>()
                .name("devtools-pages")
                .sizeRel(1f)
                .controller(tabs)
                .addPage(elementsPage())
                .addPage(sourcePage())
                .addPage(logPage());

        child(Flow.col()
                .sizeRel(1f)
                .child(Flow.row().widthRel(1f).height(16)
                        .child(tab(0, "Elements", tabs))
                        .child(tab(1, "Source", tabs))
                        .child(tab(2, "Log", tabs))
                        .child(chromeButton("Refresh", 48)
                                .onMousePressed(button -> {
                                    if (button != 0) return false;
                                    refreshElements();
                                    return true;
                                }))
                        .child(chromeButton("Clear", 38)
                                .onMousePressed(button -> {
                                    if (button != 0) return false;
                                    tools.clearLog();
                                    return true;
                                }))
                        .child(closeButton()))
                .child(pages));
        refreshElements();
    }

    private ButtonWidget<?> tab(int index, String label, PagedWidget.Controller tabs) {
        return chromeButton(label, 66)
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    tabs.setPage(index);
                    return true;
                });
    }

    private ParentWidget<?> elementsPage() {
        ParentWidget<?> page = new ParentWidget<>()
                .background(new Rectangle().color(SUB_BACKGROUND))
                .sizeRel(1f)
                .padding(3);
        elementList.name("dom-tree").width(145).heightRel(1f)
                .background(new Rectangle().color(0xff111a22));
        page.child(Flow.row().sizeRel(1f)
                .child(elementList)
                .child(elementDetails.background(new Rectangle().color(SURFACE)).width(1).widthRel(1f).heightRel(1f).padding(6)));
        return page;
    }

    private ParentWidget<?> sourcePage() {
        ParentWidget<?> page = new ParentWidget<>()
                .background(new Rectangle().color(SUB_BACKGROUND))
                .sizeRel(1f)
                .padding(4)
                .child(Flow.row().sizeRel(1f)
                        .child(sourceList.width(104).heightRel(1f).background(new Rectangle().color(0xff111a22)))
                        .child(Flow.col().sizeRel(1f)
                                .child(sourceEditor.widthRel(1f).heightRel(1f)
                                        .background(new Rectangle().color(EDITOR_BACKGROUND)))
                                .child(sourceApplyButton
                                        .onMousePressed(button -> {
                                            if (button != 0 || selectedSource == null
                                                    || tools.isSourceReadOnly(selectedSource)) return false;
                                            applySourceEdit();
                                            return true;
                                        }))));
        refreshSources();
        return page;
    }

    private ParentWidget<?> logPage() {
        return new ParentWidget<>()
                .background(new Rectangle().color(SUB_BACKGROUND))
                .sizeRel(1f)
                .padding(4)
                .child(logText.background(new Rectangle().color(SURFACE)).widthRel(1f).heightRel(1f).padding(6));
    }

    private void refreshElements() {
        tools.refresh();
        elementList.removeAll();
        List<Row> rows = new ArrayList<>();
        flatten(tools.snapshot(), 0, rows);
        for (Row row : rows) {
            elementList.child(new ButtonWidget<>()
                    .widthRel(1f)
                    .height(12)
                    .background(new Rectangle().color(SURFACE))
                    .disableHoverThemeBackground(true)
                    .overlay(IKey.str(row.label))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        tools.select(row.handle);
                        return true;
                    }));
        }
    }

    private String selectedSource;

    private void refreshSources() {
        sourceList.removeAll();
        if (tools.getSources().isEmpty()) {
            sourceEditor.setTextLines(java.util.Collections.singletonList("No registered source files"));
            selectedSource = null;
            return;
        }
        if (selectedSource == null || !tools.getSources().containsKey(selectedSource)) {
            selectedSource = tools.getSources().keySet().iterator().next();
        }
        for (String resourceId : tools.getSources().keySet()) {
            sourceList.child(new ButtonWidget<>()
                    .widthRel(1f).height(14)
                    .background(new Rectangle().color(SURFACE))
                    .disableHoverThemeBackground(true)
                    .overlay(IKey.str(resourceId))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        selectedSource = resourceId;
                        loadSelectedSource();
                        return true;
                    }));
        }
        loadSelectedSource();
    }

    private void loadSelectedSource() {
        String source = selectedSource == null ? "" : tools.getSource(selectedSource);
        sourceEditor.setTextLines(splitLines(source == null ? "" : source));
        sourceEditor.setEditable(selectedSource != null && !tools.isSourceReadOnly(selectedSource));
    }

    private void applySourceEdit() {
        try {
            tools.setSource(selectedSource, joinLines(sourceEditor.getTextLines()));
            refreshElements();
        } catch (RuntimeException exception) {
            MuiDiagnostics.error("devtools", "Unable to apply source " + selectedSource, exception);
        }
    }

    private static List<String> splitLines(String source) {
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> result = new ArrayList<>();
        java.util.Collections.addAll(result, lines);
        return result;
    }

    private static String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }

    private static ButtonWidget<?> chromeButton(String label, int width) {
        return chromeButton(IKey.str(label), width);
    }

    private static ButtonWidget<?> chromeButton(IKey label, int width) {
        return new ButtonWidget<>().width(width).height(16)
                .background(new Rectangle().color(SURFACE))
                .disableHoverThemeBackground(true)
                .overlay(label.scale(0.72f));
    }

    private static ButtonWidget<?> closeButton() {
        ButtonWidget<?> button = new ButtonWidget<>().width(16).height(16)
                .background(new Rectangle().color(0xff263541))
                .disableHoverThemeBackground(true)
                .overlay(IKey.str("x").scale(0.82f));
        return button.onMousePressed(mouseButton -> {
            if (mouseButton != 0 && mouseButton != 1) return false;
            button.getPanel().closeIfOpen();
            return true;
        });
    }

    private static void flatten(com.cleanroommc.modularui.screen.dom.MuiDevToolsSession.NodeSnapshot node,
                                int depth, List<Row> rows) {
        if (!"#document".equals(node.getTagName())) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) indent.append("  ");
            rows.add(new Row(node.getHandle(), indent + "<" + node.getTagName() + ">"));
        }
        for (com.cleanroommc.modularui.screen.dom.MuiDevToolsSession.NodeSnapshot child : node.getChildren()) {
            flatten(child, "#document".equals(node.getTagName()) ? depth : depth + 1, rows);
        }
    }

    private String formatSelected() {
        com.cleanroommc.modularui.screen.dom.MuiDevToolsSession.NodeSnapshot selected = tools.selectedSnapshot();
        if (selected == null) return "No element selected";
        StringBuilder result = new StringBuilder();
        result.append('<').append(selected.getTagName()).append('>');
        if (!selected.getAttributes().isEmpty()) result.append("\nattributes: ").append(selected.getAttributes());
        if (selected.getWidgetClass() != null) result.append("\nwidget: ").append(selected.getWidgetClass());
        if (selected.getGeometry() != null) {
            com.cleanroommc.modularui.screen.dom.MuiDevToolsSession.Geometry g = selected.getGeometry();
            result.append("\nbox: ").append(g.getX()).append(',').append(g.getY())
                    .append(' ').append(g.getWidth()).append('x').append(g.getHeight());
        }
        MuiComputedStyle style = tools.getComputedStyle();
        if (style != null && !style.getProperties().isEmpty()) result.append("\n\ncomputed:\n").append(style.getProperties());
        return result.toString();
    }

    private String formatLog() {
        List<MuiDiagnostics.Entry> entries = tools.getLog();
        if (entries.isEmpty()) return "No MUI diagnostics";
        StringBuilder result = new StringBuilder();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        format.setTimeZone(TimeZone.getDefault());
        for (MuiDiagnostics.Entry entry : entries) {
            result.append('[').append(format.format(new Date(entry.getTimestamp()))).append("] ")
                    .append(entry.getLevel()).append(' ').append(entry.getSource()).append(": ")
                    .append(entry.getMessage());
            if (entry.getErrorType() != null) {
                result.append(" (").append(entry.getErrorType());
                if (entry.getErrorMessage() != null && !entry.getErrorMessage().isEmpty()) {
                    result.append(": ").append(entry.getErrorMessage());
                }
                result.append(')');
            }
            result.append('\n');
        }
        return result.toString();
    }

    @Override
    public void dispose() {
        tools.close();
        super.dispose();
    }

    private static final class Row {
        private final NodeHandle handle;
        private final String label;

        private Row(NodeHandle handle, String label) {
            this.handle = handle;
            this.label = label;
        }
    }
}
