package com.cleanroommc.modularui.api.dom;

import com.cleanroommc.modularui.api.event.IEventTarget;
import com.cleanroommc.modularui.api.event.MuiActionRegistry;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.event.EventListenerRegistry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** A structured, transactional UI document. */
public final class MuiDocument implements IEventTarget, AutoCloseable {

    private static final AtomicLong NEXT_DOCUMENT_ID = new AtomicLong();
    private static final MuiDocumentHost NOOP_HOST = new MuiDocumentHost() {};

    private final long documentId = NEXT_DOCUMENT_ID.incrementAndGet();
    private final DocumentGeneration generation = new DocumentGeneration(1);
    private final MuiDocumentHost host;
    private final MuiActionRegistry actions = new MuiActionRegistry();
    private final MuiDeclarativeEventBindings declarativeEvents = new MuiDeclarativeEventBindings(this.actions);
    private final Map<Long, MuiNode> nodes = new LinkedHashMap<>();
    private final List<MuiNode> mutableRoots = new ArrayList<>();
    private final List<MuiNode> roots = Collections.unmodifiableList(this.mutableRoots);
    private long nextNodeId;
    private long nextTransactionId;
    private Transaction transaction;
    private volatile boolean open = true;

    public MuiDocument() {
        this(null);
    }

    @ApiStatus.Internal
    public MuiDocument(@Nullable MuiDocumentHost host) {
        this.host = host == null ? NOOP_HOST : host;
    }

    public long getDocumentId() {
        return this.documentId;
    }

    public DocumentGeneration getGeneration() {
        return this.generation;
    }

    public boolean isOpen() {
        return this.open;
    }

    /** Java and optional script actions addressable by XML event attributes in this document. */
    public MuiActionRegistry getActions() {
        requireOpen();
        return this.actions;
    }

    public @UnmodifiableView List<MuiNode> getChildNodes() {
        requireOpen();
        return this.roots;
    }

    public MuiElement createElement(String tagName) {
        requireOpen();
        return register(new MuiElement(this, nextHandle(), tagName));
    }

    public MuiText createTextNode(String data) {
        requireOpen();
        return register(new MuiText(this, nextHandle(), data));
    }

    int getScrollLeft(MuiElement element) {
        this.host.checkAccess();
        return this.host.getScrollLeft(element);
    }

    int getScrollTop(MuiElement element) {
        this.host.checkAccess();
        return this.host.getScrollTop(element);
    }

    int getScrollWidth(MuiElement element) {
        this.host.checkAccess();
        return this.host.getScrollWidth(element);
    }

    int getScrollHeight(MuiElement element) {
        this.host.checkAccess();
        return this.host.getScrollHeight(element);
    }

    void setScrollPosition(MuiElement element, int left, int top) {
        this.host.checkAccess();
        this.host.setScrollPosition(element, Math.max(0, left), Math.max(0, top));
    }

    @ApiStatus.Internal
    public LegacyWidgetElement createLegacyWidgetElement(String tagName, IWidget widget, boolean opaque) {
        requireOpen();
        return register(new LegacyWidgetElement(this, nextHandle(), tagName,
                Objects.requireNonNull(widget, "widget"), opaque));
    }

    public @Nullable MuiNode resolve(NodeHandle handle) {
        this.host.checkAccess();
        Objects.requireNonNull(handle, "handle");
        if (!this.open || handle.getDocumentId() != this.documentId
                || handle.getGeneration() != this.generation.getValue()) return null;
        MuiNode node = this.nodes.get(handle.getNodeId());
        return node != null && node.alive ? node : null;
    }

    public MuiNode resolveOrThrow(NodeHandle handle) {
        MuiNode node = resolve(handle);
        if (node == null) throw new DomException(DomException.Code.STALE_NODE, "Node handle is stale: " + handle);
        return node;
    }

    public MutationScope beginMutation() {
        requireOpen();
        Thread thread = Thread.currentThread();
        if (this.transaction == null) {
            this.transaction = new Transaction(++this.nextTransactionId, thread);
        } else if (this.transaction.owner != thread) {
            throw new DomException(DomException.Code.INVALID_STATE,
                    "DOM mutation transaction belongs to another thread");
        }
        MutationScope scope = new MutationScope(this, this.transaction.id);
        this.transaction.scopes.push(scope);
        return scope;
    }

