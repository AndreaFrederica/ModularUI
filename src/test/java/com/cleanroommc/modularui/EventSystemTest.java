package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.event.ActionEvent;
import com.cleanroommc.modularui.api.event.EventListenerOptions;
import com.cleanroommc.modularui.api.event.EventSubscription;
import com.cleanroommc.modularui.api.event.IEventTarget;
import com.cleanroommc.modularui.api.event.InputModifiers;
import com.cleanroommc.modularui.api.event.MuiEvent;
import com.cleanroommc.modularui.api.event.MuiEventType;
import com.cleanroommc.modularui.api.event.PointerEvent;
import com.cleanroommc.modularui.api.navigation.NavigationAction;
import com.cleanroommc.modularui.api.navigation.NavigationActionResult;
import com.cleanroommc.modularui.api.navigation.NavigationInfo;
import com.cleanroommc.modularui.api.navigation.NavigationRole;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.overlay.ScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.event.EventListenerRegistry;
import com.cleanroommc.modularui.screen.navigation.ModularNavigationDispatcher;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.widget.Widget;

import net.minecraft.client.gui.GuiScreen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventSystemTest {

    private static final MuiEventType<MuiEvent> TEST_EVENT = new MuiEventType<>("test", true, true);

    @BeforeAll
    static void bootstrapForge() {
        Bootstrap.perform();
    }

    @Test
    void dispatchUsesCaptureTargetAndBubbleOrder() {
        List<String> calls = new ArrayList<>();
        TestTarget root = new TestTarget(null);
        TestTarget parent = new TestTarget(root);
        TestTarget target = new TestTarget(parent);

        root.addEventListener(TEST_EVENT, event -> calls.add("root-capture"), EventListenerOptions.CAPTURE);
        parent.addEventListener(TEST_EVENT, event -> calls.add("parent-capture"), EventListenerOptions.CAPTURE);
        target.addEventListener(TEST_EVENT, event -> calls.add("target-capture"), EventListenerOptions.CAPTURE);
        target.addEventListener(TEST_EVENT, event -> calls.add("target"));
        parent.addEventListener(TEST_EVENT, event -> calls.add("parent"));
        root.addEventListener(TEST_EVENT, event -> calls.add("root"));

        assertTrue(target.dispatchEvent(new MuiEvent(TEST_EVENT)));
        assertEquals(Arrays.asList("root-capture", "parent-capture", "target-capture",
                "target", "parent", "root"), calls);
    }

    @Test
    void propagationControlsMatchDomSemantics() {
        List<String> calls = new ArrayList<>();
        TestTarget root = new TestTarget(null);
        TestTarget target = new TestTarget(root);
        target.addEventListener(TEST_EVENT, event -> {
            calls.add("first");
            event.stopPropagation();
        });
        target.addEventListener(TEST_EVENT, event -> calls.add("second"));
        root.addEventListener(TEST_EVENT, event -> calls.add("root"));

        target.dispatchEvent(new MuiEvent(TEST_EVENT));
        assertEquals(Arrays.asList("first", "second"), calls);

        calls.clear();
        EventListenerRegistry.clear(target);
        target.addEventListener(TEST_EVENT, event -> {
            calls.add("immediate");
            event.stopImmediatePropagation();
        });
        target.addEventListener(TEST_EVENT, event -> calls.add("not-called"));
        target.dispatchEvent(new MuiEvent(TEST_EVENT));
        assertEquals(Arrays.asList("immediate"), calls);
    }

    @Test
    void oncePassiveAndSubscriptionsHaveStableBehavior() {
        TestTarget target = new TestTarget(null);
        AtomicInteger onceCalls = new AtomicInteger();
        EventSubscription once = target.addEventListener(TEST_EVENT, event -> onceCalls.incrementAndGet(),
                EventListenerOptions.builder().once(true).build());
        target.addEventListener(TEST_EVENT, MuiEvent::preventDefault,
                EventListenerOptions.builder().passive(true).build());

        MuiEvent first = new MuiEvent(TEST_EVENT);
        assertTrue(target.dispatchEvent(first));
        assertFalse(first.isDefaultPrevented());
        assertFalse(once.isSubscribed());
        target.dispatchEvent(new MuiEvent(TEST_EVENT));
        assertEquals(1, onceCalls.get());

        EventSubscription regular = target.addEventListener(TEST_EVENT, MuiEvent::preventDefault);
        assertFalse(target.dispatchEvent(new MuiEvent(TEST_EVENT)));
        regular.unsubscribe();
        regular.unsubscribe();
        assertFalse(regular.isSubscribed());
    }

    @Test
    void listenerChangesDoNotAlterTheCurrentDispatchSnapshot() {
        TestTarget target = new TestTarget(null);
        List<String> calls = new ArrayList<>();
        target.addEventListener(TEST_EVENT,
                event -> target.addEventListener(TEST_EVENT, ignored -> calls.add("late")));
        target.addEventListener(TEST_EVENT, event -> calls.add("existing"));

        target.dispatchEvent(new MuiEvent(TEST_EVENT));
        assertEquals(Arrays.asList("existing"), calls);
        target.dispatchEvent(new MuiEvent(TEST_EVENT));
        assertEquals(Arrays.asList("existing", "existing", "late"), calls);
    }

    @Test
    void listenerStorageUsesTargetIdentityInsteadOfEquals() {
        EqualTarget first = new EqualTarget();
        EqualTarget second = new EqualTarget();
        AtomicInteger calls = new AtomicInteger();
        first.addEventListener(TEST_EVENT, event -> calls.incrementAndGet());

        second.dispatchEvent(new MuiEvent(TEST_EVENT));
        assertEquals(0, calls.get());
        first.dispatchEvent(new MuiEvent(TEST_EVENT));
        assertEquals(1, calls.get());
    }

    @Test
    void actionEventCanCancelTraditionalWidgetDefaultBehavior() {
        CountingInteractableWidget widget = new CountingInteractableWidget();
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main").child(widget));
        EventSubscription cancellation = widget.addEventListener(ActionEvent.ACTION, MuiEvent::preventDefault);

        assertEquals(NavigationActionResult.HANDLED,
                ModularNavigationDispatcher.perform(screen, widget, NavigationAction.ACTIVATE));
        assertEquals(0, widget.presses);

        cancellation.unsubscribe();
        assertTrue(ModularNavigationDispatcher.perform(screen, widget, NavigationAction.ACTIVATE).isHandled());
        assertEquals(1, widget.presses);
        assertEquals(1, widget.taps);
        assertEquals(1, widget.releases);
    }

    @Test
    void pointerEventCanCancelTraditionalWidgetInputBehavior() {
        CountingInteractableWidget widget = new CountingInteractableWidget();
        ModularPanel panel = ModularPanel.defaultPanel("main").child(widget);
        ModularScreen screen = openScreen(panel);
        panel.getHovering().add(LocatedWidget.of(widget));
        EventSubscription cancellation = widget.addEventListener(PointerEvent.DOWN, MuiEvent::preventDefault);

        assertTrue(screen.onMousePressed(0));
        assertEquals(0, widget.presses);
        assertTrue(cancellation.isSubscribed());
    }

    @Test
    void pointerMoveIsDispatchedWithoutAButtonAndDeduplicatedByPosition() {
        CountingInteractableWidget widget = new CountingInteractableWidget();
        ModularPanel panel = ModularPanel.defaultPanel("main").child(widget);
        ModularScreen screen = openScreen(panel);
        panel.getHovering().add(LocatedWidget.of(widget));
        AtomicInteger moves = new AtomicInteger();
        widget.addEventListener(PointerEvent.MOVE, event -> {
            assertEquals(-1, event.getButton());
            assertEquals(12, event.getScreenX());
            assertEquals(34, event.getScreenY());
            moves.incrementAndGet();
        });
        screen.getContext().updateState(12, 34, 0);

        assertFalse(screen.onPointerMove(InputModifiers.NONE));
        assertFalse(screen.onPointerMove(InputModifiers.NONE));
        assertEquals(1, moves.get());
    }

    @Test
    void abstractWidgetDisposeActivelyClearsListenersAndPointerCapture() {
        CountingInteractableWidget widget = new CountingInteractableWidget();
        openScreen(ModularPanel.defaultPanel("main").child(widget));
        widget.addEventListener(TEST_EVENT, event -> {});
        widget.setPointerCapture(0);
        assertTrue(EventListenerRegistry.hasListeners(widget));
        assertTrue(widget.hasPointerCapture(0));

        widget.dispose();

        assertFalse(EventListenerRegistry.hasListeners(widget));
    }

    private static ModularScreen openScreen(ModularPanel panel) {
        ModularScreen screen = new ModularScreen("event-test", panel);
        screen.getContext().setSettings(new UISettings());
        GuiScreen guiScreen = new GuiScreen() {};
        guiScreen.width = 320;
        guiScreen.height = 240;
        screen.construct(new ScreenWrapper(guiScreen, screen));
        screen.onResize(320, 240);
        return screen;
    }

    private static class TestTarget implements IEventTarget {

        private final IEventTarget parent;

        private TestTarget(IEventTarget parent) {
            this.parent = parent;
        }

        @Override
        public IEventTarget getEventParent() {
            return this.parent;
        }
    }

    private static final class EqualTarget extends TestTarget {

        private EqualTarget() {
            super(null);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualTarget;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class CountingInteractableWidget extends Widget<CountingInteractableWidget>
            implements Interactable {

        private int presses;
        private int taps;
        private int releases;

        @Override
        public NavigationInfo getNavigationInfo() {
            return NavigationInfo.builder(NavigationRole.BUTTON).actions(NavigationAction.ACTIVATE).build();
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            this.presses++;
            return Result.ACCEPT;
        }

        @Override
        public Result onMouseTapped(int mouseButton) {
            this.taps++;
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseRelease(int mouseButton) {
            this.releases++;
            return true;
        }
    }
}
