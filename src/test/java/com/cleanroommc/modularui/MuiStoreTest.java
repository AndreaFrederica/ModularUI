package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MuiText;
import com.cleanroommc.modularui.api.state.MuiStore;
import com.cleanroommc.modularui.api.state.MuiStoreBinding;
import com.cleanroommc.modularui.api.state.MuiStoreChange;
import com.cleanroommc.modularui.api.state.StoreSubscription;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MuiStoreTest {

    @Test
    void storePatchRevisionResetAndSubscriptionsAreAtomic() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("count", 1);
        initial.put("nullable", null);
        initial.put("items", Arrays.asList("a", "b"));
        MuiStore store = new MuiStore(initial);
        AtomicReference<MuiStoreChange> change = new AtomicReference<>();
        StoreSubscription subscription = store.subscribe(change::set);

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("count", 2);
        patch.put("extra", true);
        store.patch(patch);
        assertEquals(1, store.getRevision());
        assertEquals(2, store.get("count"));
        assertTrue(change.get().getChangedKeys().contains("extra"));
        assertThrows(UnsupportedOperationException.class, () -> ((java.util.List<?>) store.get("items")).clear());

        store.reset();
        assertEquals(2, store.getRevision());
        assertEquals(1, store.get("count"));
        assertFalse(store.contains("extra"));
        assertTrue(store.contains("nullable"));
        store.remove("nullable");
        assertFalse(store.contains("nullable"));
        long beforeRejectedPatch = store.getRevision();
        Map<String, Object> rejected = new LinkedHashMap<>();
        rejected.put("count", 99);
        rejected.put("invalid", new Object());
        assertThrows(IllegalArgumentException.class, () -> store.patch(rejected));
        assertEquals(1, store.get("count"));
        assertEquals(beforeRejectedPatch, store.getRevision());
        subscription.unsubscribe();
        assertFalse(subscription.isSubscribed());
    }

    @Test
    void storeBindingsUpdateDomAndCanBeDisposed() {
        MuiDocument document = new MuiDocument();
        MuiElement element = document.createElement("mui:text");
        MuiText text = document.createTextNode("initial");
        element.appendChild(text);
        MuiStore store = new MuiStore(Collections.singletonMap("label", "Ready"));

        StoreSubscription binding = MuiStoreBinding.text(store, "label", element);
        assertEquals("Ready", text.getData());
        store.set("label", "Updated");
        assertEquals("Updated", text.getData());
        binding.unsubscribe();
        store.set("label", "Ignored");
        assertEquals("Updated", text.getData());
    }
}
