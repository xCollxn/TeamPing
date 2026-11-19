package io.github.xcollxn.teamping;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;
import java.util.function.Consumer;

/**
 * Native Bedrock forms (Floodgate/Cumulus) – same features as the Java window, as similar as possible:
 * buttons with the same icons as the Java tabs (book = invite, hook = controls, comparator = settings),
 * all texts in the device's language, closing (X) a sub form leads back.
 */
final class BedrockMenus {

    private BedrockMenus() {}

    // ------------------------------------------------------------------ building blocks
    /** Button list: text + icon (Bedrock texture path) + action. */
    private static final class Knoepfe {
        final List<String> texte = new ArrayList<>();
        final List<String> bilder = new ArrayList<>();
        final List<Consumer<Player>> aktionen = new ArrayList<>();
        void add(String text, String bild, Consumer<Player> a) { texte.add(text); bilder.add(bild); aktionen.add(a); }
    }

    /**
     * Markers for the TeamPing resource pack (ui/server_form.json): forms with {@link #MARKE} in the title get the
     * Java-like look (frame, tabs at the top, compact rows). Buttons with {@link #REITER} / {@link #REITER_AKTIV}
     * appear as tab icons. All of these are Bedrock colour codes – invisible without the pack.
     */
    static final String MARKE = "§t§p", REITER = "§p§1", REITER_AKTIV = "§p§2";

    private enum Reiter { SQUAD, EINLADEN, STEUERUNG, EMOTES }

    /** Tabs like in the Java window (squad, invite, controls, emotes) – always the first buttons. */
    private static void reiter(TeamPing pl, Player p, Knoepfe k, Reiter aktiv) {
        Team t = pl.squads().squadVon(p);
        k.add((aktiv == Reiter.SQUAD ? REITER_AKTIV : REITER) + (t == null ? t(p, "menu.titel") : Squads.name(t)), BILD_SQUAD, x -> oeffnen(pl, x));
        if (t != null) k.add((aktiv == Reiter.EINLADEN ? REITER_AKTIV : REITER) + t(p, "menu.einladen"), BILD_EINLADEN, x -> einladen(pl, x));
        k.add((aktiv == Reiter.STEUERUNG ? REITER_AKTIV : REITER) + t(p, "menu.steuerung"), BILD_STEUERUNG, x -> steuerung(pl, x));
        k.add((aktiv == Reiter.EMOTES ? REITER_AKTIV : REITER) + t(p, "bedrock.emotes"), BILD_EMOTES, x -> emotes(pl, x));
    }

