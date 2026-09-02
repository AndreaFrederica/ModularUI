package com.cleanroommc.modularui.screen.dom;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.api.dom.DomException;
import com.cleanroommc.modularui.api.dom.DomMutation;
import com.cleanroommc.modularui.api.dom.DomUpdate;
import com.cleanroommc.modularui.api.dom.LegacyWidgetElement;
import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiDocumentHost;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiNode;
import com.cleanroommc.modularui.api.dom.MutationScope;
import com.cleanroommc.modularui.api.dom.NodeHandle;
import com.cleanroommc.modularui.api.dom.SlotViewUpdate;
import com.cleanroommc.modularui.api.component.MuiElementDescriptor;
import com.cleanroommc.modularui.api.component.MuiElementRegistry;
import com.cleanroommc.modularui.api.event.IEventTarget;
import com.cleanroommc.modularui.api.event.ScrollEvent;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.event.EventListenerRegistry;
import com.cleanroommc.modularui.style.MuiCascade;
import com.cleanroommc.modularui.style.MuiComputedStyle;
import com.cleanroommc.modularui.style.MuiStyleApplier;
import com.cleanroommc.modularui.style.MuiMediaEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.cleanroommc.modularui.widget.AbstractParentWidget;
import com.cleanroommc.modularui.widget.AbstractWidget;
import com.cleanroommc.modularui.widget.AbstractScrollWidget;
import com.cleanroommc.modularui.widget.scroll.ScrollData;
import com.cleanroommc.modularui.widget.SingleChildWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Owns one screen document and its identity-preserving projection onto the legacy Widget tree. */
public final class MuiScreenDocumentController implements MuiDocumentHost {

    private final ModularScreen screen;
    private final Thread ownerThread;
    private final MuiElementRegistry elementRegistry;
    private MuiDocument document;
    private final Map<IWidget, MuiElement> widgetToElement = new IdentityHashMap<>();
    private final Map<MuiElement, IWidget> elementToWidget = new IdentityHashMap<>();
    private final Map<MuiElement, ScrollSnapshot> scrollSnapshots = new IdentityHashMap<>();
    private final Map<MuiElement, IWidget> preparedWidgets = new IdentityHashMap<>();
    private final Set<ItemSlot> slotTable = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ItemSlot> slotsPendingRemoval = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ConcurrentLinkedQueue<DomUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final MuiStyleApplier styleApplier = new MuiStyleApplier();
    @Nullable private MuiCascade stylesheet;
    @Nullable private MuiDevToolsSession devToolsSession;
    private MuiMediaEnvironment mediaEnvironment = MuiMediaEnvironment.UNCONSTRAINED;
    private volatile boolean acceptingUpdates = true;
    private boolean applyingProjection;
    private boolean validatingConnectedProjection;

    public MuiScreenDocumentController(ModularScreen screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
        this.ownerThread = Thread.currentThread();
        this.elementRegistry = NativeElementLibrary.createDefaultRegistry(screen);
        this.document = new MuiDocument(this);
    }

    public MuiDocument getDocument() {
        return this.document;
    }

    public MuiElementRegistry getElementRegistry() {
        return this.elementRegistry;
    }

    /** Installs an optional JSON stylesheet for this screen. Styles are client-local and never enter sync packets. */
    public void setStylesheet(@Nullable MuiCascade stylesheet) {
        checkAccess();
        this.stylesheet = stylesheet;
        if (stylesheet == null) {
            this.styleApplier.clear();
            return;
        }
        for (Map.Entry<MuiElement, IWidget> entry : this.elementToWidget.entrySet()) {
            applyStyle(entry.getKey(), entry.getValue());
        }
    }

    public @Nullable MuiCascade getStylesheet() {
        return this.stylesheet;
    }

    public @Nullable MuiComputedStyle getComputedStyle(MuiElement element) {
        return this.styleApplier.getComputedStyle(element);
    }

    /** Opens the client-local inspector backend. Only one session is needed per screen. */
    public MuiDevToolsSession openDevTools() {
        checkAccess();
        if (this.devToolsSession == null) this.devToolsSession = new MuiDevToolsSession(this);
        return this.devToolsSession;
    }

    public @Nullable MuiDevToolsSession getDevToolsSession() {
        return this.devToolsSession;
    }

    void removeDevToolsSession(MuiDevToolsSession session) {
        checkAccess();
        if (this.devToolsSession == session) this.devToolsSession = null;
    }

    void refreshStyle(MuiElement element) {
        checkAccess();
        IWidget widget = this.elementToWidget.get(element);
        if (widget != null) applyStyle(element, widget);
    }

    void refreshStyles() {
        checkAccess();
        for (Map.Entry<MuiElement, IWidget> entry : this.elementToWidget.entrySet()) {
            applyStyle(entry.getKey(), entry.getValue());
        }
    }

    /** Returns whether a projected widget participates in pointer hit testing. */
    @ApiStatus.Internal
    public boolean acceptsPointerEvents(IWidget widget) {
        if (widget == null) return false;
        MuiElement element = this.widgetToElement.get(widget);
        if (element == null) return true;
        MuiComputedStyle style = this.styleApplier.getComputedStyle(element);
        String pointerEvents = style == null ? null : style.getString("pointer-events");
        return pointerEvents == null || !"none".equalsIgnoreCase(pointerEvents.trim());
    }

