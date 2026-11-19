package io.github.xcollxn.teamping;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Puts the Bedrock resource packs (light beam + UI) and the block mapping into Geyser's folders, so server owners
 * don't have to do it by hand. Runs in onLoad – before Geyser starts and reads packs/ and custom_mappings/ – so no
 * extra restart is needed. Only writes files that are missing or outdated. Off with "bedrock-install-packs: false".
 */
final class BedrockPakete {

    private static final String[][] DATEIEN = {   // resource in the jar, Geyser sub folder
            {"bedrock/TeamPing-Bedrock.mcpack", "packs"},
            {"bedrock/TeamPing-Bedrock-Fenster.mcpack", "packs"},
            {"bedrock/teamping_mappings.json", "custom_mappings"},
    };

    private static boolean erledigt;

    private BedrockPakete() {}

    private static File geyserOrdner(TeamPing pl) { return new File(pl.getDataFolder().getParentFile(), "Geyser-Spigot"); }

    /** onLoad: only if Geyser has run before (its folder exists). */
    static void beimLaden(TeamPing pl) {
        if (!pl.getConfig().getBoolean("bedrock-install-packs", true)) { erledigt = true; return; }
        File g = geyserOrdner(pl);
        if (!g.isDirectory()) return;      // Geyser not installed or never started – checked again in onEnable
        erledigt = true;
        int n = schreiben(pl, g);
        if (n > 0) pl.getLogger().info("Bedrock packs installed/updated in Geyser (" + n + " file(s)).");
    }

    /** onEnable: Geyser was started for the very first time in this run → install now, takes effect after a restart. */
    static void beimStart(TeamPing pl) {
        if (erledigt || pl.getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) return;
        int n = schreiben(pl, geyserOrdner(pl));
        if (n > 0) pl.getLogger().warning("Bedrock packs were installed into Geyser – restart the server once so Bedrock players get them.");
    }

    private static int schreiben(TeamPing pl, File geyser) {
        int n = 0;
        for (String[] d : DATEIEN) {
            try (InputStream in = pl.getResource(d[0])) {
                if (in == null) continue;
                byte[] neu = in.readAllBytes();
                File ziel = new File(new File(geyser, d[1]), d[0].substring(d[0].indexOf('/') + 1));
                if (ziel.isFile() && Arrays.equals(Files.readAllBytes(ziel.toPath()), neu)) continue;
                File ordner = ziel.getParentFile();
                if (!ordner.isDirectory() && !ordner.mkdirs()) throw new java.io.IOException("cannot create " + ordner);
                Files.write(ziel.toPath(), neu);
                n++;
            } catch (Exception e) {
                pl.getLogger().warning("Could not install Bedrock file " + d[0] + " into Geyser: " + e);
            }
        }
        return n;
    }
}
