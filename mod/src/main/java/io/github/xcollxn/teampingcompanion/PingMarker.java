package io.github.xcollxn.teampingcompanion;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Camera;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Ping markers on screen (like Fortnite/Warzone):
 * pings in view sit where they are; pings outside stick to the edge with an arrow.
 */
final class PingMarker {

    private PingMarker() {}

    static void zeichnen(GuiGraphicsExtractor g, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        Einstellungen e = Einstellungen.aktuell;
        if (!e.marker || mc.player == null || mc.level == null || Zustand.pings.isEmpty()) return;
        if (System.currentTimeMillis() - Zustand.zuletzt > 5000) return;
        boolean namen = e.markerNamen || mc.options.keyPlayerList.isDown();   // off = only while holding Tab
        if (e.markerOrt == Einstellungen.MarkerOrt.AM_PING) { amPing(g, tick, namen); return; }

        Font font = mc.font;
        int breite = g.guiWidth(), hoehe = g.guiHeight();
        int mitte = breite / 2;
        int y = e.markerOrt == Einstellungen.MarkerOrt.OBEN ? 8 : hoehe - 62;
        String welt = mc.level.dimension().identifier().toString();

        // horizontal field of view from the vertical FOV and the aspect ratio
        double vFov = Math.toRadians(mc.options.fov().get());
        double hFov = 2 * Math.atan(Math.tan(vFov / 2) * breite / (double) hoehe);
        double halb = Math.toDegrees(hFov / 2);
        float kameraYaw = mc.gameRenderer.mainCamera().yRot();

        // labels that would overlap move down one row (max. 3 rows)
        java.util.List<java.util.List<int[]>> belegt = java.util.List.of(new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>());
        for (Zustand.Ping p : Zustand.pings) {
            if (!p.welt().equals(welt)) continue;
            double dx = p.x() - mc.player.getX(), dy = p.y() - mc.player.getY(), dz = p.z() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double zielYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = ((zielYaw - kameraYaw) % 360 + 540) % 360 - 180;

            int x;
            String pfeil = "";
            if (Math.abs(rel) <= halb) {
                x = mitte + (int) Math.round(Math.tan(Math.toRadians(rel)) / Math.tan(hFov / 2) * (breite / 2.0));
            } else {
                x = rel < 0 ? 14 : breite - 14;
                pfeil = rel < 0 ? "◀" : "▶";
            }
            x = Math.max(14, Math.min(breite - 14, x));

            int farbe = 0xFF000000 | p.farbe();
            String symbol = switch (p.typ()) { case "GEFAHR" -> "⚠"; case "TOD" -> "☠"; case "SAMMELN" -> "⚑"; default -> "◆"; };
            int symbolFarbe = "GEFAHR".equals(p.typ()) ? 0xFFFF4040 : farbe;
            String hoehenHinweis = dy > 12 ? "▲" : dy < -12 ? "▼" : "";

            String oben = pfeil.isEmpty() ? symbol : (rel < 0 ? pfeil + symbol : symbol + pfeil);
            g.centeredText(font, oben, x, y, symbolFarbe);
            String meter = Math.round(dist) + "m" + hoehenHinweis;
            String text = Zustand.text(p);
            String zeile = p.von() + ": " + text;
            if (zeile.length() > 30) zeile = zeile.substring(0, 29) + "…";
            {
                int halbBreite = Math.max(font.width(meter) / 2, namen ? Math.round(font.width(zeile) * 0.75f / 2) : 0) + 3, reihe = -1;
                for (int r = 0; r < belegt.size() && reihe < 0; r++) {
                    boolean frei = true;
                    for (int[] iv : belegt.get(r)) if (x - halbBreite < iv[1] && x + halbBreite > iv[0]) { frei = false; break; }
                    if (frei) reihe = r;
                }
                if (reihe < 0) continue;
                belegt.get(reihe).add(new int[]{x - halbBreite, x + halbBreite});
                int dy2 = reihe * (namen ? 18 : 10);
                g.centeredText(font, meter, x, y + 10 + dy2, 0xFFDDDDDD);
                if (!namen) continue;
                g.pose().pushMatrix();
                g.pose().translate(x, y + 20 + dy2);
                g.pose().scale(0.75f, 0.75f);
                g.centeredText(font, zeile, 0, 0, farbe);
                g.pose().popMatrix();
            }
        }
    }

    // ------------------------------------------------------------------ mode "at ping" (like Ping Wheel)
    private static final String[] PFEILE = {"→", "↘", "↓", "↙", "←", "↖", "↑", "↗"};

    /**
     * Each ping sits on screen right above its target (visible through walls): symbol or item icon,
     * the distance above, the name below (always or only with Tab). Outside the view (also behind you)
     * it sticks to the screen edge with an arrow. Tracked targets (players, animals, items) are followed every frame.
     */
    private static void amPing(GuiGraphicsExtractor g, DeltaTracker tick, boolean namen) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int breite = g.guiWidth(), hoehe = g.guiHeight();
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 kp = cam.position();
        Matrix4f matrix = cam.getViewRotationProjectionMatrix(new Matrix4f());
        // Prefer the camera from this frame's world rendering – it includes the view bobbing while walking
        if (FernStrahl.kameraMatrix != null && System.currentTimeMillis() - FernStrahl.kameraZeit < 200) {
            matrix = new Matrix4f(FernStrahl.kameraMatrix);
            kp = FernStrahl.kameraPos;
        }
        float pt = tick.getGameTimeDeltaPartialTick(true);
        String welt = mc.level.dimension().identifier().toString();
        // safe zone at the edges (room for hotbar and hearts at the bottom)
        float links = 16, rechts = breite - 16, oben = 16, unten = hoehe - 56;
        float mx = breite / 2f, my = hoehe / 2f;

