package io.github.xcollxn.teamping;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Squad data: members = scoreboard team (prefix sq_), leader/status in squads.yml, invitations in memory. */
final class Squads {

    static final String PREFIX = "sq_";
    static final long EINLADUNG_MS = 5 * 60_000L;

    static final Map<String, NamedTextColor> FARBEN = new LinkedHashMap<>();
    /** English colour names → internal (German) keys. */
    static final Map<String, String> FARBEN_EN = Map.of("red", "rot", "blue", "blau", "green", "gruen", "yellow", "gelb",
            "aqua", "aqua", "pink", "pink", "orange", "orange", "purple", "lila");

    /** Team colour from a German or English name (null = unknown). */
    static NamedTextColor farbeVon(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT).replace("ü", "ue");
        NamedTextColor c = FARBEN.get(n);
        return c != null ? c : FARBEN.get(FARBEN_EN.getOrDefault(n, ""));
    }
    static final Map<NamedTextColor, Material> GLAS = new HashMap<>();
    static final Map<NamedTextColor, Material> FARBSTOFF = new HashMap<>();
    static final Map<NamedTextColor, Material> WOLLE = new HashMap<>();
    static {
        add("rot", NamedTextColor.RED, "RED", "RED", "RED");
        add("blau", NamedTextColor.BLUE, "BLUE", "BLUE", "BLUE");
        add("gruen", NamedTextColor.GREEN, "LIME", "LIME", "LIME");
        add("gelb", NamedTextColor.YELLOW, "YELLOW", "YELLOW", "YELLOW");
        add("aqua", NamedTextColor.AQUA, "LIGHT_BLUE", "LIGHT_BLUE", "LIGHT_BLUE");
        add("pink", NamedTextColor.LIGHT_PURPLE, "MAGENTA", "MAGENTA", "MAGENTA");
        add("orange", NamedTextColor.GOLD, "ORANGE", "ORANGE", "ORANGE");
        add("lila", NamedTextColor.DARK_PURPLE, "PURPLE", "PURPLE", "PURPLE");
        add("weiss", NamedTextColor.WHITE, "WHITE", "WHITE", "WHITE");
    }
    private static void add(String name, NamedTextColor c, String glas, String dye, String wolle) {
        FARBEN.put(name, c);
        GLAS.put(c, Material.valueOf(glas + "_STAINED_GLASS"));
        FARBSTOFF.put(c, Material.valueOf(dye + "_DYE"));
        WOLLE.put(c, Material.valueOf(wolle + "_WOOL"));
    }

    /** Well distinguishable ping colours – each player gets their own within the squad. */
    enum PingFarbe {
        ROT("Rot", 0xFF3030, Material.RED_STAINED_GLASS, Material.RED_CONCRETE),
        ORANGE("Orange", 0xFF8C00, Material.ORANGE_STAINED_GLASS, Material.ORANGE_CONCRETE),
        GELB("Gelb", 0xFFE600, Material.YELLOW_STAINED_GLASS, Material.YELLOW_CONCRETE),
        GRUEN("Grün", 0x3CFF3C, Material.LIME_STAINED_GLASS, Material.LIME_CONCRETE),
        TUERKIS("Türkis", 0x00E5E5, Material.CYAN_STAINED_GLASS, Material.CYAN_CONCRETE),
        HELLBLAU("Hellblau", 0x40B4FF, Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_CONCRETE),
        BLAU("Blau", 0x3355FF, Material.BLUE_STAINED_GLASS, Material.BLUE_CONCRETE),
        LILA("Lila", 0xA845FF, Material.PURPLE_STAINED_GLASS, Material.PURPLE_CONCRETE),
        PINK("Pink", 0xFF4FD8, Material.MAGENTA_STAINED_GLASS, Material.MAGENTA_CONCRETE),
        WEISS("Weiß", 0xFFFFFF, Material.WHITE_STAINED_GLASS, Material.WHITE_CONCRETE);

        final String anzeige; final int rgb; final Material glas, beton;
        PingFarbe(String anzeige, int rgb, Material glas, Material beton) { this.anzeige = anzeige; this.rgb = rgb; this.glas = glas; this.beton = beton; }
        net.kyori.adventure.text.format.TextColor text() { return net.kyori.adventure.text.format.TextColor.color(rgb); }
        org.bukkit.Color bukkit() { return org.bukkit.Color.fromRGB(rgb); }
    }

    /** Ways to ping – each player sets them in the "Controls" menu. */
    enum Ausloeser {
        SHIFT_F("Schleichen + F", "Schleichen halten und F drücken"),
        F("F allein", "Nur F – ersetzt dann das Hand-Tauschen"),
        DOPPEL("2× schnell schleichen", "Schleichen-Taste zweimal kurz antippen"),
        SHIFT_Q("Schleichen + Q", "Schleichen + Q (wirft dabei nichts weg)"),
        SHIFT_LINKS("Schleichen + Linksklick", "Schleichen + Linksklick in die Luft"),
        EMOTE("Emote (Bedrock)", "Ein Emote machen = Ping");

        final String anzeige, beschreibung;
        Ausloeser(String a, String b) { anzeige = a; beschreibung = b; }
    }

    private final TeamPing plugin;
    private final File datei;
    private final YamlConfiguration daten;
    /** invited player -> (squad name -> expiry time) */
    private final Map<UUID, Map<String, Long>> einladungen = new HashMap<>();

    Squads(TeamPing plugin) {
        this.plugin = plugin;
        this.datei = new File(plugin.getDataFolder(), "squads.yml");
        this.daten = YamlConfiguration.loadConfiguration(datei);
        abgleichen();
    }

    // ------------------------------------------------------------------ queries
    Scoreboard board() { return Bukkit.getScoreboardManager().getMainScoreboard(); }

    Team team(String name) { return name == null ? null : board().getTeam(PREFIX + name.toLowerCase(Locale.ROOT)); }

    Team squadVon(Player p) { return squadVonName(p.getName()); }

    Team squadVonName(String name) {
        Team t = board().getEntryTeam(name);
        return (t != null && t.getName().startsWith(PREFIX)) ? t : null;
    }

    static String name(Team t) { return t.getName().substring(PREFIX.length()); }

    static NamedTextColor farbe(Team t) {
        return (t.hasColor() && t.color() instanceof NamedTextColor n) ? n : NamedTextColor.WHITE;
    }

    List<Team> alle() {
        return board().getTeams().stream().filter(t -> t.getName().startsWith(PREFIX))
                .sorted(Comparator.comparing(Team::getName)).toList();
    }

    UUID leiter(Team t) {
        String s = daten.getString(name(t) + ".leiter");
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    String leiterName(Team t) {
        UUID id = leiter(t);
        if (id == null) return "?";
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : "?";
    }

    boolean istLeiter(Player p, Team t) { return p.getUniqueId().equals(leiter(t)); }

    boolean offen(Team t) { return daten.getBoolean(name(t) + ".offen", true); }

    List<String> mitglieder(Team t) {
        List<String> m = new ArrayList<>(t.getEntries());
        String l = leiterName(t);
        m.sort(Comparator.comparing((String s) -> !s.equals(l)).thenComparing(String.CASE_INSENSITIVE_ORDER));
        return m;
    }

    List<Player> onlineMitglieder(Team t) {
        List<Player> l = new ArrayList<>();
        for (String e : t.getEntries()) { Player o = Bukkit.getPlayerExact(e); if (o != null) l.add(o); }
        return l;
    }

    // ------------------------------------------------------------------ messages
    static Component info(String text) {
        return Component.text("[Squad] ", NamedTextColor.DARK_AQUA).append(Component.text(text, NamedTextColor.GRAY));
    }

    static Component info(Component c) {
        return Component.text("[Squad] ", NamedTextColor.DARK_AQUA).append(c.colorIfAbsent(NamedTextColor.GRAY));
    }

    void anSquad(Team t, Component c) { for (Player o : onlineMitglieder(t)) o.sendMessage(c); }

    // ------------------------------------------------------------------ actions
    Team erstellen(Player p, String roh, NamedTextColor farbe) {
        String name = roh.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]{1,12}") || name.equals("spieler")) { p.sendMessage(info(Texte.tr("squad.name_ungueltig"))); return null; }
        if (team(name) != null) { p.sendMessage(info(Texte.tr("squad.gibt_es_schon", name))); return null; }
        if (farbe == null) farbe = freieFarbe();
        verlassen(p, false);
        Team t = board().registerNewTeam(PREFIX + name);
        t.displayName(Component.text(name));
        t.setAllowFriendlyFire(true);    // PvP within the squad allowed
        t.setCanSeeFriendlyInvisibles(true);
        farbeAnwenden(t, farbe);
        t.addEntry(p.getName());
        daten.set(name + ".leiter", p.getUniqueId().toString());
        daten.set(name + ".offen", true);
        pingFarbeEindeutig(p, t);
        speichern();
        p.sendActionBar(Texte.tr("squad.erstellt", Component.text(name, farbe)));
        return t;
    }

    boolean beitreten(Player p, Team t, boolean perEinladung) {
        if (t.equals(squadVon(p))) { p.sendMessage(info(Texte.tr("squad.schon_drin"))); return false; }
        if (!perEinladung && !offen(t) && !hatEinladung(p, name(t))) {
            p.sendMessage(info(Texte.tr("squad.nur_einladung"))); return false;
        }
        verlassen(p, false);
        t.addEntry(p.getName());
        entferneEinladung(p.getUniqueId(), name(t));
        pingFarbeEindeutig(p, t);
        anSquad(t, info(Texte.tr("squad.beigetreten", p.getName(), Component.text(name(t), farbe(t)))));
        return true;
    }

    void verlassen(Player p, boolean meldung) {
        Team t = squadVon(p);
        if (t == null) { if (meldung) p.sendMessage(info(Texte.tr("squad.kein_squad"))); return; }
        plugin.allePingsEntfernen(p.getUniqueId());
        boolean warLeiter = istLeiter(p, t);
        t.removeEntry(p.getName());
        if (meldung) p.sendActionBar(Texte.tr("squad.selbst_verlassen", name(t)));
        if (t.getEntries().isEmpty()) { aufloesenIntern(t); return; }
        anSquad(t, info(Texte.tr("squad.verlassen", p.getName())));
        if (warLeiter) leiterAutomatisch(t);
    }

    void kicken(Player leiter, String ziel) {
        Team t = squadVon(leiter);
        if (t == null || !istLeiter(leiter, t)) { leiter.sendMessage(info(Texte.tr("squad.nur_leiter_kick"))); return; }
        if (ziel.equalsIgnoreCase(leiter.getName())) { leiter.sendMessage(info(Texte.tr("squad.selbst_kick"))); return; }
        String eintrag = t.getEntries().stream().filter(e -> e.equalsIgnoreCase(ziel)).findFirst().orElse(null);
        if (eintrag == null) { leiter.sendMessage(info(Texte.tr("squad.nicht_im_squad", ziel))); return; }
        t.removeEntry(eintrag);
        Player o = Bukkit.getPlayerExact(eintrag);
        if (o != null) { plugin.allePingsEntfernen(o.getUniqueId()); o.sendMessage(info(Texte.tr("squad.gekickt", name(t)))); }
        anSquad(t, info(Texte.tr("squad.entfernt", eintrag)));
    }

    void leiterUebertragen(Player leiter, String ziel) {
        Team t = squadVon(leiter);
        if (t == null || !istLeiter(leiter, t)) return;
        String eintrag = t.getEntries().stream().filter(e -> e.equalsIgnoreCase(ziel)).findFirst().orElse(null);
        UUID id = uuidVon(eintrag);
        if (id == null) { leiter.sendMessage(info(Texte.tr("squad.leiter_unbekannt"))); return; }
        daten.set(name(t) + ".leiter", id.toString());
        speichern();
        anSquad(t, info(Texte.tr("squad.neuer_leiter", eintrag)));
    }

    private void leiterAutomatisch(Team t) {
        for (String e : mitglieder(t)) {
            UUID id = uuidVon(e);
            if (id != null) {
                daten.set(name(t) + ".leiter", id.toString()); speichern();
                anSquad(t, info(Texte.tr("squad.neuer_leiter", e)));
                return;
            }
        }
    }

    void farbeSetzen(Player leiter, NamedTextColor farbe) {
        Team t = squadVon(leiter);
        if (t == null || !istLeiter(leiter, t)) { leiter.sendMessage(info(Texte.tr("squad.nur_leiter_farbe"))); return; }
        farbeAnwenden(t, farbe);
        leiter.sendActionBar(Texte.tr("squad.neue_farbe", Texte.tr("farbe." + farbName(farbe)).color(farbe)));
    }

    void offenSetzen(Player leiter, boolean offen) {
        Team t = squadVon(leiter);
        if (t == null || !istLeiter(leiter, t)) return;
        daten.set(name(t) + ".offen", offen); speichern();
        leiter.sendActionBar(Texte.tr(offen ? "squad.jetzt_offen" : "squad.jetzt_geschlossen"));
    }

    void aufloesen(Player leiter) {
        Team t = squadVon(leiter);
        if (t == null || !istLeiter(leiter, t)) { leiter.sendMessage(info(Texte.tr("squad.nur_leiter_aufloesen"))); return; }
        anSquad(t, info(Texte.tr("squad.aufgeloest", name(t), leiter.getName())));
        for (Player o : onlineMitglieder(t)) plugin.allePingsEntfernen(o.getUniqueId());
        aufloesenIntern(t);
    }

    private void aufloesenIntern(Team t) {
        String n = name(t);
        t.unregister();
        daten.set(n, null); speichern();
        for (Map<String, Long> m : einladungen.values()) m.remove(n);
    }

    private void farbeAnwenden(Team t, NamedTextColor farbe) {
        t.color(farbe);
        t.prefix(Component.text("[" + name(t) + "] ", farbe));
    }

    // ------------------------------------------------------------------ invitations
    void einladen(Player von, Player an) {
        Team t = squadVon(von);
        if (t == null) { von.sendMessage(info(Texte.tr("squad.kein_squad"))); return; }
        if (!offen(t) && !istLeiter(von, t)) { von.sendMessage(info(Texte.tr("squad.nur_leiter_einladen"))); return; }
        if (t.equals(squadVon(an))) { von.sendMessage(info(Texte.tr("squad.schon_im_squad", an.getName()))); return; }
        einladungen.computeIfAbsent(an.getUniqueId(), k -> new HashMap<>()).put(name(t), System.currentTimeMillis() + EINLADUNG_MS);
        von.sendMessage(info(Texte.tr("squad.eingeladen", an.getName())));
        String n = name(t);
        an.sendMessage(info(Texte.tr("squad.einladung", von.getName(), Component.text(n, farbe(t)))
                .append(Texte.tr("squad.annehmen").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/squad annehmen " + n))
                        .hoverEvent(HoverEvent.showText(Texte.tr("squad.annehmen_hover", n))))
                .append(Texte.tr("squad.oder_menue").color(NamedTextColor.DARK_GRAY))));
        an.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key("entity.experience_orb.pickup"),
                net.kyori.adventure.sound.Sound.Source.PLAYER, 0.8f, 1.2f));
    }

    List<Team> einladungenVon(Player p) {
        Map<String, Long> m = einladungen.get(p.getUniqueId());
        if (m == null) return List.of();
        long jetzt = System.currentTimeMillis();
        m.entrySet().removeIf(e -> e.getValue() < jetzt || team(e.getKey()) == null);
        return m.keySet().stream().map(this::team).filter(Objects::nonNull).toList();
    }

    boolean hatEinladung(Player p, String squad) {
        return einladungenVon(p).stream().anyMatch(t -> name(t).equals(squad));
    }

    boolean annehmen(Player p, String squad) {
        Team t = team(squad);
        if (t == null || !hatEinladung(p, name(t))) { p.sendMessage(info(Texte.tr("squad.keine_einladung"))); return false; }
        return beitreten(p, t, true);
    }

    private void entferneEinladung(UUID id, String squad) {
        Map<String, Long> m = einladungen.get(id);
        if (m != null) m.remove(squad);
    }

    // ------------------------------------------------------------------ ping colours (per player)
    PingFarbe pingFarbe(Player p) {
        PingFarbe f = gespeicherteFarbe(p.getUniqueId());
        if (f == null) { f = zufallsFarbe(p, squadVon(p), null); daten.set("spieler." + p.getUniqueId() + ".pingfarbe", f.name()); speichern(); }
        return f;
    }

    private PingFarbe gespeicherteFarbe(UUID id) {
        String s = daten.getString("spieler." + id + ".pingfarbe");
        try { return s == null ? null : PingFarbe.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    /** Colours other squad members already have. */
    private Set<PingFarbe> belegteFarben(Player p, Team t) {
        Set<PingFarbe> belegt = EnumSet.noneOf(PingFarbe.class);
        if (t == null) return belegt;
        for (String e : t.getEntries()) {
            if (e.equals(p.getName())) continue;
            UUID id = uuidVon(e);
            PingFarbe f = id == null ? null : gespeicherteFarbe(id);
            if (f != null) belegt.add(f);
        }
        return belegt;
    }

    private PingFarbe zufallsFarbe(Player p, Team t, PingFarbe nichtDiese) {
        List<PingFarbe> frei = new ArrayList<>(List.of(PingFarbe.values()));
        frei.removeAll(belegteFarben(p, t));
        if (nichtDiese != null && frei.size() > 1) frei.remove(nichtDiese);
        if (frei.isEmpty()) frei = new ArrayList<>(List.of(PingFarbe.values()));
        return frei.get(new Random().nextInt(frei.size()));
    }

    /** After joining: does someone in the squad already have my colour? Then pick a new one. */
    void pingFarbeEindeutig(Player p, Team t) {
        PingFarbe f = gespeicherteFarbe(p.getUniqueId());
        if (f == null || belegteFarben(p, t).contains(f)) {
            f = zufallsFarbe(p, t, null);
            daten.set("spieler." + p.getUniqueId() + ".pingfarbe", f.name()); speichern();
        }
        plugin.locatorFarbe(p);
    }

    PingFarbe pingFarbeWuerfeln(Player p) {
        PingFarbe f = zufallsFarbe(p, squadVon(p), gespeicherteFarbe(p.getUniqueId()));
        daten.set("spieler." + p.getUniqueId() + ".pingfarbe", f.name()); speichern();
        plugin.locatorFarbe(p);
        return f;
    }

    PingFarbe pingFarbeVonName(String name) {
        UUID id = uuidVon(name);
        return id == null ? null : gespeicherteFarbe(id);
    }

    // ------------------------------------------------------------------ squad overview (per player)
    /** Which ping type an emote of this player triggers: "normal", "folgen" (follow me) or "gefahr" (danger); unassigned = normal. */
    String emoteArt(Player p, String emoteId) { return daten.getString("spieler." + p.getUniqueId() + ".emotes." + emoteId, "normal"); }
    void emoteSetzen(Player p, String emoteId, String art) {
        String basis = "spieler." + p.getUniqueId() + ".emotes";
        var sek = daten.getConfigurationSection(basis);   // assign each type to one emote only
        if (sek != null) for (String k : sek.getKeys(false)) if (art.equals(sek.getString(k))) daten.set(basis + "." + k, null);
        daten.set(basis + "." + emoteId, art); speichern();
    }
    void emotesLoeschen(Player p) { daten.set("spieler." + p.getUniqueId() + ".emotes", null); speichern(); }
    /** Assigned emote per type (only for display: whether one is stored already). */
    boolean emoteZugeordnet(Player p, String art) {
        var sek = daten.getConfigurationSection("spieler." + p.getUniqueId() + ".emotes");
        if (sek == null) return false;
        for (String k : sek.getKeys(false)) if (art.equals(sek.getString(k))) return true;
        return false;
    }

    /** Simple yes/no flags per player (e.g. "already got the ping item"). */
    boolean merker(Player p, String k) { return merker(p.getUniqueId(), k); }
    boolean merker(UUID id, String k) { return daten.getBoolean("spieler." + id + "." + k, false); }
    void merkerSetzen(Player p, String k, boolean v) { daten.set("spieler." + p.getUniqueId() + "." + k, v); speichern(); }

    /** How a player wants to see pings (set by the mod): beam or dot only, width/opacity/height in percent. */
    record Aussehen(boolean strahl, float breite, float hell, float hoehe) {
        static final Aussehen STANDARD = new Aussehen(true, 1f, 1f, 1f);
    }

    Aussehen aussehen(UUID id) {
        String k = "spieler." + id + ".aussehen.";
        if (!daten.contains(k + "strahl")) return Aussehen.STANDARD;
        return new Aussehen(daten.getBoolean(k + "strahl", true), daten.getInt(k + "breite", 100) / 100f,
                daten.getInt(k + "hell", 100) / 100f, daten.getInt(k + "hoehe", 100) / 100f);
    }

    void aussehenSetzen(UUID id, boolean strahl, int breite, int hell, int hoehe) {
        String k = "spieler." + id + ".aussehen.";
        daten.set(k + "strahl", strahl);
        daten.set(k + "breite", Math.max(50, Math.min(200, breite)));
        daten.set(k + "hell", Math.max(30, Math.min(100, hell)));
        daten.set(k + "hoehe", Math.max(25, Math.min(150, hoehe)));
        speichern();
    }

    boolean uebersichtAn(Player p) { return daten.getBoolean("spieler." + p.getUniqueId() + ".uebersicht", true); }

    /** Bedrock: where the squad overview is. LEISTEN = boss bars at the top, otherwise a box (resource pack, ui/hud_screen.json). */
    enum HudPos { RECHTS, LINKS, OBEN_RECHTS, LEISTEN }

    HudPos hudPos(Player p) {
        try { return HudPos.valueOf(daten.getString("spieler." + p.getUniqueId() + ".hudpos", "RECHTS")); }
        catch (IllegalArgumentException e) { return HudPos.RECHTS; }
    }

    /** Next step: right → left → top right → bars at the top → off → right … */
    void hudWeiter(Player p) {
        if (!uebersichtAn(p)) { uebersichtUmschalten(p); daten.set("spieler." + p.getUniqueId() + ".hudpos", "RECHTS"); speichern(); return; }
        HudPos[] alle = HudPos.values();
        int i = hudPos(p).ordinal();
        if (i == alle.length - 1) { uebersichtUmschalten(p); return; }
        daten.set("spieler." + p.getUniqueId() + ".hudpos", alle[i + 1].name()); speichern();
    }

    void uebersichtUmschalten(Player p) {
        daten.set("spieler." + p.getUniqueId() + ".uebersicht", !uebersichtAn(p)); speichern();
    }

    // ------------------------------------------------------------------ controls (per player)
    boolean aktiv(Player p, Ausloeser a) {
        String pfad = "spieler." + p.getUniqueId() + ".steuerung." + a.name();
        if (daten.contains(pfad)) return daten.getBoolean(pfad);
        boolean bedrock = p.getUniqueId().getMostSignificantBits() == 0L;
        String modus = plugin.cfgString("double-sneak", "doppel-schleichen", "off");
        return switch (a) {
            case SHIFT_F -> !bedrock && plugin.getConfig().getBoolean("shift-f", true);
            case DOPPEL -> modus.equalsIgnoreCase("all") || modus.equalsIgnoreCase("alle") || (bedrock && modus.equalsIgnoreCase("bedrock"));
            case EMOTE -> bedrock;   // Bedrock: emote button = ping (phone, PC key B, controller)
            default -> false;
        };
    }

    void umschalten(Player p, Ausloeser a) {
        daten.set("spieler." + p.getUniqueId() + ".steuerung." + a.name(), !aktiv(p, a));
        speichern();
    }

    // ------------------------------------------------------------------ misc
    NamedTextColor freieFarbe() {
        Set<NamedTextColor> belegt = new HashSet<>();
        for (Team t : alle()) belegt.add(farbe(t));
        for (NamedTextColor c : FARBEN.values()) if (!belegt.contains(c)) return c;
        return NamedTextColor.WHITE;
    }

    static String farbName(NamedTextColor c) {
        for (Map.Entry<String, NamedTextColor> e : FARBEN.entrySet()) if (e.getValue().equals(c)) return e.getKey();
        return c.toString();
    }

    private static UUID uuidVon(String name) {
        if (name == null) return null;
        Player o = Bukkit.getPlayerExact(name);
        if (o != null) return o.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op != null ? op.getUniqueId() : null;
    }

    /** Sync data with the scoreboard teams (e.g. after a plugin update). */
    private void abgleichen() {
        for (Team t : alle()) {
            if (leiter(t) == null) {
                for (String e : t.getEntries()) { UUID id = uuidVon(e); if (id != null) { daten.set(name(t) + ".leiter", id.toString()); break; } }
            }
            if (!daten.contains(name(t) + ".offen")) daten.set(name(t) + ".offen", true);
            t.setAllowFriendlyFire(true);    // PvP within the squad allowed (also for old squads)
        }
        for (String k : new ArrayList<>(daten.getKeys(false))) if (!k.equals("spieler") && team(k) == null) daten.set(k, null);
        speichern();
    }

    void speichern() {
        try { plugin.getDataFolder().mkdirs(); daten.save(datei); }
        catch (IOException e) { plugin.getLogger().warning("Could not save squads.yml: " + e.getMessage()); }
    }
}
