package io.github.xcollxn.teamping;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.ClientEmoteEvent;

/**
 * Bedrock: emote = ping. Geyser reports every emote before others see it – if the gesture is on, it is intercepted
 * (others don't see it) and pinged instead. Works on phone (emote button), PC (key B) and controller.
 * Only loaded when Geyser-Spigot is running.
 */
final class GeyserEmote {
    private GeyserEmote() {}

    static void anmelden(TeamPing pl) {
        try {
            GeyserApi.api().eventBus().subscribe(EventRegistrar.of(pl), ClientEmoteEvent.class, e -> {
                Player p = Bukkit.getPlayer(e.connection().javaUuid());
                if (p == null || !(pl.lerntEmote(p) || pl.squads().aktiv(p, Squads.Ausloeser.EMOTE))) return;
                e.setCancelled(true);   // others don't see the emote
                e.silent(true);         // no emote chat line (as far as Geyser can control it)
                String id = e.emoteId();
                Bukkit.getScheduler().runTask(pl, () -> { if (p.isOnline()) pl.emote(p, id); });
            });
            pl.getLogger().info("Bedrock emotes are recognised as pings (Geyser).");
        } catch (Throwable t) {
            pl.getLogger().warning("Geyser emote hook not possible: " + t);
        }
    }
}
