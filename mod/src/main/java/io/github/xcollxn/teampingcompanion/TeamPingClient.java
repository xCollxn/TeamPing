package io.github.xcollxn.teampingcompanion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TeamPing Companion (client mod).
 * - Keys for ping / squad menu – freely bindable under Options → Controls → "TeamPing (Squad)".
 * - Squad overview HUD, ping markers, far beams, map pings (Xaero), far pings (Distant Horizons).
 * - Pings on Xaero's Minimap/World Map (optional).
 */
public final class TeamPingClient implements ClientModInitializer {

    static final Logger LOG = LoggerFactory.getLogger("TeamPing");

    static KeyMapping tastePing, tasteMenue;
    private static boolean xaero;

    static boolean xaeroDa() { return xaero; }

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(Netz.Status.TYPE, Netz.Status.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Netz.Aktion.TYPE, Netz.Aktion.CODEC);

        Einstellungen.laden();
        xaero = FabricLoader.getInstance().isModLoaded("xaerominimap");

        ClientPlayNetworking.registerGlobalReceiver(Netz.Status.TYPE, (payload, ctx) -> {
            try {
                boolean oeffnen = Zustand.lesen(payload.json());
                if (!aussehenGesendet) { aussehenGesendet = true; aussehenSenden(); }
                if (oeffnen && !(ctx.client().gui.screen() instanceof TeamPingScreen)) TeamPingScreen.oeffnen(TeamPingScreen.Reiter.SQUAD);
                if (xaero && Einstellungen.aktuell.xaero) XaeroBruecke.abgleichen(Zustand.pings);
            } catch (Exception e) {
                LOG.warn("Invalid TeamPing status: {}", e.toString());
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(TastenCheck::pruefen);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TastenCheck.pruefen(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Zustand.zuruecksetzen();
            aussehenGesendet = false;
            if (xaero) client.execute(XaeroBruecke::leeren);
        });

        KeyMapping.Category kategorie = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("teamping", "main"));
        tastePing = KeyMappingHelper.registerKeyMapping(taste("key.teamping.ping", "key.keyboard.z", kategorie));
        tasteMenue = KeyMappingHelper.registerKeyMapping(taste("key.teamping.menu", "key.keyboard.g", kategorie));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (aussehenOffen && System.currentTimeMillis() - aussehenZeit > 400) { aussehenOffen = false; aussehenSenden(); }
            while (tastePing.consumeClick()) FernPing.pingen();   // near: the server aims; far/beyond render distance: coordinates (Distant Horizons if available)
            while (tasteMenue.consumeClick()) {
                // One key for everything: open the window right away (settings always work),
                // squad data comes from the server. Without the mod the server opens the chest menu instead.
                TeamPingScreen.oeffnen(TeamPingScreen.Reiter.SQUAD);
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("teamping", "squad"), SquadHud::zeichnen);
        if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) KartenPing.registrieren();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("teamping", "marker"), PingMarker::zeichnen);
        // far away ping beams are drawn by the mod itself (the server only sends display entities up to ~110–160 blocks)
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(FernStrahl::zeichnen);   // submit before drawing
        TestPilot.starten();
        LOG.info("TeamPing Companion loaded (Xaero: {}, Distant Horizons: {})", xaero ? "yes" : "no", FernPing.dhInstalliert() ? "yes" : "no");
    }

    /** Create a key by its name (26.3 has its own key codes – fixed numbers were wrong). null = unbound. */
    private static KeyMapping taste(String id, String standard, KeyMapping.Category kategorie) {
        com.mojang.blaze3d.platform.InputConstants.Key k = standard == null
                ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN
                : com.mojang.blaze3d.platform.InputConstants.getKey(standard);
        return new KeyMapping(id, k.getType(), k.getValue(), kategorie);
    }

    private static volatile boolean aussehenGesendet, aussehenOffen;
    private static volatile long aussehenZeit;

    /** After a change in the "Look" tab: wait briefly (sliders are being dragged), then send to the server. */
    static void aussehenGeaendert() { aussehenOffen = true; aussehenZeit = System.currentTimeMillis(); }

    static void aussehenSenden() {
        Einstellungen e = Einstellungen.aktuell;
        if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(Netz.Aktion.TYPE)) return;
        ClientPlayNetworking.send(new Netz.Aktion("{\"a\":\"aussehen\",\"art\":\"" + e.strahlArt.name().toLowerCase(java.util.Locale.ROOT)
                + "\",\"breite\":" + e.strahlBreite + ",\"hell\":" + e.strahlHell + ",\"hoehe\":" + e.strahlHoehe + "}"));
    }

    static void senden(String json) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        if (!ClientPlayNetworking.canSend(Netz.Aktion.TYPE)) {
            if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(TeamPingScreen.t("server_ohne")));
            return;
        }
        ClientPlayNetworking.send(new Netz.Aktion(json));
    }
}