    /**
     * Bedrock forms (like Bedrock itself) have a light background → replace light colour codes with darker ones.
     * Markers (§t §p §1 §2) stay untouched.
     */
    static String hell(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                char neu = switch (n) { case 'a' -> '2'; case 'b' -> '3'; case 'e' -> '6'; case 'f' -> '0'; case '7' -> '8'; case 'd' -> '5'; default -> n; };
                b.append('§').append(neu); i++;
            } else b.append(c);
        }
        return b.toString();
    }

    /** Simple form; back = what happens on close (X) (null = close completely). */
    private static void zeigen(TeamPing pl, Player p, String titel, String inhalt, Knoepfe k, Consumer<Player> zurueck) {
        SimpleForm.Builder b = SimpleForm.builder().title(MARKE + hell(titel)).content(hell(inhalt));
        for (int i = 0; i < k.texte.size(); i++) {
            String bild = k.bilder.get(i);
            String text = hell(k.texte.get(i).replace("◆ ", "").replace("✔", TeamPing.HAKEN_BEDROCK).replace("✓", TeamPing.HAKEN_BEDROCK));   // the diamond is missing from Bedrock's pixel font -> otherwise a small fallback font
            if (bild == null) b.button(text);
            else b.button(text, bild.startsWith("http") ? FormImage.Type.URL : FormImage.Type.PATH, bild);
        }
        b.validResultHandler(r -> sync(pl, () -> {
            int i = r.clickedButtonId();
            if (i >= 0 && i < k.aktionen.size() && p.isOnline()) k.aktionen.get(i).accept(p);
        }));
        if (zurueck != null) b.closedOrInvalidResultHandler(() -> sync(pl, () -> { if (p.isOnline()) zurueck.accept(p); }));
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), b.build());
    }

    private static void sync(TeamPing pl, Runnable r) { Bukkit.getScheduler().runTask(pl, r); }

    /** Short form: text in the player's language (with § colour codes). */
    private static String t(Player p, String schluessel, Object... werte) { return Texte.s(p, schluessel, werte); }

    private static String farbName(Player p, String id) { return t(p, "farbe." + id); }

    static String farbcode(net.kyori.adventure.text.format.NamedTextColor c) {
        return switch (c.toString()) {
            case "red" -> "§c"; case "blue" -> "§9"; case "green" -> "§a"; case "yellow" -> "§e"; case "aqua" -> "§b";
            case "light_purple" -> "§d"; case "gold" -> "§6"; case "dark_purple" -> "§5"; default -> "§f";
        };
    }

    static String farbcode(Squads.PingFarbe f) {
        return switch (f) {
            case ROT -> "§c"; case ORANGE -> "§6"; case GELB -> "§e"; case GRUEN -> "§a"; case TUERKIS -> "§3";
            case HELLBLAU -> "§b"; case BLAU -> "§9"; case LILA -> "§5"; case PINK -> "§d"; case WEISS -> "§f";
        };
    }

    // Icons (Bedrock default textures) – the same items as the Java tabs
    private static final String BILD_SQUAD = "textures/ui/icon_steve";
    private static final String BILD_HAKEN_AN = "textures/ui/teamping/haken_an";
    private static final String BILD_HAKEN_AUS = "textures/ui/teamping/haken_aus";
    private static final String BILD_ERSTELLEN = "textures/items/nether_star";
    private static final String BILD_EINLADEN = "textures/items/book_writable";
    private static final String BILD_STEUERUNG = "textures/blocks/trip_wire_source";
    private static final String BILD_EINSTELLUNGEN = "textures/items/comparator";
    private static final String BILD_EMOTES = "textures/items/clock_item";
    private static final String BILD_GEGENSTAND = "textures/items/amethyst_shard";
    private static final String BILD_CHAT = "textures/items/paper";
    private static final String BILD_UEBERSICHT = "textures/items/spyglass";
    private static final String BILD_AUFLOESEN = "textures/blocks/tnt_side";
    private static final String BILD_VERLASSEN = "textures/blocks/barrier";
    private static final String BILD_ZURUECK = "textures/items/arrow";

    /** Head image for the member row. Bedrock players (Floodgate name with a dot) have no Java skin → Steve. */
    private static String kopf(TeamPing pl, String name) {
        // external service (mc-heads.net): only if the server owner enabled it in the config
        if (!pl.getConfig().getBoolean("bedrock-player-heads", false)) return BILD_SQUAD;
        if (name.startsWith(".") || !name.matches("[A-Za-z0-9_]{3,16}")) return BILD_SQUAD;
        return "https://mc-heads.net/avatar/" + name + "/64";
    }

    private static String wolle(net.kyori.adventure.text.format.NamedTextColor c) {
        String f = switch (c.toString()) {
            case "red" -> "red"; case "blue" -> "blue"; case "green" -> "lime"; case "yellow" -> "yellow"; case "aqua" -> "light_blue";
            case "light_purple" -> "magenta"; case "gold" -> "orange"; case "dark_purple" -> "purple"; default -> "white";
        };
        return "textures/blocks/wool_colored_" + f;
    }

    private static String farbstoff(Squads.PingFarbe f) {
        return "textures/items/dye_powder_" + switch (f) {
            case ROT -> "red"; case ORANGE -> "orange"; case GELB -> "yellow"; case GRUEN -> "lime"; case TUERKIS -> "cyan";
            case HELLBLAU -> "light_blue"; case BLAU -> "blue"; case LILA -> "purple"; case PINK -> "magenta"; case WEISS -> "white";
        };
    }

    /** Only for tests via the console: open a specific form. */
    static void testOeffnen(TeamPing pl, Player p, String welches) {
        switch (welches) {
            case "steuerung" -> steuerung(pl, p);
            case "emotes" -> emotes(pl, p);
            case "einladen" -> einladen(pl, p);
            case "erstellen" -> erstellen(pl, p);
            case "zu" -> testSchliessen(p);
            default -> oeffnen(pl, p);
        }
    }

    /** Only for tests: close an open Bedrock form. */
    static void testSchliessen(Player p) {
        var c = org.geysermc.geyser.api.GeyserApi.api().connectionByUuid(p.getUniqueId());
        if (c != null) c.closeForm();
    }

    // ------------------------------------------------------------------ entry
    static void oeffnen(TeamPing pl, Player p) {
        Team t = pl.squads().squadVon(p);
        if (t == null) haupt(pl, p); else squad(pl, p, t);
    }

    static void haupt(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        Knoepfe k = new Knoepfe();
        reiter(pl, p, k, Reiter.SQUAD);
        k.add("§a" + t(p, "menu.erstellen"), BILD_ERSTELLEN, x -> erstellen(pl, x));
        List<Team> einl = sq.einladungenVon(p);
        if (!einl.isEmpty()) k.add("§6" + t(p, "menu.einladungen", einl.size()), BILD_EINLADEN, x -> einladungen(pl, x));
        for (Team t : sq.alle()) {
            boolean offen = sq.offen(t), eingeladen = sq.hatEinladung(p, Squads.name(t));
            k.add(farbcode(Squads.farbe(t)) + "§l" + Squads.name(t) + "§r\n§7" + (offen || eingeladen ? t(p, "bedrock.beitreten") : t(p, "menu.geschlossen"))
                            + " · " + t(p, "bedrock.spieler", t.getEntries().size()), wolle(Squads.farbe(t)),
                    x -> { if (sq.beitreten(x, t, eingeladen)) squad(pl, x, t); });
        }
        k.add("§b" + t(p, "menu.pinggegenstand"), BILD_GEGENSTAND, x -> { pl.pingGegenstandGeben(x); haupt(pl, x); });
        zeigen(pl, p, t(p, "menu.titel"), t(p, "bedrock.haupt") + "\n" + pingenHinweis(pl, p), k, null);
    }

    static void erstellen(TeamPing pl, Player p) {
        List<String> farben = new ArrayList<>(Squads.FARBEN.keySet());
        List<String> anzeige = farben.stream().map(f -> farbName(p, f)).toList();
        CustomForm f = CustomForm.builder().title(t(p, "menu.erstellen"))
                .input(t(p, "bedrock.name"), t(p, "bedrock.name_beispiel"))
                .dropdown(t(p, "bedrock.teamfarbe"), anzeige)
                .validResultHandler(r -> sync(pl, () -> {
                    String name = r.asInput(0);
                    String farbe = farben.get(r.asDropdown(1));
                    Team t = pl.squads().erstellen(p, name == null ? "" : name, Squads.FARBEN.get(farbe));
                    if (t != null) squad(pl, p, t); else haupt(pl, p);
                }))
                .closedOrInvalidResultHandler(() -> sync(pl, () -> haupt(pl, p)))
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), f);
    }

    static void squad(TeamPing pl, Player p, Team t) {
        Squads sq = pl.squads();
        boolean leiter = sq.istLeiter(p, t), offen = sq.offen(t);
        String leiterName = sq.leiterName(t);
        List<String> mitglieder = sq.mitglieder(t);
        // Only characters from Bedrock's pixel font (no stars/dots/boxes) – otherwise Bedrock uses a smaller fallback font for the whole text
        String inhalt = "§f" + t(p, "bedrock.mitglieder") + " (" + mitglieder.size() + ")   "
                + (offen ? "§a" + t(p, "bedrock.status_offen") : "§6" + t(p, "bedrock.status_geschlossen")) + "§r\n§7" + pingenHinweis(pl, p)
                + (leiter && mitglieder.size() > 1 ? "\n§7" + t(p, "bedrock.mitglied_antippen") : "");

        Knoepfe k = new Knoepfe();
        reiter(pl, p, k, Reiter.SQUAD);
        for (String m : mitglieder) {
            Squads.PingFarbe f = sq.pingFarbeVonName(m);
            Player on = Bukkit.getPlayerExact(m);
            StringBuilder z = new StringBuilder(on != null ? (f != null ? farbcode(f) : "§f") : "§7").append(m);   // name in ping colour
            if (m.equals(leiterName)) z.append(" §6(").append(t(p, "bedrock.leiter_kurz")).append(")");
            if (m.equals(p.getName())) z.append(" §7(").append(t(p, "bedrock.du")).append(")");
            z.append("\n");
            if (on == null) z.append("§7").append(t(p, "bedrock.offline"));
            else {
                z.append("§c").append(TeamPing.HERZ_BEDROCK).append(on.getHealth() <= 6 ? "§c" : "§8").append(Math.round(on.getHealth()));
                if (!on.equals(p)) z.append("   §8").append(on.getWorld().equals(p.getWorld())
                        ? Math.round(on.getLocation().distance(p.getLocation())) + " m " + TeamPing.pfeil(p, on.getLocation())
                        : Uebersicht.weltName(on.getWorld()));
            }
            boolean verwaltbar = leiter && !m.equals(p.getName());
            k.add(z.toString(), kopf(pl, m), x -> { if (verwaltbar) mitglied(pl, x, m); else squad(pl, x, t); });
        }
        Squads.PingFarbe eigene = sq.pingFarbe(p);
        k.add(t(p, "menu.meine_pingfarbe", Texte.tr("pingfarbe." + eigene.name())) + "\n§7" + t(p, "bedrock.antippen_farbe"), farbstoff(eigene),
                x -> { sq.pingFarbeWuerfeln(x); squad(pl, x, t); });
        boolean chat = pl.squadChatAn(p), hud = sq.uebersichtAn(p);
        k.add(t(p, "squad.chat", Texte.tr(chat ? "an" : "aus").color(chat ? net.kyori.adventure.text.format.NamedTextColor.GREEN : net.kyori.adventure.text.format.NamedTextColor.GRAY)),
                BILD_CHAT, x -> { pl.squadChatUmschalten(x); squad(pl, x, t); });
        String hudText = hud ? "§2" + t(p, "bedrock.hud." + sq.hudPos(p).name()) : "§8" + t(p, "aus");
        k.add(t(p, "bedrock.hud.knopf") + " " + hudText + "\n§8" + t(p, "bedrock.hud.antippen"),
                BILD_UEBERSICHT, x -> { sq.hudWeiter(x); squad(pl, x, t); });
        k.add("§b" + t(p, "menu.pinggegenstand"), BILD_GEGENSTAND, x -> { pl.pingGegenstandGeben(x); squad(pl, x, t); });
        if (leiter) {
            k.add("§6" + t(p, "bedrock.einstellungen"), BILD_EINSTELLUNGEN, x -> einstellungen(pl, x, t));
            k.add("§c" + t(p, "menu.aufloesen"), BILD_AUFLOESEN, x -> frage(pl, x, t(x, "menu.aufloesen.frage"), t(x, "bedrock.aufloesen_text"), y -> { sq.aufloesen(y); haupt(pl, y); }));
        }
        k.add("§c" + t(p, "menu.verlassen"), BILD_VERLASSEN, x -> frage(pl, x, t(x, "menu.verlassen.frage"), t(x, leiter ? "menu.verlassen.leiter" : "menu.verlassen.normal"),
                y -> { sq.verlassen(y, true); haupt(pl, y); }));
        zeigen(pl, p, farbcode(Squads.farbe(t)) + t(p, "menu.squad_titel", Squads.name(t)), inhalt, k, null);
    }

    static void einladen(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        Team t = sq.squadVon(p);
        if (t == null) { haupt(pl, p); return; }
        Knoepfe k = new Knoepfe();
        reiter(pl, p, k, Reiter.EINLADEN);
        int vorher = k.texte.size();
        boolean darf = sq.offen(t) || sq.istLeiter(p, t);
        if (darf) for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || t.equals(sq.squadVon(o))) continue;
            Team anderes = sq.squadVon(o);
            k.add("§f" + o.getName() + "\n§7" + (anderes == null ? t(p, "menu.kein_squad") : t(p, "menu.gerade_in", Squads.name(anderes))), BILD_EINLADEN,
                    x -> { sq.einladen(x, o); squad(pl, x, t); });
        }
        String hinweis = k.texte.size() == vorher ? t(p, "menu.niemand") : t(p, "bedrock.wen_einladen");
        if (!darf) hinweis = t(p, "menu.geschlossen");
        zeigen(pl, p, t(p, "menu.einladen"), hinweis, k, x -> oeffnen(pl, x));
    }

    static void einladungen(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        Knoepfe k = new Knoepfe();
        for (Team t : sq.einladungenVon(p))
            k.add(farbcode(Squads.farbe(t)) + "§l" + Squads.name(t) + "§r\n§7" + t(p, "menu.leiter", sq.leiterName(t)), wolle(Squads.farbe(t)),
                    x -> { if (sq.annehmen(x, Squads.name(t))) oeffnen(pl, x); });
        k.add(t(p, "menu.zurueck"), BILD_ZURUECK, x -> haupt(pl, x));
        zeigen(pl, p, t(p, "menu.einladungen_titel"), t(p, "bedrock.antippen_annehmen"), k, x -> haupt(pl, x));
    }

    static void mitglied(TeamPing pl, Player p, String m) {
        Squads sq = pl.squads();
        Knoepfe k = new Knoepfe();
        k.add("§6" + t(p, "menu.zum_leiter"), "textures/items/gold_helmet",
                x -> frage(pl, x, t(x, "menu.zum_leiter.frage", m), t(x, "bedrock.leiter_text", m), y -> { sq.leiterUebertragen(y, m); oeffnen(pl, y); }));
        k.add("§c" + t(p, "menu.kicken"), "textures/items/iron_sword",
                x -> frage(pl, x, t(x, "menu.kicken.frage", m), t(x, "bedrock.kick_text", m), y -> { sq.kicken(y, m); oeffnen(pl, y); }));
        k.add(t(p, "menu.zurueck"), BILD_ZURUECK, x -> oeffnen(pl, x));
        zeigen(pl, p, m, t(p, "bedrock.was_tun"), k, x -> oeffnen(pl, x));
    }

    static void einstellungen(TeamPing pl, Player p, Team t) {
        Squads sq = pl.squads();
        List<String> farben = new ArrayList<>(Squads.FARBEN.keySet());
        List<String> anzeige = farben.stream().map(f -> farbName(p, f)).toList();
        int aktuell = Math.max(0, farben.indexOf(Squads.farbName(Squads.farbe(t))));
        CustomForm f = CustomForm.builder().title(t(p, "bedrock.einstellungen"))
                .dropdown(t(p, "bedrock.teamfarbe"), anzeige, aktuell)
                .toggle(t(p, "bedrock.offen_toggle"), sq.offen(t))
                .validResultHandler(r -> sync(pl, () -> {
                    var neu = Squads.FARBEN.get(farben.get(r.asDropdown(0)));
                    if (!neu.equals(Squads.farbe(t))) sq.farbeSetzen(p, neu);
                    boolean offen = r.asToggle(1);
                    if (offen != sq.offen(t)) sq.offenSetzen(p, offen);
                    squad(pl, p, t);
                }))
                .closedOrInvalidResultHandler(() -> sync(pl, () -> oeffnen(pl, p)))
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), f);
    }

    /** Gestures that work on this device at all: phone = emote + double sneak, PC/console also Q/left click. Bedrock has no F. */
    private static List<Squads.Ausloeser> gestenFuer(Player p) {
        List<Squads.Ausloeser> l = new ArrayList<>(List.of(Squads.Ausloeser.EMOTE, Squads.Ausloeser.DOPPEL));
        if (!BedrockCheck.touch(p)) { l.add(Squads.Ausloeser.SHIFT_Q); l.add(Squads.Ausloeser.SHIFT_LINKS); }
        return l;
    }

    /** Controls like in the Java window: one row per gesture with a checkbox, tap = on/off. */
    /** "Ping: emote, double sneak, /ping" – built from the gestures the player has enabled. */
    private static String pingenHinweis(TeamPing pl, Player p) {
        List<String> an = new ArrayList<>();
        for (Squads.Ausloeser a : gestenFuer(p)) if (pl.squads().aktiv(p, a)) an.add(t(p, "geste." + a.name()));
        an.add("/ping");
        return t(p, "bedrock.pingen_mit", String.join(", ", an));
    }

    static void steuerung(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        Knoepfe k = new Knoepfe();
        reiter(pl, p, k, Reiter.STEUERUNG);
        for (Squads.Ausloeser a : gestenFuer(p))
            k.add("§f" + t(p, "geste." + a.name()) + "\n§7" + t(p, "geste." + a.name() + ".info"), sq.aktiv(p, a) ? BILD_HAKEN_AN : BILD_HAKEN_AUS,
                    x -> { sq.umschalten(x, a); steuerung(pl, x); });
        zeigen(pl, p, t(p, "menu.steuerung_titel"), t(p, "bedrock.steuerung_info"), k, x -> oeffnen(pl, x));
    }

    /** Assign emotes: choose a ping type, then do the wanted emote (learn mode in the plugin). */
    static void emotes(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        Knoepfe k = new Knoepfe();
        reiter(pl, p, k, Reiter.EMOTES);
        for (String[] art : new String[][]{{"normal", "textures/items/compass_item"}, {"folgen", "textures/items/lead"}, {"gefahr", "textures/items/redstone_dust"}}) {
            String gesetzt = sq.emoteZugeordnet(p, art[0]) ? "  §a" + t(p, "bedrock.emotes.gesetzt") : "";
            k.add("§f" + t(p, "emote.art." + art[0]) + gesetzt + "\n§7" + t(p, "bedrock.emotes.tipp_" + art[0]), art[1],
                    x -> pl.emoteLernenStarten(x, art[0]));   // the form closes – now do the emote
        }
        k.add("§c" + t(p, "bedrock.emotes.loeschen"), BILD_VERLASSEN, x -> { sq.emotesLoeschen(x); x.sendActionBar(Texte.tr("emote.geloescht")); emotes(pl, x); });
        zeigen(pl, p, t(p, "bedrock.emotes"), t(p, "bedrock.emotes.text"), k, x -> oeffnen(pl, x));
    }

    static void frage(TeamPing pl, Player p, String titel, String text, Consumer<Player> ja) {
        ModalForm f = ModalForm.builder().title(titel).content(text).button1("§2" + t(p, "menu.ja")).button2("§4" + t(p, "menu.nein"))
                .validResultHandler(r -> sync(pl, () -> { if (r.clickedFirst()) ja.accept(p); else oeffnen(pl, p); }))
                .closedOrInvalidResultHandler(() -> sync(pl, () -> oeffnen(pl, p)))
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), f);
    }
}