    /** Updates viewport facts used by CSS @media rules and reapplies the current stylesheet. */
    @ApiStatus.Internal
    public void setMediaEnvironment(int width, int height) {
        checkAccess();
        MuiMediaEnvironment next = new MuiMediaEnvironment(width, height);
        if (this.mediaEnvironment.getWidth() == next.getWidth() && this.mediaEnvironment.getHeight() == next.getHeight()) return;
        this.mediaEnvironment = next;
        if (this.stylesheet != null) {
            for (Map.Entry<MuiElement, IWidget> entry : this.elementToWidget.entrySet()) applyStyle(entry.getKey(), entry.getValue());
        }
    }

    /** Updates a non-structural interaction state used by stylesheet pseudo-state selectors. */
    @ApiStatus.Internal
    public void updateInteractionState(IWidget widget, String attribute, boolean active) {
        checkAccess();
        MuiElement element = this.widgetToElement.get(widget);
        if (element == null || !element.isConnected()) return;
        String value = Boolean.toString(active);
        if (!Objects.equals(element.getAttribute(attribute), value)) element.setAttribute(attribute, value);
    }

    public void adoptWidgetTree() {
        checkAccess();
        ensureOpenDocument();
        this.elementRegistry.freeze();
        for (ModularPanel panel : this.screen.getPanelManager().getOpenPanels()) adoptPanel(panel);
    }

    public void adoptPanel(ModularPanel panel) {
        checkAccess();
        ensureOpenDocument();
        if (this.widgetToElement.containsKey(panel)) return;
        adopt(null, panel);
    }

    private MuiElement adopt(@Nullable MuiElement parent, IWidget widget) {
        MuiElement existing = this.widgetToElement.get(widget);
        if (existing instanceof LegacyWidgetElement legacy) return legacy;
        if (existing != null) return existing;
        boolean opaque = !(widget instanceof AbstractWidget);
        LegacyWidgetElement element = this.document.createLegacyWidgetElement(tagName(widget), widget, opaque);
        this.widgetToElement.put(widget, element);
        this.elementToWidget.put(element, widget);
        EventListenerRegistry.alias(widget, element);
        element.setAttribute("widget-class", widget.getClass().getName());
        if (widget.getName() != null) element.setAttribute("id", widget.getName());
        element.setAttribute("enabled", Boolean.toString(widget.isEnabled()));
        if (opaque) element.setAttribute("opaque", "true");
        if (widget instanceof ItemSlot slot) {
            validateRegisteredSlot(slot);
            this.slotTable.add(slot);
            slot.onDomMountChanged(true);
        }
        this.document.adoptNode(parent, element);
        applyStyle(element, widget);
        for (IWidget child : widget.getChildren()) adopt(element, child);
        return element;
    }

    public @Nullable MuiElement getElement(IWidget widget) {
        MuiElement element = this.widgetToElement.get(widget);
        return element != null && element.isAlive() ? element : null;
    }

    public NodeHandle getNodeHandle(IWidget widget) {
        MuiElement element = getElement(widget);
        return element == null ? NodeHandle.EMPTY : element.getHandle();
    }

    public @Nullable IWidget resolveWidget(NodeHandle handle) {
        MuiNode node = this.document.resolve(handle);
        if (!(node instanceof MuiElement element) || !element.isConnected()) return null;
        return this.elementToWidget.get(element);
    }

    public IEventTarget getCanonicalEventTarget(IWidget widget) {
        MuiElement element = getElement(widget);
        return element != null && element.isConnected() ? element : widget;
    }

    @Override
    public int getScrollLeft(MuiElement element) {
        AbstractScrollWidget<?, ?> widget = scrollWidget(element);
        return widget == null ? 0 : widget.getScrollX();
    }

    @Override
    public int getScrollTop(MuiElement element) {
        AbstractScrollWidget<?, ?> widget = scrollWidget(element);
        return widget == null ? 0 : widget.getScrollY();
    }

    @Override
    public int getScrollWidth(MuiElement element) {
        AbstractScrollWidget<?, ?> widget = scrollWidget(element);
        if (widget == null) return 0;
        ScrollData data = widget.getScrollArea().getScrollX();
        return Math.max(widget.getArea().w(), data == null ? 0 : data.getScrollSize());
    }

    @Override
    public int getScrollHeight(MuiElement element) {
        AbstractScrollWidget<?, ?> widget = scrollWidget(element);
        if (widget == null) return 0;
        ScrollData data = widget.getScrollArea().getScrollY();
        return Math.max(widget.getArea().h(), data == null ? 0 : data.getScrollSize());
    }

    @Override
    public void setScrollPosition(MuiElement element, int left, int top) {
        checkAccess();
        AbstractScrollWidget<?, ?> widget = scrollWidget(element);
        if (widget == null) return;
        if (widget.getScrollArea().getScrollX() != null) {
            widget.getScrollArea().getScrollX().scrollTo(widget.getScrollArea(), left);
        }
        if (widget.getScrollArea().getScrollY() != null) {
            widget.getScrollArea().getScrollY().scrollTo(widget.getScrollArea(), top);
        }
        pollScrollState();
    }