    MutationResult finishMutation(MutationScope scope, long transactionId, boolean commit) {
        Transaction current = this.transaction;
        if (current == null || current.id != transactionId || current.owner != Thread.currentThread()) {
            throw new IllegalStateException("Mutation scope does not belong to the active transaction");
        }
        if (current.scopes.peek() != scope) {
            throw new IllegalStateException("Mutation scopes must be closed in reverse order");
        }
        current.scopes.pop();
        if (!commit) current.aborted = true;
        if (!current.scopes.isEmpty()) return MutationResult.PENDING;
        this.transaction = null;
        if (current.aborted) {
            throw new DomException(DomException.Code.TRANSACTION_ABORTED,
                    "DOM mutation transaction was closed without commit");
        }
        return apply(current.mutations);
    }

    public MuiNode appendChild(MuiNode child) {
        insert(null, child, -1);
        return child;
    }

    public MuiNode insertChild(int index, MuiNode child) {
        insert(null, child, index);
        return child;
    }

    public MuiNode removeChild(MuiNode child) {
        remove(null, child);
        return child;
    }

    void insert(@Nullable MuiNode parent, MuiNode child, int index) {
        enqueue(new PendingMutation(DomMutation.Kind.INSERT, parent,
                Objects.requireNonNull(child, "child"), index));
    }

    void remove(@Nullable MuiNode parent, MuiNode child) {
        enqueue(new PendingMutation(DomMutation.Kind.REMOVE, parent,
                Objects.requireNonNull(child, "child"), -1));
    }

    private void enqueue(PendingMutation mutation) {
        requireOpen();
        boolean automatic = this.transaction == null;
        MutationScope scope = automatic ? beginMutation() : null;
        this.transaction.mutations.add(mutation);
        if (scope != null) scope.commit();
    }

    private MutationResult apply(List<PendingMutation> pending) {
        if (pending.isEmpty()) return new MutationResult(true, 0);
        List<DomMutation> mutations = validate(pending);
        this.host.validateMutations(Collections.unmodifiableList(mutations));
        try {
            this.host.applyMutations(Collections.unmodifiableList(mutations));
        } catch (DomException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DomException(DomException.Code.TRANSACTION_ABORTED,
                    "Widget projection failed while applying DOM mutation", exception);
        }
        for (DomMutation mutation : mutations) applyLogical(mutation);
        this.host.onMutationsApplied(Collections.unmodifiableList(mutations));
        return new MutationResult(true, mutations.size());
    }

    private List<DomMutation> validate(List<PendingMutation> pending) {
        ShadowTree shadow = new ShadowTree();
        List<DomMutation> result = new ArrayList<>(pending.size());
        IdentityHashMap<MuiNode, Boolean> explicitlyRemoved = new IdentityHashMap<>();
        for (PendingMutation mutation : pending) {
            validateNode(mutation.child);
            if (mutation.parent != null) {
                validateNode(mutation.parent);
                mutation.parent.requireCanHaveChildren();
            }
            if (mutation.kind == DomMutation.Kind.INSERT) {
                if (explicitlyRemoved.containsKey(mutation.child)) {
                    throw new DomException(DomException.Code.HIERARCHY_REQUEST,
                            "A removed node cannot be reinserted in the same transaction; insert it directly to move it");
                }
                result.add(shadow.insert(mutation.parent, mutation.child, mutation.index));
            } else {
                DomMutation normalized = shadow.remove(mutation.parent, mutation.child);
                markSubtree(mutation.child, explicitlyRemoved);
                result.add(normalized);
            }
        }
        return result;
    }

    private void validateNode(MuiNode node) {
        this.host.checkAccess();
        if (node.getOwnerDocument() != this) {
            throw new DomException(DomException.Code.WRONG_DOCUMENT, "Node belongs to another document");
        }
        node.requireAlive();
    }

