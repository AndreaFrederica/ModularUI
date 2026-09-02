package com.cleanroommc.modularui.screen.dom;

import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.debug.MuiDiagnostics;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiNode;
import com.cleanroommc.modularui.api.dom.NodeHandle;
import com.cleanroommc.modularui.api.markup.MuiResourceResolver;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.style.MuiComputedStyle;
import com.cleanroommc.modularui.style.MuiStylesheetParser;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client-local inspection and live-edit session for one MUI document.
 *
 * <p>The session deliberately has no networking or JavaScript dependency. A future
 * inspector overlay, console or optional script runtime can use this same API.
 * Runtime declarations are merged after the element's inline style and are never
 * included in document mutations or server synchronisation.</p>
 */
public final class MuiDevToolsSession implements AutoCloseable {

    private final MuiScreenDocumentController controller;
    private final Map<MuiElement, Map<String, JsonElement>> overrides = new IdentityHashMap<>();
    private final Map<String, SourceFile> sourceFiles = new LinkedHashMap<>();
    @Nullable private String stylesheetOwner;
    @Nullable private MuiResourceResolver stylesheetResolver;
    @Nullable private MuiElement selected;
    private boolean closed;

    MuiDevToolsSession(MuiScreenDocumentController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public NodeSnapshot snapshot() {
        checkOpen();
        List<NodeSnapshot> roots = new ArrayList<>();
        for (MuiNode node : controller.getDocument().getChildNodes()) {
            if (node instanceof MuiElement) roots.add(snapshot((MuiElement) node));
        }
        return NodeSnapshot.document(roots);
    }

    public @Nullable NodeSnapshot selectedSnapshot() {
        checkOpen();
        return selected == null || !selected.isConnected() ? null : snapshot(selected);
    }

    public @Nullable NodeHandle getSelected() {
        checkOpen();
        return selected == null || !selected.isConnected() ? null : selected.getHandle();
    }

    public boolean select(NodeHandle handle) {
        checkOpen();
        if (handle == null || !handle.isPresent()) {
            selected = null;
            return false;
        }
        MuiNode node = controller.getDocument().resolve(handle);
        if (!(node instanceof MuiElement) || !node.isConnected()) return false;
        selected = (MuiElement) node;
        return true;
    }

    public @Nullable MuiComputedStyle getComputedStyle() {
        checkOpen();
        return selected == null || !selected.isConnected() ? null : controller.getComputedStyle(selected);
    }

    public @Nullable MuiComputedStyle getComputedStyle(NodeHandle handle) {
        checkOpen();
        MuiNode node = controller.getDocument().resolve(handle);
        return node instanceof MuiElement && node.isConnected()
                ? controller.getComputedStyle((MuiElement) node) : null;
    }

    /** Sets one runtime declaration on the selected element. Null removes it. */
    public void setStyle(String property, @Nullable String value) {
        checkOpen();
        if (selected == null || !selected.isConnected()) throw new IllegalStateException("No connected node is selected");
        setStyle(selected.getHandle(), property, value);
    }

    /** Sets one runtime declaration by handle. Values use the same JSON scalar syntax as CSS declarations. */
    public void setStyle(NodeHandle handle, String property, @Nullable String value) {
        checkOpen();
        MuiElement element = requireElement(handle);
        String name = normalizeProperty(property);
        if (value == null) {
            Map<String, JsonElement> values = overrides.get(element);
            if (values != null) {
                values.remove(name);
                if (values.isEmpty()) overrides.remove(element);
            }
        } else {
            overrides.computeIfAbsent(element, ignored -> new LinkedHashMap<>())
                    .put(name, parseValue(value));
        }
        controller.refreshStyle(element);
    }

    /** Replaces all runtime declarations for one node. */
    public void setStyles(NodeHandle handle, Map<String, ?> values) {
        checkOpen();
        MuiElement element = requireElement(handle);
        Map<String, JsonElement> replacement = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String name = normalizeProperty(entry.getKey());
            Object value = entry.getValue();
            replacement.put(name, value instanceof JsonElement ? parseValue(((JsonElement) value).toString()) : parseValue(String.valueOf(value)));
        }
        if (replacement.isEmpty()) overrides.remove(element);
        else overrides.put(element, replacement);
        controller.refreshStyle(element);
    }

