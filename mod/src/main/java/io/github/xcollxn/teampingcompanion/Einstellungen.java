package io.github.xcollxn.teampingcompanion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings of the companion mod – stored in config/teamping.json (created on first start, editable by hand or in game:
 * Mods → TeamPing Companion → Configure). English keys; the old German keys of earlier versions are still read.
 * Ping duration, range and number of pings are SERVER settings (plugins/TeamPing/config.yml), the same for the whole squad.
 */
final class Einstellungen {

    enum Ecke {
        @SerializedName(value = "next_to_hotbar", alternate = "HOTBAR") HOTBAR("Neben der Hotbar"),
        @SerializedName(value = "bottom_left", alternate = "UNTEN_LINKS") UNTEN_LINKS("Unten links"),
        @SerializedName(value = "top_left", alternate = "OBEN_LINKS") OBEN_LINKS("Oben links"),
        @SerializedName(value = "top_right", alternate = "OBEN_RECHTS") OBEN_RECHTS("Oben rechts"),
        @SerializedName(value = "bottom_right", alternate = "UNTEN_RECHTS") UNTEN_RECHTS("Unten rechts");
        final String anzeige; Ecke(String a) { anzeige = a; }
    }

    enum Sortierung {
        @SerializedName(value = "nearest", alternate = "NAECHSTE") NAECHSTE,
        @SerializedName(value = "fixed", alternate = "FEST") FEST
    }

    enum StrahlArt {
        @SerializedName(value = "beam", alternate = "STRAHL") STRAHL,
        @SerializedName(value = "dot", alternate = "PUNKT") PUNKT
    }

    enum MarkerOrt {
        @SerializedName(value = "at_ping", alternate = "AM_PING") AM_PING("Am Ping"),
        @SerializedName(value = "bar_top", alternate = "OBEN") OBEN("Oben"),
        @SerializedName(value = "bar_bottom", alternate = "UNTEN") UNTEN("Unten (über der Hotbar)");
        final String anzeige; MarkerOrt(String a) { anzeige = a; }
    }

    /** Short help written at the top of the file (ignored when reading). */
    @SerializedName("_help") String[] hilfe = HILFE;
    private static final String[] HILFE = {
            "TeamPing Companion - your personal settings (ping duration/range are set by the SERVER in plugins/TeamPing/config.yml).",
            "overview: squad overview on/off; overview_position: next_to_hotbar|bottom_left|top_left|top_right|bottom_right",
            "overview_size: 0.5-2.0; overview_background: 0-100 %; overview_show_myself; overview_max_rows: 1-12; overview_order: nearest|fixed",
            "markers: ping markers on/off; marker_mode: at_ping|bar_top|bar_bottom; marker_names_always: false = only while holding Tab",
            "beam_style: beam|dot; beam_width: 50-200 %; beam_opacity: 30-100 %; beam_height: 25-150 % (only how YOU see pings)",
            "xaero_waypoints: pings as Xaero waypoints; distant_horizons: ping into Distant Horizons terrain",
            "Change in game: Mods > TeamPing Companion > Configure, or the TeamPing window (key G)."
    };

