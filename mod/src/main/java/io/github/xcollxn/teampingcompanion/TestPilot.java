package io.github.xcollxn.teampingcompanion;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Only for tests in the development environment (start with -Dteamping.test=&lt;script&gt;).
 * Operates the window like a player (real mouseClicked/mouseReleased calls like the MouseHandler),
 * writes values with "[TEST]" to the log and takes screenshots. Inactive in normal play.
 */
final class TestPilot {

    private record Schritt(int warten, Consumer<Minecraft> was) {}

    private static final List<Schritt> schritte = new ArrayList<>();
    private static int index, wartet;
    private static boolean bereit;

    private TestPilot() {}

    static void starten() {
        String ablauf = System.getProperty("teamping.test");
        if (ablauf == null) return;
        TeamPingClient.LOG.info("[TEST] Testablauf: {}", ablauf);
        if (ablauf.startsWith("@")) {
            try { ablauf = java.nio.file.Files.readString(java.nio.file.Path.of(ablauf.substring(1))); }
            catch (Exception e) { TeamPingClient.LOG.warn("[TEST] Datei fehlt: {}", e.toString()); return; }
        }
        for (String teil : ablauf.split("[;\n]")) if (!teil.isBlank() && !teil.trim().startsWith("#")) planen(teil.trim());
        schritt(0, mc -> { log("FERTIG"); mc.stop(); });
        ClientTickEvents.END_CLIENT_TICK.register(TestPilot::tick);
    }

    private static void schritt(int warten, Consumer<Minecraft> was) { schritte.add(new Schritt(warten, was)); }

