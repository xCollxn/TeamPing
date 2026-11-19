package io.github.xcollxn.teamping;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * TeamPing: squads (chest menu for Java, native forms for Bedrock) + pings with a light beam,
 * locator bar, far marker, death point, ping types, squad overview and companion mod support.
 * Up to max-pings normal pings per player (the next one replaces the oldest); map ping, follow me and death point have their own slot.
 */
public final class TeamPing extends JavaPlugin implements Listener, TabExecutor, PluginMessageListener {

    private static final String[] PFEILE = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    /** Bedrock: own glyphs from the TeamPing pack (font/glyph_E1.png) – ↑ etc. are missing from Bedrock's pixel font. */
    private static final String[] PFEILE_BEDROCK = {"\uE101", "\uE102", "\uE103", "\uE104", "\uE105", "\uE106", "\uE107", "\uE108"};
    static final String HERZ_BEDROCK = "\uE100", HAKEN_BEDROCK = "\uE109";
    private static final double ENTFERNEN_RADIUS = 2.5;
    /** Real beam up to here (beyond: far marker); the server's display tracking range is 128. */
    private static final double NAH_MAX = 112;
    /**
     * Bedrock: name tags have a fixed size in the world and can't be scaled. From BEDROCK_FERN_AB metres
     * the label therefore always stands BEDROCK_SCHILD_ABSTAND metres in front of the viewer on the line to the ping (stays readable).
     */
    private static final double BEDROCK_FERN_AB = 10, BEDROCK_SCHILD_ABSTAND = 5;
    /** Characters Bedrock's pixel font doesn't know (otherwise a small fallback font for the whole label). */
    private static final net.kyori.adventure.text.TextReplacementConfig BEDROCK_SYMBOLE =
            net.kyori.adventure.text.TextReplacementConfig.builder().match("[◆☠⚠📣★●■] ?").replacement("").build();
    /** Pings closer than this (or on the same entity) are merged into one display. */
    private static final double ZUSAMMEN_RADIUS = 1.5;
    private static final float STRAHL_DICKE = 0.45f;
    private static final long GEFAHR_FENSTER_MS = 1000;

    enum Typ { NORMAL, GEFAHR, SAMMELN, TOD }

    private int dauerTicks, todDauerTicks, reichweite, cooldownMs, strahlHoehe, doppelFenster, maxPings, kartenDauerTicks;
    private Squads squads;
    private Uebersicht uebersicht;

    private final Map<UUID, Long> letzterPing = new HashMap<>();
    private final Map<UUID, Long> letztesSchleichen = new HashMap<>();
    /** Key = random UUID (normal ping), derived UUID (map, follow me, death point) or player UUID (legacy). */
    private final Map<UUID, AktiverPing> aktivePings = new HashMap<>();
    /** Ping look per viewer (from the squad data, dropped on change). */
    private final Map<UUID, Squads.Aussehen> aussehenCache = new HashMap<>();

    private Squads.Aussehen aussehen(Player o) {
        return aussehenCache.computeIfAbsent(o.getUniqueId(), squads::aussehen);
    }
    /** Own normal pings per player (oldest first) – up to maxPings at once (like Ping Wheel). */
    private final Map<UUID, Deque<UUID>> eigenePings = new HashMap<>();

    /** Fixed key for pings a player can only have once (map, "follow me"/"here"). */
    private static UUID schluessel(String art, UUID spieler) {
        return UUID.nameUUIDFromBytes((art + ":" + spieler).getBytes(StandardCharsets.UTF_8));
    }

    /** New slot for a normal ping; the oldest is removed if maxPings are already active. */
    private UUID neuerPingPlatz(Player p) {
        Deque<UUID> l = eigenePings.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        l.removeIf(id -> !aktivePings.containsKey(id));
        while (l.size() >= maxPings) pingEntfernen(l.pollFirst());
        UUID id = UUID.randomUUID();
        l.addLast(id);
        return id;
    }

    /** All own pings (normal, map, follow me) – for "ping again = remove". */
    private List<AktiverPing> eigeneAktive(Player p) {
        List<AktiverPing> l = new ArrayList<>();
        Deque<UUID> d = eigenePings.get(p.getUniqueId());
        if (d != null) for (UUID id : d) { AktiverPing ap = aktivePings.get(id); if (ap != null) l.add(ap); }
        for (String art : new String[]{"karte", "folgen"}) { AktiverPing ap = aktivePings.get(schluessel(art, p.getUniqueId())); if (ap != null) l.add(ap); }
        return l;
    }

    /** Newest normal ping (for "ping twice quickly = danger"). */
    private AktiverPing neuesterPing(Player p) {
        Deque<UUID> d = eigenePings.get(p.getUniqueId());
        if (d == null) return null;
        for (Iterator<UUID> it = d.descendingIterator(); it.hasNext(); ) { AktiverPing ap = aktivePings.get(it.next()); if (ap != null) return ap; }
        return null;
    }

    /** Player leaves squad/server: all their pings are removed (the death point stays). */
    void allePingsEntfernen(UUID spieler) {
        Deque<UUID> d = eigenePings.remove(spieler);
        if (d != null) for (UUID id : d) pingEntfernen(id);
        pingEntfernen(schluessel("karte", spieler));
        pingEntfernen(schluessel("folgen", spieler));
        pingEntfernen(spieler);
    }
    private final Set<UUID> wartetAufName = new HashSet<>();
    private final Set<UUID> squadChat = new HashSet<>();

    private static final class Fern { List<Display> strahl; Entity schild; Location anker; }

    /** One ping of a player. Several pings on the same target share one display. */
    private static final class AktiverPing {
        UUID id;
        String von;
        Squads.PingFarbe farbe;
        Typ typ;
        Component was;
        String textOderSchluessel; boolean istSchluessel;
        long zeit;
        int ende;               // server tick when the ping expires
        Entity verfolgt;
        Location ziel;
        Anzeige anzeige;
    }

    /** Visible marker (beam, label, locator dot) for a target – shared by all pings on it. */
    private static final class Anzeige {
        String squad;
        final List<AktiverPing> pings = new ArrayList<>();
        final List<Player> sehen = new ArrayList<>();
        Typ typ;
        Squads.PingFarbe farbe;
        Component titel;
        int hoehe, tick;
        boolean textNeu;
        Entity verfolgt;
        Location ziel;
        /** One beam per viewer – wider with distance so it stays visible far away (and with shaders). */
        final Map<UUID, List<Display>> strahlen = new HashMap<>();
        final Map<UUID, Float> strahlBreite = new HashMap<>();
        /** One label per viewer – so it can grow with the distance for everyone. */
        final Map<UUID, Entity> schilder = new HashMap<>();   // Java: TextDisplay, Bedrock: name tag (ArmorStand)
        Component schildText = Component.empty();
        ArmorStand wegpunkt;
        Chunk chunk;
        final Map<UUID, Fern> fern = new HashMap<>();
        BukkitTask task;
        List<Location> saeule;                       // Bedrock beam column (positions), null = not computed yet
        final Set<UUID> saeuleBei = new HashSet<>(); // who got it
    }
    private final List<Anzeige> anzeigen = new ArrayList<>();
    /** Bedrock column as a custom light beam block (needs resource pack + Geyser mapping) instead of stained glass. */
    private boolean bedrockLicht = true;
    private Stil stil = Stil.LEUCHT;

    Squads squads() { return squads; }
    Uebersicht uebersicht() { return uebersicht; }
    boolean hatMod(Player p) { return uebersicht.modNutzer.contains(p.getUniqueId()); }

    // ------------------------------------------------------------------ lifecycle
    /** Test/diagnostic commands and log output only with "debug: true" in the config. */
    boolean debug() { return getConfig().getBoolean("debug", false); }

    private void hilfe(Player p) {
        for (int i = 1; i <= 4; i++) p.sendMessage(Squads.info(Texte.tr("hilfe." + i)));
    }

    // ------------------------------------------------------------------ config (English keys, old German keys still work)
    int cfgInt(String key, String oldKey, int def) {
        return getConfig().contains(key) ? getConfig().getInt(key, def) : getConfig().getInt(oldKey, def);
    }

    String cfgString(String key, String oldKey, String def) {
        String v = getConfig().contains(key) ? getConfig().getString(key) : getConfig().getString(oldKey);
        return v == null ? def : v;
    }

