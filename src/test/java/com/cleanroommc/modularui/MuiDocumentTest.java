package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.dom.DomException;
import com.cleanroommc.modularui.api.dom.DomMutation;
import com.cleanroommc.modularui.api.dom.MuiDocument;
import com.cleanroommc.modularui.api.dom.MuiDocumentHost;
import com.cleanroommc.modularui.api.dom.MuiElement;
import com.cleanroommc.modularui.api.dom.MutationScope;
import com.cleanroommc.modularui.api.dom.NodeHandle;
import com.cleanroommc.modularui.api.dom.SlotViewUpdate;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MuiDocumentTest {

    @Test
    void transactionCommitsAsOneValidatedHostBatch() {
        RecordingHost host = new RecordingHost();
        MuiDocument document = new MuiDocument(host);
        MuiElement root = document.createElement("mui:root");
        MuiElement first = document.createElement("mui:item");
        MuiElement second = document.createElement("mui:item");

        try (MutationScope scope = document.beginMutation()) {
            root.appendChild(first);
            root.appendChild(second);
            document.appendChild(root);
            scope.commit();
        }

        assertEquals(1, host.validationCalls);
        assertEquals(1, host.applyCalls);
        assertEquals(3, host.lastBatchSize);
        assertTrue(root.isConnected());
        assertEquals(Arrays.asList(first, second), root.getChildNodes());
    }

    @Test
    void abortedOrRejectedTransactionsNeverChangeTheLogicalTree() {
        MuiDocument aborted = new MuiDocument();
        MuiElement abortedRoot = aborted.createElement("mui:root");
        DomException abort = assertThrows(DomException.class, () -> {
            try (MutationScope ignored = aborted.beginMutation()) {
                aborted.appendChild(abortedRoot);
            }
        });
        assertEquals(DomException.Code.TRANSACTION_ABORTED, abort.getCode());
        assertTrue(aborted.getChildNodes().isEmpty());
        assertFalse(abortedRoot.isConnected());

        MuiDocument rejected = new MuiDocument(new MuiDocumentHost() {
            @Override
            public void validateMutations(List<DomMutation> mutations) {
                throw new DomException(DomException.Code.NOT_SUPPORTED, "rejected by test host");
            }
        });
        MuiElement rejectedRoot = rejected.createElement("mui:root");
        assertThrows(DomException.class, () -> rejected.appendChild(rejectedRoot));
        assertTrue(rejected.getChildNodes().isEmpty());
        assertFalse(rejectedRoot.isConnected());
    }

    @Test
    void selectorsHandlesAndRemovalHaveStableSemantics() {
        MuiDocument document = new MuiDocument();
        MuiElement root = document.createElement("mui:root");
        MuiElement item = document.createElement("mui:item");
        item.setAttribute("id", "iron");
        item.setAttribute("class", "material selected");
        item.setAttribute("tier", "2");
        root.appendChild(item);
        document.appendChild(root);
        NodeHandle handle = item.getHandle();

        assertSame(item, document.getElementById("iron"));
        assertSame(item, document.querySelector(".selected"));
        assertSame(item, document.querySelector("[tier=2]"));
        assertSame(item, document.resolve(handle));

        item.remove();
        assertNull(document.resolve(handle));
        assertFalse(item.isAlive());
        assertThrows(DomException.class, () -> item.setAttribute("tier", "3"));
    }

    @Test
    void oneTransactionHandlesFiveThousandNodes() {
        MuiDocument document = new MuiDocument();
        MuiElement root = document.createElement("mui:root");
        NodeHandle middle = null;
        try (MutationScope scope = document.beginMutation()) {
            for (int i = 0; i < 5000; i++) {
                MuiElement item = document.createElement("mui:item");
                item.setAttribute("index", Integer.toString(i));
                root.appendChild(item);
                if (i == 2500) middle = item.getHandle();
            }
            document.appendChild(root);
            scope.commit();
        }

        assertEquals(5000, document.querySelectorAll("mui:item").size());
        assertTrue(document.resolve(middle).isConnected());
        root.remove();
        assertNull(document.resolve(middle));
    }

    @Test
    void slotViewUpdateIsImmutableAndRejectsAmbiguousTables() {
        NodeHandle visibleParent = new NodeHandle(1, 1, 1);
        NodeHandle parkingParent = new NodeHandle(1, 1, 2);
        NodeHandle slot = new NodeHandle(1, 1, 3);
        SlotViewUpdate update = new SlotViewUpdate(visibleParent, parkingParent,
                Collections.singletonList(slot), Collections.singletonList(slot));

        assertEquals(Collections.singletonList(slot), update.getVisibleSlots());
        assertThrows(UnsupportedOperationException.class,
                () -> update.getVisibleSlots().add(new NodeHandle(1, 1, 4)));
        assertThrows(IllegalArgumentException.class, () -> new SlotViewUpdate(visibleParent, parkingParent,
                Arrays.asList(slot, slot), Collections.singletonList(slot)));
        assertThrows(IllegalArgumentException.class, () -> new SlotViewUpdate(visibleParent, parkingParent,
                Collections.singletonList(slot), Collections.singletonList(new NodeHandle(1, 1, 4))));
    }

    private static final class RecordingHost implements MuiDocumentHost {

        private int validationCalls;
        private int applyCalls;
        private int lastBatchSize;

        @Override
        public void validateMutations(List<DomMutation> mutations) {
            this.validationCalls++;
            this.lastBatchSize = mutations.size();
        }

        @Override
        public void applyMutations(List<DomMutation> mutations) {
            this.applyCalls++;
        }
    }
}
