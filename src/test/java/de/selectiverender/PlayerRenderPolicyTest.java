package de.selectiverender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlayerRenderPolicyTest {
    @Test void identityAndScopeAreIndependentForEveryCombination() {
        for(var group:PlayerRenderPolicy.Group.values()) for(var scope:PlayerRenderPolicy.Scope.values())
            for(boolean own:new boolean[]{false,true}) for(boolean inside:new boolean[]{false,true}) {
                boolean identity=group==PlayerRenderPolicy.Group.EVERYONE || group==PlayerRenderPolicy.Group.SELF && own || group==PlayerRenderPolicy.Group.OTHERS && !own;
                boolean area=scope==PlayerRenderPolicy.Scope.EVERYWHERE || scope==PlayerRenderPolicy.Scope.INSIDE && inside || scope==PlayerRenderPolicy.Scope.OUTSIDE && !inside;
                assertEquals(identity&&area,PlayerRenderPolicy.allows(group,scope,own,inside));
            }
    }
    @Test void legacySettingsKeepTheirMeaning() {
        for(var legacy:SelectiveRenderSettings.PlayerVisibility.values()) {
            var settings=PlayerRenderPolicy.migrate(legacy);
            for(boolean own:new boolean[]{false,true}) for(boolean inside:new boolean[]{false,true}) {
                boolean expected=switch(legacy) { case NONE->false; case INSIDE->inside; case OUTSIDE->!inside; case OWN_ONLY->own; case EXCEPT_OWN->!own; default->true; };
                assertEquals(expected,PlayerRenderPolicy.allows(settings.group(),settings.scope(),own,inside));
            }
        }
    }
}
