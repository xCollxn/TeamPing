package io.github.xcollxn.teampingcompanion;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mods → TeamPing Companion → Configure opens the settings. */
public final class ModMenuAnbindung implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return eltern -> new TeamPingScreen(TeamPingScreen.Reiter.EINSTELLUNGEN, eltern);   // closing returns to the mod menu
    }
}
