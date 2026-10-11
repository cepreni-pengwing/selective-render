package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FilterEditSessionTest {
    private BlockFilterRule rule(String value, BlockFilterRule.Mode mode) {
        return new BlockFilterRule(mode, BlockFilterRule.Kind.ID, value);
    }

    @Test void buttonsUseTheSelectorJustConfirmedByFocusLoss() {
        var original = rule("minecraft:stone", BlockFilterRule.Mode.HIDE);
        var edited = rule("minecraft:oak_log", BlockFilterRule.Mode.HIDE);
        var only = rule("minecraft:oak_log", BlockFilterRule.Mode.ONLY);
        var session = new FilterEditSession();
        session.replaced(original, edited);
        assertEquals(edited, session.current(original));
        session.replaced(edited, only);
        assertEquals(only, session.current(original));
        session.replaced(only, null);
        assertThrows(IllegalArgumentException.class, () -> session.current(original));
    }

    @Test void rebuildingDropsOldWidgetAliases() {
        var original = rule("minecraft:stone", BlockFilterRule.Mode.HIDE);
        var session = new FilterEditSession();
        session.replaced(original, null);
        session.clear();
        assertEquals(original, session.current(original));
    }
}