    private static void markSubtree(MuiNode node, IdentityHashMap<MuiNode, Boolean> marked) {
        marked.put(node, Boolean.TRUE);
        for (MuiNode child : node.mutableChildren) markSubtree(child, marked);
    }

    private void applyLogical(DomMutation mutation) {
        MuiNode child = mutation.getChild();
        if (mutation.getKind() == DomMutation.Kind.INSERT) {
            if (mutation.getPreviousIndex() >= 0) {
                if (mutation.wasPreviouslyRoot()) this.mutableRoots.remove(child);
                else mutation.getPreviousParent().mutableChildren.remove(child);
            }
            MuiNode parent = mutation.getParent();
            List<MuiNode> target = parent == null ? this.mutableRoots : parent.mutableChildren;
            target.add(mutation.getIndex(), child);
            child.parent = parent;
            setConnected(child, mutation.willBeConnected());
        } else {
            MuiNode parent = mutation.getParent();
            if (parent == null) this.mutableRoots.remove(child);
            else parent.mutableChildren.remove(child);
            child.parent = null;
            List<MuiNode> destroyed = new ArrayList<>();
            destroySubtree(child, destroyed);
            this.host.onNodesDestroyed(Collections.unmodifiableList(destroyed));
        }
    }

    private void destroySubtree(MuiNode node, List<MuiNode> destroyed) {
        for (MuiNode child : new ArrayList<>(node.mutableChildren)) destroySubtree(child, destroyed);
        node.mutableChildren.clear();
        node.parent = null;
        node.connected = false;
        node.alive = false;
        this.nodes.remove(node.getHandle().getNodeId());
        EventListenerRegistry.clear(node);
        this.declarativeEvents.forget(node);
        destroyed.add(node);
    }

    private static void setConnected(MuiNode node, boolean connected) {
        node.connected = connected;
        for (MuiNode child : node.mutableChildren) setConnected(child, connected);
    }

    void setAttribute(MuiElement element, String name, @Nullable String value) {
        validateNode(element);
        this.declarativeEvents.validate(name, value);
        String oldValue = element.getAttribute(name);
        if (Objects.equals(oldValue, value)) return;
        element.setAttributeDirect(name, value);
        try {
            this.host.onAttributeChanged(element, name, oldValue, value);
        } catch (RuntimeException exception) {
            // Attribute observers are part of the same mutation boundary. Do not
            // expose a value that the projection rejected.
            element.setAttributeDirect(name, oldValue);
            throw exception;
        }
        this.declarativeEvents.update(element, name, value);
    }

    void setTextData(MuiText text, String data) {
        validateNode(text);
        String oldValue = text.getData();
        if (oldValue.equals(data)) return;
        this.host.onTextChanged(text, oldValue, data);
        text.setDataDirect(data);
    }

    public @Nullable MuiElement getElementById(String id) {
        requireOpen();
        Objects.requireNonNull(id, "id");
        for (MuiNode root : this.mutableRoots) {
            MuiElement found = find(root, element -> id.equals(element.getAttribute("id")), true);
            if (found != null) return found;
        }
        return null;
    }

    public @Nullable MuiElement querySelector(String selector) {
        requireOpen();
        return querySelectorFrom(null, selector, true);
    }

    public List<MuiElement> querySelectorAll(String selector) {
        requireOpen();
        return querySelectorAllFrom(null, selector, true);
    }

    @Nullable MuiElement querySelectorFrom(@Nullable MuiElement root, String selector, boolean includeSelf) {
        validateSelector(selector);
        if (root != null) root.requireAlive();
        if (root != null) return find(root, element -> element.matchesSimpleSelector(selector), includeSelf);
        for (MuiNode node : this.mutableRoots) {
            MuiElement found = find(node, element -> element.matchesSimpleSelector(selector), true);
            if (found != null) return found;
        }
        return null;
    }

    List<MuiElement> querySelectorAllFrom(@Nullable MuiElement root, String selector, boolean includeSelf) {
        validateSelector(selector);
        if (root != null) root.requireAlive();
        List<MuiElement> result = new ArrayList<>();
        if (root != null) collect(root, selector, includeSelf, result);
        else for (MuiNode node : this.mutableRoots) collect(node, selector, true, result);
        return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
    }

