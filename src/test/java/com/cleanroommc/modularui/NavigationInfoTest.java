package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.navigation.NavigationAction;
import com.cleanroommc.modularui.api.navigation.NavigationActionResult;
import com.cleanroommc.modularui.api.navigation.NavigationInfo;
import com.cleanroommc.modularui.api.navigation.NavigationRole;
import com.cleanroommc.modularui.api.navigation.NavigationTreeEntry;
import com.cleanroommc.modularui.api.navigation.NavigationTreeView;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.overlay.ScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.navigation.ModularNavigationAccess;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;

import net.minecraft.client.gui.GuiScreen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NavigationInfoTest {

    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 450;

    @BeforeAll
    static void bootstrapForge() {
        Bootstrap.perform();
    }

    @Test
    void metadataIsImmutableAndResolvesLabelsLazily() {
        AtomicReference<String> label = new AtomicReference<>("first");
        NavigationInfo info = NavigationInfo.builder(NavigationRole.TAB)
                .id("settings")
                .label(label::get)
                .actions(NavigationAction.ACTIVATE)
                .build();

        assertEquals("first", info.getLabel());
        label.set("second");
        assertEquals("second", info.getLabel());
        assertTrue(info.isFocusable());
        assertThrows(UnsupportedOperationException.class,
                () -> info.getActions().add(NavigationAction.BACK));
    }

    @Test
    void navigationWrappingCanBeDeclaredPerAxis() {
        NavigationInfo horizontal = NavigationInfo.builder(NavigationRole.TAB)
                .wrapHorizontal(true).build();
        NavigationInfo legacy = NavigationInfo.builder(NavigationRole.TAB).wrap(true).build();

        assertTrue(horizontal.isWrapHorizontal());
        assertFalse(horizontal.isWrapVertical());
        assertFalse(horizontal.isWrap());
        assertTrue(legacy.isWrapHorizontal());
        assertTrue(legacy.isWrapVertical());
        assertTrue(legacy.isWrap());
    }

    @Test
    void standardWidgetsExposeConservativeSemanticRoles() {
        NavigationInfo button = new ButtonWidget<>().getNavigationInfo();
        assertEquals(NavigationRole.BUTTON, button.getRole());
        assertTrue(button.getActions().contains(NavigationAction.ACTIVATE));

        NavigationInfo cycle = new CycleButtonWidget().getNavigationInfo();
        assertEquals(NavigationRole.CYCLE, cycle.getRole());
        assertTrue(cycle.getActions().contains(NavigationAction.DECREMENT));

        NavigationInfo slider = new SliderWidget().getNavigationInfo();
        assertEquals(NavigationRole.SLIDER, slider.getRole());
        assertFalse(slider.getActions().contains(NavigationAction.ACTIVATE));

        NavigationInfo text = new TextFieldWidget().getNavigationInfo();
        assertEquals(NavigationRole.TEXT_INPUT, text.getRole());
        assertTrue(text.getActions().contains(NavigationAction.BEGIN_EDIT));
    }

    @Test
    void genericInteractableWidgetsRemainControllerReachable() {
        NavigationInfo info = new GenericInteractableWidget().getNavigationInfo();
        assertEquals(NavigationRole.BUTTON, info.getRole());
        assertTrue(info.isFocusable());
        assertTrue(info.getActions().contains(NavigationAction.ACTIVATE));
        assertTrue(info.getActions().contains(NavigationAction.SECONDARY));
    }

    @Test
    void controllerScrollUsesAndAccumulatesTheNativeScrollAnimation() {
        VerticalScrollData scrollData = new VerticalScrollData();
        scrollData.setScrollSize(500);
        ScrollWidget<?> widget = new ScrollWidget<>(scrollData);
        widget.getArea().setSize(100, 100);

        assertEquals(NavigationActionResult.CHANGED,
                widget.onNavigationAction(NavigationAction.SCROLL_DOWN));
        assertTrue(scrollData.isAnimating());
        assertEquals(30, scrollData.getAnimatingTo());
        assertEquals(0, scrollData.getScroll());

        assertEquals(NavigationActionResult.CHANGED,
                widget.onNavigationAction(NavigationAction.SCROLL_DOWN));
        assertEquals(60, scrollData.getAnimatingTo());
    }

    @Test
    void pagedWidgetsExposeTabNavigationSemantics() {
        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> pages = new PagedWidget<>().addPage(new Widget<>()).addPage(new Widget<>());
        pages.controller(controller);
        PageButton tab = new PageButton(0, controller);

        assertEquals(NavigationRole.TAB_LIST, pages.getNavigationInfo().getRole());
        assertEquals(NavigationRole.TAB, tab.getNavigationInfo().getRole());
        assertTrue(pages.getNavigationInfo().getActions().contains(NavigationAction.PAGE_NEXT));
        assertTrue(tab.getNavigationInfo().getActions().contains(NavigationAction.PAGE_PREVIOUS));
    }

    @Test
    void navigationCaptureIsEmptyBeforeTheMainPanelOpens() {
        ModularScreen screen = new ModularScreen("navigation-test", new ModularPanel("main"));
        NavigationTreeView view = ModularNavigationAccess.capture(screen);
        assertTrue(view.getRoots().isEmpty());
        assertTrue(view.getEntries().isEmpty());
        assertEquals(null, view.getActiveScope());
    }

    @Test
    void enabledStateIncludesTheRootPanelAndEveryAncestor() {
        ButtonWidget<?> button = new ButtonWidget<>();
        ModularPanel panel = ModularPanel.defaultPanel("main").child(button);
        openScreen(panel);

        assertTrue(panel.areAncestorsEnabled());
        assertTrue(button.areAncestorsEnabled());

        panel.setEnabled(false);
        assertFalse(panel.areAncestorsEnabled());
        assertFalse(button.areAncestorsEnabled());

        panel.setEnabled(true);
        button.setEnabled(false);
        assertFalse(button.areAncestorsEnabled());
    }

    @Test
    void navigationCaptureTraversesAnOpenedPanelTree() {
        ButtonWidget<?> button = new ButtonWidget<>();
        ModularPanel panel = ModularPanel.defaultPanel("main").child(button);
        ModularScreen screen = openScreen(panel);

        NavigationTreeView view = ModularNavigationAccess.capture(screen);

        assertEquals(1, view.getRoots().size());
        assertEquals("panel/main", view.getRoots().get(0));
        assertEquals("panel/main", view.getActiveScope());
        assertEquals(2, view.getEntries().size());
        assertTrue(view.getEntries().stream().allMatch(NavigationTreeEntry::isEnabled));
    }

    private static ModularScreen openScreen(ModularPanel panel) {
        ModularScreen screen = new ModularScreen("navigation-test", panel);
        screen.getContext().setSettings(new UISettings());
        GuiScreen guiScreen = new GuiScreen() {};
        guiScreen.width = SCREEN_WIDTH;
        guiScreen.height = SCREEN_HEIGHT;
        screen.construct(new ScreenWrapper(guiScreen, screen));
        screen.onResize(SCREEN_WIDTH, SCREEN_HEIGHT);
        return screen;
    }

    private static final class GenericInteractableWidget extends Widget<GenericInteractableWidget>
            implements Interactable {}
}