        java.util.List<Object[]> liste = new java.util.ArrayList<>();   // {ping, x, y, z, item}
        for (Zustand.Ping p : Zustand.pings) {
            if (!p.welt().equals(welt)) continue;
            double x = p.x(), y = p.y() + 0.6, z = p.z();
            ItemStack bild = null;
            if (p.eid() >= 0) {
                Entity ent = mc.level.getEntity(p.eid());
                if (ent != null) {
                    Vec3 v = ent.getPosition(pt);
                    x = v.x; y = v.y + ent.getBbHeight() + 0.35; z = v.z;
                    if (ent instanceof ItemEntity ie) bild = ie.getItem();
                } else y = p.y() + p.hoehe() + 0.35;
            }
            if (bild == null && p.item() != null) {
                try { bild = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(p.item())), Math.max(1, p.anzahl())); }
                catch (Exception ignoriert) { }
            }
            liste.add(new Object[]{p, x, y, z, bild});
        }
        // draw far ones first, near ones end up on top
        final Vec3 kpf = kp;
        liste.sort((a, b) -> Double.compare(abstand(b, kpf), abstand(a, kpf)));

        for (Object[] o : liste) {
            Zustand.Ping p = (Zustand.Ping) o[0];
            double x = (double) o[1], y = (double) o[2], z = (double) o[3];
            ItemStack bild = (ItemStack) o[4];
            double dist = Math.sqrt(abstand(o, kp));
            Vector4f v = new Vector4f((float) (x - kp.x), (float) (y - kp.y), (float) (z - kp.z), 1f);
            matrix.transform(v);
            boolean hinten = v.w <= 0.05f;
            float sx = 0, sy = 0;
            if (!hinten) { sx = (v.x / v.w + 1f) / 2f * breite; sy = (1f - v.y / v.w) / 2f * hoehe; }
            boolean drin = !hinten && sx >= links && sx <= rechts && sy >= oben && sy <= unten;
            String pfeil = null;
            if (!drin) {
                // direction from the screen centre; don't mirror behind the camera (|w| instead of w)
                float w = Math.max(Math.abs(v.w), 0.0001f);
                float dx = v.x / w, dy = -v.y / w;
                if (Math.abs(dx) < 1e-4 && Math.abs(dy) < 1e-4) dy = 1;
                double t = Double.MAX_VALUE;
                if (dx > 0) t = Math.min(t, (rechts - mx) / dx); else if (dx < 0) t = Math.min(t, (links - mx) / dx);
                if (dy > 0) t = Math.min(t, (unten - my) / dy); else if (dy < 0) t = Math.min(t, (oben - my) / dy);
                sx = (float) (mx + dx * t); sy = (float) (my + dy * t);
                int idx = (int) Math.round(Math.atan2(dy, dx) / (Math.PI / 4));
                pfeil = PFEILE[((idx % 8) + 8) % 8];
            }
            int farbe = 0xFF000000 | p.farbe();
            int symbolFarbe = "GEFAHR".equals(p.typ()) ? 0xFFFF4040 : farbe;
            int ix = Math.round(sx), iy = Math.round(sy);
            // a bit larger when near, constant from ~10 m
            float gr = (float) Math.max(1.0, Math.min(1.5, 2.0 / Math.pow(Math.max(dist, 1), 0.3)));
            g.pose().pushMatrix();
            g.pose().translate(ix, iy);
            g.pose().scale(gr, gr);
            if (bild != null) g.item(bild, -8, -8);
            else {
                String symbol = switch (p.typ()) { case "GEFAHR" -> "⚠"; case "TOD" -> "☠"; case "SAMMELN" -> "⚑"; default -> "◆"; };
                g.centeredText(font, symbol, 0, -4, symbolFarbe);
            }
            // distance above the symbol, with a dark box behind it
            String meter = Math.round(dist) + "m";
            if (pfeil != null) meter = pfeil + " " + meter;
            int mw = font.width(meter);
            int ty = -18;
            g.fill(-mw / 2 - 2, ty - 2, mw / 2 + 2, ty + 9, 0x60000000);
            g.centeredText(font, meter, 0, ty, 0xFFFFFFFF);
            if (namen) {
                String zeile = p.von() + ": " + Zustand.text(p);
                if (bild != null && p.anzahl() > 1) zeile += " ×" + p.anzahl();
                if (zeile.length() > 30) zeile = zeile.substring(0, 29) + "…";
                g.pose().pushMatrix();
                g.pose().translate(0, 10);
                g.pose().scale(0.75f, 0.75f);
                int zw = font.width(zeile);
                g.fill(-zw / 2 - 2, -2, zw / 2 + 2, 9, 0x60000000);
                g.centeredText(font, zeile, 0, 0, farbe);
                g.pose().popMatrix();
            }
            g.pose().popMatrix();
        }
    }

    private static double abstand(Object[] o, Vec3 kp) {
        double dx = (double) o[1] - kp.x, dy = (double) o[2] - kp.y, dz = (double) o[3] - kp.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
