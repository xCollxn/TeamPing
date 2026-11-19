package io.github.xcollxn.teampingcompanion;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;

/**
 * Squad overview – simple, no box and no bars: one row per member
 * "head  name   ❤18   45m ↗" with spacing between rows and enough room to the bottom edge (offhand/hotbar).
 */
final class SquadHud {

    private static final String[] PFEILE = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int ZEILE = 17, KOPF = 12, RAND_X = 6, RAND_OBEN = 6, RAND_UNTEN = 34;

    private SquadHud() {}

    static void zeichnen(GuiGraphicsExtractor g, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        Einstellungen e = Einstellungen.aktuell;
        if (!e.uebersicht || mc.player == null || mc.level == null || Zustand.squad == null) return;
        boolean hotbar = e.ecke == Einstellungen.Ecke.HOTBAR;
        boolean unten = hotbar || e.ecke == Einstellungen.Ecke.UNTEN_LINKS || e.ecke == Einstellungen.Ecke.UNTEN_RECHTS;
        boolean rechts = e.ecke == Einstellungen.Ecke.OBEN_RECHTS || e.ecke == Einstellungen.Ecke.UNTEN_RECHTS;
        if (e.ecke == Einstellungen.Ecke.UNTEN_LINKS && mc.gui.screen() instanceof ChatScreen) return; // otherwise the chat covers it
        if (!Zustand.statusDa()) return;

        List<Zustand.Mitglied> online = Zustand.mitglieder.stream().filter(Zustand.Mitglied::online)
                .filter(m -> e.selbst || Zustand.ich == null || !Zustand.ich.equals(m.uuid())).toList();
        List<Zustand.Mitglied> alle = auswahl(mc, online, e);
        int weitere = online.size() - alle.size();
        Font font = mc.font;
        float s = (float) e.groesse;
        int bildB = Math.round(g.guiWidth() / s), bildH = Math.round(g.guiHeight() / s);
        int zeilen = Math.max(1, alle.size()) + (weitere > 0 ? 1 : 0);
        int hoehe = 12 + zeilen * ZEILE;
        // Next to the hotbar: right of the hotbar (182 px wide, centred), at the bottom
        int linksX = hotbar ? bildB / 2 + 91 + 10 : RAND_X;
        int y = hotbar ? bildH - 4 - hoehe : unten ? bildH - RAND_UNTEN - hoehe : RAND_OBEN;

        g.pose().pushMatrix();
        g.pose().scale(s, s);
        String titel = "◆ " + Zustand.squad;
        g.text(font, titel, rechts ? bildB - RAND_X - font.width(titel) : linksX, y, 0xFF000000 | Zustand.teamfarbe, true);
        y += 13;

        if (alle.isEmpty()) {
            String t = TeamPingScreen.t("hud.allein");
            g.text(font, t, rechts ? bildB - RAND_X - font.width(t) : linksX, y + 2, 0xFFAAAAAA, true);
            g.pose().popMatrix();
            return;
        }
        String meineWelt = mc.level.dimension().identifier().toString();
        for (Zustand.Mitglied m : alle) {
            String name = (m.name().equals(Zustand.leiter) ? "★ " : "") + m.name();
            String info;
            int infoFarbe = 0xFFBBBBBB;
            String herz = "";
            if (!m.online()) info = TeamPingScreen.t("gui.offline");
            else {
                herz = "❤" + Math.round(m.hp());
                if (Zustand.ich != null && Zustand.ich.equals(m.uuid())) info = TeamPingScreen.t("gui.du");
                else if (!m.welt().equals(meineWelt)) info = m.welt().contains("nether") ? "Nether" : m.welt().contains("end") ? "End" : "?";
                else {
                    double dx = m.x() - mc.player.getX(), dz = m.z() - mc.player.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(m.y() - mc.player.getY(), 2));
                    double rel = ((Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot()) % 360 + 540) % 360 - 180;
                    int idx = (int) Math.round(rel / 45.0);
                    info = Math.round(dist) + "m " + PFEILE[((idx % 8) + 8) % 8];
                }
            }
            int nameFarbe = m.online() ? (0xFF000000 | m.farbe()) : 0xFF777777;
            boolean wenig = m.online() && m.max() > 0 && m.hp() / m.max() <= 0.3f;
            int breite = KOPF + 5 + font.width(name) + (herz.isEmpty() ? 0 : 8 + font.width(herz)) + 8 + font.width(info);
            int x = rechts ? bildB - RAND_X - breite : linksX;

            PlayerFaceExtractor.extractRenderState(g, skin(mc, m), x, y, KOPF);
            if (!m.online()) g.fill(x, y, x + KOPF, y + KOPF, 0x99000000);
            int tx = x + KOPF + 5, ty = y + 2;
            g.text(font, name, tx, ty, nameFarbe, true);
            tx += font.width(name) + 8;
            if (!herz.isEmpty()) {
                g.text(font, herz, tx, ty, wenig ? 0xFFFF5555 : 0xFFFF7777, true);
                tx += font.width(herz) + 8;
            }
            g.text(font, info, tx, ty, infoFarbe, true);
            y += ZEILE;
        }
        if (weitere > 0) {
            String t = TeamPingScreen.t("hud.weitere", weitere);
            g.text(font, t, rechts ? bildB - RAND_X - font.width(t) : linksX + KOPF + 5, y + 2, 0xFF999999, true);
        }
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ selection & order
    /** Current order (names); re-sorted at most once per second and only on a clear difference. */
    private static final List<String> reihenfolge = new java.util.ArrayList<>();
    private static long letzteSortierung;
    private static final double TAUSCH_ABSTAND = 10;   // two rows only swap places from a 10 m difference

