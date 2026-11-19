package io.github.xcollxn.teampingcompanion;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ping key of the mod: the server itself only aims up to ~250 blocks. If the target is further away, the mod sends the
 * block's coordinates ("fern"). If it is beyond render distance and Distant Horizons is installed and enabled, the spot
 * is searched in the DH terrain data (like Ping Wheel). Without DH nothing changes.
 */
final class FernPing {

    private FernPing() {}

    /** Up to here the server aims itself (plugin setting "range", default 250) – with some margin. */
    private static final double SERVER_REICHWEITE = 240;

    static boolean dhInstalliert() { return FabricLoader.getInstance().isModLoaded("distanthorizons"); }

    static void pingen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double sicht = Math.max(64, mc.options.getEffectiveRenderDistance() * 16.0);
        HitResult hit = mc.player.pick(sicht, 1f, false);
        double weite = hit == null ? 0 : hit.getLocation().distanceTo(mc.player.getEyePosition(1f));
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && weite > SERVER_REICHWEITE) {
            fernSenden(b.getBlockPos().getX(), b.getBlockPos().getY(), b.getBlockPos().getZ());   // loaded, but too far for the server
            return;
        }
        if ((hit == null || hit.getType() == HitResult.Type.MISS) && Einstellungen.aktuell.distantHorizons && dhInstalliert()) {
            Vec3 auge = mc.player.getEyePosition(1f), blick = mc.player.getViewVector(1f);
            DhStrahl.suchen(auge, blick, pos -> mc.execute(() -> fernSenden(pos[0], pos[1], pos[2])),
                    () -> mc.execute(() -> TeamPingClient.senden("{\"a\":\"ping\"}")));   // nothing found → the server reports "nothing"
            return;
        }
        TeamPingClient.senden("{\"a\":\"ping\"}");
    }

    private static void fernSenden(int x, int y, int z) {
        TeamPingClient.senden("{\"a\":\"fern\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}");
    }

    /** Separate class: only loaded when DH is installed (otherwise the DH classes are missing). */
    static final class DhStrahl {
        private static com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache cache;
        private static long cacheZeit;

        static void suchen(Vec3 auge, Vec3 blick, java.util.function.Consumer<int[]> gefunden, Runnable nichts) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    var welt = com.seibel.distanthorizons.api.DhApi.Delayed.worldProxy;
                    var repo = com.seibel.distanthorizons.api.DhApi.Delayed.terrainRepo;
                    if (welt == null || repo == null) { nichts.run(); return; }
                    var level = welt.getSinglePlayerLevel();
                    if (level == null) { nichts.run(); return; }
                    if (cache == null || System.currentTimeMillis() - cacheZeit > 10_000) { cache = repo.createSoftCache(); cacheZeit = System.currentTimeMillis(); }
                    var r = repo.raycast(level, auge.x, auge.y, auge.z, (float) blick.x, (float) blick.y, (float) blick.z, 4096, cache);
                    if (r == null || !r.success || r.payload == null || r.payload.pos == null) { nichts.run(); return; }
                    gefunden.accept(new int[]{r.payload.pos.x, r.payload.pos.y, r.payload.pos.z});
                } catch (Throwable t) {
                    TeamPingClient.LOG.warn("Distant Horizons ping failed: {}", t.toString());
                    nichts.run();
                }
            });
        }
    }
}