    /** Edits the DOM inline-style attribute while preserving all unrelated declarations. */
    public void setInlineStyle(String property, @Nullable String value) {
        checkOpen();
        if (selected == null || !selected.isConnected()) throw new IllegalStateException("No connected node is selected");
        String name = normalizeProperty(property);
        JsonObject inline = parseInline(selected.getAttribute("style"));
        if (value == null) inline.remove(name);
        else inline.add(name, parseValue(value));
        if (inline.entrySet().isEmpty()) selected.removeAttribute("style");
        else selected.setAttribute("style", inline.toString());
    }

    public void clearStyles(NodeHandle handle) {
        checkOpen();
        MuiElement element = requireElement(handle);
        overrides.remove(element);
        controller.refreshStyle(element);
    }

    public void refresh() {
        checkOpen();
        controller.refreshStyles();
    }

    /** Replaces the current CSS-subset stylesheet and reapplies it immediately. */
    public void setStylesheetCss(String source) {
        checkOpen();
        controller.setStylesheet(parseStylesheet(source));
    }

    /**
     * Authorizes CSS {@code @import} resolution for this inspector session.
     * The resolver is supplied by the owning application and must only expose
     * resources that application has already authorized.
     */
    public void setStylesheetContext(@Nullable String owner, @Nullable MuiResourceResolver resolver) {
        checkOpen();
        if ((owner == null) != (resolver == null)) {
            throw new IllegalArgumentException("stylesheet owner and resolver must both be present or absent");
        }
        this.stylesheetOwner = owner;
        this.stylesheetResolver = resolver;
    }

    /** Registers a source file that was used to construct this screen. Client-local and optional. */
    public void registerSource(String resourceId, String source) {
        registerSource(resourceId, source, null, false);
    }

    /** Registers an editable source whose owner can validate and apply updates at runtime. */
    public void registerSource(String resourceId, String source, SourceApplier applier) {
        registerSource(resourceId, source, Objects.requireNonNull(applier, "applier"), false);
    }

    /** Registers a source for inspection only. */
    public void registerReadOnlySource(String resourceId, String source) {
        registerSource(resourceId, source, null, true);
    }

    private void registerSource(String resourceId, String source, @Nullable SourceApplier applier,
                                boolean readOnly) {
        checkOpen();
        if (resourceId == null || resourceId.trim().isEmpty()) throw new IllegalArgumentException("resourceId must not be empty");
        sourceFiles.put(resourceId, new SourceFile(source == null ? "" : source, applier, readOnly));
    }

