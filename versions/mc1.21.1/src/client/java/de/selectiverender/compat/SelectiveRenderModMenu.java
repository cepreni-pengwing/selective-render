package de.selectiverender.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.selectiverender.SelectiveRenderSettingsScreen;

/** Optional entrypoint: loaded by Mod Menu only when it is installed. */
public final class SelectiveRenderModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<SelectiveRenderSettingsScreen> getModConfigScreenFactory() {
        return SelectiveRenderSettingsScreen::new;
    }
}