    private static void validateSelector(String selector) {
        if (selector == null || selector.trim().isEmpty()) throw new IllegalArgumentException("selector must not be empty");
        if (selector.indexOf(',') >= 0 || selector.trim().indexOf(' ') >= 0) {
            throw new DomException(DomException.Code.NOT_SUPPORTED,
                    "Only simple tag, #id, .class and [attribute] selectors are supported");
        }
    }

    private static @Nullable MuiElement find(MuiNode node, ElementPredicate predicate, boolean includeSelf) {
        if (includeSelf && node instanceof MuiElement element && predicate.test(element)) return element;
        for (MuiNode child : node.mutableChildren) {
            MuiElement found = find(child, predicate, true);
            if (found != null) return found;
        }
        return null;
    }

    private static void collect(MuiNode node, String selector, boolean includeSelf, List<MuiElement> result) {
        if (includeSelf && node instanceof MuiElement element && element.matchesSimpleSelector(selector)) result.add(element);
        for (MuiNode child : node.mutableChildren) collect(child, selector, true, result);
    }

    @ApiStatus.Internal
    public void adoptNode(@Nullable MuiNode parent, MuiNode child) {
        validateNode(child);
        if (parent != null) validateNode(parent);
        if (child.parent != null || child.connected) {
            throw new DomException(DomException.Code.HIERARCHY_REQUEST, "Node is already attached");
        }
        if (parent == null) this.mutableRoots.add(child);
        else parent.mutableChildren.add(child);
        child.parent = parent;
        setConnected(child, parent == null || parent.connected);
    }

    @ApiStatus.Internal
    public void destroyFromHost(MuiNode node) {
        if (node == null || !node.alive || node.getOwnerDocument() != this) return;
        if (node.parent != null) node.parent.mutableChildren.remove(node);
        else this.mutableRoots.remove(node);
        List<MuiNode> destroyed = new ArrayList<>();
        destroySubtree(node, destroyed);
        this.host.onNodesDestroyed(Collections.unmodifiableList(destroyed));
    }

    @ApiStatus.Internal
    public void moveFromHost(MuiNode parent, MuiNode child, int index) {
        validateNode(parent);
        validateNode(child);
        if (child.parent != parent) throw new DomException(DomException.Code.NOT_FOUND, "Node is not a child of parent");
        int from = parent.mutableChildren.indexOf(child);
        if (from < 0 || index < 0 || index >= parent.mutableChildren.size()) {
            throw new DomException(DomException.Code.NOT_FOUND, "Host child move index is out of bounds");
        }
        if (from == index) return;
        parent.mutableChildren.remove(from);
        parent.mutableChildren.add(index, child);
    }

    @Override
    public void close() {
        this.host.checkAccess();
        if (!this.open) return;
        this.open = false;
        this.transaction = null;
        this.declarativeEvents.clear();
        this.actions.clear();
        List<MuiNode> all = new ArrayList<>(this.nodes.values());
        for (MuiNode node : all) {
            EventListenerRegistry.clear(node);
            node.alive = false;
            node.connected = false;
            node.parent = null;
            node.mutableChildren.clear();
        }
        this.nodes.clear();
        this.mutableRoots.clear();
        this.host.onNodesDestroyed(Collections.unmodifiableList(all));
        this.host.onDocumentClosed();
        EventListenerRegistry.clear(this);
    }

    private NodeHandle nextHandle() {
        return new NodeHandle(this.documentId, this.generation.getValue(), ++this.nextNodeId);
    }

    private <T extends MuiNode> T register(T node) {
        this.nodes.put(node.getHandle().getNodeId(), node);
        return node;
    }

    private void requireOpen() {
        this.host.checkAccess();
        if (!this.open) throw new DomException(DomException.Code.INVALID_STATE, "Document is closed");
    }

    private interface ElementPredicate {
        boolean test(MuiElement element);
    }

    private static final class PendingMutation {
        private final DomMutation.Kind kind;
        private final MuiNode parent;
        private final MuiNode child;
        private final int index;

