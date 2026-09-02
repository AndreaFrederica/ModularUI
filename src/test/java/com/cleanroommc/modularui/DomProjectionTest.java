package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.dom.DomException;
import com.cleanroommc.modularui.api.dom.LegacyWidgetElement;
import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.NodeHandle;
import com.cleanroommc.modularui.api.event.IEventTarget;
import com.cleanroommc.modularui.api.event.MuiEvent;
import com.cleanroommc.modularui.api.event.MuiEventType;
import com.cleanroommc.modularui.api.navigation.NavigationAction;
import com.cleanroommc.modularui.api.navigation.NavigationInfo;
import com.cleanroommc.modularui.api.navigation.NavigationRole;
import com.cleanroommc.modularui.api.navigation.NavigationTargetHandle;
import com.cleanroommc.modularui.api.navigation.NavigationTreeEntry;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.overlay.ScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.navigation.ModularNavigationAccess;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;

import net.minecraft.client.gui.GuiScreen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DomProjectionTest {

    private static final MuiEventType<MuiEvent> TEST_EVENT = new MuiEventType<>("projection-test", true, true);

    @BeforeAll
    static void bootstrapForge() {
        Bootstrap.perform();
    }

    @Test
    void legacyTreeIsAdoptedWithoutCopyingWidgetsAndPreMountListenersMigrate() {
        TestWidget child = new TestWidget().name("child");
        AtomicReference<IEventTarget> target = new AtomicReference<>();
        child.addEventListener(TEST_EVENT, event -> target.set(event.getTarget()));
        TestParent parent = new TestParent().name("parent").child(child);
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main").child(parent));

        MuiElement element = screen.getDocument().getElementById("child");
        assertNotNull(element);
        assertTrue(element instanceof LegacyWidgetElement);
        assertSame(child, ((LegacyWidgetElement) element).getWidgetInternal());
        assertEquals(element.getHandle(), child.getNodeHandle());

        screen.getEventDispatcher().dispatch(child, new MuiEvent(TEST_EVENT));
        assertSame(element, target.get());
    }

    @Test
    void domMoveReparentsTheSameWidgetAndBatchesNavigationInvalidation() {
        TestWidget child = new TestWidget().name("child");
        TestParent left = new TestParent().name("left").child(child);
        TestParent right = new TestParent().name("right");
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main").child(left).child(right));
        MuiElement childElement = screen.getDocumentController().getElement(child);
        MuiElement rightElement = screen.getDocumentController().getElement(right);
        NodeHandle handle = child.getNodeHandle();
        long revision = screen.getPanelManager().getNavigationStructureRevision();

        rightElement.appendChild(childElement);

        assertTrue(left.getChildren().isEmpty());
        assertEquals(1, right.getChildren().size());
        assertSame(child, right.getChildren().get(0));
        assertSame(right, child.getParent());
        assertEquals(handle, child.getNodeHandle());
        assertEquals(revision + 1, screen.getPanelManager().getNavigationStructureRevision());
    }

    @Test
    void legacyMutationApisKeepDocumentInSyncAndChildrenViewsAreReadOnly() {
        TestParent parent = new TestParent().name("parent");
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main").child(parent));
        TestWidget child = new TestWidget().name("dynamic");

        parent.child(child);
        NodeHandle handle = child.getNodeHandle();
        assertTrue(handle.isPresent());
        assertSame(child, ((LegacyWidgetElement) screen.getDocument().getElementById("dynamic")).getWidgetInternal());
        assertThrows(UnsupportedOperationException.class, () -> parent.getChildren().clear());

        assertTrue(parent.remove(child));
        assertFalse(child.isValid());
        assertNull(screen.getDocument().resolve(handle));
        assertNull(screen.getDocument().getElementById("dynamic"));
    }

    @Test
    void workerThreadsQueueImmutableUpdatesInsteadOfTouchingTheLiveDocument() throws InterruptedException {
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main"));
        AtomicReference<RuntimeException> directFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                screen.getDocument().createElement("mui:illegal");
            } catch (RuntimeException exception) {
                directFailure.set(exception);
            }
            screen.getDocumentController().enqueueUpdate(document -> {
                MuiElement status = document.createElement("mui:status");
                status.setAttribute("id", "queued");
                document.appendChild(status);
            });
        }, "dom-projection-test-worker");

        worker.start();
        worker.join();

        assertTrue(directFailure.get() instanceof DomException);
        assertEquals(DomException.Code.INVALID_STATE, ((DomException) directFailure.get()).getCode());
        assertNull(screen.getDocument().getElementById("queued"));
        assertEquals(1, screen.getDocumentController().flushPendingUpdates());
        assertNotNull(screen.getDocument().getElementById("queued"));
    }

    @Test
    void navigationHandleStillTargetsTheSameWidgetAfterSiblingInsertion() {
        TestParent parent = new TestParent();
        TestButton original = new TestButton();
        parent.child(original);
        ModularScreen screen = openScreen(ModularPanel.defaultPanel("main").child(parent));
        NavigationTreeEntry entry = ModularNavigationAccess.capture(screen).getEntries().stream()
                .filter(candidate -> candidate.getWidget() == original)
                .findFirst().orElseThrow(AssertionError::new);
        NavigationTargetHandle handle = entry.getTargetHandle();

        parent.child(0, new TestButton());
        ModularNavigationAccess.perform(screen, handle, NavigationAction.ACTIVATE);

        assertEquals(1, original.presses);
    }

    private static ModularScreen openScreen(ModularPanel panel) {
        ModularScreen screen = new ModularScreen("dom-projection-test", panel);
        screen.getContext().setSettings(new UISettings());
        GuiScreen guiScreen = new GuiScreen() {};
        guiScreen.width = 320;
        guiScreen.height = 240;
        screen.construct(new ScreenWrapper(guiScreen, screen));
        screen.onResize(320, 240);
        return screen;
    }

    private static final class TestParent extends ParentWidget<TestParent> {}

    private static final class TestWidget extends Widget<TestWidget> {}

    private static final class TestButton extends Widget<TestButton> implements Interactable {

        private int presses;

        @Override
        public NavigationInfo getNavigationInfo() {
            return NavigationInfo.builder(NavigationRole.BUTTON).actions(NavigationAction.ACTIVATE).build();
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            this.presses++;
            return Result.STOP;
        }
    }
}