    @Override
    public void onLoad() {
        BedrockPakete.beimLaden(this);   // before Geyser reads its packs/mappings
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BedrockPakete.beimStart(this);
        dauerTicks = Math.max(2, cfgInt("ping-seconds", "dauer-sekunden", 6)) * 20;
        todDauerTicks = Math.max(10, cfgInt("death-point-seconds", "todespunkt-sekunden", 300)) * 20;
        reichweite = cfgInt("range", "reichweite", 250);
        maxPings = Math.max(1, cfgInt("max-pings", "max-pings", 3));
        kartenDauerTicks = Math.max(5, cfgInt("map-ping-seconds", "karten-ping-sekunden", 30)) * 20;
        cooldownMs = cfgInt("cooldown-ms", "cooldown-ms", 1500);
        strahlHoehe = cfgInt("beam-height", "strahl-hoehe", 80);
        doppelFenster = cfgInt("double-sneak-window-ms", "doppel-schleichen-fenster-ms", 400);
        String bs = cfgString("bedrock-beam", "bedrock-strahl", "light");
        bedrockLicht = !(bs.equalsIgnoreCase("glass") || bs.equalsIgnoreCase("glas"));
        if (!umzugKennung().isEmpty() && !Bukkit.getWorlds().isEmpty()) {
            Location sp = Bukkit.getWorlds().getFirst().getSpawnLocation();
            umzugSpawn = new Location(sp.getWorld(), sp.getBlockX() + 0.5, sp.getY(), sp.getBlockZ() + 0.5);
            getLogger().info("World move \"" + umzugKennung() + "\" active: players start at " + umzugSpawn.getBlockX() + " " + umzugSpawn.getBlockY() + " " + umzugSpawn.getBlockZ());
        }
        stil = stilVon(cfgString("beam-style", "strahl-stil", "glow"));
        Texte.registrieren();
        pingSchluessel = new NamespacedKey(this, "pinggegenstand");
        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) GeyserEmote.anmelden(this);
        squads = new Squads(this);
        uebersicht = new Uebersicht(this);
        getServer().getPluginManager().registerEvents(this, this);
        for (String c : List.of("squad", "ping", "sc")) {
            Objects.requireNonNull(getCommand(c)).setExecutor(this);
            Objects.requireNonNull(getCommand(c)).setTabCompleter(this);
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, Uebersicht.KANAL_STATUS);
        getServer().getMessenger().registerIncomingPluginChannel(this, Uebersicht.KANAL_AKTION, this);
        Bukkit.getScheduler().runTaskTimer(this, uebersicht::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.getOnlinePlayers().forEach(this::locatorFarbe), 40L);
        getLogger().info("TeamPing active: ping " + (dauerTicks / 20) + " s, map ping " + (kartenDauerTicks / 20) + " s, death point " + (todDauerTicks / 20) + " s, range " + reichweite);
    }

    @Override
    public void onDisable() {
        for (Anzeige a : new ArrayList<>(anzeigen)) anzeigeEntfernen(a);
        for (Player p : Bukkit.getOnlinePlayers()) uebersicht.leistenEntfernen(p);
    }

    boolean istBedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    /** Player dot in the locator bar in their ping colour (seen by everyone). */
    void locatorFarbe(Player p) {
        if (!p.isOnline()) return;
        Squads.PingFarbe f = squads.pingFarbe(p);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "waypoint modify " + p.getUniqueId() + " color hex " + String.format("%06X", f.rgb));
    }

    // ------------------------------------------------------------------ companion mod
    @EventHandler
    public void onKanal(PlayerRegisterChannelEvent e) {
        if (e.getChannel().equals(Uebersicht.KANAL_STATUS) && uebersicht.modNutzer.add(e.getPlayer().getUniqueId()))
            getLogger().info(e.getPlayer().getName() + " uses the TeamPing companion mod");
    }

    @Override
    public void onPluginMessageReceived(String kanal, Player p, byte[] daten) {
        if (!kanal.equals(Uebersicht.KANAL_AKTION)) return;
        try {
            JsonObject o = JsonParser.parseString(new String(daten, StandardCharsets.UTF_8)).getAsJsonObject();
            String a = o.has("a") ? o.get("a").getAsString() : "";
            uebersicht.modNutzer.add(p.getUniqueId());
            switch (a) {
                case "ping" -> ping(p);
                case "menu" -> SquadMenu.oeffnen(this, p);
                case "aussehen" -> {   // mod: how this player wants to see pings
                    boolean strahl = !o.has("art") || !o.get("art").getAsString().equalsIgnoreCase("punkt");
                    squads.aussehenSetzen(p.getUniqueId(), strahl, o.has("breite") ? o.get("breite").getAsInt() : 100,
                            o.has("hell") ? o.get("hell").getAsInt() : 100, o.has("hoehe") ? o.get("hoehe").getAsInt() : 100);
                    aussehenCache.remove(p.getUniqueId());
                    for (Anzeige an : anzeigen) { strahlEntfernen(an, p.getUniqueId()); fernEntfernen(an, p.getUniqueId()); }   // rebuild
                }
                case "fern" -> {   // mod: target further away than our own range (or found via Distant Horizons)
                    World w = p.getWorld();
                    int x = o.get("x").getAsInt(), y = o.get("y").getAsInt(), z = o.get("z").getAsInt();
                    if (Math.abs(x - p.getX()) > 4096 || Math.abs(z - p.getZ()) > 4096 || y < w.getMinHeight() || y >= w.getMaxHeight()) return;
                    // load the area in the background (no lag), then a normal ping on exactly this block
                    w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(c -> {
                        if (p.isOnline() && p.getWorld().equals(w)) ping(p, w.getBlockAt(x, y, z), null);
                    });
                }
                case "karte" -> {   // ping from the map (coordinates)
                    World w = p.getWorld();
                    int x = o.get("x").getAsInt(), z = o.get("z").getAsInt();
                    int y = o.has("y") ? o.get("y").getAsInt() : w.getHighestBlockYAt(x, z) + 1;
                    pingAnOrt(p, new Location(w, x + 0.5, y, z + 0.5), Component.translatable("teamping.ping.karte", "Kartenpunkt"), "teamping.ping.karte", true);
                }
                default -> { }
            }
        } catch (Exception ex) {
            getLogger().warning("Invalid mod message from " + p.getName() + ": " + ex.getMessage());
        }
    }

    /** All pings this player may see – for the companion mod (markers, HUD, Xaero). */
    JsonArray pingsFuer(Player p) {
        JsonArray arr = new JsonArray();
        for (Anzeige a : anzeigen) {
            if (a.sehen.stream().noneMatch(o -> o.getUniqueId().equals(p.getUniqueId()))) continue;
            AktiverPing haupt = a.pings.getFirst();
            for (AktiverPing ap : a.pings) if (rang(ap.typ) > rang(haupt.typ)) haupt = ap;
            JsonObject o = new JsonObject();
            o.addProperty("id", a.pings.getFirst().id.toString());
            o.addProperty("n", String.join(", ", a.pings.stream().map(x -> x.von).toList()));
            o.addProperty("f", a.farbe.rgb);
            o.addProperty("typ", a.typ.name());
            o.addProperty("t", haupt.textOderSchluessel);
            o.addProperty("tk", haupt.istSchluessel);
            o.addProperty("x", a.ziel.getX()); o.addProperty("y", a.ziel.getY()); o.addProperty("z", a.ziel.getZ());
            o.addProperty("w", a.ziel.getWorld().getKey().toString());
            if (a.verfolgt != null && a.verfolgt.isValid()) {
                o.addProperty("eid", a.verfolgt.getEntityId());
                o.addProperty("eh", a.verfolgt.getHeight());
            }
            if (a.verfolgt instanceof Item it) {
                o.addProperty("item", it.getItemStack().getType().getKey().toString());
                o.addProperty("anz", it.getItemStack().getAmount());
            }
            arr.add(o);
        }
        return arr;
    }

    // ------------------------------------------------------------------ triggers
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        boolean ausloesen = p.isSneaking() ? squads.aktiv(p, Squads.Ausloeser.SHIFT_F) : squads.aktiv(p, Squads.Ausloeser.F);
        if (ausloesen) { e.setCancelled(true); ping(p); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!squads.aktiv(p, Squads.Ausloeser.DOPPEL)) return;
        long jetzt = System.currentTimeMillis();
        if (BedrockCheck.touch(p)) {
            // Phone/tablet: sneaking is a toggle there → "double tap" = on and right off again
            if (e.isSneaking()) letztesSchleichen.put(p.getUniqueId(), jetzt);
            else {
                Long an = letztesSchleichen.remove(p.getUniqueId());
                if (an != null && jetzt - an <= doppelFenster + 150) ping(p);
            }
            return;
        }
        if (!e.isSneaking()) return;
        Long vorher = letztesSchleichen.put(p.getUniqueId(), jetzt);
        if (vorher != null && jetzt - vorher <= doppelFenster) { letztesSchleichen.remove(p.getUniqueId()); ping(p); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (p.isSneaking() && squads.aktiv(p, Squads.Ausloeser.SHIFT_Q)) { e.setCancelled(true); ping(p); }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() == Action.LEFT_CLICK_AIR && p.isSneaking() && squads.aktiv(p, Squads.Ausloeser.SHIFT_LINKS)) ping(p);
    }

    // ------------------------------------------------------------------ ping item (phone: just tap)
    private NamespacedKey pingSchluessel;
    private final Map<UUID, Long> letzterGegenstandPing = new HashMap<>();

    ItemStack pingGegenstand(Player p) {
        ItemStack i = new ItemStack(Material.AMETHYST_SHARD);
        var meta = i.getItemMeta();
        meta.displayName(Texte.fuer(p, "item.name").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD));
        meta.lore(List.of(Texte.fuer(p, "item.lore1").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Texte.fuer(p, "item.lore2").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(pingSchluessel, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        i.setItemMeta(meta);
        return i;
    }

    boolean istPingGegenstand(ItemStack i) {
        return i != null && i.getType() == Material.AMETHYST_SHARD && i.hasItemMeta()
                && i.getItemMeta().getPersistentDataContainer().has(pingSchluessel, org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** Gives the ping item if the player doesn't have it in the inventory yet. */
    void pingGegenstandGeben(Player p) {
        for (ItemStack i : p.getInventory().getContents()) if (istPingGegenstand(i)) { p.sendActionBar(Texte.tr("item.schon_da")); return; }
        p.getInventory().addItem(pingGegenstand(p));
        p.sendActionBar(Texte.tr("item.erhalten"));
    }

    /** Tapping with the ping item triggers at most once per 0.35 s (Bedrock often sends two packets per tap). */
    private boolean gegenstandFrei(Player p) {
        long jetzt = System.currentTimeMillis();
        Long vorher = letzterGegenstandPing.put(p.getUniqueId(), jetzt);
        return vorher == null || jetzt - vorher > 350;
    }

    /** The ping item isn't lost on death – you get it back on respawn. */
    private final Set<UUID> gegenstandZurueck = new HashSet<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onTodGegenstand(PlayerDeathEvent e) {
        if (e.getDrops().removeIf(this::istPingGegenstand)) gegenstandZurueck.add(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWiederbeleben(PlayerRespawnEvent e) {
        if (gegenstandZurueck.remove(e.getPlayer().getUniqueId())) e.getPlayer().getInventory().addItem(pingGegenstand(e.getPlayer()));
    }

    /** For triggers from outside (e.g. Bedrock emote via Geyser). */

    @EventHandler(priority = EventPriority.LOW)
    public void onGegenstandTippen(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !istPingGegenstand(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!gegenstandFrei(p)) return;
        Block b = e.getClickedBlock();
        if (b != null) ping(p, b, null); else ping(p);   // block tapped → exactly this spot, otherwise the view direction
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onGegenstandTier(PlayerInteractEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !istPingGegenstand(e.getPlayer().getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        if (gegenstandFrei(e.getPlayer())) ping(e.getPlayer(), null, e.getRightClicked());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onGegenstandSchlag(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !istPingGegenstand(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);   // the ping item doesn't hit, it marks
        if (gegenstandFrei(p)) ping(p, null, e.getEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        umzugAbschliessen(p);
        Bukkit.getScheduler().runTaskLater(this, () -> locatorFarbe(p), 20L);
        if (BedrockCheck.aktiv(p)) getLogger().info(p.getName() + " plays Bedrock: " + BedrockCheck.geraet(p));
        // Bedrock players get a short hint the first time about how to ping (emote / double tap sneak)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || !BedrockCheck.aktiv(p) || squads.merker(p, "bedrock_tipp")) return;
            squads.merkerSetzen(p, "bedrock_tipp", true);
            p.sendMessage(Squads.info(Texte.tr("item.hinweis")));
        }, 60L);
    }

    // ------------------------------------------------------------------ world move (new world, keep items/stats)
    /**
     * One-time move to a new world (config "world-move: <id>"): the player data was taken over from the old world
     * and still contains the old coordinates. So on the first join after the move everyone starts at the
     * world spawn of the new world, their old bed is forgotten. Afterwards back to normal (flag per player).
     */
    private final Set<UUID> umzugOffen = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile Location umzugSpawn;

    private String umzugKennung() { return cfgString("world-move", "welt-umzug", ""); }

    @EventHandler
    public void onSpawnOrt(io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent e) {
        String k = umzugKennung();
        if (k.isEmpty() || umzugSpawn == null) return;
        UUID id = e.getConnection().getProfile().getId();
        if (id == null || squads.merker(id, "umzug-" + k)) return;
        e.setSpawnLocation(umzugSpawn.clone());
        umzugOffen.add(id);
    }

    private void umzugAbschliessen(Player p) {
        if (!umzugOffen.remove(p.getUniqueId())) return;
        p.setRespawnLocation(null);   // the old bed doesn't exist in the new world
        squads.merkerSetzen(p, "umzug-" + umzugKennung(), true);
        getLogger().info(p.getName() + " moved to the new world (start at world spawn, items kept)");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        allePingsEntfernen(id);
        aussehenCache.remove(id);
        for (Anzeige a : anzeigen) { fernEntfernen(a, id); schildEntfernen(a, id); strahlEntfernen(a, id); a.sehen.removeIf(o -> o.getUniqueId().equals(id)); }
        uebersicht.abmelden(e.getPlayer());
        letztesSchleichen.remove(id); letzterPing.remove(id); wartetAufName.remove(id);
    }

    /** Death point: the squad sees where a member died (e.g. to recover items). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTod(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (squads.squadVon(p) == null) return;
        Location ort = e.getPlayer().getLocation();
        if (ort.getY() < ort.getWorld().getMinHeight()) ort.setY(ort.getWorld().getMinHeight() + 1);
        UUID schluessel = UUID.nameUUIDFromBytes(("tod:" + p.getUniqueId()).getBytes(StandardCharsets.UTF_8));
        pingAnzeigen(p, schluessel, gerade(ort), Component.translatable("teamping.ping.tod", "Todespunkt"),
                "teamping.ping.tod", true,
                null, Typ.TOD, todDauerTicks);
    }

    // ------------------------------------------------------------------ menu clicks
    @EventHandler(priority = EventPriority.HIGH)
    public void onKlick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof SquadMenu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        Consumer<Player> aktion = m.aktionen.get(e.getSlot());
        if (aktion == null) return;
        p.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f));
        Bukkit.getScheduler().runTask(this, () -> aktion.accept(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onZiehen(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof SquadMenu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ chat
    void namensEingabe(Player p) {
        if (BedrockCheck.aktiv(p)) { BedrockMenus.erstellen(this, p); return; }
        p.closeInventory();
        wartetAufName.add(p.getUniqueId());
        p.sendMessage(Squads.info(Texte.tr("squad.name_eingabe", Texte.tr("squad.abbrechen_wort").color(NamedTextColor.RED))));
    }

    boolean squadChatAn(Player p) { return squadChat.contains(p.getUniqueId()); }

    void squadChatUmschalten(Player p) {
        if (!squadChat.remove(p.getUniqueId())) squadChat.add(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (wartetAufName.remove(p.getUniqueId())) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(this, () -> {
                if (text.equalsIgnoreCase("abbrechen") || text.equalsIgnoreCase("cancel")) { p.sendMessage(Squads.info(Texte.tr("squad.abgebrochen"))); return; }
                Team t = squads.erstellen(p, text, null);
                if (t != null) SquadMenu.squadMenue(this, p, t);
            });
            return;
        }
        if (text.startsWith("xaero-waypoint:")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(this, () -> xaeroPing(p, text));
            return;
        }
        if (squadChat.contains(p.getUniqueId())) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(this, () -> squadNachricht(p, text));
        }
    }

    private void xaeroPing(Player p, String text) {
        String[] t = text.split(":");
        if (t.length < 6) { p.sendMessage(Squads.info(Texte.tr("squad.karte_fehler"))); return; }
        try {
            int x = Integer.parseInt(t[3].trim()), z = Integer.parseInt(t[5].trim());
            World w = p.getWorld();
            int y = t[4].trim().equals("~") ? w.getHighestBlockYAt(x, z) + 1 : Integer.parseInt(t[4].trim());
            String name = t[1].replace("^col^", ":").trim();
            boolean standard = name.isEmpty() || name.toLowerCase(Locale.ROOT).contains("shared") || name.toLowerCase(Locale.ROOT).contains("geteilt");
            Component label = standard ? Component.translatable("teamping.ping.karte", "Kartenpunkt")
                    : Component.translatable("teamping.ping.karte", "Kartenpunkt").append(Component.text(": " + name));
            pingAnOrt(p, new Location(w, x + 0.5, y, z + 0.5), label, standard ? "teamping.ping.karte" : name, standard);
        } catch (NumberFormatException ex) {
            p.sendMessage(Squads.info(Texte.tr("squad.karte_fehler")));
        }
    }

    private void squadNachricht(Player p, String text) {
        Team t = squads.squadVon(p);
        if (t == null) { squadChat.remove(p.getUniqueId()); p.sendMessage(Squads.info(Texte.tr("squad.chat_kein_squad"))); return; }
        NamedTextColor c = Squads.farbe(t);
        squads.anSquad(t, Component.text("[" + Squads.name(t) + "] ", c).append(Component.text(p.getName() + ": ", c))
                .append(Component.text(text, NamedTextColor.WHITE)));
    }

    // ------------------------------------------------------------------ ping triggering
    private static Location gerade(Location l) {
        Location k = l.clone(); k.setYaw(0f); k.setPitch(0f); return k;
    }

    /**
     * What the player looks at: blocks (grass/flowers and glass/panes are passed through, water too) and
     * living entities or dropped items (hit box +0.3). The nearer one wins.
     */
    private RayTraceResult zielen(Player p) {
        Location auge = p.getEyeLocation();
        Vector dir = auge.getDirection();
        World w = p.getWorld();
        Vector start = auge.toVector();
        double rest = reichweite;
        RayTraceResult block = null;
        for (int i = 0; i < 24 && rest > 0; i++) {
            RayTraceResult r = w.rayTraceBlocks(start.toLocation(w), dir, rest, FluidCollisionMode.NEVER, true);
            if (r == null) break;
            Block b = r.getHitBlock();
            if (b != null && durchsichtig(b.getType())) {   // window: keep looking behind the pane
                double schritt = Math.max(austritt(b.getBoundingBox(), start, dir), start.distance(r.getHitPosition())) + 0.01;
                start = start.clone().add(dir.clone().multiply(schritt));
                rest -= schritt;
                continue;
            }
            block = r;
            break;
        }
        double blockWeite = block == null ? reichweite : auge.toVector().distance(block.getHitPosition());
        RayTraceResult ent = w.rayTraceEntities(auge, dir, blockWeite, 0.3,
                e -> !e.equals(p) && !(e instanceof Display) && !(e instanceof ArmorStand a && a.isMarker())
                        && (e instanceof LivingEntity || e instanceof Item) && !(e instanceof Player sp && sp.getGameMode() == GameMode.SPECTATOR));
        return ent != null ? ent : block;
    }

    private static boolean durchsichtig(Material m) {
        String n = m.name();
        return n.endsWith("GLASS") || n.endsWith("GLASS_PANE");
    }

    /** Distance from start (along dir) until leaving the box. */
    private static double austritt(org.bukkit.util.BoundingBox k, Vector start, Vector dir) {
        double t = Double.MAX_VALUE;
        double[][] ax = {{k.getMinX(), k.getMaxX(), start.getX(), dir.getX()}, {k.getMinY(), k.getMaxY(), start.getY(), dir.getY()},
                {k.getMinZ(), k.getMaxZ(), start.getZ(), dir.getZ()}};
        for (double[] a : ax) {
            if (Math.abs(a[3]) < 1e-9) continue;
            double t1 = (a[0] - a[2]) / a[3], t2 = (a[1] - a[2]) / a[3];
            t = Math.min(t, Math.max(t1, t2));
        }
        return t == Double.MAX_VALUE ? 1 : Math.max(0, t);
    }

    private boolean cooldownOk(Player p) {
        long jetzt = System.currentTimeMillis();
        Long vorher = letzterPing.get(p.getUniqueId());
        if (vorher != null && jetzt - vorher < cooldownMs) return false;
        letzterPing.put(p.getUniqueId(), jetzt);
        return true;
    }

    void pingAnOrt(Player p, Location ziel, Component was, String text, boolean istSchluessel) {
        if (!cooldownOk(p)) return;
        pingAnzeigen(p, schluessel("karte", p.getUniqueId()), ziel, was, text, istSchluessel, null, Typ.NORMAL, kartenDauerTicks);
    }

    private void ping(Player p) { ping(p, null, null); }

    private void ping(Player p, Block vorgabeBlock, Entity vorgabeEntity) { ping(p, vorgabeBlock, vorgabeEntity, null); }

    /**
     * Pings the view direction – or, if given, exactly this block / entity (ping item: tapped spot).
     * zwang = GEFAHR forces a danger ping (e.g. via an emote).
     */
    private void ping(Player p, Block vorgabeBlock, Entity vorgabeEntity, Typ zwang) {
        boolean vorgabe = vorgabeBlock != null || vorgabeEntity != null || zwang != null;
        long jetzt = System.currentTimeMillis();
        AktiverPing alt = neuesterPing(p);

        // Ping twice quickly = danger (also within the cooldown) – turns the last ping into danger
        // items can never be danger – the second quick ping doesn't count there
        if (alt != null && alt.typ == Typ.NORMAL && jetzt - alt.zeit <= GEFAHR_FENSTER_MS && !(alt.verfolgt instanceof Item)) {
            letzterPing.put(p.getUniqueId(), jetzt);
            pingAnzeigen(p, alt.id, alt.ziel, alt.was, alt.textOderSchluessel, alt.istSchluessel, alt.verfolgt, Typ.GEFAHR, dauerTicks);
            return;
        }
        if (!cooldownOk(p)) return;

        // Looking up = "Here!" at your own position
        if (!vorgabe && p.getLocation().getPitch() < -55f) {
            pingAnzeigen(p, schluessel("folgen", p.getUniqueId()), gerade(p.getLocation()), Component.translatable("teamping.ping.hierher", "Hierher!"),
                    "teamping.ping.hierher", true, p, Typ.SAMMELN, dauerTicks);
            return;
        }

        Entity trefferEntity = vorgabeEntity;
        Block trefferBlock = vorgabeBlock;
        if (vorgabeBlock == null && vorgabeEntity == null) {
            RayTraceResult treffer = zielen(p);
            if (debug())
                getLogger().info("[debug] Ping " + p.getName() + " view " + Math.round(p.getLocation().getYaw()) + "/" + Math.round(p.getLocation().getPitch())
                        + " -> " + (treffer == null ? "nothing" : treffer.getHitEntity() != null ? treffer.getHitEntity().getType() + " @" + treffer.getHitPosition()
                        : treffer.getHitBlock().getType() + " @" + treffer.getHitPosition()));
            if (treffer == null) { p.sendActionBar(Texte.tr("ping.nichts").color(NamedTextColor.GRAY)); return; }
            trefferEntity = treffer.getHitEntity();
            trefferBlock = treffer.getHitBlock();
        }

        Location ziel;
        Component was;
        String text;
        boolean istSchluessel;
        boolean gefahr = false;
        Entity zielEntity = trefferEntity;
        if (zielEntity != null) {
            ziel = gerade(zielEntity.getLocation());
            gefahr = zielEntity instanceof Enemy;
            if (zielEntity instanceof Player zp) { was = Component.text(zp.getName()); text = zp.getName(); istSchluessel = false; }
            else if (zielEntity instanceof Item it) {   // dropped item: "Diamond ×3"
                ItemStack st = it.getItemStack();
                text = st.translationKey();
                was = st.getAmount() > 1 ? st.effectiveName().append(Component.text(" ×" + st.getAmount())) : st.effectiveName();
                istSchluessel = true;
            }
            else { text = zielEntity.getType().translationKey(); was = Component.translatable(text); istSchluessel = true; }
        } else if (trefferBlock != null) {
            Block b = trefferBlock;
            ziel = b.getLocation().add(0.5, 1.0, 0.5);
            // Normal terrain is just called "Ping", special blocks keep their name; plus the structure if any (village …)
            String struktur = struktur(b);
            boolean gelaende = gelaende(b.getType()) || b.getType().isAir();   // far ping into the air: just "Ping"
            List<String> teile = new ArrayList<>();
            if (!gelaende) teile.add(b.getType().translationKey());
            if (struktur != null) teile.add("teamping.struktur." + struktur);
            if (teile.isEmpty()) teile.add("teamping.ping.stelle");
            Component w = Component.empty();
            for (int i = 0; i < teile.size(); i++) {
                if (i > 0) w = w.append(Component.text(" · "));
                String k = teile.get(i);
                w = w.append(k.startsWith("teamping.") ? Texte.tr(k.substring("teamping.".length())) : Component.translatable(k));
            }
            was = w; text = String.join("|", teile); istSchluessel = true;   // mod: several keys separated by |
        } else return;

        // Pinging one of your own pings again = remove it (applies to all your own, incl. the map ping)
        for (AktiverPing eigen : eigeneAktive(p)) {
            boolean gleich = zielEntity != null ? zielEntity.equals(eigen.verfolgt)
                    : eigen.verfolgt == null && eigen.ziel.getWorld().equals(ziel.getWorld()) && eigen.ziel.distance(ziel) <= ENTFERNEN_RADIUS;
            if (gleich) {
                pingEntfernen(eigen.id);
                p.sendActionBar(Texte.tr("ping.entfernt").color(NamedTextColor.GRAY));
                return;
            }
        }
        Entity verfolgen = zielEntity;   // pinged entities/items are tracked – the label always sits above them
        pingAnzeigen(p, neuerPingPlatz(p), ziel, was, text, istSchluessel, verfolgen, (gefahr || zwang == Typ.GEFAHR) && !(zielEntity instanceof Item) ? Typ.GEFAHR : Typ.NORMAL, dauerTicks);
    }

    // ------------------------------------------------------------------ Bedrock emotes
    /** Players currently assigning an emote (UUID → ping type) – the next emote is stored instead of pinged. */
    private final Map<UUID, String> emoteLernen = new HashMap<>();

    void emoteLernenStarten(Player p, String art) {
        emoteLernen.put(p.getUniqueId(), art);
        p.sendActionBar(Texte.tr("emote.jetzt", Texte.tr("emote.art." + art)).color(NamedTextColor.YELLOW));
        Bukkit.getScheduler().runTaskLater(this, () -> emoteLernen.remove(p.getUniqueId(), art), 20L * 30);
    }

    boolean lerntEmote(Player p) { return emoteLernen.containsKey(p.getUniqueId()); }

    /** A Bedrock emote: assign it (learn mode) or trigger the assigned ping type. */
    void emote(Player p, String emoteId) {
        String lernen = emoteLernen.remove(p.getUniqueId());
        if (lernen != null) {
            squads.emoteSetzen(p, emoteId, lernen);
            p.sendActionBar(Texte.tr("emote.gelernt", Texte.tr("emote.art." + lernen)).color(NamedTextColor.GREEN));
            return;
        }
        switch (squads.emoteArt(p, emoteId)) {
            case "folgen" -> {   // "Follow me!": marker at your own position, follows the player
                if (!cooldownOk(p)) return;
                pingAnzeigen(p, schluessel("folgen", p.getUniqueId()), gerade(p.getLocation()), Texte.tr("ping.folgen"), "teamping.ping.folgen", true, p, Typ.SAMMELN, dauerTicks);
            }
            case "gefahr" -> ping(p, null, null, Typ.GEFAHR);
            default -> ping(p);
        }
    }

    // ------------------------------------------------------------------ terrain & structures
    private static final Set<Material> GELAENDE = EnumSet.of(Material.GRAVEL, Material.CLAY, Material.SNOW, Material.SNOW_BLOCK,
            Material.POWDER_SNOW, Material.SANDSTONE, Material.RED_SANDSTONE, Material.DIRT_PATH, Material.FARMLAND, Material.SHORT_GRASS,
            Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN, Material.WATER, Material.LAVA, Material.END_STONE, Material.SOUL_SAND,
            Material.SOUL_SOIL, Material.CALCITE, Material.DRIPSTONE_BLOCK, Material.BEDROCK, Material.MAGMA_BLOCK, Material.DEAD_BUSH,
            Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP, Material.KELP_PLANT, Material.VINE, Material.SMOOTH_BASALT);

    static {   // blocks that may not exist in every version, by name
        for (String n : new String[]{"GRASS_BLOCK", "DIRT", "COARSE_DIRT", "PODZOL", "MYCELIUM", "ROOTED_DIRT", "MUD", "MOSS_BLOCK",
                "PALE_MOSS_BLOCK", "MOSS_CARPET", "PALE_MOSS_CARPET", "SHORT_DRY_GRASS", "TALL_DRY_GRASS", "BUSH", "FIREFLY_BUSH", "LEAF_LITTER"}) {
            Material m = Material.matchMaterial(n);
            if (m != null) GELAENDE.add(m);
        }
    }

    /** Normal terrain (grass, dirt, stone, sand, leaves …) – the ping is just called "Ping" there. */
    private static boolean gelaende(Material m) {
        return GELAENDE.contains(m) || Tag.DIRT.isTagged(m) || Tag.SAND.isTagged(m) || Tag.BASE_STONE_OVERWORLD.isTagged(m)
                || Tag.BASE_STONE_NETHER.isTagged(m) || Tag.LEAVES.isTagged(m) || Tag.TERRACOTTA.isTagged(m) || Tag.ICE.isTagged(m)
                || Tag.NYLIUM.isTagged(m) || Tag.SMALL_FLOWERS.isTagged(m);
    }

    /** Known structures (key prefix → text key). Buried treasure deliberately not – otherwise the ping would reveal it. */
    private static final String[] STRUKTUREN = {"village", "pillager_outpost", "mansion", "monument", "stronghold", "mineshaft",
            "desert_pyramid", "jungle_pyramid", "igloo", "swamp_hut", "shipwreck", "ocean_ruin", "ruined_portal", "fortress",
            "bastion_remnant", "end_city", "ancient_city", "trail_ruins", "trial_chambers"};

    /** Is the block inside a structure (village, temple …)? Returns e.g. "village" or null. */
    private static String struktur(Block b) {
        try {
            for (var gs : b.getWorld().getStructures(b.getX() >> 4, b.getZ() >> 4)) {
                if (!gs.getBoundingBox().clone().expand(1.5).contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)) continue;
                String id = Registry.STRUCTURE.getKeyOrThrow(gs.getStructure()).getKey();
                for (String s : STRUKTUREN) if (id.startsWith(s)) return s;
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    // ------------------------------------------------------------------ ping display
    /** What the beam looks like (config beam-style). */
    enum Stil { GLAS, KERN, BETON, BAND, LEUCHT }

    /** Beam style from the config – English (glass|core|concrete|band|glow) or old German names. */
    static Stil stilVon(String s) {
        return switch (s == null ? "" : s.toLowerCase(Locale.ROOT)) {
            case "glass", "glas" -> Stil.GLAS;
            case "core", "kern" -> Stil.KERN;
            case "concrete", "beton" -> Stil.BETON;
            case "band" -> Stil.BAND;
            default -> Stil.LEUCHT;
        };
    }

    private List<Display> strahlErzeugen(Location ort, Squads.PingFarbe f, float hoehe, float dicke, boolean fern, float hell) {
        List<Display> teile = new ArrayList<>();
        switch (stil) {
            case BAND -> teile.add(bandErzeugen(ort, f.rgb, Math.round(0xB0 * hell), hoehe, dicke, 0f));
            case LEUCHT -> {   // dark rim (contrast against the day sky) + glow + strong core – casts no shadow with shaders
                teile.add(bandErzeugen(ort, 0x000000, Math.round(0x55 * hell), hoehe, dicke * 1.9f, 0f));
                teile.add(bandErzeugen(ort, f.rgb, Math.round(0x99 * hell), hoehe, dicke * 1.5f, 0.02f));
                teile.add(bandErzeugen(ort, f.rgb, Math.round(0xFF * hell), hoehe, dicke * 0.5f, 0.04f));
            }
            case BETON -> teile.add(blockErzeugen(ort, f.beton, f, hoehe, dicke * 0.6f, true));
            case KERN -> {
                teile.add(blockErzeugen(ort, f.glas, f, hoehe, dicke, true));
                teile.add(blockErzeugen(ort, f.beton, f, hoehe, dicke * 0.35f, false));
            }
            default -> teile.add(blockErzeugen(ort, f.glas, f, hoehe, dicke, true));
        }
        return teile;
    }

    /** Width of the single parts relative to the beam thickness (same order as in strahlErzeugen). */
    private float[] faktoren() {
        return switch (stil) {
            case LEUCHT -> new float[]{1.9f, 1.5f, 0.5f};
            case KERN -> new float[]{1f, 0.35f};
            case BETON -> new float[]{0.6f};
            default -> new float[]{1f};
        };
    }

    /** Bring the beam to a new thickness (smoothly, over 5 ticks). */
    private void breiteSetzen(List<Display> teile, float dicke, float hoehe) {
        float[] f = faktoren();
        for (int i = 0; i < teile.size(); i++) {
            Display d = teile.get(i);
            float w = dicke * (i < f.length ? f[i] : 1f);
            Transformation tr = d instanceof TextDisplay
                    ? new Transformation(new Vector3f(0f, 0f, d.getTransformation().getTranslation().z()), new AxisAngle4f(),
                            new Vector3f(w / (6f / 40f), hoehe / (10f / 40f), 1f), new AxisAngle4f())
                    : new Transformation(new Vector3f(-w / 2, 0f, -w / 2), new AxisAngle4f(), new Vector3f(w, hoehe, w), new AxisAngle4f());
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(5);
            d.setTransformation(tr);
        }
    }

    /** From 30 m the beam grows with distance → always looks about the same width. */
    private static float strahlDicke(double dist) { return (float) (STRAHL_DICKE * Math.max(1.0, dist / 30.0)); }

    private void strahlEntfernen(Anzeige a, UUID betrachter) {
        List<Display> l = a.strahlen.remove(betrachter);
        a.strahlBreite.remove(betrachter);
        if (l != null) for (Display d : l) if (d.isValid()) d.remove();
    }

    private BlockDisplay blockErzeugen(Location ort, Material m, Squads.PingFarbe f, float hoehe, float dicke, boolean leuchten) {
        return ort.getWorld().spawn(gerade(ort), BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(-dicke / 2, 0f, -dicke / 2), new AxisAngle4f(),
                    new Vector3f(dicke, hoehe, dicke), new AxisAngle4f()));
            grundwerte(d);
            if (leuchten) { d.setGlowing(true); d.setGlowColorOverride(f.bukkit()); }
        });
    }

    /** Coloured band from a TextDisplay background – always turns towards the viewer. */
    /** vorne = small offset towards the viewer (billboard), so the layers lie cleanly behind each other. */
    private TextDisplay bandErzeugen(Location ort, int rgb, int alpha, float hoehe, float dicke, float vorne) {
        return ort.getWorld().spawn(gerade(ort), TextDisplay.class, d -> {
            d.text(Component.text(" "));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(alpha, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255));
            d.setSeeThrough(true);
            // " " is 4 px wide + 2 px border, 10 px high; 1 px = 1/40 block
            float sx = dicke / (6f / 40f), sy = hoehe / (10f / 40f);
            d.setTransformation(new Transformation(new Vector3f(0f, 0f, vorne), new AxisAngle4f(), new Vector3f(sx, sy, 1f), new AxisAngle4f()));
            grundwerte(d);
        });
    }

    private static void grundwerte(Display d) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setViewRange(8f);
        d.setTeleportDuration(2);
        d.setPersistent(false);
        d.setVisibleByDefault(false);
    }

    /**
     * Label for exactly this viewer. Bedrock (Geyser) can't show TextDisplays –
     * there the text is shown as the name tag of an invisible marker instead.
     */
    private Entity schildFuer(Player o, Location ort, Component text, float groesse) {
        if (!BedrockCheck.aktiv(o)) return schildErzeugen(ort, text, groesse);
        return ort.getWorld().spawn(gerade(ort), ArmorStand.class, s -> {
            s.setMarker(true); s.setInvisible(true); s.setGravity(false); s.setInvulnerable(true); s.setSilent(true);
            s.setCanTick(false); s.setPersistent(false); s.setVisibleByDefault(false);
            s.customName(text.replaceText(BEDROCK_SYMBOLE)); s.setCustomNameVisible(true);
        });
    }

    private static void schildText(Entity e, Component t) {
        if (e instanceof TextDisplay td) td.text(t); else e.customName(t.replaceText(BEDROCK_SYMBOLE));
    }

    private TextDisplay schildErzeugen(Location ort, Component text, float groesse) {
        return ort.getWorld().spawn(gerade(ort), TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setShadowed(true);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(groesse, groesse, groesse), new AxisAngle4f()));
            grundwerte(d);
        });
    }

    private static Component titel(Typ typ, Component was) {
        return switch (typ) {
            case GEFAHR -> Component.text("⚠ ", NamedTextColor.RED).append(Component.translatable("teamping.ping.gefahr", "Gefahr", NamedTextColor.RED))
                    .append(Component.text(": ", NamedTextColor.RED)).append(was.color(NamedTextColor.RED));
            case SAMMELN -> Component.text("📣 ", NamedTextColor.GOLD).append(was.color(NamedTextColor.GOLD));
            case TOD -> was.color(NamedTextColor.GRAY);
            default -> was.color(NamedTextColor.WHITE);
        };
    }

    /** Sound from the direction of the ping (up to 8 blocks away, louder) – you hear where it is. */
    private static void tonAusRichtung(Player o, Sound ton, Location ziel) {
        if (!o.getWorld().equals(ziel.getWorld())) { o.playSound(ton); return; }
        Vector auge = o.getEyeLocation().toVector();
        Vector d = ziel.toVector().subtract(auge);
        double weite = d.length();
        if (weite < 2) { o.playSound(ton); return; }
        Vector q = auge.add(d.multiply(Math.min(weite, 64) / 64 * 8 / weite));
        o.playSound(Sound.sound(ton.name(), ton.source(), ton.volume() * 2f, ton.pitch()), q.getX(), q.getY(), q.getZ());
    }

    private static Sound ton(Typ typ) {
        return switch (typ) {
            case GEFAHR -> Sound.sound(Key.key("block.note_block.bit"), Sound.Source.PLAYER, 1f, 0.7f);
            case SAMMELN -> Sound.sound(Key.key("block.note_block.pling"), Sound.Source.PLAYER, 1f, 1.2f);
            case TOD -> Sound.sound(Key.key("block.bell.use"), Sound.Source.PLAYER, 0.8f, 0.6f);
            default -> Sound.sound(Key.key("block.note_block.bell"), Sound.Source.PLAYER, 1f, 1.7f);
        };
    }

    private static int rang(Typ t) { return switch (t) { case GEFAHR -> 3; case SAMMELN -> 2; case TOD -> 1; default -> 0; }; }

    /** Existing display of the same squad for the same target (same entity or almost the same spot). */
    private Anzeige gleichesZiel(String squad, Location ziel, Entity entity) {
        for (Anzeige a : anzeigen) {
            if (!a.squad.equals(squad) || a.typ == Typ.TOD || !a.ziel.getWorld().equals(ziel.getWorld())) continue;
            if (entity != null ? entity.equals(a.verfolgt) : a.verfolgt == null && a.ziel.distance(ziel) <= ZUSAMMEN_RADIUS) return a;
        }
        return null;
    }

    private void pingAnzeigen(Player p, UUID schluessel, Location ziel, Component was, String text, boolean istSchluessel,
                              Entity zielEntity, Typ typ, int dauer) {
        pingEntfernen(schluessel);
        Team squad = squads.squadVon(p);
        String sqName = squad == null ? "solo:" + p.getUniqueId() : Squads.name(squad);

        AktiverPing ap = new AktiverPing();
        ap.id = schluessel; ap.von = p.getName(); ap.farbe = squads.pingFarbe(p); ap.typ = typ; ap.was = was;
        ap.textOderSchluessel = text; ap.istSchluessel = istSchluessel;
        ap.zeit = System.currentTimeMillis(); ap.ende = Bukkit.getCurrentTick() + dauer;
        ap.verfolgt = zielEntity; ap.ziel = ziel.clone();

        Anzeige a = typ == Typ.TOD ? null : gleichesZiel(sqName, ziel, zielEntity);
        if (a == null) a = neueAnzeige(sqName, ziel, zielEntity, ap.farbe, typ == Typ.TOD ? 40 : strahlHoehe);
        ap.anzeige = a;
        ap.ziel = a.ziel.clone();
        a.pings.add(ap);
        aktivePings.put(schluessel, ap);

        List<Player> sehen = new ArrayList<>();
        if (squad != null) { for (Player o : squads.onlineMitglieder(squad)) if (o.getWorld().equals(ziel.getWorld())) sehen.add(o); }
        if (!sehen.contains(p) && p.getWorld().equals(ziel.getWorld())) sehen.add(p);
        for (Player o : sehen) if (!a.sehen.contains(o)) { a.sehen.add(o); o.showEntity(this, a.wegpunkt); }
        aktualisieren(a);

        Sound ton = ton(typ);
        for (Player o : a.sehen) {
            tonAusRichtung(o, ton, a.ziel);
            if (typ == Typ.GEFAHR) tonAusRichtung(o, ton, a.ziel);
        }
    }

    private Anzeige neueAnzeige(String squad, Location ziel, Entity verfolgt, Squads.PingFarbe farbe, int hoehe) {
        Anzeige a = new Anzeige();
        a.squad = squad; a.ziel = ziel.clone(); a.verfolgt = verfolgt; a.farbe = farbe; a.hoehe = hoehe;
        World w = ziel.getWorld();
        a.chunk = ziel.getChunk();
        a.chunk.addPluginChunkTicket(this);
        a.wegpunkt = w.spawn(ziel.clone(), ArmorStand.class, s -> {
            s.setMarker(true); s.setInvisible(true); s.setGravity(false); s.setInvulnerable(true); s.setSilent(true);
            s.setCanTick(false); s.setPersistent(false); s.setVisibleByDefault(false);
            var attr = s.getAttribute(org.bukkit.attribute.Attribute.WAYPOINT_TRANSMIT_RANGE);
            if (attr != null) attr.setBaseValue(6.0E7);
        });
        wegpunktFarbe(a);
        anzeigen.add(a);
        a.task = Bukkit.getScheduler().runTaskTimer(this, () -> anzeigeTick(a), 0L, 1L);
        return a;
    }

    /** Fixed spot (block) or dropped item – the Bedrock light column may stand there. */
    private static boolean ortsfest(Anzeige a) { return a.verfolgt == null || a.verfolgt instanceof Item; }

    private static double schildHoehe(Anzeige a) {
        return a.verfolgt != null ? a.verfolgt.getHeight() + 0.9 : 1.6;   // for players/animals above the head and name tag
    }

    private void wegpunktFarbe(Anzeige a) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "waypoint modify " + a.wegpunkt.getUniqueId() + " color hex " + String.format("%06X", a.farbe.rgb));
    }

    /** Determine type, colour and label again (after a ping was added/expired). */
    private void aktualisieren(Anzeige a) {
        AktiverPing wichtigster = a.pings.getFirst();
        for (AktiverPing ap : a.pings) if (rang(ap.typ) > rang(wichtigster.typ)) wichtigster = ap;
        a.typ = wichtigster.typ;
        a.titel = titel(a.typ, wichtigster.was);
        Squads.PingFarbe farbe = a.pings.getFirst().farbe;
        if (farbe != a.farbe) {
            a.farbe = farbe;
            for (List<Display> l : a.strahlen.values()) for (Display d : l) faerben(d, farbe);
            for (Fern f : a.fern.values()) for (Display d : f.strahl) faerben(d, farbe);
            wegpunktFarbe(a);
        }
        Component t = Component.text(a.typ == Typ.TOD ? "☠ " : "◆ ", a.farbe.text()).append(a.titel);
        for (AktiverPing ap : a.pings) t = t.append(Component.newline()).append(Component.text(ap.von, ap.farbe.text()));
        a.schildText = t;
        for (Entity sd : a.schilder.values()) schildText(sd, t);
        a.textNeu = true;
    }

    private void faerben(Display d, Squads.PingFarbe f) {
        if (d instanceof BlockDisplay b) {
            boolean kern = b.getBlock().getMaterial().name().endsWith("_CONCRETE") && stil == Stil.KERN;
            b.setBlock((stil == Stil.BETON || kern ? f.beton : f.glas).createBlockData());
            if (b.isGlowing()) b.setGlowColorOverride(f.bukkit());
        } else if (d instanceof TextDisplay t && t.getBackgroundColor() != null) {
            Color alt = t.getBackgroundColor();
            if (alt.getRed() == 0 && alt.getGreen() == 0 && alt.getBlue() == 0) return;   // the dark rim stays dark
            t.setBackgroundColor(Color.fromARGB(alt.getAlpha(), (f.rgb >> 16) & 255, (f.rgb >> 8) & 255, f.rgb & 255));
        }
    }

    /** From here on the viewer gets the far marker instead of the real beam – depends on their view distance. */
    static double nahGrenze(Player o) {
        int chunks = Math.min(o.getClientViewDistance(), Bukkit.getViewDistance());
        double grenze = Math.max(48, Math.min(NAH_MAX, chunks * 16 - 16));
        // Bedrock: the server only sends name tag markers (armor stands) up to ~64 blocks → switch to the far marker earlier
        return BedrockCheck.aktiv(o) ? Math.min(grenze, 56) : grenze;
    }

    private void anzeigeTick(Anzeige a) {
        a.tick++;
        int jetzt = Bukkit.getCurrentTick();
        boolean geaendert = false;
        for (AktiverPing ap : new ArrayList<>(a.pings)) {
            if (ap.ende < jetzt) { a.pings.remove(ap); aktivePings.remove(ap.id); geaendert = true; }
        }
        if (a.pings.isEmpty()) { anzeigeEntfernen(a); return; }
        if (geaendert) aktualisieren(a);

        World w = a.ziel.getWorld();
        if (a.verfolgt != null) {
            if (a.verfolgt.isValid() && a.verfolgt.getWorld().equals(w)) {
                Location neu = gerade(a.verfolgt.getLocation());
                if (neu.distanceSquared(a.ziel) > 0.0004) {
                    a.ziel = neu;
                    for (List<Display> l : a.strahlen.values()) for (Display d : l) d.teleport(neu);
                    a.wegpunkt.teleport(neu);
                    for (AktiverPing ap : a.pings) ap.ziel = neu.clone();
                }
            } else if (a.verfolgt instanceof Item) {   // item picked up / gone → the ping ends
                for (AktiverPing ap : new ArrayList<>(a.pings)) pingEntfernen(ap.id);
                return;
            } else a.verfolgt = null;   // target gone (dead, unloaded): the marker stays at the last spot
        }
        Location z = a.ziel;
        boolean sekunde = a.tick % 20 == 1, partikel = a.tick % 5 == 1;
        boolean textNeu = sekunde || a.textNeu;
        a.textNeu = false;
        for (Player o : new ArrayList<>(a.sehen)) {
            if (!o.isOnline()) { a.sehen.remove(o); fernEntfernen(a, o.getUniqueId()); verstecken(o, a); continue; }
            if (!o.getWorld().equals(w)) { fernEntfernen(a, o.getUniqueId()); verstecken(o, a); continue; }
            boolean selbstMarkiert = o.equals(a.verfolgt);
            double dist = o.getLocation().distance(z);
            if (ortsfest(a) && a.tick % 40 == 1 && BedrockCheck.aktiv(o) && aussehen(o).strahl()) saeuleSenden(a, o);
            if (selbstMarkiert) {
                // whoever is marked themselves gets nothing in their face (no beam, no label, no particles)
                verstecken(o, a);
                fernEntfernen(a, o.getUniqueId());
            } else if (dist <= nahGrenze(o) && !(BedrockCheck.aktiv(o) && dist > BEDROCK_FERN_AB)) {
                fernEntfernen(a, o.getUniqueId());
                UUID oid = o.getUniqueId();
                Squads.Aussehen au = aussehen(o);
                float soll = strahlDicke(dist) * au.breite();
                float hoch = a.hoehe * au.hoehe();
                List<Display> strahl = a.strahlen.get(oid);
                if (BedrockCheck.aktiv(o)) { /* Bedrock shows no display beams – there it gets particles + name tag */ }
                else if (!au.strahl()) { if (strahl != null) strahlEntfernen(a, oid); }   // "dot only": label + marker, no beam
                else if (strahl == null || strahl.isEmpty() || !strahl.getFirst().isValid()) {
                    strahl = strahlErzeugen(z, a.farbe, hoch, soll, false, au.hell());
                    for (Display d : strahl) o.showEntity(this, d);
                    a.strahlen.put(oid, strahl);
                    a.strahlBreite.put(oid, soll);
                } else if (partikel && Math.abs(a.strahlBreite.getOrDefault(oid, soll) - soll) > soll * 0.06f) {
                    breiteSetzen(strahl, soll, hoch);
                    a.strahlBreite.put(oid, soll);
                }
                Entity sd = a.schilder.get(o.getUniqueId());
                Location schildOrt = z.clone().add(0, schildHoehe(a) + schildAnheben(dist), 0);
                if (sd == null || !sd.isValid()) {
                    sd = schildFuer(o, schildOrt, a.schildText, schildGroesse(dist));
                    o.showEntity(this, sd);
                    a.schilder.put(o.getUniqueId(), sd);
                } else {
                    if (sd.getLocation().distanceSquared(schildOrt) > 0.0025) sd.teleport(schildOrt);   // follows the target and rises with distance
                    if (partikel && sd instanceof TextDisplay td) skalieren(td, schildGroesse(dist));
                }
                double dx = o.getX() - z.getX(), dz = o.getZ() - z.getZ();
                if (partikel && dx * dx + dz * dz > 2.5 * 2.5 && !bedrockStrahlDa(a, o) && aussehen(o).strahl()) {
                    ParticleBuilder nah = new ParticleBuilder(Particle.DUST).data(new Particle.DustOptions(a.farbe.bukkit(), 2.2f))
                            .force(true).count(2).offset(0.05, 0.4, 0.05).receivers(o);
                    if (BedrockCheck.aktiv(o)) {
                        // Bedrock without light beam (moving target or glass mode): denser dust particles.
                        // No end rod sparks any more – they keep floating for seconds and drift down after the ping is gone.
                        nah.count(2).offset(0.08, 0.3, 0.08);
                        for (int y = 0; y < Math.min(a.hoehe, 48); y++) nah.location(z.clone().add(0, y, 0)).spawn();
                    } else for (int y = 0; y < Math.min(a.hoehe, 40); y += 2) nah.location(z.clone().add(0, y, 0)).spawn();
                }
            } else if (hatMod(o) && !BedrockCheck.aktiv(o)) {
                // Java with mod: the mod draws far beams itself – at the real spot, any distance (status field "nah")
                verstecken(o, a);
                fernEntfernen(a, o.getUniqueId());
            } else {
                verstecken(o, a);
                fernAktualisieren(a, o, z, (int) Math.round(dist), textNeu || !a.fern.containsKey(o.getUniqueId()), partikel);
            }
            // direction line above the hotbar – only without the mod (Java with mod has markers + edge arrows instead)
            if (sekunde && a.typ != Typ.TOD && !selbstMarkiert && !(hatMod(o) && !BedrockCheck.aktiv(o))) {
                AktiverPing erster = a.pings.getFirst();
                String namen = a.pings.size() == 1 ? erster.von : erster.von + " +" + (a.pings.size() - 1);
                o.sendActionBar(Component.text(pfeil(o, z) + " ", a.farbe.text()).decorate(TextDecoration.BOLD)
                        .append(Component.text(namen + ": ", a.farbe.text()).decoration(TextDecoration.BOLD, false))
                        .append(a.titel.decoration(TextDecoration.BOLD, false))
                        .append(Component.text("  " + Math.round(dist) + " m", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
            }
        }
    }

    private void verstecken(Player o, Anzeige a) {
        strahlEntfernen(a, o.getUniqueId());
        schildEntfernen(a, o.getUniqueId());
    }

    private static void schildEntfernen(Anzeige a, UUID betrachter) {
        Entity sd = a.schilder.remove(betrachter);
        if (sd != null && sd.isValid()) sd.remove();
    }

    /** The label grows with distance so it always looks about the same size (from ~20 m). */
    private static float schildGroesse(double dist) { return (float) Math.max(1.3, dist * 0.12); }

    /** From afar the label stands higher (about 3 degrees above the target), so it doesn't stick to the ground. */
    private static double schildAnheben(double dist) { return Math.max(0, dist - 8) * 0.05; }

    private static void skalieren(TextDisplay sd, float g) {
        if (Math.abs(sd.getTransformation().getScale().x() - g) < 0.05f * g) return;
        sd.setInterpolationDelay(0);
        sd.setInterpolationDuration(5);
        sd.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(g, g, g), new AxisAngle4f()));
    }

    /**
     * Far marker: stands fixed in the world on the line eye→target (just inside the view distance) and
     * only slides forward when you get too close, move away or the direction no longer fits.
     * So it doesn't wobble while riding/walking.
     */
    private void fernAktualisieren(Anzeige a, Player o, Location ziel, int dist, boolean textNeu, boolean partikel) {
        Location auge = o.getEyeLocation();
        Vector zuZiel = ziel.toVector().add(new Vector(0, 1.0, 0)).subtract(auge.toVector());
        double laenge = zuZiel.length();
        if (laenge < 1) return;
        boolean bedrock = BedrockCheck.aktiv(o);
        double soll = bedrock ? BEDROCK_SCHILD_ABSTAND : nahGrenze(o) - 10;
        Fern f = a.fern.get(o.getUniqueId());
        boolean setzen = f == null;
        if (f != null) {
            Vector zuAnker = f.anker.toVector().add(new Vector(0, 1.0, 0)).subtract(auge.toVector());
            double dAnker = zuAnker.length();
            setzen = dAnker < soll * 0.55 || dAnker > soll + 8 || zuAnker.angle(zuZiel) > Math.toRadians(4);
        }
        if (setzen) {
            Location fuss = gerade(auge.clone().add(zuZiel.clone().multiply(soll / laenge))).subtract(0, 1.0, 0);
            if (f == null) {
                f = new Fern();
                f.anker = fuss;
                Squads.Aussehen au = aussehen(o);
                f.strahl = BedrockCheck.aktiv(o) || !au.strahl() ? new ArrayList<>()
                        : strahlErzeugen(fuss, a.farbe, 30f * au.hoehe(), strahlDicke(soll) * au.breite(), true, au.hell());   // Bedrock: beam strips would show up as boxes on the ground; further away = thicker so it doesn't become a line
                f.schild = schildFuer(o, fuss.clone().add(0, bedrock ? 0.6 : 1.4 + schildAnheben(soll), 0), Component.empty(), (float) Math.max(1.1, soll * 0.12));
                for (Display d : f.strahl) o.showEntity(this, d);
                o.showEntity(this, f.schild);
                a.fern.put(o.getUniqueId(), f);
            } else {
                f.anker = fuss;
                for (Display d : f.strahl) { d.setTeleportDuration(12); d.teleport(fuss); }
                if (f.schild instanceof Display fd) fd.setTeleportDuration(12);
                f.schild.teleport(fuss.clone().add(0, bedrock ? 0.6 : 1.4 + schildAnheben(soll), 0));
            }
        }
        if (textNeu) {
            Component t = Component.text(a.typ == Typ.TOD ? "☠ " : "◆ ", a.farbe.text()).append(a.titel)
                    .append(Component.text("  " + dist + " m", NamedTextColor.GRAY));
            for (AktiverPing ap : a.pings) t = t.append(Component.newline()).append(Component.text(ap.von, ap.farbe.text()));
            schildText(f.schild, t);
        }
        if (partikel && !bedrock && aussehen(o).strahl()) {   // Bedrock: the far label stands 5 m in front of the player – no particles there
            ParticleBuilder pb = new ParticleBuilder(Particle.DUST).data(new Particle.DustOptions(a.farbe.bukkit(), 1.6f))
                    .receivers(o).force(true).count(1).offset(0.03, 0.3, 0.03);
            for (int y = 0; y < 16; y += 2) pb.location(f.anker.clone().add(0, y, 0)).spawn();
        }
    }

    private static void fernEntfernen(Anzeige a, UUID betrachter) {
        Fern f = a.fern.remove(betrachter);
        if (f == null) return;
        for (Display d : f.strahl) if (d.isValid()) d.remove();
        if (f.schild.isValid()) f.schild.remove();
    }

    // ------------------------------------------------------------------ Bedrock: beam column
    /**
     * Bedrock sees no display beams. Instead every Bedrock viewer gets a column of fake blocks that ONLY they see
     * (the world doesn't change): with the resource pack the custom light beam block, otherwise stained glass. It only
     * replaces air and is re-sent every 2 s (in case chunks were reloaded).
     */
    private void saeuleSenden(Anzeige a, Player o) {
        if (a.saeule == null) {
            a.saeule = new ArrayList<>();
            World w = a.ziel.getWorld();
            int x = a.ziel.getBlockX(), z = a.ziel.getBlockZ(), y0 = a.ziel.getBlockY() + (bedrockLicht ? 0 : 3);   // the light beam has no collision -> from the ground
            for (int y = y0; y < Math.min(w.getMaxHeight(), y0 + 80); y++) {   // tall, so it sticks out above forests
                Block b = w.getBlockAt(x, y, z);
                if (b.getType().isAir()) a.saeule.add(b.getLocation());
            }
        }
        if (a.saeule.isEmpty()) return;
        boolean breit = o.getWorld().equals(a.ziel.getWorld()) && o.getLocation().distanceSquared(a.ziel) > 40 * 40;
        // With the TeamPing resource pack: light block level 1-10 = custom glowing beam block (no collision) in ping colour
        org.bukkit.block.data.BlockData block = bedrockLicht
                ? Bukkit.createBlockData(Material.LIGHT, d -> { ((org.bukkit.block.data.Levelled) d).setLevel(a.farbe.ordinal() + 1);
                                                               // waterlogged = wide variant (resource pack) for viewers from 40 m – thicker from afar like on Java
                                                               ((org.bukkit.block.data.Waterlogged) d).setWaterlogged(breit); })
                : a.farbe.glas.createBlockData();
        Map<Location, org.bukkit.block.data.BlockData> bloecke = new HashMap<>();
        for (Location l : a.saeule) bloecke.put(l, block);
        o.sendMultiBlockChange(bloecke);
        a.saeuleBei.add(o.getUniqueId());
    }

    /** The Bedrock viewer sees the light beam (fixed spot) – then no particles that keep drifting after the ping. */
    private boolean bedrockStrahlDa(Anzeige a, Player o) {
        return bedrockLicht && ortsfest(a) && BedrockCheck.aktiv(o) && a.saeule != null && !a.saeule.isEmpty();
    }

    /** Send the real blocks back – the column disappears. */
    private void saeuleEntfernen(Anzeige a) {
        if (a.saeule == null || a.saeuleBei.isEmpty()) return;
        Map<Location, org.bukkit.block.data.BlockData> echt = new HashMap<>();
        for (Location l : a.saeule) echt.put(l, l.getBlock().getBlockData());
        for (UUID id : a.saeuleBei) {
            Player o = Bukkit.getPlayer(id);
            if (o != null && o.getWorld().equals(a.ziel.getWorld())) o.sendMultiBlockChange(echt);
        }
        a.saeuleBei.clear();
    }

    private void anzeigeEntfernen(Anzeige a) {
        saeuleEntfernen(a);
        anzeigen.remove(a);
        if (a.task != null) a.task.cancel();
        for (UUID id : new ArrayList<>(a.strahlen.keySet())) strahlEntfernen(a, id);
        for (UUID id : new ArrayList<>(a.schilder.keySet())) schildEntfernen(a, id);
        if (a.wegpunkt != null && a.wegpunkt.isValid()) a.wegpunkt.remove();
        if (a.chunk != null) a.chunk.removePluginChunkTicket(this);
        for (UUID id : new ArrayList<>(a.fern.keySet())) fernEntfernen(a, id);
        for (AktiverPing ap : a.pings) aktivePings.remove(ap.id);
        a.pings.clear();
    }

    static String pfeil(Player o, Location ziel) {
        Vector d = ziel.toVector().subtract(o.getLocation().toVector());
        double zielYaw = Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        double rel = ((zielYaw - o.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        int idx = (int) Math.round(rel / 45.0);
        return (BedrockCheck.aktiv(o) ? PFEILE_BEDROCK : PFEILE)[((idx % 8) + 8) % 8];
    }

    void pingEntfernen(UUID schluessel) {
        AktiverPing ap = aktivePings.remove(schluessel);
        if (ap == null || ap.anzeige == null) return;
        Anzeige a = ap.anzeige;
        a.pings.remove(ap);
        if (a.pings.isEmpty()) anzeigeEntfernen(a); else aktualisieren(a);
    }

    // ------------------------------------------------------------------ commands
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            // Console/RCON (test): /squad testfenster <player> <haupt|steuerung|emotes|einladen|erstellen|zu|ping> – opens a Bedrock form
            if (debug() && cmd.getName().equalsIgnoreCase("squad") && args.length >= 3 && args[0].equalsIgnoreCase("testfenster")) {
                Player z = Bukkit.getPlayerExact(args[1]);
                if (z == null) { sender.sendMessage("Player not online"); return true; }
                if (args[2].equalsIgnoreCase("ping")) ping(z, null, null, null);   // ping as if from the player (view direction)
                else BedrockMenus.testOeffnen(this, z, args[2]);
                sender.sendMessage("Opened " + args[2] + " for " + z.getName());
                return true;
            }
            sender.sendMessage("Players only."); return true;
        }
        // Mod users get the new state right away (not only on the next 0.5 s tick) → buttons react immediately
        if (hatMod(p)) Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline()) uebersicht.statusSofort(p); });
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "ping" -> { ping(p); return true; }
            case "sc" -> {
                if (args.length == 0) { squadChatUmschalten(p); p.sendActionBar(Texte.tr("squad.chat", Texte.anAus(squadChatAn(p)))); }
                else squadNachricht(p, String.join(" ", args));
                return true;
            }
        }
        if (args.length == 0) { SquadMenu.oeffnen(this, p); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String arg1 = args.length > 1 ? args[1] : null;
        switch (sub) {
            case "erstellen", "create" -> {
                if (arg1 == null) namensEingabe(p);
                else squads.erstellen(p, arg1, args.length > 2 ? Squads.farbeVon(args[2]) : null);
            }
            case "beitreten", "join" -> {
                Team t = squads.team(arg1);
                if (t == null) p.sendMessage(Squads.info(Texte.tr("squad.nicht_gefunden")));
                else squads.beitreten(p, t, false);
            }
            case "annehmen", "accept" -> { if (arg1 != null) squads.annehmen(p, arg1); }
            case "einladen", "invite" -> {
                Player o = arg1 == null ? null : Bukkit.getPlayerExact(arg1);
                if (o == null) p.sendMessage(Squads.info(Texte.tr("squad.nicht_online")));
                else squads.einladen(p, o);
            }
            case "kicken", "kick" -> { if (arg1 != null) squads.kicken(p, arg1); }
            case "leiter", "leader" -> { if (arg1 != null) squads.leiterUebertragen(p, arg1); }
            case "farbe", "color" -> {
                NamedTextColor c = Squads.farbeVon(arg1);
                if (c == null) p.sendMessage(Squads.info(Texte.tr("squad.farben", String.join(", ", Squads.FARBEN.keySet()))));
                else squads.farbeSetzen(p, c);
            }
            case "pingfarbe", "pingcolor", "pingcolour" -> {
                Squads.PingFarbe f = squads.pingFarbeWuerfeln(p);
                p.sendActionBar(Texte.tr("squad.neue_pingfarbe", Component.text("■ ", f.text()).append(Texte.tr("pingfarbe." + f.name()).color(f.text()))));
            }
            case "uebersicht", "übersicht", "hud", "overview" -> {
                squads.uebersichtUmschalten(p);
                p.sendActionBar(Texte.tr("squad.uebersicht", Texte.anAus(squads.uebersichtAn(p))));
            }
            case "steuerung", "keybinds", "controls" -> {
                if (BedrockCheck.aktiv(p)) BedrockMenus.steuerung(this, p); else SquadMenu.steuerungMenue(this, p);
            }
            case "taste", "key", "gesture" -> {
                try { Squads.Ausloeser a = Squads.Ausloeser.valueOf(arg1 == null ? "" : arg1.toUpperCase(Locale.ROOT)); squads.umschalten(p, a);
                      p.sendActionBar(Texte.tr("squad.taste", Texte.tr("geste." + a.name()), Texte.anAus(squads.aktiv(p, a)))); }
                catch (IllegalArgumentException ex) { p.sendMessage(Squads.info(Texte.tr("squad.unbekannte_taste"))); }
            }
            case "offen", "open" -> squads.offenSetzen(p, true);
            case "geschlossen", "closed" -> squads.offenSetzen(p, false);
            case "aufloesen", "auflösen", "disband" -> squads.aufloesen(p);
            case "verlassen", "leave" -> squads.verlassen(p, true);
            case "liste", "list" -> {
                List<Team> alle = squads.alle();
                if (alle.isEmpty()) { p.sendMessage(Squads.info(Texte.tr("squad.keine_squads"))); return true; }
                p.sendMessage(Squads.info(Texte.tr("squad.liste")));
                for (Team t : alle) p.sendMessage(Component.text(" • " + Squads.name(t), Squads.farbe(t))
                        .append(Component.text((squads.offen(t) ? "" : " 🔒") + " – " + String.join(", ", squads.mitglieder(t)), NamedTextColor.GRAY)));
            }
            case "öffnen", "oeffnen", "menu", "gui" -> SquadMenu.oeffnen(this, p);
            case "kiste", "chest" -> SquadMenu.kisteOeffnen(this, p);   // chest menu also for mod users
            case "pingitem", "pinggegenstand" -> pingGegenstandGeben(p);
            case "strahlstil", "beamstyle" -> {   // debug + OPs only: switch the beam style (glass|core|concrete|band|glow)
                if (!p.isOp() || !debug()) { hilfe(p); return true; }
                try {
                    stil = stilVon(arg1);
                    getConfig().set("beam-style", switch (stil) { case GLAS -> "glass"; case KERN -> "core"; case BETON -> "concrete"; case BAND -> "band"; default -> "glow"; });
                    getConfig().set("strahl-stil", null); saveConfig();
                    p.sendMessage(Squads.info("Strahl-Stil: " + stil + " (gilt fuer neue Pings)"));
                } catch (IllegalArgumentException ex) { p.sendMessage(Squads.info("Stile: glas, kern, beton, band, leucht")); }
            }
            case "testgehe" -> {     // debug + OPs only: teleport to the nearest structure (e.g. village_plains) – test of the structure detection
                if (!p.isOp() || !debug() || arg1 == null) { hilfe(p); return true; }
                var st = Registry.STRUCTURE.get(NamespacedKey.minecraft(arg1.toLowerCase(Locale.ROOT)));
                if (st == null) { p.sendMessage(Squads.info("Unbekannte Struktur")); return true; }
                var r = p.getWorld().locateNearestStructure(p.getLocation(), st, 100, false);
                if (r == null) { p.sendMessage(Squads.info("Keine " + arg1 + " gefunden")); return true; }
                Location l = r.getLocation();
                int hx = l.getBlockX() + 8, hz = l.getBlockZ() + 8;
                p.getWorld().getChunkAt(hx >> 4, hz >> 4).load(true);
                p.teleport(new Location(p.getWorld(), hx + 0.5, p.getWorld().getHighestBlockYAt(hx, hz) + 1, hz + 0.5, 0, 60));
                p.sendMessage(Squads.info("Bei " + arg1 + " " + hx + " " + hz));
            }
            case "testping" -> {     // debug + OPs only: fake ping with another name on the target under the crosshair (test for merging)
                if (!p.isOp() || !debug() || arg1 == null) { hilfe(p); return true; }
                Location auge = p.getEyeLocation();
                RayTraceResult r = p.getWorld().rayTraceBlocks(auge, auge.getDirection(), reichweite, FluidCollisionMode.NEVER, true);
                if (r == null || r.getHitBlock() == null) return true;
                Block b = r.getHitBlock();
                Team t = squads.squadVon(p);
                AktiverPing ap = new AktiverPing();
                ap.id = UUID.nameUUIDFromBytes(("test:" + arg1).getBytes(StandardCharsets.UTF_8));
                pingEntfernen(ap.id);
                ap.von = arg1; ap.farbe = Squads.PingFarbe.values()[Math.floorMod(arg1.hashCode(), Squads.PingFarbe.values().length)];
                ap.typ = Typ.NORMAL; ap.was = Component.translatable(b.getType().translationKey());
                ap.textOderSchluessel = b.getType().translationKey(); ap.istSchluessel = true;
                ap.zeit = System.currentTimeMillis(); ap.ende = Bukkit.getCurrentTick() + dauerTicks;
                Location ziel = b.getLocation().add(0.5, 1.0, 0.5);
                String sq = t == null ? "solo:" + p.getUniqueId() : Squads.name(t);
                Anzeige a = gleichesZiel(sq, ziel, null);
                if (a == null) a = neueAnzeige(sq, ziel, null, ap.farbe, strahlHoehe);
                ap.anzeige = a; ap.ziel = a.ziel.clone();
                a.pings.add(ap); aktivePings.put(ap.id, ap);
                if (!a.sehen.contains(p)) { a.sehen.add(p); p.showEntity(this, a.wegpunkt); }
                aktualisieren(a);
            }
            default -> {
                for (int i = 1; i <= 4; i++) p.sendMessage(Squads.info(Texte.tr("hilfe." + i)));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("squad") || !(sender instanceof Player p)) return List.of();
        boolean de = p.locale().getLanguage().equals("de");   // suggestions in the player's language (both names always work)
        if (args.length == 1) return filter(de
                ? List.of("erstellen", "beitreten", "annehmen", "einladen", "kicken", "leiter", "farbe", "pingfarbe", "steuerung",
                          "uebersicht", "offen", "geschlossen", "aufloesen", "verlassen", "liste", "kiste", "pingitem", "hilfe")
                : List.of("create", "join", "accept", "invite", "kick", "leader", "color", "pingcolor", "controls",
                          "overview", "open", "closed", "disband", "leave", "list", "chest", "pingitem", "help"), args[0]);
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.matches("beitreten|join")) return filter(squads.alle().stream().map(Squads::name).toList(), args[1]);
            if (sub.matches("annehmen|accept")) return filter(squads.einladungenVon(p).stream().map(Squads::name).toList(), args[1]);
            if (sub.matches("einladen|invite")) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            if (sub.matches("kicken|kick|leiter|leader")) { Team t = squads.squadVon(p); return t == null ? List.of() : filter(new ArrayList<>(t.getEntries()), args[1]); }
            if (sub.matches("farbe|color")) return filter(de ? new ArrayList<>(Squads.FARBEN.keySet()) : new ArrayList<>(Squads.FARBEN_EN.keySet()), args[1]);
        }
        if (args.length == 3 && args[0].toLowerCase(Locale.ROOT).matches("erstellen|create"))
            return filter(de ? new ArrayList<>(Squads.FARBEN.keySet()) : new ArrayList<>(Squads.FARBEN_EN.keySet()), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> optionen, String start) {
        String s = start.toLowerCase(Locale.ROOT);
        return optionen.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(s)).toList();
    }
}
