package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.component.MuiElementDescriptor;
import com.cleanroommc.modularui.api.component.MuiComponentRegistry;
import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.api.dom.DomException;
import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiText;
import com.cleanroommc.modularui.api.dom.MutationScope;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.overlay.ScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.style.MuiStylesheetParser;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.api.dom.NodeHandle;
import com.cleanroommc.modularui.api.dom.SlotViewUpdate;
import com.cleanroommc.modularui.api.event.ScrollEvent;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Field;
import java.awt.Rectangle;

import net.minecraftforge.items.ItemStackHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeElementProjectionTest {

    @BeforeAll
    static void bootstrapForge() {
        Bootstrap.perform();
    }

    @Test
    void nativeElementsCreateWidgetsAndBridgeTypedAttributesAndText() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiDocument document = screen.getDocument();
        MuiElement panel = document.querySelector("mui:panel");
        MuiElement container = document.createElement("mui:container");
        MuiElement button = document.createElement("mui:button");
        MuiElement text = document.createElement("mui:text");
        MuiText textNode = document.createTextNode("Ready");

        container.setAttribute("id", "native-container");
        container.setAttribute("left", "12");
        container.setAttribute("top", "7");
        container.setAttribute("width", "80");
        container.setAttribute("height", "30");
        button.setAttribute("play-click-sound", "false");
        text.setAttribute("scale", "1.5");
        text.setAttribute("shadow", "true");
        text.setAttribute("max-width", "64");
        text.setAttribute("width", "40");
        text.setAttribute("height", "12");
        text.appendChild(textNode);
        button.appendChild(text);
        container.appendChild(button);
        panel.appendChild(container);
        screen.onResize(320, 240);

        ParentWidget<?> containerWidget = assertInstanceOf(ParentWidget.class, resolve(screen, container));
        ButtonWidget<?> buttonWidget = assertInstanceOf(ButtonWidget.class, resolve(screen, button));
        TextWidget<?> textWidget = assertInstanceOf(TextWidget.class, resolve(screen, text));
        assertEquals("native-container", containerWidget.getName());
        assertEquals(panelWidget(screen).getArea().x() + 12, containerWidget.getArea().x());
        assertEquals(panelWidget(screen).getArea().y() + 7, containerWidget.getArea().y());
        assertEquals(80, containerWidget.getArea().w());
        assertEquals(30, containerWidget.getArea().h());
        assertFalse(buttonWidget.isPlayClickSound());
        assertEquals(1.5f, textWidget.getScale());
        assertEquals(Boolean.TRUE, textWidget.isShadow());
        assertEquals(64, textWidget.getMaxWidth());
        assertEquals("\u00a7rReady", textWidget.getKey().getFormatted());
        assertSame(buttonWidget, textWidget.getParent());

        textNode.setData("Running");
        assertEquals("\u00a7rRunning", textWidget.getKey().getFormatted());
        container.setAttribute("hidden", "true");
        assertFalse(containerWidget.isEnabled());
        container.removeAttribute("hidden");
        assertTrue(containerWidget.isEnabled());
    }

    @Test
    void unknownTagsStayLogicalAndCannotWrapNativeWidgets() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiDocument document = screen.getDocument();
        MuiElement panel = document.querySelector("mui:panel");
        MuiElement logical = document.createElement("app:state");

        panel.appendChild(logical);
        assertTrue(logical.isConnected());
        assertNull(screen.getDocumentController().resolveWidget(logical.getHandle()));

        MuiElement wrapper = document.createElement("app:wrapper");
        MuiElement nativeChild = document.createElement("mui:container");
        int panelChildren = panel.getChildNodes().size();
        DomException failure = assertThrows(DomException.class, () -> {
            try (MutationScope scope = document.beginMutation()) {
                wrapper.appendChild(nativeChild);
                panel.appendChild(wrapper);
                scope.commit();
            }
        });

        assertEquals(DomException.Code.NOT_SUPPORTED, failure.getCode());
        assertTrue(wrapper.getChildNodes().isEmpty());
        assertFalse(wrapper.isConnected());
        assertFalse(nativeChild.isConnected());
        assertEquals(panelChildren, panel.getChildNodes().size());
        assertNull(screen.getDocumentController().resolveWidget(nativeChild.getHandle()));
    }

    @Test
    void singleChildLimitRejectsWholeTransactionBeforeProjection() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiDocument document = screen.getDocument();
        MuiElement panel = document.querySelector("mui:panel");
        MuiElement button = document.createElement("mui:button");
        MuiElement first = document.createElement("mui:text");
        MuiElement second = document.createElement("mui:text");

        DomException failure = assertThrows(DomException.class, () -> {
            try (MutationScope scope = document.beginMutation()) {
                button.appendChild(first);
                button.appendChild(second);
                scope.commit();
            }
        });

        assertEquals(DomException.Code.HIERARCHY_REQUEST, failure.getCode());
        assertTrue(button.getChildNodes().isEmpty());
        assertNull(screen.getDocumentController().resolveWidget(button.getHandle()));
        assertNull(screen.getDocumentController().resolveWidget(first.getHandle()));
        assertNull(screen.getDocumentController().resolveWidget(second.getHandle()));

        button.appendChild(first);
        panel.appendChild(button);
        ButtonWidget<?> buttonWidget = assertInstanceOf(ButtonWidget.class, resolve(screen, button));
        assertSame(resolve(screen, first), buttonWidget.getChild());
    }

    @Test
    void gridMaintainsFlatDomOrderAcrossColumnsReorderAndCrossParentMove() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiDocument document = screen.getDocument();
        MuiElement panel = document.querySelector("mui:panel");
        MuiElement gridElement = document.createElement("mui:grid");
        MuiElement otherGridElement = document.createElement("mui:grid");
        MuiElement firstElement = document.createElement("mui:container");
        MuiElement secondElement = document.createElement("mui:container");
        MuiElement thirdElement = document.createElement("mui:container");
        gridElement.setAttribute("columns", "2");
        gridElement.appendChild(firstElement);
        gridElement.appendChild(secondElement);
        gridElement.appendChild(thirdElement);
        panel.appendChild(gridElement);
        panel.appendChild(otherGridElement);

        Grid grid = assertInstanceOf(Grid.class, resolve(screen, gridElement));
        Grid otherGrid = assertInstanceOf(Grid.class, resolve(screen, otherGridElement));
        IWidget first = resolve(screen, firstElement);
        IWidget second = resolve(screen, secondElement);
        IWidget third = resolve(screen, thirdElement);
        assertEquals(2, grid.getDomColumns());
        assertEquals(Arrays.asList(first, second, third), grid.getChildren());

        gridElement.insertChild(0, thirdElement);
        assertEquals(Arrays.asList(third, first, second), grid.getChildren());
        gridElement.setAttribute("columns", "3");
        assertEquals(3, grid.getDomColumns());
        assertEquals(Arrays.asList(third, first, second), grid.getChildren());

        otherGridElement.appendChild(firstElement);
        assertEquals(Arrays.asList(third, second), grid.getChildren());
        assertEquals(Arrays.asList(first), otherGrid.getChildren());
        assertSame(otherGrid, first.getParent());
        assertTrue(first.isValid());
    }

    @Test
    void registryAllowsPreOpenExtensionsThenFreezesAndIncludesBoundWidgets() {
        ModularPanel panel = ModularPanel.defaultPanel("main");
        ModularScreen screen = new ModularScreen("native-registry-test", panel);
        assertInstanceOf(MuiElementDescriptor.class, screen.getElementRegistry().find("mui:item-slot"));
        assertInstanceOf(MuiElementDescriptor.class, screen.getElementRegistry().find("mui:progress"));
        assertInstanceOf(MuiElementDescriptor.class, screen.getElementRegistry().find("mui:slider"));
        assertInstanceOf(MuiElementDescriptor.class, screen.getElementRegistry().find("mui:text-field"));
        screen.getElementRegistry().register(MuiElementDescriptor.builder(
                "test:custom", CustomWidget.class, element -> new CustomWidget()).build());

        construct(screen);

        assertTrue(screen.getElementRegistry().isFrozen());
        assertThrows(IllegalStateException.class, () -> screen.getElementRegistry().register(
                MuiElementDescriptor.builder("test:late", CustomWidget.class,
                        element -> new CustomWidget()).build()));
        MuiElement custom = screen.getDocument().createElement("test:custom");
        screen.getDocument().querySelector("mui:panel").appendChild(custom);
        assertInstanceOf(CustomWidget.class, resolve(screen, custom));
    }

    @Test
    void invalidTypedPropertyDoesNotMutateDomOrWidget() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiDocument document = screen.getDocument();
        MuiElement gridElement = document.createElement("mui:grid");

        assertThrows(IllegalArgumentException.class, () -> gridElement.setAttribute("columns", "0"));
        assertNull(gridElement.getAttribute("columns"));
        document.querySelector("mui:panel").appendChild(gridElement);
        Grid grid = assertInstanceOf(Grid.class, resolve(screen, gridElement));
        assertEquals(1, grid.getDomColumns());
        assertThrows(IllegalArgumentException.class, () -> gridElement.setAttribute("columns", "nope"));
        assertNull(gridElement.getAttribute("columns"));
        assertEquals(1, grid.getDomColumns());
    }

    @Test
    void compiledXmlCanBeInstalledBelowTheProjectedMainPanel() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiElement root = screen.installCompiledDocument("markup-test",
                "<mui:container xmlns:mui=\"urn:mui\" id=\"compiled\"><mui:text>Ready</mui:text></mui:container>",
                new MuiComponentRegistry(), (owner, resource) -> null);

        assertTrue(root.isConnected());
        ParentWidget<?> widget = assertInstanceOf(ParentWidget.class,
                screen.getDocumentController().resolveWidget(root.getHandle()));
        assertEquals("compiled", widget.getName());
        assertEquals("\u00a7rReady", ((TextWidget<?>) widget.getChildren().get(0)).getKey().getFormatted());
    }

    @Test
    void compiledXmlCanBeAtomicallyReplaced() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiComponentRegistry components = new MuiComponentRegistry();
        com.cleanroommc.modularui.api.markup.MuiResourceResolver resolver = (owner, resource) -> null;
        MuiElement original = screen.installCompiledDocument("markup-test",
                "<mui:container xmlns:mui=\"urn:mui\" id=\"before\"><mui:text text=\"Before\"/></mui:container>",
                components, resolver);

        MuiElement replacement = screen.replaceCompiledDocument(original, "markup-test",
                "<mui:container xmlns:mui=\"urn:mui\" id=\"after\"><mui:text text=\"After\"/></mui:container>",
                components, resolver);

        assertFalse(original.isAlive());
        assertTrue(replacement.isConnected());
        assertNull(screen.getDocument().querySelector("#before"));
        assertSame(replacement, screen.getDocument().querySelector("#after"));
        ParentWidget<?> widget = assertInstanceOf(ParentWidget.class, resolve(screen, replacement));
        assertEquals("\u00a7rAfter", ((TextWidget<?>) widget.getChildren().get(0)).getKey().getFormatted());

        assertThrows(RuntimeException.class, () -> screen.replaceCompiledDocument(replacement, "markup-test",
                "<mui:container xmlns:mui=\"urn:mui\"><mui:text></mui:container>", components, resolver));
        assertTrue(replacement.isConnected());
        assertSame(replacement, screen.getDocument().querySelector("#after"));
    }

    @Test
    void insertedComponentDescendantsRecomputeStylesAgainstCommittedAncestors() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        screen.setStylesheet(MuiStylesheetParser.parseCss(
                ".inventory-label { top: 105px; width: 100px; height: 9px; }"
                        + ".furnace-shell .inventory-label { top: 116px; }"));
        MuiDocument document = screen.getDocument();
        MuiElement panel = document.querySelector("mui:panel");
        MuiElement shell = document.createElement("mui:container");
        MuiElement component = document.createElement("mui:container");
        MuiElement label = document.createElement("mui:text");
        shell.setAttribute("class", "furnace-shell");
        label.setAttribute("class", "inventory-label");
        label.setAttribute("text", "Inventory");

        component.appendChild(label);
        shell.appendChild(component);
        panel.appendChild(shell);
        screen.onResize(320, 240);

        TextWidget<?> labelWidget = assertInstanceOf(TextWidget.class, resolve(screen, label));
        assertEquals("116px", screen.getComputedStyle(label).get("top").getAsString());
        assertEquals(resolve(screen, component).getArea().y() + 116, labelWidget.getArea().y());
    }

    @Test
    void xmlBindingsAreImmutableAfterMaterialization() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiElement root = screen.installCompiledDocument("binding-test",
                "<mui:container xmlns:mui=\"urn:mui\"><mui:progress id=\"progress\" bind=\"first\"/></mui:container>",
                new MuiComponentRegistry(), (owner, resource) -> null);
        MuiElement progress = root.querySelector("#progress");
        assertInstanceOf(ProgressWidget.class,
                screen.getDocumentController().resolveWidget(progress.getHandle()));

        DomException exception = assertThrows(DomException.class,
                () -> progress.setAttribute("bind", "second"));
        assertEquals(DomException.Code.NOT_SUPPORTED, exception.getCode());
        assertEquals("first", progress.getAttribute("bind"));
    }

    @Test
    void realSyncedSlotsKeepContainerOrdinalsAcrossDomParkingRefresh() throws Exception {
        ItemSlot firstSlotWidget = new ItemSlot();
        ItemSlot secondSlotWidget = new ItemSlot();
        ParentWidget<?> visible = new ParentWidget<>();
        ParentWidget<?> parking = new ParentWidget<>();
        parking.setEnabled(false);
        visible.child(firstSlotWidget).child(secondSlotWidget);

        ModularContainerFixture fixture = new ModularContainerFixture();
        ModularSlot firstSlot = fixture.createSlot();
        ModularSlot secondSlot = fixture.createSlot();
        ItemSlotSH firstHandler = new ItemSlotSH(firstSlot);
        ItemSlotSH secondHandler = new ItemSlotSH(secondSlot);
        fixture.register("first", firstHandler);
        fixture.register("second", secondHandler);
        fixture.initialize();

        firstSlotWidget.syncHandler(firstHandler);
        secondSlotWidget.syncHandler(secondHandler);
        ModularScreen screen = new FixtureScreen(visible, parking, fixture.container);
        construct(screen);

        MuiDocument document = screen.getDocument();
        MuiElement visibleElement = screen.getDocumentController().getElement(visible);
        MuiElement parkingElement = screen.getDocumentController().getElement(parking);
        assertSame(visibleElement, document.resolve(screen.getDocumentController().getNodeHandle(visible)));
        assertSame(parkingElement, document.resolve(screen.getDocumentController().getNodeHandle(parking)));
        assertTrue(firstSlot.isInitialized());
        assertTrue(secondSlot.isInitialized());
        assertEquals(2, fixture.container.inventorySlots.size());
        assertSame(firstSlot, fixture.container.getSlot(0));
        assertSame(secondSlot, fixture.container.getSlot(1));

        NodeHandle firstHandle = screen.getDocumentController().getNodeHandle(firstSlotWidget);
        NodeHandle secondHandle = screen.getDocumentController().getNodeHandle(secondSlotWidget);
        assertSame(firstSlotWidget, screen.getDocumentController().resolveWidget(firstHandle));
        assertSame(secondSlotWidget, screen.getDocumentController().resolveWidget(secondHandle));

        AtomicReference<RuntimeException> workerFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                screen.getDocumentController().enqueueSlotViewUpdate(new SlotViewUpdate(
                        screen.getDocumentController().getNodeHandle(visible),
                        screen.getDocumentController().getNodeHandle(parking),
                        Arrays.asList(firstHandle, secondHandle),
                        Collections.singletonList(secondHandle)));
            } catch (RuntimeException failure) {
                workerFailure.set(failure);
            }
        });
        worker.start();
        worker.join();
        assertNull(workerFailure.get());
        assertEquals(1, screen.getDocumentController().flushPendingUpdates());

        assertEquals(Arrays.asList(secondSlotWidget), visible.getChildren());
        assertEquals(Arrays.asList(firstSlotWidget), parking.getChildren());
        assertSame(secondSlotWidget, screen.getDocumentController().resolveWidget(secondHandle));
        assertSame(firstSlotWidget, screen.getDocumentController().resolveWidget(firstHandle));
        assertSame(firstSlot, fixture.container.getSlot(0));
        assertSame(secondSlot, fixture.container.getSlot(1));
        assertTrue(firstHandler.isValid());
        assertTrue(secondHandler.isValid());
        assertFalse(parking.isEnabled());
        assertEquals(firstHandle, screen.getDocumentController().getNodeHandle(firstSlotWidget));
        assertEquals(secondHandle, screen.getDocumentController().getNodeHandle(secondSlotWidget));
    }

    @Test
    void containerReadyLifecycleCanCreateXmlSlotFromPreRegisteredHandler() throws Exception {
        ModularContainerFixture fixture = new ModularContainerFixture();
        ModularSlot slot = fixture.createSlot();
        ItemSlotSH handler = new ItemSlotSH(slot);
        fixture.register("machine", handler);
        fixture.initialize();

        ContainerReadyScreen screen = new ContainerReadyScreen();
        screen.getContext().setSettings(new UISettings());
        screen.construct(new TestContainerWrapper(fixture.container, screen));
        screen.onResize(320, 240);

        assertTrue(screen.callbackInvoked);
        assertTrue(slot.isInitialized());
        assertTrue(handler.isValid());
        assertTrue(fixture.container.isSlotRegistered(slot));
        MuiElement element = screen.getDocument().querySelector("#xml-slot");
        ItemSlot slotWidget = assertInstanceOf(ItemSlot.class,
                screen.getDocumentController().resolveWidget(element.getHandle()));
        assertSame(handler, slotWidget.getSyncHandler());
    }

    @Test
    void compiledXmlCanReplaceAViewOfTheSameFixedContainerSlot() throws Exception {
        ModularContainerFixture fixture = new ModularContainerFixture();
        ModularSlot slot = fixture.createSlot();
        ItemSlotSH handler = new ItemSlotSH(slot);
        fixture.register("machine", handler);
        fixture.initialize();

        ContainerReadyScreen screen = new ContainerReadyScreen();
        screen.getContext().setSettings(new UISettings());
        screen.construct(new TestContainerWrapper(fixture.container, screen));
        screen.onResize(320, 240);

        MuiElement original = screen.getDocument().querySelector("#xml-root");
        ItemSlot originalWidget = assertInstanceOf(ItemSlot.class,
                screen.getDocumentController().resolveWidget(
                        screen.getDocument().querySelector("#xml-slot").getHandle()));
        MuiElement replacement = screen.replaceCompiledDocument(original, "container-ready-test",
                "<mui:container xmlns:mui=\"urn:mui\" id=\"xml-root-after\">"
                        + "<mui:text text=\"Changed\"/>"
                        + "<mui:item-slot id=\"xml-slot-after\" bind=\"machine\"/>"
                        + "</mui:container>",
                new MuiComponentRegistry(), (owner, resource) -> null);

        ItemSlot replacementWidget = assertInstanceOf(ItemSlot.class,
                screen.getDocumentController().resolveWidget(
                        screen.getDocument().querySelector("#xml-slot-after").getHandle()));
        assertFalse(original.isAlive());
        assertTrue(replacement.isConnected());
        assertFalse(originalWidget.isDomMounted());
        assertTrue(replacementWidget.isDomMounted());
        assertSame(handler, replacementWidget.getSyncHandler());
        assertEquals(1, fixture.container.inventorySlots.size());
        assertSame(slot, fixture.container.getSlot(0));
    }

    @Test
    void overlayOnContainerScreenDoesNotDispatchContainerReadyLifecycle() {
        LifecycleScreen overlay = new LifecycleScreen();
        overlay.getContext().setSettings(new UISettings());
        GuiContainer host = new GuiContainer(new ModularContainer()) {
            @Override
            protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}
        };

        overlay.constructOverlay(host);
        assertTrue(overlay.getScreenWrapper().isGuiContainer());
        overlay.onResize(320, 240);

        assertTrue(overlay.isOverlay());
        assertFalse(overlay.callbackInvoked);
    }

    @Test
    void scrollPropertiesAndEventsBridgeTheProjectedViewport() {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        MuiDocument document = screen.getDocument();
        MuiElement panel = document.querySelector("mui:panel");
        MuiElement element = document.createElement("mui:scroll");
        element.setAttribute("width", "100");
        element.setAttribute("height", "50");
        panel.appendChild(element);

        ScrollWidget<?> widget = (ScrollWidget<?>) resolve(screen, element);
        VerticalScrollData data = new VerticalScrollData();
        widget.getScrollArea().setScrollDataY(data);
        data.setScrollSize(200);
        data.scrollTo(widget.getScrollArea(), 20);

        AtomicReference<ScrollEvent> received = new AtomicReference<>();
        element.addEventListener(ScrollEvent.SCROLL, received::set);
        screen.getDocumentController().pollScrollState();
        assertEquals(20, element.getScrollTop());
        assertEquals(200, element.getScrollHeight());
        assertNull(received.get());

        data.scrollTo(widget.getScrollArea(), 35);
        screen.getDocumentController().pollScrollState();
        assertEquals(35, element.getScrollTop());
        assertEquals(20, received.get().getOldTop());
        assertEquals(35, received.get().getTop());
        assertEquals(200, received.get().getScrollHeight());

        element.scrollTo(0, 60);
        assertEquals(60, element.getScrollTop());
        assertEquals(60, received.get().getTop());
    }

    private static IWidget resolve(ModularScreen screen, MuiElement element) {
        return screen.getDocumentController().resolveWidget(element.getHandle());
    }

    private static com.cleanroommc.modularui.screen.ModularPanel panelWidget(ModularScreen screen) {
        return screen.getPanelManager().getMainPanel();
    }

    private static ModularScreen openScreen(ModularPanel panel) {
        ModularScreen screen = new ModularScreen("native-projection-test", panel);
        construct(screen);
        return screen;
    }

    private static void construct(ModularScreen screen) {
        screen.getContext().setSettings(new UISettings());
        GuiScreen guiScreen = new GuiScreen() {};
        guiScreen.width = 320;
        guiScreen.height = 240;
        screen.construct(new ScreenWrapper(guiScreen, screen));
        screen.onResize(320, 240);
    }

    private static final class CustomWidget extends Widget<CustomWidget> {}

    private static final class FixtureScreen extends ModularScreen {
        private final ModularContainer container;

        private FixtureScreen(ParentWidget<?> visible, ParentWidget<?> parking, ModularContainer container) {
            super("slot-integration-test", ModularPanel.defaultPanel("main").child(visible).child(parking));
            this.container = container;
        }

        @Override
        public ModularContainer getContainer() {
            return container;
        }
    }

    private static final class ContainerReadyScreen extends ModularScreen {
        private boolean callbackInvoked;

        private ContainerReadyScreen() {
            super("container-ready-test", ModularPanel.defaultPanel("main"));
        }

        @Override
        public void onContainerReady(ModularContainer container) {
            callbackInvoked = true;
            installCompiledDocument("container-ready-test",
                    "<mui:container xmlns:mui=\"urn:mui\" id=\"xml-root\">"
                            + "<mui:item-slot id=\"xml-slot\" bind=\"machine\"/>"
                            + "</mui:container>",
                    new MuiComponentRegistry(), (owner, resource) -> null);
        }
    }

    private static final class LifecycleScreen extends ModularScreen {
        private boolean callbackInvoked;

        private LifecycleScreen() {
            super("container-ready-overlay-test", ModularPanel.defaultPanel("main"));
        }

        @Override
        public void onContainerReady(ModularContainer container) {
            callbackInvoked = true;
        }
    }

    private static final class TestContainerWrapper extends GuiContainer implements IMuiScreen {
        private final ModularScreen screen;

        private TestContainerWrapper(ModularContainer container, ModularScreen screen) {
            super(container);
            this.screen = screen;
        }

        @Override
        public ModularScreen getScreen() {
            return this.screen;
        }

        @Override
        public void updateGuiArea(Rectangle area) {}

        @Override
        protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}
    }

    private static final class ModularContainerFixture {
        private final ModularContainer container = new ModularContainer();
        private final ModularSyncManager syncManager = new ModularSyncManager(true);
        private final PanelSyncManager panelSyncManager = new PanelSyncManager(syncManager, true);
        private final ItemStackHandler itemHandler = new ItemStackHandler(2);
        private int nextSlot;

        private ModularContainerFixture() throws Exception {
            Field field = ModularSyncManager.class.getDeclaredField("container");
            field.setAccessible(true);
            field.set(syncManager, container);
            Field containerSyncManager = ModularContainer.class.getDeclaredField("syncManager");
            containerSyncManager.setAccessible(true);
            containerSyncManager.set(container, syncManager);
        }

        private ModularSlot createSlot() {
            return new ModularSlot(itemHandler, nextSlot++);
        }

        private void register(String key, ItemSlotSH handler) {
            panelSyncManager.syncValue(key, 0, handler);
        }

        private void initialize() {
            panelSyncManager.initialize("main");
        }
    }
}
