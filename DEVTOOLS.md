# MUI DevTools backend

`ModularScreen#openDevTools()` opens a client-local inspection session for the
screen's existing DOM and Java Widget projection. It is intentionally a backend
API so an addon can provide its own inspector overlay, console, or optional JS
adapter without adding a runtime dependency to MUI.

```java
MuiDevToolsSession tools = screen.openDevTools();
tools.select(screen.getDocument().getElementById("input-slot").getHandle());
tools.setStyle("border-color", "#38bda6"); // live override, no restart
tools.setInlineStyle("width", "24px");     // edits the DOM style attribute
MuiComputedStyle computed = tools.getComputedStyle();
MuiDevToolsSession.NodeSnapshot tree = tools.snapshot();
tools.setStylesheetCss("mui|button { background: #263640; }");
List<MuiDiagnostics.Entry> log = tools.getLog();
tools.registerSource("ui.xml", xmlSource, this::reloadDocument);
tools.registerSource("styles.css", cssSource);
tools.registerReadOnlySource("protocol.xml", protocolSource);
tools.setSource("styles.css", editedCss); // CSS is parsed and applied now
```

Runtime overrides are client-only and are merged after the element inline style
when the existing cascade is recomputed. They never enter a sync packet, mutate
the server store, or change the stable `NodeHandle`. `clearStyles(handle)` and
`close()` remove the overrides; destroying a DOM node removes them automatically.

All calls must run on the screen owner thread, matching the existing DOM mutation
contract. The session exposes tag name, attributes, projected Widget class, live
geometry, children, selected handle, and computed declarations. No JS engine is
required for this baseline; GraalJS remains an optional addon concern.

## Diagnostic log

`MuiDiagnostics` is a client-local ring buffer with a capacity of 512 entries.
Entries contain a timestamp, `INFO`/`WARN`/`ERROR` level, source, message and
the exception type when one is available. The buffer is independent from
Log4j/Forge logging: code which reports through `MuiDiagnostics` is also written
to the normal logger where appropriate, but third-party Log4j records are not
automatically copied into this buffer.

An inspector log pane can read the current entries with
`MuiDevToolsSession#getLog()` and clear them with `clearLog()`. Texture loading
failures, for example, are recorded here before the renderer uses its fallback
rectangle, so a broken optional texture no longer has to crash the client.

Applications that load markup can register their effective files with
`registerSource`. The Source tab can then display and edit XML, CSS and JSON
buffers. `setSource` immediately reparses CSS and reapplies it to projected
widgets. Other source types remain buffered by default. A loader can register a
`SourceApplier` to validate and apply XML edits; `replaceCompiledDocument`
atomically replaces a connected compiled root while preserving the old tree when
compilation or Widget projection fails. Files that cannot safely change at runtime,
such as fixed protocol XML negotiated with the server, should use
`registerReadOnlySource`.

The existing `DebugOverlay` menu now opens a browser-like `DevToolsPanel` with
Elements, Source and Log tabs. It is implemented with ordinary MUI widgets and
is opened as a client-only sub-panel, so it does not add a JS runtime or change
the network/synchronisation protocol. External-editor integration is
intentionally deferred.

## CSS progress bars and borders

The Widget-backed CSS adapter supports `border-style: solid` and `border-style:
dashed`. For `ProgressWidget`, set `progress-render: bar` and use
`progress-track-color` / `progress-fill-color`. The progress value remains the
existing server-synchronised `IDoubleValue`; CSS only changes its presentation:

```css
mui|progress {
    progress-render: bar;
    progress-track-color: #27323d;
    progress-fill-color: #38bda6;
}
```

Omitting `progress-render: bar` preserves the legacy texture renderer, so existing
screens remain source-compatible.
