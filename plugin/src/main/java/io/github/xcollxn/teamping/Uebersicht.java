package io.github.xcollxn.teamping;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Squad overview.
 * - Players WITH the companion mod: get the squad status as JSON every 0.5 s (the mod draws the HUD, markers, far beams).
 * - Players WITHOUT the mod (Java): boss bars at the top – one per member with health, distance and direction; Bedrock: box or bars.
 */
final class Uebersicht {

    static final String KANAL_STATUS = "teamping:status";
    static final String KANAL_AKTION = "teamping:action";

    private final TeamPing pl;
    private final Map<UUID, Map<UUID, BossBar>> leisten = new HashMap<>();
    final Set<UUID> modNutzer = new HashSet<>();
    private final Set<UUID> menuOeffnen = new HashSet<>();

    /** Mod users: let the mod open its own squad window (instead of the chest menu). */
    void menuOeffnen(Player p) { menuOeffnen.add(p.getUniqueId()); statusSenden(p, pl.squads().squadVon(p)); }

    Uebersicht(TeamPing pl) { this.pl = pl; }

    void tick() {
        Squads sq = pl.squads();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team t = sq.squadVon(p);
            if (modNutzer.contains(p.getUniqueId())) {
                leistenEntfernen(p);
                statusSenden(p, t);
                continue;
            }
            boolean bedrockKasten = BedrockCheck.aktiv(p) && sq.hudPos(p) != Squads.HudPos.LEISTEN;
            if (t == null || !sq.uebersichtAn(p) || bedrockKasten) {
                leistenEntfernen(p);
                if (BedrockCheck.aktiv(p)) kastenSenden(p, t != null && sq.uebersichtAn(p) && bedrockKasten ? t : null);
                continue;
            }
            kastenSenden(p, null);
            Map<UUID, BossBar> meine = leisten.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
            Set<UUID> noch = new HashSet<>();
            for (Player m : sq.onlineMitglieder(t)) {
                if (m.equals(p)) continue;
                noch.add(m.getUniqueId());
                Squads.PingFarbe f = sq.pingFarbe(m);
                double max = Optional.ofNullable(m.getAttribute(Attribute.MAX_HEALTH)).map(a -> a.getValue()).orElse(20.0);
                float anteil = (float) Math.max(0, Math.min(1, m.getHealth() / max));
                Component titel = Component.text(m.getName(), f.text())
                        .append(Component.text("   " + (BedrockCheck.aktiv(p) ? TeamPing.HERZ_BEDROCK : "❤ ") + Math.round(m.getHealth()), m.getHealth() <= 6 ? NamedTextColor.RED : NamedTextColor.WHITE))
                        .append(Component.text("   " + entfernung(p, m.getLocation()), NamedTextColor.GRAY));
                BossBar b = meine.get(m.getUniqueId());
                if (b == null) {
                    b = BossBar.bossBar(titel, anteil, leistenFarbe(f), BossBar.Overlay.NOTCHED_10);
                    meine.put(m.getUniqueId(), b);
                    p.showBossBar(b);
                } else {
                    b.name(titel); b.progress(anteil); b.color(leistenFarbe(f));
                }
            }
            for (UUID id : new ArrayList<>(meine.keySet()))
                if (!noch.contains(id)) p.hideBossBar(meine.remove(id));
        }
    }

    // ------------------------------------------------------------------ Bedrock box (title with marker → resource pack draws it)
    /** Marker + position: the pack hides the normal title and shows the text as a box in the corner. */
    static final String HUD_MARKE = "§h§u";
    private final Map<UUID, String> kastenZuletzt = new HashMap<>();
    private final Map<UUID, Integer> kastenWann = new HashMap<>();

    private static String kennung(Squads.HudPos pos) {
        return HUD_MARKE + switch (pos) { case RECHTS -> "§1"; case LINKS -> "§2"; case OBEN_RECHTS -> "§3"; case LEISTEN -> "§0"; };
    }

    /** t == null: hide the box. Only sends on change or every 2 s (so the title doesn't expire). */
    private void kastenSenden(Player p, Team t) {
        Squads sq = pl.squads();
        String text;
        if (t == null) {
            if (!kastenZuletzt.containsKey(p.getUniqueId())) return;   // was never on
            text = HUD_MARKE + "§0";
        } else {
            StringBuilder b = new StringBuilder(kennung(sq.hudPos(p)));
            b.append(BedrockMenus.farbcode(Squads.farbe(t))).append("§l").append(Squads.name(t)).append("§r");
            List<Player> andere = new ArrayList<>();
            for (Player m : sq.onlineMitglieder(t)) if (!m.equals(p)) andere.add(m);
            andere.sort(Comparator.comparingDouble(m -> m.getWorld().equals(p.getWorld()) ? m.getLocation().distanceSquared(p.getLocation()) : Double.MAX_VALUE));
            int max = 6;
            for (int i = 0; i < Math.min(max, andere.size()); i++) {
                Player m = andere.get(i);
                Squads.PingFarbe f = sq.pingFarbe(m);
                long hp = Math.round(m.getHealth());
                b.append("\n").append(BedrockMenus.farbcode(f)).append(m.getName())
                        .append("  ").append(hp <= 6 ? "§c" : "§f").append(TeamPing.HERZ_BEDROCK).append(hp)
                        .append("  §7").append(entfernung(p, m.getLocation()));
            }
            if (andere.size() > max) b.append("\n§7+").append(andere.size() - max);
            if (andere.isEmpty()) b.append("\n§7").append(Texte.s(p, "bedrock.hud.allein"));
            text = b.toString();
        }
        int jetzt = Bukkit.getCurrentTick();
        String alt = kastenZuletzt.get(p.getUniqueId());
        if (text.equals(alt) && jetzt - kastenWann.getOrDefault(p.getUniqueId(), 0) < 40) return;
        kastenZuletzt.put(p.getUniqueId(), text);
        kastenWann.put(p.getUniqueId(), jetzt);
        if (t == null) kastenZuletzt.remove(p.getUniqueId());
        p.showTitle(net.kyori.adventure.title.Title.title(Component.text(text), Component.empty(),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(4), java.time.Duration.ZERO)));
    }

    void leistenEntfernen(Player p) {
        Map<UUID, BossBar> meine = leisten.remove(p.getUniqueId());
        if (meine != null) meine.values().forEach(p::hideBossBar);
    }

    void abmelden(Player p) {
        leisten.remove(p.getUniqueId());
        kastenZuletzt.remove(p.getUniqueId()); kastenWann.remove(p.getUniqueId());
        modNutzer.remove(p.getUniqueId());
    }

    private static String entfernung(Player von, Location ziel) {
        if (!von.getWorld().equals(ziel.getWorld())) return weltName(ziel.getWorld());
        return Math.round(von.getLocation().distance(ziel)) + " m " + TeamPing.pfeil(von, ziel);
    }

    static String weltName(World w) {
        return switch (w.getEnvironment()) { case NETHER -> "im Nether"; case THE_END -> "im End"; default -> "andere Welt"; };
    }

    private static BossBar.Color leistenFarbe(Squads.PingFarbe f) {
        return switch (f) {
            case ROT -> BossBar.Color.RED;
            case ORANGE, GELB -> BossBar.Color.YELLOW;
            case GRUEN -> BossBar.Color.GREEN;
            case TUERKIS, HELLBLAU, BLAU -> BossBar.Color.BLUE;
            case LILA -> BossBar.Color.PURPLE;
            case PINK -> BossBar.Color.PINK;
            case WEISS -> BossBar.Color.WHITE;
        };
    }

    // ------------------------------------------------------------------ companion mod
    private JsonObject menuDaten(Player p, Team t) {
        Squads sq = pl.squads();
        JsonObject m = new JsonObject();
        m.addProperty("ichLeiter", t != null && sq.istLeiter(p, t));
        m.addProperty("offen", t != null && sq.offen(t));
        if (t != null) m.addProperty("teamfarbeName", Squads.farbName(Squads.farbe(t)));
        Squads.PingFarbe f = sq.pingFarbe(p);
        m.addProperty("pingfarbe", f.anzeige);
        m.addProperty("pingfarbeId", f.name());
        m.addProperty("pingrgb", f.rgb);
        m.addProperty("chat", pl.squadChatAn(p));
        m.addProperty("uebersicht", sq.uebersichtAn(p));
        JsonArray squads = new JsonArray();
        for (Team s : sq.alle()) {
            JsonObject j = new JsonObject();
            j.addProperty("n", Squads.name(s));
            j.addProperty("f", Squads.farbe(s).value());
            j.addProperty("offen", sq.offen(s));
            j.addProperty("anzahl", s.getEntries().size());
            j.addProperty("leiter", sq.leiterName(s));
            j.addProperty("eingeladen", sq.hatEinladung(p, Squads.name(s)));
            JsonArray mit = new JsonArray();       // for the info button in the squad list
            for (String name : sq.mitglieder(s)) {
                JsonObject mj = new JsonObject();
                mj.addProperty("n", name);
                mj.addProperty("on", Bukkit.getPlayerExact(name) != null);
                mj.addProperty("l", name.equals(sq.leiterName(s)));
                mit.add(mj);
            }
            j.add("m", mit);
            squads.add(j);
        }
        m.add("squads", squads);
        JsonArray einl = new JsonArray();
        for (Team s : sq.einladungenVon(p)) einl.add(Squads.name(s));
        m.add("einladungen", einl);
        JsonArray spieler = new JsonArray();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || (t != null && t.equals(sq.squadVon(o)))) continue;
            JsonObject j = new JsonObject();
            j.addProperty("n", o.getName());
            j.addProperty("u", o.getUniqueId().toString());
            Team ot = sq.squadVon(o);
            if (ot != null) j.addProperty("squad", Squads.name(ot));
            spieler.add(j);
        }
        m.add("spieler", spieler);
        JsonArray st = new JsonArray();
        for (Squads.Ausloeser a : Squads.Ausloeser.values()) {
            if (a == Squads.Ausloeser.EMOTE) continue;   // Bedrock only – the mod is Java
            JsonObject j = new JsonObject();
            j.addProperty("id", a.name()); j.addProperty("n", a.anzeige); j.addProperty("b", a.beschreibung); j.addProperty("an", sq.aktiv(p, a));
            st.add(j);
        }
        m.add("steuerung", st);
        return m;
    }

    /** Send right away (e.g. right after a command) instead of waiting for the next tick. */
    void statusSofort(Player p) { if (modNutzer.contains(p.getUniqueId())) statusSenden(p, pl.squads().squadVon(p)); }

    private void statusSenden(Player p, Team t) {
        Squads sq = pl.squads();
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("ich", p.getUniqueId().toString());
        o.addProperty("nah", TeamPing.nahGrenze(p));   // from here on the mod draws the beam itself
        if (t != null) {
            o.addProperty("squad", Squads.name(t));
            o.addProperty("teamfarbe", Squads.farbe(t).value());
            o.addProperty("leiter", sq.leiterName(t));
            JsonArray mit = new JsonArray();
            for (String name : sq.mitglieder(t)) {
                JsonObject m = new JsonObject();
                m.addProperty("n", name);
                Squads.PingFarbe f = sq.pingFarbeVonName(name);
                m.addProperty("f", f == null ? 0xFFFFFF : f.rgb);
                Player on = Bukkit.getPlayerExact(name);
                m.addProperty("on", on != null);
                if (on != null) {
                    m.addProperty("u", on.getUniqueId().toString());
                    m.addProperty("hp", on.getHealth());
                    m.addProperty("max", Optional.ofNullable(on.getAttribute(Attribute.MAX_HEALTH)).map(a -> a.getValue()).orElse(20.0));
                    Location l = on.getLocation();
                    m.addProperty("x", l.getX()); m.addProperty("y", l.getY()); m.addProperty("z", l.getZ());
                    m.addProperty("w", l.getWorld().getKey().toString());
                }
                mit.add(m);
            }
            o.add("mitglieder", mit);
        }
        o.add("pings", pl.pingsFuer(p));
        o.add("menu", menuDaten(p, t));
        if (menuOeffnen.remove(p.getUniqueId())) o.addProperty("oeffnen", true);
        p.sendPluginMessage(pl, KANAL_STATUS, o.toString().getBytes(StandardCharsets.UTF_8));
    }
}