    /** Samples native scroll positions and emits one DOM scroll event per changed viewport. */
    @ApiStatus.Internal
    public void pollScrollState() {
        checkAccess();
        List<Map.Entry<MuiElement, IWidget>> entries = new ArrayList<>(this.elementToWidget.entrySet());
        Set<MuiElement> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<MuiElement, IWidget> entry : entries) {
            MuiElement element = entry.getKey();
            IWidget widget = entry.getValue();
            if (!(widget instanceof AbstractScrollWidget) || !element.isConnected()) continue;
            AbstractScrollWidget<?, ?> scroll = (AbstractScrollWidget<?, ?>) widget;
            int left = scroll.getScrollX();
            int top = scroll.getScrollY();
            int width = getScrollWidth(element);
            int height = getScrollHeight(element);
            ScrollSnapshot previous = this.scrollSnapshots.get(element);
            ScrollSnapshot current = new ScrollSnapshot(left, top, width, height);
            this.scrollSnapshots.put(element, current);
            live.add(element);
            if (previous != null && (previous.left != left || previous.top != top)) {
                element.dispatchEvent(new ScrollEvent(previous.left, previous.top, left, top, width, height));
            }
        }
        this.scrollSnapshots.keySet().removeIf(element -> !live.contains(element));
    }

    @Nullable
    private AbstractScrollWidget<?, ?> scrollWidget(MuiElement element) {
        IWidget widget = this.elementToWidget.get(element);
        return widget instanceof AbstractScrollWidget ? (AbstractScrollWidget<?, ?>) widget : null;
    }

    private static final class ScrollSnapshot {
        private final int left;
        private final int top;
        @SuppressWarnings("unused")
        private final int width;
        @SuppressWarnings("unused")
        private final int height;

        private ScrollSnapshot(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    @ApiStatus.Internal
    public void onLegacyChildAdded(IWidget parent, IWidget child, int index) {
        if (this.applyingProjection) return;
        checkAccess();
        MuiElement parentElement = this.widgetToElement.get(parent);
        if (parentElement == null) return;
        MuiElement childElement = adopt(parentElement, child);
        if (index != parentElement.getChildNodes().size() - 1) {
            this.document.moveFromHost(parentElement, childElement, index);
        }
    }

    @ApiStatus.Internal
    public void onLegacyChildRemoved(IWidget parent, IWidget child) {
        if (this.applyingProjection) return;
        checkAccess();
        MuiElement element = this.widgetToElement.get(child);
        if (element != null) this.document.destroyFromHost(element);
    }

    @ApiStatus.Internal
    public void onLegacyChildMoved(IWidget parent, IWidget child, int index) {
        if (this.applyingProjection) return;
        checkAccess();
        MuiElement parentElement = this.widgetToElement.get(parent);
        MuiElement childElement = this.widgetToElement.get(child);
        if (parentElement != null && childElement != null) {
            this.document.moveFromHost(parentElement, childElement, index);
        }
    }

    /** Queues an immutable-data update for the next client frame. The update never runs on the calling worker thread. */
    public void enqueueUpdate(DomUpdate update) {
        Objects.requireNonNull(update, "update");
        if (!this.acceptingUpdates) throw new IllegalStateException("Screen document is closed");
        this.pendingUpdates.add(update);
        if (!this.acceptingUpdates && this.pendingUpdates.remove(update)) {
            throw new IllegalStateException("Screen document closed while queuing an update");
        }
    }

    /** Accepts a worker-computed slot order and applies it later without changing the container slot table. */
    public void enqueueSlotViewUpdate(SlotViewUpdate update) {
        Objects.requireNonNull(update, "update");
        enqueueUpdate(document -> applySlotViewUpdate(update));
    }

    private void applySlotViewUpdate(SlotViewUpdate update) {
        MuiElement visibleParent = requireElement(update.getVisibleParent());
        MuiElement parkingParent = requireElement(update.getParkingParent());
        IWidget parkingWidget = requireWidget(parkingParent);
        if (parkingWidget.isEnabled()) {
            throw new DomException(DomException.Code.INVALID_STATE,
                    "SlotViewUpdate parking parent must be disabled before it is used");
        }

        Set<NodeHandle> visible = new HashSet<>(update.getVisibleSlots());
        int visibleIndex = 0;
        for (NodeHandle handle : update.getVisibleSlots()) {
            visibleParent.insertChild(visibleIndex++, requireSlotElement(handle));
        }
        int parkingIndex = 0;
        for (NodeHandle handle : update.getSlotTable()) {
            if (!visible.contains(handle)) parkingParent.insertChild(parkingIndex++, requireSlotElement(handle));
        }
    }

    @ApiStatus.Internal
    public int flushPendingUpdates() {
        checkAccess();
        int applied = 0;
        DomUpdate update;
        while ((update = this.pendingUpdates.poll()) != null) {
            try (MutationScope scope = this.document.beginMutation()) {
                update.apply(this.document);
                scope.commit();
                applied++;
            } catch (RuntimeException exception) {
                ModularUI.LOGGER.error("Failed to apply queued DOM update for screen '{}'", this.screen, exception);
            }
        }
        return applied;
    }

    @Override
    public void checkAccess() {
        if (Thread.currentThread() != this.ownerThread) {
            throw new DomException(DomException.Code.INVALID_STATE,
                    "MuiDocument can only be accessed by its owning client thread; queue an immutable DomUpdate instead");
        }
    }

    @Override
    public void validateMutations(List<DomMutation> mutations) {
        checkAccess();
        this.preparedWidgets.clear();
        this.slotsPendingRemoval.clear();
        for (DomMutation mutation : mutations) {
            if (mutation.getKind() == DomMutation.Kind.REMOVE) {
                collectProjectedSlots(mutation.getChild(), this.slotsPendingRemoval);
            }
        }
        IdentityHashMap<MuiNode, List<MuiNode>> shadowChildren = new IdentityHashMap<>();
        try {
            for (DomMutation mutation : mutations) {
                MuiNode child = mutation.getChild();
                this.validatingConnectedProjection = mutation.wasConnected() || mutation.willBeConnected();
                if (child instanceof MuiElement element && isProjectable(element)) prepareSubtree(element);
                if (mutation.getParent() == null && mutation.getKind() == DomMutation.Kind.INSERT
                        && child instanceof MuiElement element && isProjectable(element)) {
                    throw notSupported("Native Widget elements must be inserted below a projected panel or Widget parent");
                }

                MuiNode previousParent = mutation.getPreviousParent();
                if (mutation.isMove() && previousParent != null) {
                    shadowList(previousParent, shadowChildren).remove(child);
                    validateProjectedChildren(previousParent, shadowList(previousParent, shadowChildren));
                }
                MuiNode parent = mutation.getParent();
                if (parent != null) {
                    if (child instanceof MuiElement childElement && isProjectable(childElement)
                            && (!(parent instanceof MuiElement parentElement) || !isProjectable(parentElement))) {
                        throw notSupported("Projected Widget elements require a directly projected Widget parent");
                    }
                    List<MuiNode> children = shadowList(parent, shadowChildren);
                    if (mutation.getKind() == DomMutation.Kind.REMOVE) {
                        children.remove(child);
                    } else {
                        children.add(mutation.getIndex(), child);
                    }
                    validateProjectedChildren(parent, children);
                }
            }
        } catch (RuntimeException exception) {
            this.preparedWidgets.clear();
            this.slotsPendingRemoval.clear();
            this.validatingConnectedProjection = false;
            throw exception;
        }
    }

    @Override
    public void applyMutations(List<DomMutation> mutations) {
        checkAccess();
        if (this.applyingProjection) throw new IllegalStateException("Nested Widget projection is not supported");
        this.applyingProjection = true;
        Set<IWidget> resize = new LinkedHashSet<>();
        this.screen.getPanelManager().beginUiMutationBatch();
        try {
            for (DomMutation mutation : mutations) {
                if (mutation.wasConnected() || mutation.willBeConnected()) {
                    this.validatingConnectedProjection = true;
                    applyMutation(mutation, resize);
                }
            }
            for (IWidget widget : resize) if (widget.isValid()) widget.scheduleResize();
        } finally {
            this.preparedWidgets.clear();
            this.slotsPendingRemoval.clear();
            this.validatingConnectedProjection = false;
            this.screen.getPanelManager().endUiMutationBatch();
            this.applyingProjection = false;
        }
    }

    private void applyMutation(DomMutation mutation, Set<IWidget> resize) {
        if (!(mutation.getChild() instanceof MuiElement childElement)) {
            scheduleProjectedTextOwner(mutation.getParent(), resize);
            return;
        }
        IWidget child = materialize(childElement);
        if (child == null) return;
        IWidget parent = requireProjectedParent(mutation.getParent());
        if (mutation.getKind() == DomMutation.Kind.REMOVE) {
            unmountProjectedSlots(childElement);
            resize.add(parent);
            removeProjectedChild(parent, child);
            return;
        }
        if (mutation.isMove()) {
            IWidget previousParent = requireProjectedParent(mutation.getPreviousParent());
            if (previousParent == parent) {
                int from = parent.getChildren().indexOf(child);
                moveProjectedChild(parent, from, mutation.getIndex());
            } else {
                detachProjectedChild(previousParent, child);
                attachMovedProjectedChild(parent, child, mutation.getIndex());
                resize.add(previousParent);
            }
        } else {
            insertProjectedChild(parent, child, mutation.getIndex());
            if (child instanceof ItemSlot slot) slot.onDomMountChanged(true);
        }
        if (child instanceof ItemSlot slot) slot.refreshDomEnabledState();
        resize.add(parent);
    }

    @Override
    public void onAttributeChanged(MuiElement element, String name, String oldValue, String newValue) {
        validateCommonAttribute(name, newValue);
        MuiElementDescriptor<?> descriptor = this.elementRegistry.find(element.getTagName());
        if (descriptor != null) descriptor.validateProperty(name, newValue);
        IWidget widget = this.elementToWidget.get(element);
        if (widget == null) return;
        if (("bind".equals(name) || "bind-id".equals(name)) && !Objects.equals(oldValue, newValue)) {
            throw notSupported("A materialized XML Widget binding is immutable; replace the view or reopen with a new protocol");
        }
        applyCommonAttribute(element, widget, name, newValue);
        if (descriptor != null) descriptor.applyPropertyUnchecked(widget, name, newValue);
        applyStyle(element, widget);
        if ("text".equals(name) && widget.isValid()) widget.scheduleResize();
    }

    /**
     * A fixed legacy widget can be adopted before an XML document exists and then moved into
     * the document by a later mutation. Recompute its stylesheet after the logical move so
     * ancestor selectors see the new XML parent (for example a slot inside .slot-grid).
     */
    @Override
    public void onMutationsApplied(List<DomMutation> mutations) {
        checkAccess();
        if (this.stylesheet == null) return;
        Set<MuiElement> refreshed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DomMutation mutation : mutations) {
            if (mutation.getKind() != DomMutation.Kind.INSERT) continue;
            MuiNode node = mutation.getChild();
            if (node instanceof MuiElement element) refreshInsertedSubtreeStyles(element, refreshed);
        }
    }

    private void refreshInsertedSubtreeStyles(MuiElement element, Set<MuiElement> refreshed) {
        if (!refreshed.add(element)) return;
        IWidget widget = this.elementToWidget.get(element);
        if (widget != null) applyStyle(element, widget);
        for (MuiNode child : element.getChildNodes()) {
            if (child instanceof MuiElement childElement) refreshInsertedSubtreeStyles(childElement, refreshed);
        }
    }

    @Override
    public void onTextChanged(com.cleanroommc.modularui.api.dom.MuiText text,
                              String oldValue, String newValue) {
        MuiNode current = text.getParentNode();
        while (current instanceof MuiElement element) {
            IWidget widget = this.elementToWidget.get(element);
            if (widget != null) {
                if (widget.isValid()) widget.scheduleResize();
                return;
            }
            current = element.getParentNode();
        }
    }

    @Override
    public void onNodesDestroyed(List<MuiNode> nodes) {
        for (MuiNode node : nodes) {
            if (!(node instanceof MuiElement element)) continue;
            IWidget widget = this.elementToWidget.remove(element);
            if (widget != null) {
                if (widget instanceof ItemSlot slot) {
                    slot.onDomMountChanged(false);
                    this.slotTable.remove(slot);
                }
                if (widget instanceof Widget<?>) this.styleApplier.remove(element, (Widget<?>) widget);
                this.widgetToElement.remove(widget);
                this.scrollSnapshots.remove(element);
                EventListenerRegistry.removeAlias(widget, element);
                if (this.devToolsSession != null) this.devToolsSession.removeOverrides(element);
            }
        }
    }

    @Override
    public void onDocumentClosed() {
        this.acceptingUpdates = false;
        this.pendingUpdates.clear();
        this.widgetToElement.clear();
        this.elementToWidget.clear();
        this.scrollSnapshots.clear();
        this.slotTable.clear();
        this.styleApplier.clear();
        this.stylesheet = null;
        this.devToolsSession = null;
    }

    public void close() {
        checkAccess();
        this.document.close();
    }

    public void onPanelDisposed(ModularPanel panel) {
        checkAccess();
        MuiElement element = this.widgetToElement.get(panel);
        if (element != null && element.isAlive()) this.document.destroyFromHost(element);
    }

    private void ensureOpenDocument() {
        if (!this.document.isOpen()) {
            this.document = new MuiDocument(this);
            this.acceptingUpdates = true;
        }
    }

    private List<MuiNode> shadowList(MuiNode parent, IdentityHashMap<MuiNode, List<MuiNode>> shadowChildren) {
        return shadowChildren.computeIfAbsent(parent, ignored -> new java.util.ArrayList<>(parent.getChildNodes()));
    }

    private boolean isProjectable(MuiElement element) {
        return element instanceof LegacyWidgetElement || this.elementToWidget.containsKey(element)
                || this.preparedWidgets.containsKey(element)
                || this.elementRegistry.find(element.getTagName()) != null;
    }

    private void collectProjectedSlots(MuiNode node, Set<ItemSlot> slots) {
        if (node instanceof MuiElement element) {
            IWidget widget = this.elementToWidget.get(element);
            if (widget instanceof ItemSlot slot) slots.add(slot);
        }
        for (MuiNode child : node.getChildNodes()) collectProjectedSlots(child, slots);
    }

    private void unmountProjectedSlots(MuiElement element) {
        IWidget widget = this.elementToWidget.get(element);
        if (widget instanceof ItemSlot slot) {
            slot.onDomMountChanged(false);
            this.slotTable.remove(slot);
        }
        for (MuiNode child : element.getChildNodes()) {
            if (child instanceof MuiElement childElement) unmountProjectedSlots(childElement);
        }
    }

    private IWidget prepareSubtree(MuiElement element) {
        IWidget widget = this.elementToWidget.get(element);
        if (widget != null) return widget;
        widget = this.preparedWidgets.get(element);
        if (widget != null) return widget;
        MuiElementDescriptor<?> descriptor = this.elementRegistry.find(element.getTagName());
        if (descriptor == null) return null;
        for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
            validateCommonAttribute(attribute.getKey(), attribute.getValue());
            descriptor.validateProperty(attribute.getKey(), attribute.getValue());
        }
        widget = descriptor.create(element);
        this.preparedWidgets.put(element, widget);
        for (MuiNode node : element.getChildNodes()) {
            if (!(node instanceof MuiElement child)) continue;
            if (isProjectable(child)) {
                prepareSubtree(child);
            } else if (containsProjectedWidget(child)) {
                throw notSupported("Logical-only elements cannot wrap projected Widget elements yet: " + child.getTagName());
            }
        }
        validateProjectedChildren(element, element.getChildNodes());
        return widget;
    }

    private void validateProjectedChildren(MuiNode parentNode, List<MuiNode> children) {
        if (!(parentNode instanceof MuiElement parentElement) || !isProjectable(parentElement)) return;
        IWidget parent = prepareSubtreeShallow(parentElement);
        if (parent == null) return;
        MuiElementDescriptor.ChildModel childModel = childModel(parentElement, parent);
        int projectedChildren = 0;
        for (MuiNode node : children) {
            if (!(node instanceof MuiElement childElement)) continue;
            if (!isProjectable(childElement)) {
                if (containsProjectedWidget(childElement)) {
                    throw notSupported("Logical-only elements cannot wrap projected Widget elements yet: "
                            + childElement.getTagName());
                }
                continue;
            }
            IWidget child = prepareSubtree(childElement);
            projectedChildren++;
            if (child instanceof ModularPanel) throw notSupported("Panel roots are owned by PanelManager");
            if (child instanceof ItemSlot slot && !this.slotTable.contains(slot)) validateNewBoundSlot(slot);
            if (!parent.getChildren().contains(child) && !canAcceptProjectedChild(parent, child)) {
                throw new DomException(DomException.Code.HIERARCHY_REQUEST,
                        "Target Widget parent does not accept child " + child.getClass().getName());
            }
            if (child.isValid() && child.getParent() != parent && !(child instanceof AbstractWidget)) {
                throw notSupported("Third-party IWidget implementations can only be reordered within their current parent");
            }
        }
        if (childModel == MuiElementDescriptor.ChildModel.NONE && projectedChildren > 0) {
            throw new DomException(DomException.Code.HIERARCHY_REQUEST,
                    "Element '" + parentElement.getTagName() + "' does not accept Widget children");
        }
        if (childModel == MuiElementDescriptor.ChildModel.SINGLE && projectedChildren > 1) {
            throw new DomException(DomException.Code.HIERARCHY_REQUEST,
                    "Element '" + parentElement.getTagName() + "' accepts only one Widget child");
        }
    }

    private IWidget prepareSubtreeShallow(MuiElement element) {
        IWidget widget = this.elementToWidget.get(element);
        if (widget != null) return widget;
        widget = this.preparedWidgets.get(element);
        if (widget != null) return widget;
        MuiElementDescriptor<?> descriptor = this.elementRegistry.find(element.getTagName());
        if (descriptor == null) return null;
        for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
            validateCommonAttribute(attribute.getKey(), attribute.getValue());
            descriptor.validateProperty(attribute.getKey(), attribute.getValue());
        }
        widget = descriptor.create(element);
        this.preparedWidgets.put(element, widget);
        return widget;
    }

    private MuiElementDescriptor.ChildModel childModel(MuiElement element, IWidget widget) {
        MuiElementDescriptor<?> descriptor = this.elementRegistry.find(element.getTagName());
        if (!(element instanceof LegacyWidgetElement) && descriptor != null) return descriptor.getChildModel();
        if (widget instanceof SingleChildWidget<?>) return MuiElementDescriptor.ChildModel.SINGLE;
        if (widget instanceof AbstractParentWidget<?, ?> parent && parent.supportsDomChildMutations()) {
            return MuiElementDescriptor.ChildModel.MULTIPLE;
        }
        return MuiElementDescriptor.ChildModel.NONE;
    }

    private IWidget materialize(MuiElement element) {
        IWidget widget = this.elementToWidget.get(element);
        if (widget != null) return widget;
        MuiElementDescriptor<?> descriptor = this.elementRegistry.find(element.getTagName());
        if (descriptor == null) return null;
        widget = this.preparedWidgets.remove(element);
        if (widget == null) widget = descriptor.create(element);
        this.elementToWidget.put(element, widget);
        this.widgetToElement.put(widget, element);
        EventListenerRegistry.alias(widget, element);
        if (widget instanceof ItemSlot slot && !this.slotTable.contains(slot)) {
            validateNewBoundSlot(slot);
            this.slotTable.add(slot);
        }
        for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
            applyCommonAttribute(element, widget, attribute.getKey(), attribute.getValue());
        }
        applyStyle(element, widget);
        int index = 0;
        for (MuiNode node : element.getChildNodes()) {
            if (!(node instanceof MuiElement childElement)) continue;
            IWidget child = materialize(childElement);
            if (child != null && !widget.getChildren().contains(child)) {
                insertProjectedChild(widget, child, index++);
            } else if (child != null) {
                index++;
            }
        }
        return widget;
    }

    private IWidget requireProjectedParent(@Nullable MuiNode node) {
        if (!(node instanceof MuiElement element)) {
            throw notSupported("Projected Widget nodes require a projected Widget parent");
        }
        IWidget widget = materialize(element);
        if (widget == null) {
            throw notSupported("Logical-only element cannot directly contain a projected Widget: " + element.getTagName());
        }
        if (!supportsProjectedChildren(widget)) {
            throw notSupported("Widget parent requires a dedicated DOM mutation adapter: " + widget.getClass().getName());
        }
        return widget;
    }

    private static boolean supportsProjectedChildren(IWidget parent) {
        return parent instanceof AbstractParentWidget<?, ?> abstractParent && abstractParent.supportsDomChildMutations()
                || parent instanceof SingleChildWidget<?> single && single.supportsDomChildMutations();
    }

    private static boolean canAcceptProjectedChild(IWidget parent, IWidget child) {
        if (parent instanceof AbstractParentWidget<?, ?> abstractParent) {
            return abstractParent.isDomChildTypeValid(child);
        }
        if (parent instanceof SingleChildWidget<?> single) return single.canAcceptDomChild(child);
        return false;
    }

    private static void insertProjectedChild(IWidget parent, IWidget child, int index) {
        if (parent instanceof AbstractParentWidget<?, ?> abstractParent) abstractParent.insertDomChild(child, index);
        else if (parent instanceof SingleChildWidget<?> single) single.insertDomChild(child, index);
        else throw notSupported("Widget parent does not support child insertion: " + parent.getClass().getName());
    }

    private static void removeProjectedChild(IWidget parent, IWidget child) {
        if (parent instanceof AbstractParentWidget<?, ?> abstractParent) abstractParent.removeDomChild(child);
        else if (parent instanceof SingleChildWidget<?> single) single.removeDomChild(child);
        else throw notSupported("Widget parent does not support child removal: " + parent.getClass().getName());
    }

    private static void moveProjectedChild(IWidget parent, int from, int to) {
        if (parent instanceof AbstractParentWidget<?, ?> abstractParent) abstractParent.moveDomChild(from, to);
        else if (parent instanceof SingleChildWidget<?> && from == 0 && to == 0) return;
        else throw notSupported("Widget parent does not support child movement: " + parent.getClass().getName());
    }

    private static void detachProjectedChild(IWidget parent, IWidget child) {
        if (parent instanceof AbstractParentWidget<?, ?> abstractParent) abstractParent.detachDomChildForMove(child);
        else if (parent instanceof SingleChildWidget<?> single) single.detachDomChildForMove(child);
        else throw notSupported("Widget parent does not support child detachment: " + parent.getClass().getName());
    }

    private static void attachMovedProjectedChild(IWidget parent, IWidget child, int index) {
        if (parent instanceof AbstractParentWidget<?, ?> abstractParent) abstractParent.attachMovedDomChild(child, index);
        else if (parent instanceof SingleChildWidget<?> single) single.attachMovedDomChild(child, index);
        else throw notSupported("Widget parent does not support moved children: " + parent.getClass().getName());
    }

    private void scheduleProjectedTextOwner(@Nullable MuiNode parent, Set<IWidget> resize) {
        MuiNode current = parent;
        while (current instanceof MuiElement element) {
            IWidget widget = this.elementToWidget.get(element);
            if (widget != null) {
                resize.add(widget);
                return;
            }
            current = element.getParentNode();
        }
    }

    private static void validateCommonAttribute(String name, @Nullable String value) {
        switch (name) {
            case "enabled", "hidden" -> NativeElementLibrary.bool(value,
                    "enabled".equals(name));
            case "left", "top", "width", "height" -> {
                if (value == null) throw notSupported("Removing a projected geometry attribute is not supported yet: " + name);
                NativeElementLibrary.intValue(value, 0);
            }
            case "left-rel", "top-rel", "width-rel", "height-rel" -> {
                if (value == null) throw notSupported("Removing a projected geometry attribute is not supported yet: " + name);
                NativeElementLibrary.floatValue(value, 0f);
            }
            default -> {}
        }
    }

    private static void applyCommonAttribute(MuiElement element, IWidget widget,
                                             String name, @Nullable String value) {
        if ("enabled".equals(name) || "hidden".equals(name)) {
            String enabledValue = "enabled".equals(name) ? value : element.getAttribute("enabled");
            String hiddenValue = "hidden".equals(name) ? value : element.getAttribute("hidden");
            widget.setEnabled(NativeElementLibrary.bool(enabledValue, true)
                    && !NativeElementLibrary.bool(hiddenValue, false));
            return;
        }
        if (!(widget instanceof Widget<?> positioned)) return;
        switch (name) {
            case "id", "name" -> {
                String effective = "name".equals(name) ? value : element.getAttribute("name");
                if (effective == null) effective = "id".equals(name) ? value : element.getAttribute("id");
                if (!Objects.equals(positioned.getName(), effective)) positioned.name(effective);
            }
            case "left" -> positioned.left(NativeElementLibrary.intValue(value, 0));
            case "top" -> positioned.top(NativeElementLibrary.intValue(value, 0));
            case "width" -> positioned.width(NativeElementLibrary.intValue(value, 0));
            case "height" -> positioned.height(NativeElementLibrary.intValue(value, 0));
            case "left-rel" -> positioned.leftRel(NativeElementLibrary.floatValue(value, 0f));
            case "top-rel" -> positioned.topRel(NativeElementLibrary.floatValue(value, 0f));
            case "width-rel" -> positioned.widthRel(NativeElementLibrary.floatValue(value, 0f));
            case "height-rel" -> positioned.heightRel(NativeElementLibrary.floatValue(value, 0f));
            default -> {}
        }
    }

    private void applyStyle(@Nullable MuiElement element, IWidget widget) {
        if (element == null || this.stylesheet == null || !(widget instanceof Widget<?>)) return;
        Map<String, JsonElement> inline = new java.util.LinkedHashMap<>(parseInlineStyle(element.getAttribute("style")));
        if (this.devToolsSession != null) {
            Map<String, JsonElement> overrides = this.devToolsSession.getOverrides().get(element);
            if (overrides != null) inline.putAll(overrides);
        }
        MuiComputedStyle computed = this.stylesheet.compute(element, inline, this.mediaEnvironment);
        this.styleApplier.apply(element, (Widget<?>) widget, computed);
    }

    private static Map<String, JsonElement> parseInlineStyle(@Nullable String source) {
        if (source == null || source.trim().isEmpty()) return Collections.emptyMap();
        try {
            JsonElement parsed = new JsonParser().parse(source);
            if (!parsed.isJsonObject()) return Collections.emptyMap();
            Map<String, JsonElement> values = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                values.put(entry.getKey(), entry.getValue());
            }
            return values;
        } catch (RuntimeException ignored) {
            return Collections.emptyMap();
        }
    }

    private IWidget requireWidget(MuiElement element) {
        IWidget widget = this.elementToWidget.get(element);
        if (widget == null) throw new DomException(DomException.Code.STALE_NODE, "Element is not projected to a Widget");
        return widget;
    }

    private MuiElement requireElement(NodeHandle handle) {
        MuiNode node = this.document.resolveOrThrow(handle);
        if (!(node instanceof MuiElement element) || !this.elementToWidget.containsKey(element)) {
            throw new DomException(DomException.Code.HIERARCHY_REQUEST, "Handle does not reference a Widget element");
        }
        return element;
    }

    private MuiElement requireSlotElement(NodeHandle handle) {
        MuiElement element = requireElement(handle);
        IWidget widget = requireWidget(element);
        if (!(widget instanceof ItemSlot slot) || !this.slotTable.contains(slot)) {
            throw notSupported("SlotViewUpdate contains a node outside the fixed SlotTable");
        }
        return element;
    }

    private boolean containsProjectedWidget(MuiNode node) {
        if (node instanceof MuiElement element && isProjectable(element)) return true;
        for (MuiNode child : node.getChildNodes()) if (containsProjectedWidget(child)) return true;
        return false;
    }

    private void validateRegisteredSlot(ItemSlot slot) {
        if (!slot.isValid() || !slot.getSyncHandler().isValid()) {
            throw notSupported("ItemSlot must have an initialized, pre-registered sync handler before DOM adoption");
        }
        if (!this.screen.getContainer().isSlotRegistered(slot.getSlot())) {
            throw notSupported("ItemSlot is not present in the fixed ModularContainer slot table");
        }
    }

    private void validateNewBoundSlot(ItemSlot slot) {
        if (!slot.getSyncHandler().isValid()) {
            throw notSupported("XML ItemSlot requires an initialized protocol binding before DOM insertion");
        }
        if (!this.validatingConnectedProjection) return;
        if (!this.screen.getContainer().isSlotRegistered(slot.getSlot())) {
            throw notSupported("XML ItemSlot binding is not present in the fixed ModularContainer slot table");
        }
        for (ItemSlot existing : this.slotTable) {
            if (existing != slot && !this.slotsPendingRemoval.contains(existing)
                    && existing.getSlot() == slot.getSlot()) {
                throw notSupported("A fixed ModularContainer slot can only have one active DOM ItemSlot view");
            }
        }
        for (IWidget prepared : this.preparedWidgets.values()) {
            if (prepared instanceof ItemSlot other && other != slot && other.getSlot() == slot.getSlot()) {
                throw notSupported("A fixed ModularContainer slot can only be bound once in one DOM transaction");
            }
        }
    }

    private static DomException notSupported(String message) {
        return new DomException(DomException.Code.NOT_SUPPORTED, message);
    }

    private static String tagName(IWidget widget) {
        if (widget instanceof ModularPanel) return "mui:panel";
        if (widget instanceof ItemSlot) return "mui:item-slot";
        String name = widget.getClass().getSimpleName();
        if (name.endsWith("Widget")) name = name.substring(0, name.length() - "Widget".length());
        StringBuilder tag = new StringBuilder("mui:");
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isUpperCase(character) && i > 0) tag.append('-');
            tag.append(Character.toLowerCase(character));
        }
        return tag.length() == 4 ? "mui:legacy-widget" : tag.toString().toLowerCase(Locale.ROOT);
    }
}
