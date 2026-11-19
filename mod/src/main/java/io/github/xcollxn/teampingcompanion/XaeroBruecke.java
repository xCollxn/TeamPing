package io.github.xcollxn.teampingcompanion;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

import java.util.*;

/**
 * Shows squad pings as waypoints on Xaero's Minimap and World Map (via Xaero's third-party waypoint API).
 * Only loaded when Xaero's Minimap is installed.
 */
final class XaeroBruecke {

    private static final Identifier HERKUNFT = Identifier.fromNamespaceAndPath("teamping", "pings");
    private static final Set<String> benutzteWelten = new HashSet<>();
    private static String letzterStand = "";
    private static boolean kaputt;

    private XaeroBruecke() {}

    static void abgleichen(List<Zustand.Ping> pings) {
        if (kaputt) return;
        try {
            String stand = pings.stream().map(p -> p.id() + p.typ() + (int) p.x() + "," + (int) p.y() + "," + (int) p.z()).sorted().reduce("", String::concat);
            if (stand.equals(letzterStand)) return;
            letzterStand = stand;

            MinimapSession s = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (s == null) return;
            MinimapWorldRootContainer root = s.getWorldManager().getAutoRootContainer();
            if (root == null) return;

            Map<String, List<Zustand.Ping>> nachWelt = new HashMap<>();
            for (Zustand.Ping p : pings) nachWelt.computeIfAbsent(p.welt(), k -> new ArrayList<>()).add(p);
            Set<String> welten = new HashSet<>(benutzteWelten);
            welten.addAll(nachWelt.keySet());

            for (String welt : welten) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(welt));
                String ordner = s.getDimensionHelper().getDimensionDirectoryName(key);
                MinimapWorldContainer c = root.addSubContainer(root.getPath().resolve(ordner));
                ThirdPartyWaypoints wp = c.getThirdPartyWaypointManager().get(HERKUNFT);
                if (!wp.hasEnabledStateGetter()) wp.setEnabledStateGetter(() -> true);
                wp.clear();
                for (Zustand.Ping p : nachWelt.getOrDefault(welt, List.of())) {
                    String text = Zustand.text(p);
                    String name = switch (p.typ()) {
                        case "GEFAHR" -> "⚠ " + p.von() + ": " + text;
                        case "SAMMELN" -> p.von() + ": " + text;
                        case "TOD" -> "☠ " + text;
                        default -> p.von() + ": " + text;
                    };
                    String symbol = switch (p.typ()) {
                        case "GEFAHR" -> "!"; case "SAMMELN" -> "H"; case "TOD" -> "X";
                        default -> p.von().isEmpty() ? "P" : p.von().substring(0, 1).toUpperCase(Locale.ROOT);
                    };
                    WaypointPurpose zweck = "TOD".equals(p.typ()) ? WaypointPurpose.DEATH : WaypointPurpose.NORMAL;
                    wp.add(p.id(), new Waypoint((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()),
                            name, symbol, naechsteFarbe(p.farbe()), zweck, true));
                }
            }
            benutzteWelten.clear();
            benutzteWelten.addAll(nachWelt.keySet());
        } catch (Throwable t) {
            kaputt = true;   // different Xaero version or similar: turn off the map display, everything else keeps running
            TeamPingClient.LOG.warn("Xaero integration disabled: {}", t.toString());
        }
    }

    static void leeren() {
        if (kaputt) return;
        letzterStand = "";
        abgleichen(List.of());
    }

    private static WaypointColor naechsteFarbe(int rgb) {
        WaypointColor beste = WaypointColor.WHITE;
        double bester = Double.MAX_VALUE;
        for (WaypointColor c : WaypointColor.values()) {
            int h = c.getHex();
            double d = Math.pow(((h >> 16) & 255) - ((rgb >> 16) & 255), 2) + Math.pow(((h >> 8) & 255) - ((rgb >> 8) & 255), 2)
                    + Math.pow((h & 255) - (rgb & 255), 2);
            if (d < bester) { bester = d; beste = c; }
        }
        return beste;
    }
}
