package io.github.xcollxn.teampingcompanion;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Field;

/**
 * Pinging on Xaero's World Map: middle click or double click on the map pings the centre of the clicked chunk.
 * Normal clicks (panning, right-click menu) stay unchanged.
 */
final class KartenPing {

    private static final String KARTEN_KLASSE = "xaero.map.gui.GuiMap";
    private static Field feldX, feldZ, feldDim;
    private static boolean kaputt;
    private static long letzterKlick;
    private static double letztX, letztY;

    private KartenPing() {}

    static void registrieren() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (kaputt || !screen.getClass().getName().equals(KARTEN_KLASSE)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, ev) -> {
                if (ev.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE) { pingen(s); return false; }                 // middle click: only ping
                if (ev.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                    long jetzt = System.currentTimeMillis();
                    boolean doppel = jetzt - letzterKlick < 350 && Math.abs(ev.x() - letztX) < 4 && Math.abs(ev.y() - letztY) < 4;
                    letzterKlick = doppel ? 0 : jetzt; letztX = ev.x(); letztY = ev.y();
                    if (doppel) pingen(s);
                }
                return true;
            });
        });
    }

    private static void pingen(Screen karte) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (feldX == null) {
                Class<?> k = karte.getClass();
                feldX = k.getDeclaredField("mouseBlockPosX"); feldX.setAccessible(true);
                feldZ = k.getDeclaredField("mouseBlockPosZ"); feldZ.setAccessible(true);
                feldDim = k.getDeclaredField("mouseBlockDim"); feldDim.setAccessible(true);
            }
            int x = feldX.getInt(karte), z = feldZ.getInt(karte);
            Object dim = feldDim.get(karte);
            if (mc.player == null || mc.level == null) return;
            if (dim instanceof ResourceKey<?> rk && !rk.equals(mc.level.dimension())) {
                mc.player.sendOverlayMessage(Component.literal(TeamPingScreen.t("karte.dimension")));
                return;
            }
            int cx = Math.floorDiv(x, 16) * 16 + 8, cz = Math.floorDiv(z, 16) * 16 + 8;   // centre of the chunk
            if (!Zustand.statusDa()) {
                mc.player.sendOverlayMessage(Component.literal(TeamPingScreen.t("gui.keine_verbindung")));
                return;
            }
            TeamPingClient.senden("{\"a\":\"karte\",\"x\":" + cx + ",\"z\":" + cz + "}");
            mc.player.sendOverlayMessage(Component.literal(TeamPingScreen.t("karte.gepingt", cx, cz)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            kaputt = true;
            TeamPingClient.LOG.warn("Map ping disabled (different Xaero version?): {}", e.toString());
        }
    }
}
