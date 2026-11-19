package io.github.xcollxn.teamping;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * All texts the server sends to players (chat, action bar, ping label) – German and English.
 * Paper renders Component.translatable("teamping....") per player in their game language (GlobalTranslator);
 * all other languages get English. Placeholders: {0}, {1} (MessageFormat – don't use apostrophes!).
 */
final class Texte {

    private static final Map<String, String[]> TEXTE = new LinkedHashMap<>();
    private static void t(String k, String de, String en) { TEXTE.put("teamping." + k, new String[]{de, en}); }

    static {
        // pings
        t("ping.hierher", "Hierher!", "Over here!");
        t("ping.karte", "Kartenpunkt", "Map marker");
        t("ping.tod", "Todespunkt", "Death point");
        t("ping.gefahr", "Gefahr", "Danger");
        t("ping.stelle", "Ping", "Ping");
        t("ping.nichts", "Nichts in Reichweite zum Pingen", "Nothing in range to ping");
        t("ping.entfernt", "Ping entfernt", "Ping removed");
        // structures
        t("struktur.village", "Dorf", "Village");
        t("struktur.pillager_outpost", "Plünderer-Außenposten", "Pillager outpost");
        t("struktur.mansion", "Waldanwesen", "Woodland mansion");
        t("struktur.monument", "Ozeanmonument", "Ocean monument");
        t("struktur.stronghold", "Festung", "Stronghold");
        t("struktur.mineshaft", "Mine", "Mineshaft");
        t("struktur.desert_pyramid", "Wüstentempel", "Desert temple");
        t("struktur.jungle_pyramid", "Dschungeltempel", "Jungle temple");
        t("struktur.igloo", "Iglu", "Igloo");
        t("struktur.swamp_hut", "Hexenhütte", "Witch hut");
        t("struktur.shipwreck", "Schiffswrack", "Shipwreck");
        t("struktur.ocean_ruin", "Ozeanruine", "Ocean ruin");
        t("struktur.ruined_portal", "Portalruine", "Ruined portal");
        t("struktur.fortress", "Netherfestung", "Nether fortress");
        t("struktur.bastion_remnant", "Bastionsruine", "Bastion remnant");
        t("struktur.end_city", "Endsiedlung", "End city");
        t("struktur.ancient_city", "Antike Stätte", "Ancient city");
        t("struktur.trail_ruins", "Pfadruine", "Trail ruins");
        t("struktur.trial_chambers", "Prüfungskammern", "Trial chambers");
        // general
        t("an", "AN", "ON");
        t("aus", "AUS", "OFF");
        // squads
        t("squad.name_ungueltig", "Name: 1–12 Zeichen, nur a–z, 0–9 und _", "Name: 1–12 characters, only a–z, 0–9 and _");
        t("squad.gibt_es_schon", "Das Squad „{0}“ gibt es schon.", "The squad “{0}” already exists.");
        t("squad.erstellt", "Squad {0} erstellt – du bist Leiter.", "Squad {0} created – you are the leader.");
        t("squad.schon_drin", "Du bist schon in diesem Squad.", "You are already in this squad.");
        t("squad.nur_einladung", "Dieses Squad ist nur mit Einladung beitretbar.", "This squad is invite only.");
        t("squad.beigetreten", "{0} ist dem Squad {1} beigetreten.", "{0} joined the squad {1}.");
        t("squad.kein_squad", "Du bist in keinem Squad.", "You are not in a squad.");
        t("squad.selbst_verlassen", "Du hast das Squad {0} verlassen.", "You left the squad {0}.");
        t("squad.verlassen", "{0} hat das Squad verlassen.", "{0} left the squad.");
        t("squad.nur_leiter_kick", "Nur der Squad-Leiter kann kicken.", "Only the squad leader can kick.");
        t("squad.selbst_kick", "Dich selbst kannst du nicht kicken – nutze „Verlassen“.", "You cannot kick yourself – use “Leave”.");
        t("squad.nicht_im_squad", "{0} ist nicht in deinem Squad.", "{0} is not in your squad.");
        t("squad.gekickt", "Du wurdest aus dem Squad {0} entfernt.", "You were removed from the squad {0}.");
        t("squad.entfernt", "{0} wurde aus dem Squad entfernt.", "{0} was removed from the squad.");
        t("squad.leiter_unbekannt", "Die Leitung kann nur an bekannte Spieler übergeben werden.", "Leadership can only be given to known players.");
        t("squad.neuer_leiter", "👑 {0} ist jetzt Squad-Leiter.", "👑 {0} is now the squad leader.");
        t("squad.nur_leiter_farbe", "Nur der Squad-Leiter kann die Farbe ändern.", "Only the squad leader can change the colour.");
        t("squad.neue_farbe", "Neue Squad-Farbe: {0}", "New squad colour: {0}");
        t("squad.jetzt_offen", "Das Squad ist jetzt offen.", "The squad is now open.");
        t("squad.jetzt_geschlossen", "Das Squad ist jetzt nur mit Einladung beitretbar.", "The squad is now invite only.");
        t("squad.nur_leiter_aufloesen", "Nur der Squad-Leiter kann das Squad auflösen.", "Only the squad leader can disband the squad.");
        t("squad.aufgeloest", "Das Squad {0} wurde von {1} aufgelöst.", "The squad {0} was disbanded by {1}.");
        t("squad.nur_leiter_einladen", "In geschlossenen Squads lädt nur der Leiter ein.", "In closed squads only the leader can invite.");
        t("squad.schon_im_squad", "{0} ist schon in deinem Squad.", "{0} is already in your squad.");
        t("squad.eingeladen", "{0} wurde eingeladen.", "{0} has been invited.");
        t("squad.einladung", "{0} lädt dich ins Squad {1} ein. ", "{0} invites you to the squad {1}. ");
        t("squad.annehmen", "[Annehmen]", "[Accept]");
        t("squad.annehmen_hover", "Squad {0} beitreten", "Join squad {0}");
        t("squad.oder_menue", "  (oder /squad)", "  (or /squad)");
        t("squad.keine_einladung", "Keine gültige Einladung für dieses Squad.", "No valid invite for this squad.");
        t("squad.neue_pingfarbe", "Neue Ping-Farbe: {0}", "New ping colour: {0}");
        t("squad.name_eingabe", "Schreib den Squad-Namen in den Chat (1–12 Zeichen, a–z, 0–9, _) – oder {0}.", "Type the squad name in chat (1–12 characters, a–z, 0–9, _) – or {0}.");
        t("squad.abbrechen_wort", "abbrechen", "cancel");
        t("squad.abgebrochen", "Abgebrochen.", "Cancelled.");
        t("squad.karte_fehler", "Der Kartenpunkt konnte nicht gelesen werden.", "Could not read the map marker.");
        t("squad.chat_kein_squad", "Du bist in keinem Squad – Squad-Chat aus.", "You are not in a squad – squad chat off.");
        t("squad.chat", "Squad-Chat {0}", "Squad chat {0}");
        t("squad.nicht_gefunden", "Squad nicht gefunden – /squad liste", "Squad not found – /squad list");
        t("squad.nicht_online", "Spieler nicht online.", "Player not online.");
        t("squad.farben", "Farben: {0}", "Colours: {0}");
        t("squad.uebersicht", "Squad-Übersicht {0}", "Squad overview {0}");
        t("squad.taste", "{0}: {1}", "{0}: {1}");
        t("squad.unbekannte_taste", "Unbekannte Geste.", "Unknown gesture.");
        t("squad.keine_squads", "Noch keine Squads.", "No squads yet.");
        t("squad.liste", "Squads:", "Squads:");
        t("squad.steuerung_gespeichert", "Steuerung gespeichert.", "Controls saved.");
        t("hilfe.1", "/squad – Menü · /squad steuerung – Ping-Gesten · /squad übersicht – Anzeige an/aus",
                "/squad – menu · /squad controls – ping gestures · /squad overview – overview on/off");
        t("hilfe.2", "/squad erstellen <name> [farbe] · beitreten <name> · annehmen <name> · verlassen · pingfarbe",
                "/squad create <name> [colour] · join <name> · accept <name> · leave · pingcolor");
        t("hilfe.3", "Leiter: einladen <spieler> · kicken <spieler> · leiter <spieler> · farbe <farbe> · offen/geschlossen · aufloesen",
                "Leader: invite <player> · kick <player> · leader <player> · color <colour> · open/closed · disband");
        t("hilfe.4", "/sc <text> – Squad-Chat · /ping – pingen (nach oben schauen = Hierher!, 2× schnell = Gefahr)",
                "/sc <text> – squad chat · /ping – ping (look up = Over here!, twice quickly = Danger)");
        // gestures
        t("geste.SHIFT_F", "Schleichen + F", "Sneak + F");
        t("geste.F", "F allein", "F only");
        t("geste.DOPPEL", "2× schnell schleichen", "Double sneak");
        t("geste.SHIFT_Q", "Schleichen + Q", "Sneak + Q");
        t("geste.SHIFT_LINKS", "Schleichen + Linksklick", "Sneak + left click");
        t("geste.EMOTE", "Emote", "Emote");
        // gestures – what they do
        t("geste.SHIFT_F.info", "Schleichen halten + F drücken = Ping", "Hold sneak + press F = ping");
        t("geste.F.info", "F drücken = Ping (F tauscht dann nicht mehr die Hände)", "Press F = ping (F no longer swaps hands)");
        t("geste.DOPPEL.info", "Schleichen 2× kurz antippen = Ping", "Tap sneak twice quickly = ping");
        t("geste.SHIFT_Q.info", "Schleichen halten + Q = Ping (wirft nichts weg)", "Hold sneak + press Q = ping (drops nothing)");
        t("geste.SHIFT_LINKS.info", "Schleichen halten + in die Luft klicken = Ping", "Hold sneak + click into the air = ping");
        t("geste.EMOTE.info", "Emote machen = Ping (PC: Taste B)", "Do an emote = ping (PC: key B)");
        // chest menu (Java without mod)
        t("menu.titel", "Squads", "Squads");
        t("menu.info.titel", "Squads & Pings", "Squads & pings");
        t("menu.info.1", "Gemeinsam spielen mit Pings,", "Play together with pings");
        t("menu.info.2", "die nur dein Squad sieht.", "that only your squad can see.");
        t("menu.info.ping_bedrock", "Ping: Emote oder Schleichen doppelt antippen", "Ping: emote or double-tap sneak");
        t("menu.info.ping_java", "Ping: Schleichen + F", "Ping: sneak + F");
        t("menu.info.oder_ping", "oder /ping", "or /ping");
        t("menu.erstellen", "Squad erstellen", "Create squad");
        t("menu.erstellen.1", "Du wirst Leiter des neuen Squads.", "You become the leader of the new squad.");
        t("menu.erstellen.2", "Den Namen schreibst du danach in den Chat.", "Then type the name in chat.");
        t("menu.einladungen", "Einladungen ({0})", "Invites ({0})");
        t("menu.einladungen_titel", "Einladungen", "Invites");
        t("menu.keine_einladungen", "Keine offenen Einladungen", "No open invites");
        t("menu.klick_ansehen", "Klicken zum Ansehen", "Click to view");
        t("menu.leiter", "Leiter: {0}", "Leader: {0}");
        t("menu.mitglieder", "Mitglieder: {0}", "Members: {0}");
        t("menu.klick_beitreten", "▶ Klicken zum Beitreten", "▶ Click to join");
        t("menu.nur_einladung", "🔒 Nur mit Einladung", "🔒 Invite only");
        t("menu.keine_squads", "Noch keine Squads", "No squads yet");
        t("menu.erstelle_erstes", "Erstelle das erste!", "Create the first one!");
        t("menu.squad_titel", "Squad: {0}", "Squad: {0}");
        t("menu.leiter_krone", "Leiter: 👑 {0}", "Leader: 👑 {0}");
        t("menu.status", "Status: {0}", "Status: {0}");
        t("menu.offen", "Offen", "Open");
        t("menu.geschlossen", "Nur mit Einladung", "Invite only");
        t("menu.offen.info", "Jeder kann beitreten, alle dürfen einladen.", "Anyone can join, everyone may invite.");
        t("menu.geschlossen.info", "Beitritt nur mit Einladung vom Leiter.", "Joining only with an invite from the leader.");
        t("menu.ist_leiter", "👑 Squad-Leiter", "👑 Squad leader");
        t("menu.ist_mitglied", "Mitglied", "Member");
        t("menu.online", "● online", "● online");
        t("menu.offline", "○ offline", "○ offline");
        t("menu.pingfarbe", "Ping-Farbe: {0}", "Ping colour: {0}");
        t("menu.klick_verwalten", "▶ Klicken: kicken / Leitung übergeben", "▶ Click: kick / make leader");
        t("menu.einladen", "Spieler einladen", "Invite players");
        t("menu.einladen.info", "Online-Spieler auswählen", "Choose an online player");
        t("menu.chat_an", "Deine Chatnachrichten gehen nur ans Squad.", "Your chat messages only go to your squad.");
        t("menu.chat_aus", "Normaler Chat für alle.", "Normal chat for everyone.");
        t("menu.chat_einzeln", "Einzelne Nachricht: /sc <Text>", "Single message: /sc <text>");
        t("menu.klick_umschalten", "▶ Klicken zum Umschalten", "▶ Click to toggle");
        t("menu.farbe", "Farbe ändern", "Change colour");
        t("menu.aktuell", "Aktuell: {0}", "Current: {0}");
        t("menu.aufloesen", "Squad auflösen", "Disband squad");
        t("menu.aufloesen.info", "Entfernt alle Mitglieder.", "Removes all members.");
        t("menu.aufloesen.frage", "Squad wirklich auflösen?", "Really disband the squad?");
        t("menu.meine_pingfarbe", "Meine Ping-Farbe: {0}", "My ping colour: {0}");
        t("menu.pingfarbe.info", "Jeder im Squad hat eine eigene Farbe.", "Everyone in the squad has their own colour.");
        t("menu.pingfarbe.klick", "▶ Klicken für eine andere freie Farbe", "▶ Click for another free colour");
        t("menu.pings", "Pings", "Pings");
        t("menu.pings.1", "Womit du pingst: siehe „Steuerung“", "How to ping: see “Controls”");
        t("menu.pings.2", "/ping funktioniert immer", "/ping always works");
        t("menu.pings.3", "2× schnell pingen = ⚠ Gefahr", "Ping twice quickly = ⚠ danger");
        t("menu.pings.4", "Nach oben schauen + pingen = 📣 Hierher!", "Look up + ping = 📣 over here!");
        t("menu.pings.5", "Monster pingen = ⚠ Gefahr", "Ping a monster = ⚠ danger");
        t("menu.pings.6", "Eigenen Ping nochmal anpingen = entfernen", "Ping your own ping again = remove it");
        t("menu.pings.7", "Beim Tod sieht dein Squad deinen ☠ Todespunkt", "When you die, your squad sees your ☠ death point");
        t("menu.pings.8", "Pings erscheinen auch in der Locator-Leiste", "Pings also show up in the locator bar");
        t("menu.uebersicht.1", "Leben, Entfernung und Richtung deiner Mitglieder.", "Health, distance and direction of your members.");
        t("menu.uebersicht.2", "Mit der TeamPing-Mod neben der Hotbar, sonst oben als Leisten.", "With the TeamPing mod next to the hotbar, otherwise as bars at the top.");
        t("menu.verlassen", "Squad verlassen", "Leave squad");
        t("menu.verlassen.leiter", "Die Leitung geht an ein anderes Mitglied.", "Leadership passes to another member.");
        t("menu.verlassen.normal", "Du kannst später wieder beitreten.", "You can join again later.");
        t("menu.verlassen.frage", "Squad verlassen?", "Leave squad?");
        t("menu.zum_leiter", "Zum Leiter machen", "Make leader");
        t("menu.zum_leiter.info", "Du wirst normales Mitglied.", "You become a normal member.");
        t("menu.zum_leiter.frage", "Leitung an {0}?", "Make {0} the leader?");
        t("menu.kicken", "Aus dem Squad kicken", "Kick from squad");
        t("menu.kicken.frage", "{0} kicken?", "Kick {0}?");
        t("menu.zurueck", "Zurück", "Back");
        t("menu.kein_squad", "In keinem Squad", "Not in a squad");
        t("menu.gerade_in", "Gerade in: {0}", "Currently in: {0}");
        t("menu.klick_einladen", "▶ Klicken zum Einladen", "▶ Click to invite");
        t("menu.niemand", "Niemand zum Einladen online", "Nobody online to invite");
        t("menu.farbe_waehlen", "Squad-Farbe wählen", "Choose squad colour");
        t("menu.klick_annehmen", "▶ Klicken zum Annehmen", "▶ Click to accept");
        t("menu.steuerung", "Steuerung", "Controls");
        t("menu.steuerung.info", "Lege fest, womit du pingst.", "Choose how you ping.");
        t("menu.klick", "▶ Klicken", "▶ Click");
        t("menu.steuerung_titel", "Steuerung – womit du pingst", "Controls – how you ping");
        t("menu.tipp", "Tipp", "Tip");
        t("menu.tipp.1", "Mehrere Wege dürfen gleichzeitig an sein.", "Several ways can be on at the same time.");
        t("menu.tipp.2", "/ping funktioniert immer.", "/ping always works.");
        t("menu.tipp_bedrock.1", "„2× schnell schleichen“ klappt am besten.", "“Double sneak” works best.");
        t("menu.tipp_bedrock.2", "Am Handy/Controller: Schleichen-Taste doppelt tippen.", "On phone/controller: tap the sneak button twice.");
        t("menu.ja", "Ja", "Yes");
        t("menu.nein", "Nein", "No");
        // Bedrock forms
        t("bedrock.haupt", "Spiele im Squad mit Pings, die nur dein Squad sieht.",
                "Play in a squad with pings that only your squad can see.");
        t("bedrock.pingen_mit", "Pingen: {0}", "Ping: {0}");
        t("bedrock.beitreten", "Beitreten", "Join");
        t("bedrock.spieler", "{0} Spieler", "members: {0}");
        t("bedrock.name", "Name (1–12 Zeichen: a–z, 0–9, _)", "Name (1–12 characters: a–z, 0–9, _)");
        t("bedrock.name_beispiel", "z. B. wolfsrudel", "e.g. wolfpack");
        t("bedrock.teamfarbe", "Teamfarbe (Namensfarbe)", "Team colour (name colour)");
        t("bedrock.mitglieder", "Mitglieder", "Members");
        t("bedrock.leiter_kurz", "Leiter", "leader");
        t("bedrock.du", "du", "you");
        t("bedrock.mitglied_antippen", "Mitglied antippen = zum Leiter machen oder rauswerfen", "Tap a member = make leader or kick");
        t("bedrock.hud.knopf", "Squad-Übersicht:", "Squad overview:");
        t("bedrock.hud.antippen", "antippen = Position ändern", "tap = change position");
        t("bedrock.hud.RECHTS", "rechts", "right");
        t("bedrock.hud.LINKS", "links", "left");
        t("bedrock.hud.OBEN_RECHTS", "oben rechts", "top right");
        t("bedrock.hud.LEISTEN", "Leisten oben", "bars at the top");
        t("bedrock.hud.allein", "noch niemand online", "nobody else online");
        t("bedrock.offline", "offline", "offline");
        t("bedrock.leiter_markierung", "(Leiter)", "(leader)");
        t("bedrock.antippen_farbe", "antippen = neue Farbe", "tap = new colour");
        t("bedrock.einstellungen", "Squad-Einstellungen", "Squad settings");
        t("bedrock.aufloesen_text", "Alle Mitglieder werden entfernt.", "All members will be removed.");
        t("bedrock.wen_einladen", "Wen möchtest du einladen?", "Who do you want to invite?");
        t("bedrock.antippen_annehmen", "Antippen zum Annehmen.", "Tap to accept.");
        t("bedrock.was_tun", "Was möchtest du tun?", "What do you want to do?");
        t("bedrock.leiter_text", "An {0} – du wirst normales Mitglied.", "To {0} – you become a normal member.");
        t("bedrock.kick_text", "{0} wird aus dem Squad entfernt.", "{0} will be removed from the squad.");
        t("bedrock.offen_toggle", "Offen (jeder kann beitreten)", "Open (anyone can join)");
        t("bedrock.steuerung_info", "Antippen = an/aus. Mehrere dürfen an sein, /ping geht immer.\nWelches Emote welchen Ping auslöst: Reiter „Emotes“.",
                "Tap = on/off. Several can be on, /ping always works.\nWhich emote triggers which ping: “Emotes” tab.");
        // ping item
        t("item.name", "◆ Ping", "◆ Ping");
        t("item.lore1", "Auf Block oder Tier tippen / Rechtsklick = Ping genau dort", "Tap / right-click a block or mob = ping right there");
        t("item.lore2", "In die Luft = Ping dahin, wo du hinschaust", "Into the air = ping where you are looking");
        t("item.erhalten", "◆ Ping-Gegenstand in der Hotbar – damit antippen = Ping", "◆ Ping item in your hotbar – tap with it = ping");
        t("item.schon_da", "Du hast den ◆ Ping-Gegenstand schon.", "You already have the ◆ ping item.");
        t("item.hinweis", "Tipp: Pingen geht mit einem Emote (Emote-Knopf) oder Schleichen doppelt antippen. Lieber antippen? /squad pingitem gibt dir den ◆ Ping-Gegenstand.",
                "Tip: ping by doing an emote (emote button) or double-tapping sneak. Prefer tapping? /squad pingitem gives you the ◆ ping item.");
        t("menu.pinggegenstand", "◆ Ping-Gegenstand holen", "◆ Get ping item");
        t("menu.pinggegenstand.info", "Antippen/Rechtsklick damit = Ping – ideal fürs Handy", "Tap/right-click with it = ping – ideal on phones");
        // emotes (Bedrock)
        t("ping.folgen", "Folge mir!", "Follow me!");
        t("emote.art.normal", "Normaler Ping", "Normal ping");
        t("emote.art.folgen", "Folge mir", "Follow me");
        t("emote.art.gefahr", "Gefahr", "Danger");
        t("emote.jetzt", "Mach jetzt das Emote für: {0}", "Now do the emote for: {0}");
        t("emote.gelernt", "Emote gespeichert: {0}", "Emote saved: {0}");
        t("emote.geloescht", "Emote-Zuordnungen gelöscht – alle Emotes pingen normal.", "Emote assignments cleared – all emotes ping normally.");
        t("bedrock.emotes", "Emotes zuordnen", "Assign emotes");
        t("bedrock.emotes.text", "Jedes Emote kann einen eigenen Ping auslösen.\n\nSo geht’s: Unten eine Ping-Art antippen, das Fenster schließt sich – dann das Emote machen, das dafür stehen soll.\nNicht zugeordnete Emotes pingen normal.",
                "Each emote can trigger its own ping.\n\nHow: tap a ping type below, the window closes – then do the emote you want for it.\nUnassigned emotes ping normally.");
        t("bedrock.emotes.tipp_normal", "Vorschlag: „Da drüben“", "Suggestion: “Over There!”");
        t("bedrock.emotes.tipp_folgen", "Vorschlag: „Folge mir“", "Suggestion: “Follow Me”");
        t("bedrock.emotes.tipp_gefahr", "Vorschlag: „Winken“", "Suggestion: “Wave”");
        t("bedrock.emotes.gesetzt", "✔ zugeordnet", "✔ assigned");
        t("bedrock.emotes.loeschen", "Zuordnungen löschen", "Clear assignments");
        t("bedrock.status_offen", "offen", "open");
        t("bedrock.status_geschlossen", "nur Einladung", "invite only");
        // colours
        for (String[] f : new String[][]{{"rot", "rot", "red"}, {"blau", "blau", "blue"}, {"gruen", "grün", "green"}, {"gelb", "gelb", "yellow"},
                {"aqua", "aqua", "aqua"}, {"pink", "pink", "pink"}, {"orange", "orange", "orange"}, {"lila", "lila", "purple"}, {"weiss", "weiß", "white"}})
            t("farbe." + f[0], f[1], f[2]);
        for (String[] f : new String[][]{{"ROT", "Rot", "Red"}, {"ORANGE", "Orange", "Orange"}, {"GELB", "Gelb", "Yellow"}, {"GRUEN", "Grün", "Green"},
                {"TUERKIS", "Türkis", "Cyan"}, {"HELLBLAU", "Hellblau", "Light blue"}, {"BLAU", "Blau", "Blue"}, {"LILA", "Lila", "Purple"},
                {"PINK", "Pink", "Pink"}, {"WEISS", "Weiß", "White"}})
            t("pingfarbe." + f[0], f[1], f[2]);
    }

    private static boolean fertig;

    private Texte() {}

    static void registrieren() {
        if (fertig) return;
        TranslationStore.StringBased<MessageFormat> store = TranslationStore.messageFormat(Key.key("teamping", "texte"));
        store.defaultLocale(Locale.ENGLISH);
        TEXTE.forEach((k, t) -> {
            store.register(k, Locale.GERMAN, new MessageFormat(t[0], Locale.GERMAN));
            store.register(k, Locale.ENGLISH, new MessageFormat(t[1], Locale.ENGLISH));
        });
        GlobalTranslator.translator().addSource(store);
        fertig = true;
    }

    /** Translatable text; values may be components or any objects. Fallback = German. */
    static Component tr(String schluessel, Object... werte) {
        ComponentLike[] args = new ComponentLike[werte.length];
        for (int i = 0; i < werte.length; i++) args[i] = werte[i] instanceof ComponentLike c ? c : Component.text(String.valueOf(werte[i]));
        String[] t = TEXTE.get("teamping." + schluessel);
        String fallback = null;
        if (t != null) {   // {0} → %1$s (client format, in case the client has to translate itself)
            fallback = t[0];
            for (int i = 0; i < 9; i++) fallback = fallback.replace("{" + i + "}", "%" + (i + 1) + "$s");
        }
        return Component.translatable("teamping." + schluessel, fallback, args);
    }

    static Component anAus(boolean an) { return tr(an ? "an" : "aus"); }

    /** Text ready in this player's language (for chest menus and Bedrock forms). */
    static Component fuer(org.bukkit.entity.Player p, String schluessel, Object... werte) {
        return GlobalTranslator.render(tr(schluessel, werte), p.locale());
    }

    /** Like fuer(), but as text with § colour codes (Bedrock forms need plain text). */
    static String s(org.bukkit.entity.Player p, String schluessel, Object... werte) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(fuer(p, schluessel, werte));
    }
}
