package com.cleanroommc.modularui;

import com.cleanroommc.modularui.api.bus.MuiEventBus;
import com.cleanroommc.modularui.api.bus.MuiEventScope;
import com.cleanroommc.modularui.api.bus.MuiEventTopic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MuiEventBusTest {

    @Test
    void busUsesPriorityStableOrderSnapshotsAndScopeDisposal() {
        MuiEventBus bus = new MuiEventBus();
        MuiEventTopic<String> topic = new MuiEventTopic<>("test:changed", String.class);
        MuiEventScope scope = new MuiEventScope();
        List<String> calls = new ArrayList<>();
        bus.subscribe(scope, topic, value -> calls.add("normal:" + value), 0);
        bus.subscribe(scope, topic, value -> {
            calls.add("high:" + value);
            scope.close();
        }, 10);
        bus.subscribe(topic, value -> calls.add("late:" + value), 0);

        bus.publish(topic, "one");
        assertEquals(Arrays.asList("high:one", "late:one"), calls);
        calls.clear();
        bus.publish(topic, "two");
        assertEquals(Arrays.asList("late:two"), calls);
        assertTrue(scope.isClosed());
        bus.close();
        assertThrows(IllegalStateException.class, () -> bus.publish(topic, "closed"));
    }
}
