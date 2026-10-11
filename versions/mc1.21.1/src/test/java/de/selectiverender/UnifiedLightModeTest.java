package de.selectiverender;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedLightModeTest {
    @Test void defaultsAndLegacySettingsChooseOnePolicyForAllRegions() {
        var both = SelectiveRenderSettings.VirtualLightMode.BOTH;
        var none = SelectiveRenderSettings.VirtualLightMode.NONE;
        var top = SelectiveRenderSettings.VirtualLightMode.TOP;
        assertEquals(both, SelectiveRenderSettings.commonVirtualLightMode(null, null));
        assertEquals(none, SelectiveRenderSettings.commonVirtualLightMode(none, top));
        assertEquals(top, SelectiveRenderSettings.commonVirtualLightMode(null, top));
        assertEquals(SelectiveRenderSettings.virtualLightMode(), SelectiveRenderSettings.hiddenVirtualLightMode());
    }
}
