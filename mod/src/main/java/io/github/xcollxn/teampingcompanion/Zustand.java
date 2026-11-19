package io.github.xcollxn.teampingcompanion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Last squad state reported by the server (incl. menu data for the squad window). */
final class Zustand {

    record Mitglied(String name, UUID uuid, int farbe, boolean online, float hp, float max,
                    double x, double y, double z, String welt) {}

    /** eid = entity id of the tracked target (-1 = fixed spot), item = item (e.g. minecraft:diamond) or null. */
    record Ping(String id, String von, int farbe, String typ, String text, boolean textIstSchluessel,
                double x, double y, double z, String welt, int eid, double hoehe, String item, int anzahl) {}

    record SquadMitglied(String name, boolean online, boolean leiter) {}
    record SquadInfo(String name, int farbe, boolean offen, int anzahl, String leiter, boolean eingeladen, List<SquadMitglied> mitglieder) {}
    record Spieler(String name, UUID uuid, String squad) {}
    record Geste(String id, String name, String beschreibung, boolean an) {}

    static volatile String squad;
    static volatile int teamfarbe = 0xFFFFFF;
    static volatile String leiter = "";
    static volatile UUID ich;
    static volatile List<Mitglied> mitglieder = List.of();
    static volatile List<Ping> pings = List.of();
    static volatile long zuletzt;
    /** From this distance on the mod draws the ping beam itself (closer, the server does it). */
    static volatile double nah = 112;

    // menu data
    static volatile boolean menuDa, ichLeiter, offen, chat, uebersicht;
    static volatile String teamfarbeName = "", pingfarbe = "", pingfarbeId = "";
    static volatile int pingrgb = 0xFFFFFF;
    static volatile List<SquadInfo> squads = List.of();
    static volatile List<String> einladungen = List.of();
    static volatile List<Spieler> spieler = List.of();
    static volatile List<Geste> gesten = List.of();
    /** Increases when menu or member data changed (the window then rebuilds). */
    static volatile int version;
    private static String letzterMenuStand = "";

    /** Locally toggled values apply right away; until the server confirms them (max. 2 s), a different server value is ignored. */
    private static Boolean chatErwartet, offenErwartet;
    private static long erwartetBis;
    /** Only for drafts/tests: extra fake members. */
    static volatile List<Mitglied> testMitglieder = List.of();

    private Zustand() {}

    static void chatLokal(boolean an) { chat = an; chatErwartet = an; erwartetBis = System.currentTimeMillis() + 2000; version++; }
    static void offenLokal(boolean an) { offen = an; offenErwartet = an; erwartetBis = System.currentTimeMillis() + 2000; version++; }

    private static boolean wert(boolean server, Boolean erwartet) {
        return erwartet != null && System.currentTimeMillis() < erwartetBis ? erwartet : server;
    }

    static void zuruecksetzen() {
        squad = null; mitglieder = List.of(); pings = List.of(); zuletzt = 0; menuDa = false; version++;
    }