    /** Steps: "cmd:squad create x", "open:SQUAD", "tab:1", "click:Chat", "shot:name", "dump", "wait:40", "scroll:-1". */
    private static void planen(String t) {
        String[] p = t.split(":", 2);
        String a = p.length > 1 ? p[1] : "";
        switch (p[0]) {
            case "cmd" -> schritt(12, mc -> { log("Befehl /" + a); mc.getConnection().sendCommand(a); });
            case "open" -> schritt(20, mc -> TeamPingScreen.oeffnen(TeamPingScreen.Reiter.valueOf(a)));
            case "tab" -> schritt(15, mc -> {
                if (mc.gui.screen() instanceof TeamPingScreen s) {
                    int i = Integer.parseInt(a);
                    klick(s, s.reiterX(i) + 13, s.reiterY() + 14);
                    log("Reiter-Klick " + i + " -> jetzt " + s.reiterName());
                }
            });
            case "click" -> schritt(30, mc -> {
                Screen s = mc.gui.screen();
                AbstractWidget w = finde(s, a);
                if (w == null) { log("Knopf nicht gefunden: " + a); return; }
                klick(s, w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0);
                log("Klick auf '" + w.getMessage().getString() + "'");
            });
            case "haken" -> schritt(15, mc -> {   // click the n-th checkbox (0-based)
                Screen sc = mc.gui.screen(); int n = Integer.parseInt(a), i = 0;
                for (GuiEventListener l : sc.children())
                    if (l instanceof net.minecraft.client.gui.components.Checkbox c && i++ == n) {
                        klick(sc, c.getX() + 5, c.getY() + 5); log("Haken " + n + " jetzt " + c.selected()); return;
                    }
                log("Haken " + n + " nicht gefunden");
            });
            case "scroll" -> schritt(10, mc -> {
                Screen s = mc.gui.screen();
                if (s instanceof TeamPingScreen tp) s.mouseScrolled(tp.listeMitteX(), tp.listeMitteY(), 0, Double.parseDouble(a));
                log("Gescrollt " + a);
            });
            case "shot" -> schritt(5, mc -> Screenshot.grab(mc.gameDirectory, "test-" + a + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> log("Screenshot " + a)));
            case "dump" -> schritt(1, mc -> dump(mc.gui.screen()));
            case "wait" -> schritt(Integer.parseInt(a), mc -> {});
            case "zustand" -> schritt(1, mc -> log("Zustand: squad=" + Zustand.squad + " chat=" + Zustand.chat + " version=" + Zustand.version
                    + " mitglieder=" + Zustand.mitglieder.size() + " pings=" + Zustand.pings.size()));
            case "fake" -> schritt(20, mc -> {
                double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
                String w = mc.level.dimension().identifier().toString();
                Zustand.testMitglieder = List.of(
                        new Zustand.Mitglied("Alex_07", java.util.UUID.nameUUIDFromBytes("a".getBytes()), 0xA845FF, true, 18, 20, x + 30, y, z - 32, w),
                        new Zustand.Mitglied("Builder", java.util.UUID.nameUUIDFromBytes("b".getBytes()), 0x3CFF3C, true, 7, 20, x - 120, y, z + 5, w),
                        new Zustand.Mitglied("Kumpel_42", java.util.UUID.nameUUIDFromBytes("c".getBytes()), 0xFF3030, true, 20, 20, x, y, z, "minecraft:the_nether"),
                        new Zustand.Mitglied("Schlafmuetze", java.util.UUID.nameUUIDFromBytes("d".getBytes()), 0xFFE600, false, 0, 20, 0, 0, 0, ""),
                        new Zustand.Mitglied("Nummer5", java.util.UUID.nameUUIDFromBytes("e".getBytes()), 0xFF8C00, true, 13, 20, x + 4, y, z + 9, w));
                log("Schein-Mitglieder gesetzt");
            });
            case "fakeviele" -> schritt(20, mc -> {
                // fixed world positions around (0,0) – so teleports change the distances
                String w = mc.level.dimension().identifier().toString();
                String[] n = {"Alex_07", "Builder", "Kumpel_42", "Nummer5", "Bauarbeiter", "Gruftie", "Pilot", "Angler", "Weitweg"};
                double[][] pos = {{10, 5}, {-40, 0}, {80, 80}, {0, -60}, {150, 0}, {-200, -40}, {30, -30}, {-10, 120}, {400, 400}};
                float[] hp = {18, 20, 20, 13, 20, 20, 20, 20, 4};
                java.util.List<Zustand.Mitglied> l = new java.util.ArrayList<>();
                for (int i = 0; i < n.length; i++)
                    l.add(new Zustand.Mitglied(n[i], java.util.UUID.nameUUIDFromBytes(n[i].getBytes()), 0x40B4FF + i * 0x151515, true, hp[i], 20,
                            pos[i][0], -60, pos[i][1], w));
                Zustand.testMitglieder = l;
                log("9 Schein-Mitglieder gesetzt");
            });
            case "maus" -> schritt(10, mc -> {   // real mouse position (GUI coordinates) – as if moved by the player
                String[] xy = a.split(",");
                double f = mc.getWindow().getGuiScale();
                try {
                    var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos"); fx.setAccessible(true);
                    var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos"); fy.setAccessible(true);
                    fx.setDouble(mc.mouseHandler, Double.parseDouble(xy[0]) * f);
                    fy.setDouble(mc.mouseHandler, Double.parseDouble(xy[1]) * f);
                    if (mc.gui.screen() != null) mc.gui.screen().mouseMoved(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
                } catch (ReflectiveOperationException e) { log("Maus nicht setzbar: " + e); }
            });
            case "mauszu" -> schritt(10, mc -> {   // mouse to the centre of a button
                AbstractWidget w = finde(mc.gui.screen(), a);
                if (w == null) { log("Knopf nicht gefunden: " + a); return; }
                double f = mc.getWindow().getGuiScale();
                try {
                    var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos"); fx.setAccessible(true);
                    var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos"); fy.setAccessible(true);
                    fx.setDouble(mc.mouseHandler, (w.getX() + w.getWidth() / 2.0) * f);
                    fy.setDouble(mc.mouseHandler, (w.getY() + w.getHeight() / 2.0) * f);
                } catch (ReflectiveOperationException e) { log("Maus nicht setzbar: " + e); }
            });
            case "gesten" -> schritt(1, mc -> { for (Zustand.Geste g : Zustand.gesten) log("Geste " + g.id() + " = " + g.an()); });
            case "einst" -> schritt(1, mc -> { Einstellungen e = Einstellungen.aktuell;
                log("Einstellungen: uebersicht=" + e.uebersicht + " selbst=" + e.selbst + " marker=" + e.marker + " namen=" + e.markerNamen
                        + " xaero=" + e.xaero + " ecke=" + e.ecke + " markerOrt=" + e.markerOrt + " groesse=" + e.groesse + " max=" + e.maxEintraege + " sort=" + e.sortierung); });
            case "menu" -> schritt(5, mc -> { if (mc.gui.screen() instanceof TeamPingScreen s) log("Menue: ichLeiter=" + Zustand.ichLeiter + " offen=" + Zustand.offen
                    + " chat=" + Zustand.chat + " pingfarbe=" + Zustand.pingfarbeId + " teamfarbe=" + Zustand.teamfarbeName + " squads=" + Zustand.squads.size()); });
            case "hover" -> schritt(5, mc -> {
                String[] xy = a.split(",");
                if (mc.gui.screen() instanceof TeamPingScreen s) s.testMaus = new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])};
            });
            case "look" -> schritt(5, mc -> { String[] w = a.split(","); mc.player.setYRot(Float.parseFloat(w[0])); mc.player.setXRot(Float.parseFloat(w[1])); });
            case "send" -> schritt(20, mc -> { TeamPingClient.senden(a); log("Gesendet " + a); });
            case "slot" -> schritt(5, mc -> { mc.player.getInventory().setSelectedSlot(Integer.parseInt(a)); log("Slot " + a + ": " + mc.player.getInventory().getSelectedItem().getHoverName().getString()); });
            case "use" -> schritt(15, mc -> { net.minecraft.client.KeyMapping.click(mc.options.keyUse.getDefaultKey()); log("Rechtsklick"); });
            case "attack" -> schritt(15, mc -> { net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getDefaultKey()); log("Linksklick"); });
            case "close" -> schritt(10, mc -> mc.gui.setScreen(null));
            case "pings" -> schritt(1, mc -> { for (Zustand.Ping p2 : Zustand.pings) log("Ping " + p2.von() + " typ=" + p2.typ() + " t=" + p2.text() + " @" + Math.round(p2.x()) + "," + Math.round(p2.z()) + " eid=" + p2.eid() + " item=" + p2.item() + " anz=" + p2.anzahl()); });
            default -> log("Unbekannter Baustein: " + t);
        }
    }

    private static void tick(Minecraft mc) {
        if (!bereit) {
            if (mc.player == null || !Zustand.serverDa()) return;
            bereit = true; wartet = 40;
            log("Verbunden, Status vom Server da");
        }
        if (--wartet > 0 || index >= schritte.size()) return;
        Schritt s = schritte.get(index++);
        try { s.was().accept(mc); } catch (Exception e) { log("Fehler: " + e); }
        wartet = Math.max(1, s.warten());
    }

    private static void klick(Screen s, double x, double y) {
        MouseButtonInfo info = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
        boolean r = s.mouseClicked(new MouseButtonEvent(x, y, info), false);
        s.mouseReleased(new MouseButtonEvent(x, y, info));
        log("mouseClicked(" + Math.round(x) + "," + Math.round(y) + ") -> " + r);
    }

    private static AbstractWidget finde(Screen s, String anfang) {
        if (s == null) return null;
        for (GuiEventListener l : s.children())
            if (l instanceof AbstractWidget w && w.getMessage().getString().startsWith(anfang)) return w;
        return null;
    }

    private static void dump(Screen s) {
        if (s == null) { log("Kein Fenster offen"); return; }
        log("Fenster " + s.getClass().getSimpleName() + (s instanceof TeamPingScreen tp ? " Reiter " + tp.reiterName() : ""));
        for (GuiEventListener l : s.children())
            if (l instanceof AbstractWidget w)
                log("  " + w.getClass().getSimpleName() + " '" + w.getMessage().getString() + "' @" + w.getX() + "," + w.getY() + " " + w.getWidth() + "x" + w.getHeight()
                        + (w.active ? "" : " (inaktiv)"));
    }

    private static void log(String t) { TeamPingClient.LOG.info("[TEST] {}", t); }
}