        private PendingMutation(DomMutation.Kind kind, @Nullable MuiNode parent, MuiNode child, int index) {
            this.kind = kind;
            this.parent = parent;
            this.child = child;
            this.index = index;
        }
    }

    private static final class Transaction {
        private final long id;
        private final Thread owner;
        private final Deque<MutationScope> scopes = new ArrayDeque<>();
        private final List<PendingMutation> mutations = new ArrayList<>();
        private boolean aborted;

        private Transaction(long id, Thread owner) {
            this.id = id;
            this.owner = owner;
        }
    }

    private final class ShadowTree {
        private final IdentityHashMap<MuiNode, MuiNode> parents = new IdentityHashMap<>();
        private final IdentityHashMap<MuiNode, List<MuiNode>> children = new IdentityHashMap<>();
        private final List<MuiNode> roots = new ArrayList<>(MuiDocument.this.mutableRoots);

        private ShadowTree() {
            for (MuiNode node : MuiDocument.this.nodes.values()) {
                if (!node.alive) continue;
                this.parents.put(node, node.parent);
                this.children.put(node, new ArrayList<>(node.mutableChildren));
            }
        }

        private DomMutation insert(@Nullable MuiNode parent, MuiNode child, int requestedIndex) {
            if (parent == child || isAncestor(child, parent)) {
                throw new DomException(DomException.Code.HIERARCHY_REQUEST, "Mutation would create a cycle");
            }
            MuiNode oldParent = this.parents.get(child);
            boolean oldRoot = this.roots.contains(child);
            List<MuiNode> oldList = oldRoot ? this.roots : oldParent == null ? null : this.children.get(oldParent);
            int oldIndex = oldList == null ? -1 : oldList.indexOf(child);
            boolean connectedBefore = isConnected(child);
            List<MuiNode> target = parent == null ? this.roots : this.children.get(parent);
            if (target == null) throw new DomException(DomException.Code.HIERARCHY_REQUEST, "Parent cannot contain children");

            int index = requestedIndex < 0 ? target.size() : requestedIndex;
            if (oldList == target && oldIndex >= 0 && oldIndex < index) index--;
            if (index < 0 || index > target.size() - (oldList == target && oldIndex >= 0 ? 1 : 0)) {
                throw new DomException(DomException.Code.NOT_FOUND, "Child insertion index is out of bounds: " + requestedIndex);
            }
            if (oldIndex >= 0) oldList.remove(oldIndex);
            target.add(index, child);
            this.parents.put(child, parent);
            boolean connectedAfter = parent == null || isConnected(parent);
            return new DomMutation(DomMutation.Kind.INSERT, parent, oldParent, child, index, oldIndex,
                    oldRoot, connectedBefore, connectedAfter);
        }

        private DomMutation remove(@Nullable MuiNode parent, MuiNode child) {
            MuiNode actualParent = this.parents.get(child);
            boolean root = this.roots.contains(child);
            if ((parent == null && !root) || (parent != null && actualParent != parent)) {
                throw new DomException(DomException.Code.NOT_FOUND, "Node is not a child of the requested parent");
            }
            List<MuiNode> list = root ? this.roots : this.children.get(parent);
            int index = list.indexOf(child);
            if (index < 0) throw new DomException(DomException.Code.NOT_FOUND, "Node is not a child");
            boolean connectedBefore = isConnected(child);
            list.remove(index);
            this.parents.put(child, null);
            return new DomMutation(DomMutation.Kind.REMOVE, parent, parent, child, index, index,
                    root, connectedBefore, false);
        }

        private boolean isAncestor(MuiNode ancestor, @Nullable MuiNode node) {
            MuiNode current = node;
            while (current != null) {
                if (current == ancestor) return true;
                current = this.parents.get(current);
            }
            return false;
        }

        private boolean isConnected(MuiNode node) {
            MuiNode current = node;
            IdentityHashMap<MuiNode, Boolean> visited = new IdentityHashMap<>();
            while (current != null) {
                if (visited.put(current, Boolean.TRUE) != null) return false;
                if (this.roots.contains(current)) return true;
                current = this.parents.get(current);
            }
            return false;
        }
    }
}