    /** @return true if the server wants to open the squad window */
    static boolean lesen(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        ich = o.has("ich") ? UUID.fromString(o.get("ich").getAsString()) : null;
        if (o.has("nah")) nah = o.get("nah").getAsDouble();
        squad = o.has("squad") ? o.get("squad").getAsString() : null;
        teamfarbe = o.has("teamfarbe") ? o.get("teamfarbe").getAsInt() : 0xFFFFFF;
        leiter = o.has("leiter") ? o.get("leiter").getAsString() : "";
        List<Mitglied> m = new ArrayList<>();
        StringBuilder stand = new StringBuilder(String.valueOf(squad)).append(leiter);
        if (o.has("mitglieder")) {
            for (JsonElement e : o.getAsJsonArray("mitglieder")) {
                JsonObject j = e.getAsJsonObject();
                boolean on = j.get("on").getAsBoolean();
                m.add(new Mitglied(j.get("n").getAsString(),
                        j.has("u") ? UUID.fromString(j.get("u").getAsString()) : null,
                        j.get("f").getAsInt(), on,
                        on ? j.get("hp").getAsFloat() : 0f, on ? j.get("max").getAsFloat() : 20f,
                        on ? j.get("x").getAsDouble() : 0, on ? j.get("y").getAsDouble() : 0, on ? j.get("z").getAsDouble() : 0,
                        on ? j.get("w").getAsString() : ""));
                stand.append(j.get("n").getAsString()).append(on).append(j.get("f").getAsInt());
            }
        }
        if (!testMitglieder.isEmpty()) m.addAll(testMitglieder);
        mitglieder = m;
        List<Ping> p = new ArrayList<>();
        JsonArray arr = o.has("pings") ? o.getAsJsonArray("pings") : new JsonArray();
        for (JsonElement e : arr) {
            JsonObject j = e.getAsJsonObject();
            p.add(new Ping(j.get("id").getAsString(), j.get("n").getAsString(), j.get("f").getAsInt(), j.get("typ").getAsString(),
                    j.get("t").getAsString(), j.get("tk").getAsBoolean(),
                    j.get("x").getAsDouble(), j.get("y").getAsDouble(), j.get("z").getAsDouble(), j.get("w").getAsString(),
                    j.has("eid") ? j.get("eid").getAsInt() : -1, j.has("eh") ? j.get("eh").getAsDouble() : 0,
                    j.has("item") ? j.get("item").getAsString() : null, j.has("anz") ? j.get("anz").getAsInt() : 1));
        }
        pings = p;

        if (o.has("menu")) {
            JsonObject mn = o.getAsJsonObject("menu");
            stand.append(mn);
            for (Mitglied t : testMitglieder) stand.append(t.name());
            menuDa = true;
            ichLeiter = mn.get("ichLeiter").getAsBoolean();
            boolean offenServer = mn.get("offen").getAsBoolean();
            if (offenErwartet != null && offenServer == offenErwartet) offenErwartet = null;   // confirmed
            offen = wert(offenServer, offenErwartet);
            teamfarbeName = mn.has("teamfarbeName") ? mn.get("teamfarbeName").getAsString() : "";
            pingfarbe = mn.get("pingfarbe").getAsString();
            pingfarbeId = mn.has("pingfarbeId") ? mn.get("pingfarbeId").getAsString() : "";
            pingrgb = mn.get("pingrgb").getAsInt();
            boolean chatServer = mn.get("chat").getAsBoolean();
            if (chatErwartet != null && chatServer == chatErwartet) chatErwartet = null;
            chat = wert(chatServer, chatErwartet);
            uebersicht = mn.get("uebersicht").getAsBoolean();
            List<SquadInfo> sq = new ArrayList<>();
            for (JsonElement e : mn.getAsJsonArray("squads")) {
                JsonObject j = e.getAsJsonObject();
                List<SquadMitglied> sm = new ArrayList<>();
                if (j.has("m")) for (JsonElement me : j.getAsJsonArray("m")) {
                    JsonObject mo = me.getAsJsonObject();
                    sm.add(new SquadMitglied(mo.get("n").getAsString(), mo.get("on").getAsBoolean(), mo.get("l").getAsBoolean()));
                }
                sq.add(new SquadInfo(j.get("n").getAsString(), j.get("f").getAsInt(), j.get("offen").getAsBoolean(),
                        j.get("anzahl").getAsInt(), j.get("leiter").getAsString(), j.get("eingeladen").getAsBoolean(), sm));
            }
            squads = sq;
            List<String> ei = new ArrayList<>();
            for (JsonElement e : mn.getAsJsonArray("einladungen")) ei.add(e.getAsString());
            einladungen = ei;
            List<Spieler> sp = new ArrayList<>();
            for (JsonElement e : mn.getAsJsonArray("spieler")) {
                JsonObject j = e.getAsJsonObject();
                sp.add(new Spieler(j.get("n").getAsString(), UUID.fromString(j.get("u").getAsString()),
                        j.has("squad") ? j.get("squad").getAsString() : null));
            }
            spieler = sp;
            List<Geste> ge = new ArrayList<>();
            for (JsonElement e : mn.getAsJsonArray("steuerung")) {
                JsonObject j = e.getAsJsonObject();
                ge.add(new Geste(j.get("id").getAsString(), j.get("n").getAsString(), j.get("b").getAsString(), j.get("an").getAsBoolean()));
            }
            gesten = ge;
        }
        String s = stand.toString();
        if (!s.equals(letzterMenuStand)) { letzterMenuStand = s; version++; }
        zuletzt = System.currentTimeMillis();
        return o.has("oeffnen") && o.get("oeffnen").getAsBoolean();
    }

    /** Display text of a ping; several translation keys are separated by | (e.g. chest|village → "Chest · Village"). */
    static String text(Ping p) {
        if (!p.textIstSchluessel()) return p.text();
        StringBuilder b = new StringBuilder();
        for (String k : p.text().split("\\|")) {
            if (!b.isEmpty()) b.append(" · ");
            b.append(net.minecraft.network.chat.Component.translatable(k).getString());
        }
        return b.toString();
    }

    /** Connected to a server with the TeamPing plugin (status arrives). */
    static boolean statusDa() { return System.currentTimeMillis() - zuletzt < 5000; }
    /** The server also sends menu data (plugin 1.5+). */
    static boolean serverDa() { return menuDa && statusDa(); }
}