    /** Returns the source files registered by the screen/application loader. */
    public Map<String, String> getSources() {
        checkOpen();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, SourceFile> entry : sourceFiles.entrySet()) {
            result.put(entry.getKey(), entry.getValue().source);
        }
        return Collections.unmodifiableMap(result);
    }

    public @Nullable String getSource(String resourceId) {
        checkOpen();
        SourceFile source = sourceFiles.get(resourceId);
        return source == null ? null : source.source;
    }

    public boolean isSourceReadOnly(String resourceId) {
        checkOpen();
        SourceFile source = sourceFiles.get(resourceId);
        if (source == null) throw new IllegalArgumentException("Unknown source file: " + resourceId);
        return source.readOnly;
    }

    /**
     * Updates a registered source in the client-local inspector buffer. CSS files
     * are parsed and applied immediately. A source-specific applier runs before the
     * buffer is committed; other non-CSS sources remain buffered.
     */
    public void setSource(String resourceId, String source) {
        checkOpen();
        SourceFile registered = sourceFiles.get(resourceId);
        if (registered == null) throw new IllegalArgumentException("Unknown source file: " + resourceId);
        if (registered.readOnly) throw new IllegalStateException("Source file is read-only: " + resourceId);
        String value = source == null ? "" : source;
        if (registered.applier != null) {
            registered.applier.apply(value);
        } else if (resourceId.toLowerCase(java.util.Locale.ROOT).endsWith(".css")) {
            // Parse before changing the editor buffer, so a syntax/import error
            // leaves both the active stylesheet and the previous source intact.
            controller.setStylesheet(parseStylesheet(value));
        }
        registered.source = value;
    }

    @FunctionalInterface
    public interface SourceApplier {
        void apply(String source);
    }

    private static final class SourceFile {
        private String source;
        @Nullable private final SourceApplier applier;
        private final boolean readOnly;

        private SourceFile(String source, @Nullable SourceApplier applier, boolean readOnly) {
            this.source = source;
            this.applier = applier;
            this.readOnly = readOnly;
        }
    }

    /** Returns the shared client-local MUI diagnostics ring buffer for an inspector log pane. */
    public List<MuiDiagnostics.Entry> getLog() {
        checkOpen();
        return MuiDiagnostics.snapshot();
    }

    public void clearLog() {
        checkOpen();
        MuiDiagnostics.clear();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            controller.removeDevToolsSession(this);
        }
    }

    Map<MuiElement, Map<String, JsonElement>> getOverrides() { return overrides; }

    void removeOverrides(MuiElement element) {
        overrides.remove(element);
        if (selected == element) selected = null;
    }

    private NodeSnapshot snapshot(MuiElement element) {
        IWidget widget = controller.resolveWidget(element.getHandle());
        Area area = widget == null ? null : widget.getArea();
        List<NodeSnapshot> children = new ArrayList<>();
        for (MuiNode child : element.getChildNodes()) {
            if (child instanceof MuiElement) children.add(snapshot((MuiElement) child));
        }
        return new NodeSnapshot(element.getHandle(), element.getTagName(), element.getAttributes(),
                widget == null ? null : widget.getClass().getName(),
                area == null ? null : new Geometry(area.x, area.y, area.width, area.height, area.z()), children);
    }

    private MuiElement requireElement(NodeHandle handle) {
        MuiNode node = controller.getDocument().resolve(handle);
        if (!(node instanceof MuiElement) || !node.isConnected()) throw new IllegalArgumentException("Unknown or disconnected node: " + handle);
        return (MuiElement) node;
    }

    private static JsonObject parseInline(@Nullable String source) {
        if (source == null || source.trim().isEmpty()) return new JsonObject();
        try {
            JsonElement parsed = new JsonParser().parse(source);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) { return new JsonObject(); }
    }

    private static JsonElement parseValue(String value) {
        try { return new JsonParser().parse(value); }
        catch (RuntimeException ignored) { return new com.google.gson.JsonPrimitive(value); }
    }

    private com.cleanroommc.modularui.style.MuiCascade parseStylesheet(String source) {
        if (stylesheetOwner != null && stylesheetResolver != null) {
            return MuiStylesheetParser.parseCss(stylesheetOwner, source, stylesheetResolver);
        }
        return MuiStylesheetParser.parseCss(source);
    }

    private static String normalizeProperty(String property) {
        if (property == null || property.trim().isEmpty()) throw new IllegalArgumentException("Style property must not be empty");
        return property.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("DevTools session is closed");
    }

    public static final class Geometry {
        private final int x, y, width, height, z;
        Geometry(int x, int y, int width, int height, int z) { this.x = x; this.y = y; this.width = width; this.height = height; this.z = z; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getZ() { return z; }
    }

    public static final class NodeSnapshot {
        private final NodeHandle handle;
        private final String tagName;
        private final Map<String, String> attributes;
        private final String widgetClass;
        private final Geometry geometry;
        private final List<NodeSnapshot> children;

        private NodeSnapshot(NodeHandle handle, String tagName, Map<String, String> attributes, String widgetClass, Geometry geometry, List<NodeSnapshot> children) {
            this.handle = handle;
            this.tagName = tagName;
            this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            this.widgetClass = widgetClass;
            this.geometry = geometry;
            this.children = Collections.unmodifiableList(new ArrayList<>(children));
        }

        private static NodeSnapshot document(List<NodeSnapshot> roots) {
            return new NodeSnapshot(NodeHandle.EMPTY, "#document", Collections.emptyMap(), null, null, roots);
        }
        public NodeHandle getHandle() { return handle; }
        public String getTagName() { return tagName; }
        public Map<String, String> getAttributes() { return attributes; }
        public @Nullable String getWidgetClass() { return widgetClass; }
        public @Nullable Geometry getGeometry() { return geometry; }
        public List<NodeSnapshot> getChildren() { return children; }
    }
}