    @SerializedName(value = "overview", alternate = "uebersicht") boolean uebersicht = true;
    @SerializedName(value = "overview_position", alternate = "ecke") Ecke ecke = Ecke.HOTBAR;
    @SerializedName(value = "overview_size", alternate = "groesse") double groesse = 1.0;        // 0.5 – 2.0
    @SerializedName(value = "overview_background", alternate = "deckkraft") int deckkraft = 55;  // background 0 – 100 %
    @SerializedName(value = "overview_show_myself", alternate = "selbst") boolean selbst = true;
    @SerializedName(value = "overview_max_rows", alternate = "maxEintraege") int maxEintraege = 5;
    @SerializedName(value = "overview_order", alternate = "sortierung") Sortierung sortierung = Sortierung.NAECHSTE;
    @SerializedName(value = "markers", alternate = "marker") boolean marker = true;
    @SerializedName(value = "marker_mode", alternate = "markerOrt") MarkerOrt markerOrt = MarkerOrt.AM_PING;
    @SerializedName(value = "marker_names_always", alternate = "markerNamen") boolean markerNamen = false;   // off = only while holding Tab
    // How YOU see pings (only for you; the server builds your beam accordingly)
    @SerializedName(value = "beam_style", alternate = "strahlArt") StrahlArt strahlArt = StrahlArt.STRAHL;
    @SerializedName(value = "beam_width", alternate = "strahlBreite") int strahlBreite = 100;      // 50 – 200 %
    @SerializedName(value = "beam_opacity", alternate = "strahlHell") int strahlHell = 100;       // 30 – 100 %
    @SerializedName(value = "beam_height", alternate = "strahlHoehe") int strahlHoehe = 100;      // 25 – 150 %
    @SerializedName(value = "xaero_waypoints", alternate = "xaero") boolean xaero = false;        // off: they lagged behind, the markers are enough
    @SerializedName(value = "distant_horizons", alternate = "distantHorizons") boolean distantHorizons = true;
    @SerializedName(value = "settings_version", alternate = "layout") int layout;    // version of the default values (0 = old file without this field)

    static Einstellungen aktuell = new Einstellungen();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Path datei() { return FabricLoader.getInstance().getConfigDir().resolve("teamping.json"); }

    static void laden() {
        aktuell.layout = 5;          // fresh install: current default values
        try {
            Path d = datei();
            if (!Files.exists(d)) { speichern(); return; }   // first start: create the file with all defaults
            {
                Einstellungen e = GSON.fromJson(Files.readString(d, StandardCharsets.UTF_8), Einstellungen.class);
                if (e != null) {
                    if (e.ecke == null || e.layout < 2) { e.ecke = Ecke.HOTBAR; e.layout = 2; }
                    if (e.layout < 3) { e.selbst = true; e.layout = 3; aktuell = e; speichern(); }   // 1.6: "show myself" on by default
                    if (e.layout < 4) { e.markerOrt = MarkerOrt.AM_PING; e.markerNamen = false; e.layout = 4; aktuell = e; speichern(); }   // 1.9: markers at the ping, names with Tab
                    if (e.layout < 5) { e.xaero = false; e.layout = 5; aktuell = e; speichern(); }   // 1.12: Xaero waypoints off
                    if (e.markerOrt == null) e.markerOrt = MarkerOrt.AM_PING;
                    if (e.strahlArt == null) e.strahlArt = StrahlArt.STRAHL;
                    if (e.strahlBreite < 50 || e.strahlBreite > 200) e.strahlBreite = 100;
                    if (e.strahlHell < 30 || e.strahlHell > 100) e.strahlHell = 100;
                    if (e.strahlHoehe < 25 || e.strahlHoehe > 150) e.strahlHoehe = 100;
                    if (e.sortierung == null) e.sortierung = Sortierung.NAECHSTE;
                    if (e.maxEintraege < 1 || e.maxEintraege > 12) e.maxEintraege = 5;
                    e.groesse = Math.max(0.5, Math.min(2.0, e.groesse));
                    e.deckkraft = Math.max(0, Math.min(100, e.deckkraft));
                    e.hilfe = HILFE;
                    aktuell = e;
                    speichern();   // rewrite with current key names (old German keys → English), new options appear
                }
            }
        } catch (Exception ex) {
            TeamPingClient.LOG.warn("Could not read teamping.json: {}", ex.toString());
        }
    }

    static void speichern() {
        try { Files.writeString(datei(), GSON.toJson(aktuell), StandardCharsets.UTF_8); }
        catch (Exception ex) { TeamPingClient.LOG.warn("Could not save teamping.json: {}", ex.toString()); }
    }
}
