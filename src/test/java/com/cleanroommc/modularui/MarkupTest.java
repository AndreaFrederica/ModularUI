package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.event.ActionEvent;
import com.cleanroommc.modularui.api.event.InputModifiers;
import com.cleanroommc.modularui.api.event.PointerEvent;
import com.cleanroommc.modularui.api.navigation.NavigationAction;
import com.cleanroommc.modularui.api.markup.MuiApplicationDescriptor;
import com.cleanroommc.modularui.api.markup.MuiManifestRegistry;
import com.cleanroommc.modularui.api.markup.MuiMarkupException;
import com.cleanroommc.modularui.api.component.MuiComponentDescriptor;
import com.cleanroommc.modularui.api.component.MuiComponentRegistry;
import com.cleanroommc.modularui.markup.MuiDocumentCompiler;
import com.cleanroommc.modularui.markup.MuiXmlParser;
import com.cleanroommc.modularui.markup.MuiApplicationDescriptorParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MarkupTest {

    @Test
    void xmlEventAttributesInvokeDocumentLocalJavaActionsAndTrackRuntimeChanges() {
        MuiDocument document = new MuiDocument();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<MuiElement> source = new AtomicReference<>();
        document.getActions().register("test:save", invocation -> {
            calls.incrementAndGet();
            source.set(invocation.getElement());
            invocation.getEvent().preventDefault();
        });
        document.getActions().register("test:other", invocation -> calls.addAndGet(10));

        MuiElement button = MuiXmlParser.parse("<button onclick=\"test:save\"/>", document);
        assertFalse(button.dispatchEvent(new PointerEvent(PointerEvent.DOWN, 0, 4, 5,
                0, 1, 0, 0, 0, InputModifiers.NONE)));
        assertEquals(1, calls.get());
        assertSame(button, source.get());

        button.dispatchEvent(new PointerEvent(PointerEvent.DOWN, 0, 4, 5,
                1, 2, 0, 0, 0, InputModifiers.NONE));
        button.dispatchEvent(new ActionEvent(NavigationAction.SECONDARY));
        assertEquals(1, calls.get());
        button.dispatchEvent(new ActionEvent(NavigationAction.ACTIVATE));
        assertEquals(2, calls.get());

        button.setAttribute("onclick", "test:other");
        button.dispatchEvent(new ActionEvent(NavigationAction.ACTIVATE));
        assertEquals(12, calls.get());
        button.removeAttribute("onclick");
        button.dispatchEvent(new ActionEvent(NavigationAction.ACTIVATE));
        assertEquals(12, calls.get());
    }

    @Test
    void xmlEventAttributesRejectCodeAndUnnamespacedReferences() {
        MuiDocument document = new MuiDocument();
        assertThrows(IllegalArgumentException.class,
                () -> MuiXmlParser.parse("<button onclick=\"save()\"/>", document));
        assertTrue(document.getChildNodes().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> MuiXmlParser.parse("<button onpointerdown=\"save\"/>", document));
        assertTrue(document.getChildNodes().isEmpty());
    }

    @Test
    void applicationDescriptorJsonRegistersComponentsAndStylesheetsWithLimits() {
        MuiApplicationDescriptor descriptor = MuiApplicationDescriptorParser.parse("{"
                + "\"formatVersion\":1,\"owner\":\"showcase\","
                + "\"namespaces\":{\"nfr\":\"urn:nfr\"},"
                + "\"components\":{\"nfr:card\":\"components/card.xml\"},"
                + "\"stylesheets\":[\"styles/main.css\"]}");
        assertEquals("showcase", descriptor.getOwner());
        assertEquals("urn:nfr", descriptor.getNamespaces().get("nfr"));
        assertEquals("components/card.xml", descriptor.getComponents().get("nfr:card"));
        assertEquals(Collections.singletonList("styles/main.css"), descriptor.getStylesheets());
        assertThrows(MuiMarkupException.class, () -> MuiApplicationDescriptorParser.parse(
                "{\"formatVersion\":2,\"owner\":\"x\"}"));
        assertThrows(MuiMarkupException.class, () -> MuiApplicationDescriptorParser.parse(
                "{\"formatVersion\":1,\"owner\":\"x\",\"unknown\":true}"));
    }

    @Test
    void xmlCompilesStructureAttributesNamespacesAndTextIntoTheJavaDom() {
        MuiDocument document = new MuiDocument();
        MuiElement root = MuiXmlParser.parse(
                "<ui:container xmlns:ui=\"urn:mui\" id=\"root\"><ui:text>Hello <b>world</b></ui:text></ui:container>",
                document);

        assertEquals("ui:container", root.getTagName());
        assertEquals("root", root.getAttribute("id"));
        assertTrue(root.isConnected());
        assertEquals("ui:text", ((MuiElement) root.getChildNodes().get(0)).getTagName());
        assertEquals("Hello world", root.getTextContent());
        assertEquals("b", ((MuiElement) root.getChildNodes().get(0).getChildNodes().get(1)).getTagName());
    }

    @Test
    void xmlParserRejectsDtdAndDoesNotCommitPartialTree() {
        MuiDocument document = new MuiDocument();
        assertThrows(MuiMarkupException.class, () -> MuiXmlParser.parse(
                "<!DOCTYPE ui:container [<!ENTITY x SYSTEM 'file:///tmp/secret'>]><ui:container>&x;</ui:container>",
                document));
        assertTrue(document.getChildNodes().isEmpty());
    }

    @Test
    void xmlParserEnforcesLimitsAndLeavesDocumentUnchanged() {
        MuiDocument document = new MuiDocument();
        MuiXmlParser.Limits limits = new MuiXmlParser.Limits(1024, 2, 4, 3, 8);
        assertThrows(MuiMarkupException.class, () -> MuiXmlParser.parse(
                "<root><child><leaf/></child></root>", document, limits));
        assertTrue(document.getChildNodes().isEmpty());

        MuiXmlParser.Limits textLimits = new MuiXmlParser.Limits(1024, 8, 4, 8, 3);
        assertThrows(MuiMarkupException.class, () -> MuiXmlParser.parse("<root>abcd</root>", document, textLimits));
        assertTrue(document.getChildNodes().isEmpty());
    }

    @Test
    void manifestRegistryIsImmutableToCallersAndFreezesBeforeCompilation() {
        MuiApplicationDescriptor descriptor = MuiApplicationDescriptor.builder("rtsbuilding")
                .namespace("rts", "urn:rtsbuilding:components")
                .component("rts:storage-workspace", "mui/component/storage-workspace.xml")
                .stylesheet("mui/style/base.json")
                .build();
        MuiManifestRegistry registry = new MuiManifestRegistry();
        registry.register(descriptor);

        assertEquals(descriptor, registry.require("rtsbuilding"));
        assertEquals("mui/component/storage-workspace.xml",
                registry.require("rtsbuilding").getComponents().get("rts:storage-workspace"));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.require("rtsbuilding").getComponents().put("x", "y"));
        registry.freeze();
        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class, () -> registry.register(
                MuiApplicationDescriptor.builder("nfr").build()));
    }

    @Test
    void malformedXmlReportsMarkupFailureInsteadOfTransactionAbort() {
        MuiDocument document = new MuiDocument();
        MuiMarkupException failure = assertThrows(MuiMarkupException.class,
                () -> MuiXmlParser.parse("<root><broken></root>", document));
        assertFalse(failure.getMessage().isEmpty());
        assertTrue(document.getChildNodes().isEmpty());
    }

    @Test
    void componentCompilerExpandsPropsNamedSlotsAndScopedRootAtomically() {
        MuiComponentRegistry registry = new MuiComponentRegistry();
        registry.register(MuiComponentDescriptor.builder("test:card", "card.xml").build());
        MuiDocumentCompiler compiler = new MuiDocumentCompiler(registry, (owner, resource) -> {
            if (!"test-owner".equals(owner) || !"card.xml".equals(resource)) return null;
            return new ByteArrayInputStream((
                    "<mui:component-root xmlns:mui=\"urn:mui\"><mui:container class=\"${class}\" title=\"${title}\">"
                            + "<mui:slot name=\"body\"><mui:text>fallback</mui:text></mui:slot>"
                            + "</mui:container></mui:component-root>").getBytes(StandardCharsets.UTF_8));
        });

        MuiDocument document = new MuiDocument();
        MuiElement root = compiler.compile("test-owner",
                "<test:card xmlns:test=\"urn:test\" xmlns:mui=\"urn:mui\" class=\"wide\" title=\"Storage\"><mui:text slot=\"body\">Ready</mui:text></test:card>",
                document);

        assertEquals("mui:container", root.getTagName());
        assertEquals("wide", root.getAttribute("class"));
        assertEquals("Storage", root.getAttribute("title"));
        assertEquals("Ready", root.getTextContent());
        assertEquals("mui:text", ((MuiElement) root.getChildNodes().get(0)).getTagName());
    }

    @Test
    void componentExpansionFailureDoesNotCommitTargetRoot() {
        MuiComponentRegistry registry = new MuiComponentRegistry();
        registry.register(MuiComponentDescriptor.builder("test:missing", "missing.xml").build());
        MuiDocumentCompiler compiler = new MuiDocumentCompiler(registry, (owner, resource) -> null);
        MuiDocument document = new MuiDocument();
        MuiElement existing = MuiXmlParser.parse("<existing/>", document);

        assertThrows(MuiMarkupException.class, () -> compiler.compile("test-owner", "<test:missing/>", document));
        assertEquals(1, document.getChildNodes().size());
        assertSame(existing, (MuiElement) document.getChildNodes().get(0));
    }
}
