package de.selectiverender;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldSessionLifecycleTest {
    @Test
    void setWorldFollowedByJoinDoesNotReloadRestoredPlots() {
        WorldSessionLifecycle<Object> lifecycle = new WorldSessionLifecycle<>();
        Object world = new Object();
        List<String> calls = new ArrayList<>();
        lifecycle.switchTo(world, () -> calls.add("leave"), next -> calls.add("restore"));
        lifecycle.switchTo(world, () -> calls.add("leave"), next -> calls.add("reload saved groups"));
        assertEquals(List.of("restore"), calls);
    }

    @Test
    void dimensionChangeAndRejoinBothSaveBeforeRestoring() {
        WorldSessionLifecycle<Object> lifecycle = new WorldSessionLifecycle<>();
        List<String> calls = new ArrayList<>();
        Object overworld = new Object();
        Object nether = new Object();
        Runnable leave = () -> calls.add("save outgoing plots");
        lifecycle.switchTo(overworld, leave, next -> calls.add("restore overworld"));
        lifecycle.switchTo(nether, leave, next -> calls.add("restore nether"));
        lifecycle.switchTo(null, leave, next -> calls.add("unexpected"));
        // DISCONNECT and setWorld(null) may both notify us.
        lifecycle.switchTo(null, leave, next -> calls.add("unexpected"));
        lifecycle.switchTo(new Object(), leave, next -> calls.add("restore overworld"));
        assertEquals(List.of("restore overworld", "save outgoing plots", "restore nether",
                "save outgoing plots", "restore overworld"), calls);
    }

    @Test
    void initialNullNotificationsDoNothing() {
        WorldSessionLifecycle<Object> lifecycle = new WorldSessionLifecycle<>();
        List<String> calls = new ArrayList<>();
        lifecycle.switchTo(null, () -> calls.add("leave"), next -> calls.add("enter"));
        assertEquals(List.of(), calls);
    }
}
