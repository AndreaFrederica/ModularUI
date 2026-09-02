package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.markup.MuiMarkupException;
import com.cleanroommc.modularui.markup.MuiXmlParser;
import com.cleanroommc.modularui.style.MuiCascade;
import com.cleanroommc.modularui.style.MuiComputedStyle;
import com.cleanroommc.modularui.style.MuiSelector;
import com.cleanroommc.modularui.style.MuiStylesheetParser;
import com.cleanroommc.modularui.style.MuiStyleApplier;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.cleanroommc.modularui.widgets.textfield.CodeEditorWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.overlay.ScreenWrapper;
import net.minecraft.client.gui.GuiScreen;

import org.junit.jupiter.api.BeforeAll;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StyleTest {

    @BeforeAll
    static void bootstrapForge() {
        Bootstrap.perform();
    }

    @Test
    void selectorCascadeResolvesVariablesSpecificityStatesAndCombinators() {
        MuiDocument document = new MuiDocument();
        MuiElement root = MuiXmlParser.parse(
                "<mui:container xmlns:mui=\"urn:mui\" id=\"root\" class=\"toolbar\"><mui:button id=\"submit\" data-hover=\"true\"><mui:text>Go</mui:text></mui:button></mui:container>",
                document);
        MuiElement button = (MuiElement) root.getChildNodes().get(0);
        MuiElement text = (MuiElement) button.getChildNodes().get(0);
        MuiCascade cascade = MuiStylesheetParser.parse("{"
                + "\"formatVersion\":1,\"variables\":{\"--accent\":\"#55c2ff\",\"--gap\":6},"
                + "\"rules\":["
                + "{\"selector\":\"mui|button\",\"style\":{\"color\":\"black\",\"gap\":\"var(--gap)\"}},"
                + "{\"selector\":\".toolbar mui|button:hover\",\"style\":{\"color\":\"var(--accent)\"}},"
                + "{\"selector\":\"#submit\",\"style\":{\"color\":\"white\",\"width\":80}},"
                + "{\"selector\":\"mui|container > mui|button\",\"style\":{\"flow\":\"row\"}}"
                + "]}");

        MuiComputedStyle style = cascade.compute(button);
        assertEquals("white", style.getString("color"));
        assertEquals("6", style.getString("gap"));
        assertEquals(80, style.getInt("width", 0));
        assertEquals("row", style.getString("flow"));
        assertFalse(cascade.compute(text).has("flow"));

        button.removeAttribute("data-hover");
        assertEquals("white", cascade.compute(button).getString("color"));
        assertEquals("#55c2ff", cascade.compute(button, Collections.singletonMap(
                "color", new JsonParser().parse("\"var(--accent)\""))).getString("color"));
        assertTrue(MuiSelector.parse(".toolbar > mui|button:disabled").getSpecificity() > 10);
    }

    @Test
    void stylesheetParserRejectsUnsupportedVersionAndSelectors() {
        assertThrows(MuiMarkupException.class, () -> MuiStylesheetParser.parse(
                "{\"formatVersion\":2,\"rules\":[]}"));
        assertThrows(MuiMarkupException.class, () -> MuiStylesheetParser.parse(
                "{\"rules\":[{\"selector\":\"button:has(.x)\",\"style\":{}}]}"));
        assertThrows(IllegalArgumentException.class, () -> MuiSelector.parse("button >"));
    }

    @Test
    void cssSubsetSharesJsonCascadeAndSupportsRootVariables() {
        MuiDocument document = new MuiDocument();
        MuiElement root = MuiXmlParser.parse(
                "<mui:container xmlns:mui=\"urn:mui\"><mui:button class=\"primary\" data-hover=\"true\"/></mui:container>",
                document);
        MuiElement button = (MuiElement) root.getChildNodes().get(0);
        MuiCascade cascade = MuiStylesheetParser.parseCss("/* base */\n"
                + ":root { --accent: #55c2ff; --gap: 6; }\n"
                + "mui|button, .primary { color: var(--accent); gap: var(--gap); }\n"
                + ".primary:hover { background: #101820; }");

        MuiComputedStyle style = cascade.compute(button);
        assertEquals("#55c2ff", style.getString("color"));
        assertEquals("6", style.getString("gap"));
        assertEquals("#101820", style.getString("background"));
    }

    @Test
    void cssMediaQueriesUseViewportAndImportsUseAuthorizedResolver() {
        MuiDocument document = new MuiDocument();
        MuiElement button = MuiXmlParser.parse(
                "<mui:button xmlns:mui=\"urn:mui\"/>", document);
        MuiCascade cascade = MuiStylesheetParser.parseCss("client",
                "@import \"base.css\"; @media (min-width: 400px) and (orientation: landscape) {"
                        + "mui|button { color: red; } } @import url(\"wide.css\") screen and (min-width: 400px);",
                resourceResolver(new HashMap<String, String>() {{
                    put("base.css", "mui|button { gap: 2; }");
                    put("wide.css", "mui|button { border: 1; }");
                }}));

        assertEquals("2", cascade.compute(button, new com.cleanroommc.modularui.style.MuiMediaEnvironment(320, 240)).getString("gap"));
        assertFalse(cascade.compute(button, new com.cleanroommc.modularui.style.MuiMediaEnvironment(320, 240)).has("color"));
        assertFalse(cascade.compute(button, new com.cleanroommc.modularui.style.MuiMediaEnvironment(320, 240)).has("border"));
        assertEquals("red", cascade.compute(button, new com.cleanroommc.modularui.style.MuiMediaEnvironment(480, 240)).getString("color"));
        assertEquals("1", cascade.compute(button, new com.cleanroommc.modularui.style.MuiMediaEnvironment(480, 240)).getString("border"));
        assertThrows(MuiMarkupException.class, () -> MuiStylesheetParser.parseCss("@import \"base.css\";"));
        assertThrows(MuiMarkupException.class, () -> MuiStylesheetParser.parseCss("@important \"base.css\";"));
    }

    @Test
    void screenResizeRecomputesMediaStylesThroughDocumentController() {
        ModularScreen screen = new ModularScreen("style-media-test", ModularPanel.defaultPanel("main"));
        screen.getContext().setSettings(new UISettings());
        GuiScreen guiScreen = new GuiScreen() {{
            width = 320;
            height = 240;
        }};
        screen.construct(new ScreenWrapper(guiScreen, screen));
        screen.onResize(320, 240);

        MuiElement panel = screen.getDocument().querySelector("mui:panel");
        MuiElement button = screen.getDocument().createElement("mui:button");
        panel.appendChild(button);
        screen.setStylesheet(MuiStylesheetParser.parseCss(
                "@media (min-width: 400px) { mui|button { width: 120px; } }"));

        assertNull(screen.getComputedStyle(button).getString("width"));
        screen.onResize(480, 240);
        assertEquals("120px", screen.getComputedStyle(button).getString("width"));
        IWidget buttonWidget = screen.getDocumentController().resolveWidget(button.getHandle());
        assertEquals(120, buttonWidget.getArea().w());

        screen.setStylesheet(MuiStylesheetParser.parseCss("mui|button { pointer-events: none; }"));
        assertFalse(screen.getDocumentController().acceptsPointerEvents(buttonWidget));
        screen.setStylesheet(null);
        assertTrue(screen.getDocumentController().acceptsPointerEvents(buttonWidget));
    }

    @Test
    void devToolsSnapshotsAndLiveStyleOverridesStayClientLocal() {
        ModularScreen screen = new ModularScreen("devtools-test", ModularPanel.defaultPanel("main"));
        screen.getContext().setSettings(new UISettings());
        GuiScreen guiScreen = new GuiScreen() {{ width = 320; height = 240; }};
        screen.construct(new ScreenWrapper(guiScreen, screen));
        screen.onResize(320, 240);

        MuiElement panel = screen.getDocument().querySelector("mui:panel");
        MuiElement button = screen.getDocument().createElement("mui:button");
        button.setAttribute("id", "inspect-me");
        panel.appendChild(button);
        screen.setStylesheet(MuiStylesheetParser.parseCss("mui|button { width: 40px; height: 10px; }"));

        com.cleanroommc.modularui.screen.dom.MuiDevToolsSession devTools = screen.openDevTools();
        assertTrue(devTools.select(button.getHandle()));
        assertEquals("mui:panel", devTools.snapshot().getChildren().get(0).getTagName());
        devTools.setStyle("width", "120px");
        assertEquals("120px", devTools.getComputedStyle().getString("width"));
        devTools.setInlineStyle("height", "24px");
        assertEquals("24px", devTools.getComputedStyle().getString("height"));
        devTools.clearStyles(button.getHandle());
        devTools.setStylesheetCss("mui|button { width: 64px; height: 12px; }");
        assertEquals("64px", devTools.getComputedStyle().getString("width"));
        AtomicReference<String> applied = new AtomicReference<>();
        devTools.registerSource("screen.xml", "before", applied::set);
        devTools.setSource("screen.xml", "after");
        assertEquals("after", applied.get());
        assertEquals("after", devTools.getSource("screen.xml"));
        devTools.registerReadOnlySource("protocol.xml", "fixed");
        assertTrue(devTools.isSourceReadOnly("protocol.xml"));
        assertThrows(IllegalStateException.class, () -> devTools.setSource("protocol.xml", "changed"));
        assertEquals("fixed", devTools.getSource("protocol.xml"));
        devTools.close();
    }

    private static com.cleanroommc.modularui.api.markup.MuiResourceResolver resourceResolver(Map<String, String> resources) {
        return (owner, id) -> {
            String source = resources.get(id);
            return source == null ? null : new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
        };
    }

    @Test
    void readOnlyCodeEditorAllowsSelectionWithoutTextMutation() {
        CodeEditorWidget editor = new CodeEditorWidget();
        editor.setTextLines(Collections.singletonList("    fixed"));
        editor.setEditable(false);
        ModularScreen screen = new ModularScreen("read-only-editor-test",
                ModularPanel.defaultPanel("main").child(editor));
        screen.getContext().setSettings(new UISettings());
        GuiScreen guiScreen = new GuiScreen() {{ width = 320; height = 240; }};
        screen.construct(new ScreenWrapper(guiScreen, screen));
        screen.onResize(320, 240);
        screen.getContext().focus(editor);

        editor.onKeyPressed('\r', Keyboard.KEY_RETURN);
        editor.onKeyPressed('\0', Keyboard.KEY_BACK);

        assertEquals(Collections.singletonList("    fixed"), editor.getTextLines());
    }

    @Test
    void computedStyleAppliesToLegacyWidgetAndCanBeRemoved() {
        MuiDocument document = new MuiDocument();
        MuiElement element = document.createElement("mui:text");
        TextWidget<?> widget = new TextWidget<>(IKey.str("hello"));
        widget.size(20, 10);
        widget.setEnabled(true);

        MuiCascade cascade = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|text\",\"style\":{"
                + "\"width\":80,\"height\":24,\"padding\":\"1px 2px\","
                + "\"color\":\"#ff0000\",\"background\":\"#101820\",\"visibility\":\"hidden\"}}]}");
        MuiStyleApplier applier = new MuiStyleApplier();
        applier.apply(element, widget, cascade.compute(element));

        assertFalse(widget.isEnabled());
        assertEquals(0xFFFF0000, widget.getColor().getAsInt());
        assertTrue(widget.getBackground() instanceof Rectangle);
        assertEquals(1, widget.getArea().getPadding().getTop());
        assertEquals(2, widget.getArea().getPadding().getLeft());
        assertTrue(widget.requiresResize());

        MuiElement transparentBorderElement = document.createElement("mui:container");
        ParentWidget<?> transparentBorderWidget = new ParentWidget<>();
        MuiCascade transparentBorder = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|container\",\"style\":{\"border-style\":\"solid\","
                + "\"border-width\":1,\"border-color\":\"transparent\"}}]}");
        applier.apply(transparentBorderElement, transparentBorderWidget,
                transparentBorder.compute(transparentBorderElement));
        assertEquals(0, assertInstanceOf(Rectangle.class, transparentBorderWidget.getOverlay()).getColor());

        applier.clear();
        assertTrue(widget.isEnabled());
        assertNull(widget.getColor());

        MuiElement flowElement = document.createElement("mui:row");
        Flow flow = Flow.row();
        MuiCascade flowCascade = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|row\",\"style\":{\"gap\":4,\"wrap\":true,"
                + "\"justify\":\"space-between\",\"align\":\"end\"}}]}");
        applier.apply(flowElement, flow, flowCascade.compute(flowElement));
        assertEquals(4, flow.getChildPadding());
        assertEquals(4, flow.getCrossAxisChildPadding());
        assertTrue(flow.isWrap());
        assertEquals(com.cleanroommc.modularui.utils.Alignment.MainAxis.SPACE_BETWEEN, flow.getMaa());
        assertEquals(com.cleanroommc.modularui.utils.Alignment.CrossAxis.END, flow.getCaa());

        MuiElement gridElement = document.createElement("mui:grid");
        Grid grid = new Grid();
        MuiCascade gridCascade = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|grid\",\"style\":{\"grid-columns\":3}}]}");
        applier.apply(gridElement, grid, gridCascade.compute(gridElement));
        assertEquals(3, grid.getDomColumns());

        MuiElement buttonElement = document.createElement("mui:button");
        ButtonWidget<?> button = new ButtonWidget<>();
        com.cleanroommc.modularui.api.navigation.NavigationInfo originalNavigation = button.getNavigationInfo();
        MuiCascade navigationCascade = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|button\",\"style\":{\"focusable\":false,\"tab-index\":12}}]}");
        applier.apply(buttonElement, button, navigationCascade.compute(buttonElement));
        assertFalse(button.getNavigationInfo().isFocusable());
        assertEquals(12, button.getNavigationInfo().getOrder());
        applier.remove(buttonElement, button);
        assertSame(originalNavigation, button.getNavigationInfo());
    }

    @Test
    void overflowStyleAddsAndRemovesOptionalScrollAxesWithoutBreakingLegacyAxes() {
        MuiDocument document = new MuiDocument();
        MuiStyleApplier applier = new MuiStyleApplier();

        MuiElement gridElement = document.createElement("mui:grid");
        Grid grid = new Grid();
        MuiCascade auto = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|grid\",\"style\":{\"overflow-y\":\"auto\",\"overflow-x\":\"scroll\"}}]}");
        applier.apply(gridElement, grid, auto.compute(gridElement));
        assertTrue(grid.getScrollArea().getScrollY() instanceof VerticalScrollData);
        assertTrue(grid.getScrollArea().getScrollX() != null);

        MuiCascade hidden = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|grid\",\"style\":{\"overflow-y\":\"hidden\",\"overflow-x\":\"visible\"}}]}");
        applier.apply(gridElement, grid, hidden.compute(gridElement));
        assertNull(grid.getScrollArea().getScrollY());
        assertNull(grid.getScrollArea().getScrollX());

        applier.apply(gridElement, grid, MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":[]}").compute(gridElement));
        assertNull(grid.getScrollArea().getScrollY());
        assertNull(grid.getScrollArea().getScrollX());

        ListWidget<?, ?> list = new ListWidget<>();
        list.onInit();
        VerticalScrollData originalY = (VerticalScrollData) list.getScrollArea().getScrollY();
        MuiElement listElement = document.createElement("mui:list");
        MuiCascade listStyle = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|list\",\"style\":{\"overflow-x\":\"auto\",\"overflow-y\":\"hidden\"}}]}");
        applier.apply(listElement, list, listStyle.compute(listElement));
        assertSame(originalY, list.getScrollArea().getScrollY());
        assertTrue(list.getScrollArea().getScrollX() != null);

        ListWidget<?, ?> preInitList = new ListWidget<>();
        MuiElement preInitElement = document.createElement("mui:list");
        MuiCascade preInitStyle = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|list\",\"style\":{\"overflow-x\":\"auto\"}}]}");
        applier.apply(preInitElement, preInitList, preInitStyle.compute(preInitElement));
        assertTrue(preInitList.getScrollArea().getScrollX() != null);
        preInitList.onInit();
        assertTrue(preInitList.getScrollArea().getScrollX() != null);

        TextFieldWidget field = new TextFieldWidget();
        MuiElement fieldElement = document.createElement("mui:text-field");
        MuiCascade fieldStyle = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|text-field\",\"style\":{\"overflow-x\":\"hidden\"}}]}");
        applier.apply(fieldElement, field, fieldStyle.compute(fieldElement));
        assertTrue(field.getScrollArea().getScrollX() != null);

        Grid themedGrid = new Grid().scrollable();
        themedGrid.getScrollArea().setScrollBarBackgroundColor(0xff010203);
        themedGrid.getScrollArea().getScrollX().setScrollSpeed(11);
        themedGrid.getScrollArea().getScrollY().setCancelScrollEdge(false);
        MuiElement themedElement = document.createElement("mui:grid");
        MuiStyleApplier themedApplier = new MuiStyleApplier();
        MuiCascade scrollbarStyle = MuiStylesheetParser.parse("{\"formatVersion\":1,\"rules\":["
                + "{\"selector\":\"mui|grid\",\"style\":{\"scrollbar-background\":\"#123456\","
                + "\"scrollbar-speed\":7,\"scrollbar-cancel-edge\":true}}]}");
        themedApplier.apply(themedElement, themedGrid, scrollbarStyle.compute(themedElement));
        assertEquals(0xff123456, themedGrid.getScrollArea().getScrollBarBackgroundColor());
        assertEquals(7, themedGrid.getScrollArea().getScrollX().getScrollSpeed());
        assertTrue(themedGrid.getScrollArea().getScrollY().isCancelScrollEdge());
        themedApplier.clear();
        assertEquals(0xff010203, themedGrid.getScrollArea().getScrollBarBackgroundColor());
        assertEquals(11, themedGrid.getScrollArea().getScrollX().getScrollSpeed());
        assertFalse(themedGrid.getScrollArea().getScrollY().isCancelScrollEdge());
    }
}
