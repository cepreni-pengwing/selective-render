package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InteractionPolicyTest {
    @Test void noOpModeWinsUnlessInactiveFilteringIsExplicitlyEnabled() {
        var inside = SelectiveRenderSettings.InteractionMode.INSIDE;
        assertFalse(InteractionPolicy.active(inside, false, false, true));
        assertFalse(InteractionPolicy.active(inside, false, true, false));
        assertTrue(InteractionPolicy.active(inside, false, true, true));
        assertTrue(InteractionPolicy.active(inside, true, false, false));
        assertFalse(InteractionPolicy.active(SelectiveRenderSettings.InteractionMode.EVERYWHERE,
                true, true, true));
    }

    @Test void everyInteractionModeHasStableSemantics() {
        assertFalse(InteractionPolicy.allows(SelectiveRenderSettings.InteractionMode.NONE, true));
        assertTrue(InteractionPolicy.allows(SelectiveRenderSettings.InteractionMode.INSIDE, true));
        assertFalse(InteractionPolicy.allows(SelectiveRenderSettings.InteractionMode.INSIDE, false));
        assertFalse(InteractionPolicy.allows(SelectiveRenderSettings.InteractionMode.OUTSIDE, true));
        assertTrue(InteractionPolicy.allows(SelectiveRenderSettings.InteractionMode.OUTSIDE, false));
        assertTrue(InteractionPolicy.allows(SelectiveRenderSettings.InteractionMode.EVERYWHERE, false));
    }
}
