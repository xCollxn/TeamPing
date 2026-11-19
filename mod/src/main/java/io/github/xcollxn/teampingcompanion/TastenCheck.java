package io.github.xcollxn.teampingcompanion;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Protection against crashes from invalid keys (26.3 uses SDL key codes 0..511).
 * Old/broken bindings (e.g. "key.keyboard.-1" from earlier mod versions or other mods)
 * crash Minecraft when joining a world – they are repaired here and written to the log.
 */
final class TastenCheck {

    private static final int MAX = 512; // SDL_SCANCODE_COUNT

    private TastenCheck() {}

    static void pruefen(Minecraft mc) {
        if (mc.options == null) return;
        boolean geaendert = false;
        for (KeyMapping km : mc.options.keyMappings) {
            InputConstants.Key k;
            try { k = InputConstants.getKey(km.saveString()); }
            catch (Exception e) { k = null; }
            boolean kaputt = k == null || (k.getType() == InputConstants.Type.KEYBOARD && (k.getValue() < 0 || k.getValue() >= MAX));
            if (!kaputt) continue;
            InputConstants.Key standard = km.getDefaultKey();
            boolean standardOk = standard.getType() != InputConstants.Type.KEYBOARD || (standard.getValue() >= 0 && standard.getValue() < MAX);
            km.setKey(standardOk ? standard : InputConstants.UNKNOWN);
            TeamPingClient.LOG.warn("Repaired invalid key: {} was '{}' -> now '{}'", km.getName(), km.saveString().isEmpty() ? "?" : km.saveString(),
                    (standardOk ? standard : InputConstants.UNKNOWN).getName());
            geaendert = true;
        }
        if (geaendert) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
    }
}