    /**
     * Who is in the overview: yourself first, then members with low health (<= 30 %), then the nearest
     * (other dimension at the very end). At most e.maxEintraege rows.
     */
    private static List<Zustand.Mitglied> auswahl(Minecraft mc, List<Zustand.Mitglied> online, Einstellungen e) {
        int max = Math.max(1, e.maxEintraege);
        if (e.sortierung == Einstellungen.Sortierung.FEST) return online.subList(0, Math.min(max, online.size()));
        java.util.Map<String, Zustand.Mitglied> nachName = new java.util.LinkedHashMap<>();
        for (Zustand.Mitglied m : online) nachName.put(m.name(), m);
        reihenfolge.removeIf(n -> !nachName.containsKey(n));
        for (String n : nachName.keySet()) if (!reihenfolge.contains(n)) reihenfolge.add(n);
        long jetzt = System.currentTimeMillis();
        if (jetzt - letzteSortierung >= 1000) {
            letzteSortierung = jetzt;
            // Single neighbour swaps instead of a full re-sort → the list "moves" calmly instead of jumping
            for (int runde = 0; runde < reihenfolge.size(); runde++) {
                boolean getauscht = false;
                for (int i = 0; i + 1 < reihenfolge.size(); i++) {
                    Zustand.Mitglied a = nachName.get(reihenfolge.get(i)), b = nachName.get(reihenfolge.get(i + 1));
                    if (vorher(mc, b, a)) { java.util.Collections.swap(reihenfolge, i, i + 1); getauscht = true; }
                }
                if (!getauscht) break;
            }
        }
        List<Zustand.Mitglied> r = new java.util.ArrayList<>();
        for (String n : reihenfolge) { if (r.size() >= max) break; r.add(nachName.get(n)); }
        return r;
    }

    /** Does b clearly belong before a? */
    private static boolean vorher(Minecraft mc, Zustand.Mitglied b, Zustand.Mitglied a) {
        int ka = klasse(a), kb = klasse(b);
        if (kb != ka) return kb < ka;
        return abstand(mc, b) + TAUSCH_ABSTAND < abstand(mc, a);
    }

    private static int klasse(Zustand.Mitglied m) {
        if (Zustand.ich != null && Zustand.ich.equals(m.uuid())) return 0;              // me
        if (m.max() > 0 && m.hp() / m.max() <= 0.3f) return 1;                          // low health
        return 2;
    }

    private static double abstand(Minecraft mc, Zustand.Mitglied m) {
        if (mc.player == null || mc.level == null || !m.welt().equals(mc.level.dimension().identifier().toString())) return 1e9;
        double dx = m.x() - mc.player.getX(), dy = m.y() - mc.player.getY(), dz = m.z() - mc.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Distance with direction arrow ("45 m ↗"), dimension name, or null if it can't be computed. */
    static String entfernung(Minecraft mc, Zustand.Mitglied m, boolean leerzeichen) {
        if (mc.player == null || mc.level == null || !m.online()) return null;
        if (!m.welt().equals(mc.level.dimension().identifier().toString()))
            return m.welt().contains("nether") ? "Nether" : m.welt().contains("end") ? "End" : "?";
        double dx = m.x() - mc.player.getX(), dz = m.z() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(m.y() - mc.player.getY(), 2));
        double rel = ((Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot()) % 360 + 540) % 360 - 180;
        int idx = (int) Math.round(rel / 45.0);
        return Math.round(dist) + (leerzeichen ? " m " : "m ") + PFEILE[((idx % 8) + 8) % 8];
    }

    static PlayerSkin skin(Minecraft mc, Zustand.Mitglied m) {
        if (m.uuid() != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(m.uuid());
            if (info != null) return info.getSkin();
            return DefaultPlayerSkin.get(m.uuid());
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }
}
