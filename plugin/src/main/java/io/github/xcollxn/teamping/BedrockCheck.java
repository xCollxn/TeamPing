package io.github.xcollxn.teamping;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Checks whether a player plays via Bedrock (Floodgate) – without loading the Bedrock form class.
 * Important: if Floodgate is missing, BedrockMenus must never be touched (otherwise NoClassDefFoundError).
 */
final class BedrockCheck {
    private BedrockCheck() {}

    static boolean aktiv(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false;
        try { return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId()); }
        catch (Throwable t) { return false; }
    }

    /** Does the player use touch (phone/tablet)? There sneaking is an on/off toggle. */
    static boolean touch(Player p) {
        if (!aktiv(p)) return false;
        try {
            var fp = org.geysermc.floodgate.api.FloodgateApi.getInstance().getPlayer(p.getUniqueId());
            if (fp == null) return false;
            if (String.valueOf(fp.getInputMode()).equals("TOUCH")) return true;
            String os = String.valueOf(fp.getDeviceOs()).toUpperCase(java.util.Locale.ROOT);   // The input mode doesn't always arrive → detect phone/tablet by the operating system
            return os.contains("ANDROID") || os.contains("IOS") || os.contains("AMAZON") || os.contains("FIRE");
        } catch (Throwable t) { return false; }
    }

    /** Short description for the server log: device and input mode (e.g. "ANDROID / TOUCH"). */
    static String geraet(Player p) {
        if (!aktiv(p)) return "Java";
        try {
            var fp = org.geysermc.floodgate.api.FloodgateApi.getInstance().getPlayer(p.getUniqueId());
            return fp == null ? "Bedrock ?" : fp.getDeviceOs() + " / " + fp.getInputMode() + " / UI " + fp.getUiProfile();
        } catch (Throwable t) { return "Bedrock (" + t.getClass().getSimpleName() + ")"; }
    }
}
