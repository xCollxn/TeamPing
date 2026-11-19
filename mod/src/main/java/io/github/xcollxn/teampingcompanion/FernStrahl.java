package io.github.xcollxn.teampingcompanion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Beam for far away pings – drawn by the mod itself, because the server can only send display entities up to ~110–160 blocks.
 * Same style as the server's light band (dark rim, colour, bright core), turned towards the camera and wider with
 * distance. If the ping is beyond render distance, it is pulled closer along the line of sight and scaled down accordingly –
 * so it appears exactly where the ping is.
 */
final class FernStrahl {

    private FernStrahl() {}

    private static final int VOLL_HELL = 0xF000F0;

    /** Real camera of this frame (projection incl. view bobbing) – for the screen markers, so they don't drift. */
    static volatile org.joml.Matrix4f kameraMatrix;
    static volatile Vec3 kameraPos;
    static volatile long kameraZeit;

    static void kameraMerken(LevelRenderContext ctx) {
        var cam = ctx.levelState().cameraRenderState;
        if (cam == null || cam.projectionMatrix == null || cam.viewRotationMatrix == null || cam.pos == null) return;
        kameraMatrix = new org.joml.Matrix4f(cam.projectionMatrix).mul(cam.viewRotationMatrix);
        kameraPos = cam.pos;
        kameraZeit = System.currentTimeMillis();
    }

    static void zeichnen(LevelRenderContext ctx) {
        kameraMerken(ctx);
        Minecraft mc = Minecraft.getInstance();
        Einstellungen e = Einstellungen.aktuell;
        if (mc.player == null || mc.level == null || Zustand.pings.isEmpty() || e.strahlArt == Einstellungen.StrahlArt.PUNKT) return;
        if (System.currentTimeMillis() - Zustand.zuletzt > 5000) return;
        Vec3 kp = mc.gameRenderer.mainCamera().position();
        String welt = mc.level.dimension().identifier().toString();
        // Further away the beam is pulled closer along the line of sight and scaled down (looks the same) – otherwise the fog swallows it
        double grenze = 48;
        RenderType art = RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true);
        PoseStack ps = ctx.poseStack();

        for (Zustand.Ping p : Zustand.pings) {
            if (!p.welt().equals(welt)) continue;
            double x = p.x(), y = p.y(), z = p.z();
            if (p.eid() >= 0) {
                Entity ent = mc.level.getEntity(p.eid());
                if (ent != null) { Vec3 v = ent.position(); x = v.x; y = v.y; z = v.z; }
            }
            double dx = x - kp.x, dy = y - kp.y, dz = z - kp.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < Zustand.nah - 2) continue;   // this close the server draws the beam
            double s = dist > grenze ? grenze / dist : 1.0;
            // taller from afar so it doesn't become a stub on screen (at least a third of the distance)
            float hoehe = (float) (Math.max("TOD".equals(p.typ()) ? 40 : 80, dist * 0.35) * s * e.strahlHoehe / 100.0);
            float breite = (float) (0.45 * Math.max(1, dist / 30) * s * e.strahlBreite / 100.0);
            float hell = e.strahlHell / 100f;
            float bx = (float) (dx * s), by = (float) (dy * s), bz = (float) (dz * s);
            double waag = Math.sqrt(bx * bx + bz * bz);
            if (waag < 0.01) continue;
            float rx = (float) (-bz / waag), rz = (float) (bx / waag);   // horizontal, across the view direction
            int farbe = p.farbe() & 0xFFFFFF;
            ctx.submitNodeCollector().submitCustomGeometry(ps, art, (pose, vc) -> {
                band(pose, vc, bx, by, bz, rx, rz, breite * 1.9f / 2, hoehe, alpha(0x55, hell));                       // dark rim
                band(pose, vc, bx, by, bz, rx, rz, breite * 1.5f / 2, hoehe, alpha(0x99, hell) | farbe);               // glow
                band(pose, vc, bx, by, bz, rx, rz, breite * 0.5f / 2, hoehe, alpha(0xFF, hell) | aufhellen(farbe));    // core
            });
        }
    }

    /** A vertical band (two-sided), bottom centre at (x, y, z). */
    private static void band(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float rx, float rz, float halb, float h, int argb) {
        float x1 = x - rx * halb, z1 = z - rz * halb, x2 = x + rx * halb, z2 = z + rz * halb;
        ecke(pose, vc, x1, y, z1, argb, 0, h); ecke(pose, vc, x1, y + h, z1, argb, 0, 0);
        ecke(pose, vc, x2, y + h, z2, argb, 1, 0); ecke(pose, vc, x2, y, z2, argb, 1, h);
        ecke(pose, vc, x2, y, z2, argb, 1, h); ecke(pose, vc, x2, y + h, z2, argb, 1, 0);
        ecke(pose, vc, x1, y + h, z1, argb, 0, 0); ecke(pose, vc, x1, y, z1, argb, 0, h);
    }

    private static void ecke(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, int argb, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(VOLL_HELL).setNormal(pose, 0, 1, 0);
    }

    private static int alpha(int a, float hell) { return Math.round(a * hell) << 24; }

    private static int aufhellen(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + (255 - r) * 2 / 5) << 16) | ((g + (255 - g) * 2 / 5) << 8) | (b + (255 - b) * 2 / 5);
    }
}
