package io.github.xcollxn.teampingcompanion;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * TeamPing window in vanilla style (like a Minecraft inventory / Simple Voice Chat):
 * tabs at the top like the creative inventory, grey window, inset lists, vanilla buttons/checkboxes.
 * All graphics are Minecraft's own → resource packs (e.g. Default Dark Mode) recolour the window too.
 * Every setting has a tooltip; multi-value options cycle on click (shift+click backwards).
 */
final class TeamPingScreen extends Screen {

    enum Reiter { SQUAD, EINLADEN, STEUERUNG, EINSTELLUNGEN, KARTEN, AUSSEHEN }

    private static final String[][] TEAMFARBEN = {
            {"rot", "FF5555"}, {"blau", "5555FF"}, {"gruen", "55FF55"}, {"gelb", "FFFF55"}, {"aqua", "55FFFF"},
            {"pink", "FF55FF"}, {"orange", "FFAA00"}, {"lila", "AA00AA"}, {"weiss", "FFFFFF"}};

    /** Window background = creative inventory (recoloured by resource packs). */
    private static final Identifier FENSTER = Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_items.png");

    private static int hell = -1;   // -1 = not checked yet, 1 = light window, 0 = dark window
    private int TEXT, TEXT2, EINSATZ, EINSATZ_HELL, EINSATZ_DUNKEL, ZEILE_BG;

    private Reiter reiter;
    private int px, py, pw, ph;
    private int gesehen = -1;
    private boolean warSquadDa = Zustand.serverDa();
    private String nameEingabe = "";
    private int farbeWahl = 0;

    private record Zeile(int x, int y, int b, UUID kopf, String text, int punkt, String unten, int untenFarbe, float hp, boolean zeigHp) {}
    private record Einsatz(int x, int y, int b, int h) {}
    /** Player tile in the member list. */
    private record Kachel(int x, int y, int b, int h, Zustand.Mitglied m, boolean leiter, boolean ich, int rechtsFrei) {}
    private final List<Kachel> kacheln = new ArrayList<>();
    // list with mouse wheel/scroll bar
    private int scroll, scrollMax;
    private Einsatz scrollListe;
    private record Text(int x, int y, String t, int farbe) {}
    private final List<Zeile> zeilen = new ArrayList<>();
    private final List<Einsatz> einsaetze = new ArrayList<>();
    private final List<Text> texte = new ArrayList<>();

    private KeyMapping wartetAuf;
    private final List<Button> tastenKnoepfe = new ArrayList<>();
    private final List<KeyMapping> tastenListe = new ArrayList<>();
    private final List<String> tastenNamen = new ArrayList<>();

    /** Where "close" leads (mod menu) – null = back to the game. */
    private final net.minecraft.client.gui.screens.Screen eltern;

    TeamPingScreen(Reiter start) { this(start, null); }

    TeamPingScreen(Reiter start, net.minecraft.client.gui.screens.Screen eltern) {
        super(Component.literal("TeamPing"));
        this.reiter = start;
        this.eltern = eltern;
    }

    static void oeffnen(Reiter r) { Minecraft.getInstance().gui.setScreen(new TeamPingScreen(r)); }

    @Override public boolean isPauseScreen() { return false; }

    // ================================================================== colours from the resource pack
    private void farbenBestimmen() {
        if (hell < 0) {
            hell = 1;
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(FENSTER); NativeImage bild = NativeImage.read(in)) {
                int skala = Math.max(1, bild.getWidth() / 256);
                int argb = bild.getPixel(20 * skala, 10 * skala);
                int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
                hell = (0.299 * r + 0.587 * g + 0.114 * b) > 110 ? 1 : 0;
            } catch (Exception e) {
                TeamPingClient.LOG.debug("Fensterfarbe nicht lesbar: {}", e.toString());
            }
        }
        if (hell == 1) {
            TEXT = 0xFF404040; TEXT2 = 0xFF6B6B6B; EINSATZ = 0xFF8B8B8B; EINSATZ_DUNKEL = 0xFF373737; EINSATZ_HELL = 0xFFFFFFFF; ZEILE_BG = 0xFFC6C6C6;
        } else {
            TEXT = 0xFFE0E0E0; TEXT2 = 0xFF9A9A9A; EINSATZ = 0xFF1E1E1E; EINSATZ_DUNKEL = 0xFF0C0C0C; EINSATZ_HELL = 0xFF4A4A4A; ZEILE_BG = 0xFF2E2E2E;
        }
    }

    static void texturpaketGeaendert() { hell = -1; }

    // for the TestPilot
    int[] testMaus;
    void neuAufbauen() { rebuildWidgets(); }
    int reiterX(int i) { return px + i * 27; }
    int reiterY() { return py - 28; }
    String reiterName() { return reiter.name(); }
    double listeMitteX() { return px + pw / 2.0; }
    double listeMitteY() { return py + 60; }

    // ================================================================== layout
    @Override
    protected void init() {
        farbenBestimmen();
        zeilen.clear(); kacheln.clear(); einsaetze.clear(); texte.clear(); scrollMax = 0; scrollListe = null; tastenKnoepfe.clear(); tastenListe.clear(); tastenNamen.clear();
        // total height: tabs (28) + window + button below (24)
        pw = Math.min(260, width - 16); ph = Math.min(206, height - 60);
        px = (width - pw) / 2; py = (height - ph - 52) / 2 + 28;
        gesehen = Zustand.version;

        int inhaltY = py + 8;
        switch (reiter) {
            case SQUAD -> { if (!Zustand.serverDa()) keinServer(inhaltY); else if (Zustand.squad != null) squadReiter(inhaltY); else squadsReiter(inhaltY); }
            case EINLADEN -> { if (!Zustand.serverDa()) keinServer(inhaltY); else einladenReiter(inhaltY); }
            case STEUERUNG -> steuerungReiter(inhaltY);
            case EINSTELLUNGEN -> einstellungenReiter(inhaltY);
            case KARTEN -> kartenReiter(inhaltY);
            case AUSSEHEN -> aussehenReiter(inhaltY);
        }
    }

    /** Text into the window; whatever doesn't fit is cut with "…" (other languages are often longer). */
    private void text(int x, int y, String t, int farbe) {
        int max = px + pw - 6 - x;
        if (font.width(t) > max) {
            while (!t.isEmpty() && font.width(t + "…") > max) t = t.substring(0, t.length() - 1);
            t = t.stripTrailing() + "…";
        }
        texte.add(new Text(x, y, t, farbe));
    }
    private static Tooltip tip(String t) { return Tooltip.create(Component.literal(t)); }
    /** Translated text (assets/teamping/lang/de_de.json or en_us.json – follows the game language). */
    static String t(String schluessel, Object... werte) { return I18n.get("teamping." + schluessel, werte); }
    static String farbName(String id) { return net.minecraft.locale.Language.getInstance().has("teamping.farbe." + id) ? t("farbe." + id) : id; }

    private void keinServer(int y) {
        if (Zustand.statusDa()) {
            text(px + 8, y, t("gui.plugin_alt"), 0xFFAA3333);
            text(px + 8, y + 11, t("gui.neustart"), TEXT2);
            knopf(t("gui.kiste"), px + 8, y + 26, 160, 20, t("gui.kiste.tip"), b -> { onClose(); befehl("squad"); });
        } else {
            text(px + 8, y, t("gui.keine_verbindung"), 0xFFAA3333);
            text(px + 8, y + 11, t("gui.server_frage"), TEXT2);
        }
        text(px + 8, y + 54, t("gui.trotzdem"), TEXT2);
    }

    private static void befehl(String b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(b);
    }

    private void fragen(String titel, String text, Runnable ja) {
        Screen zurueck = this;
        minecraft.gui.setScreen(new ConfirmScreen(ok -> { if (ok) ja.run(); minecraft.gui.setScreen(zurueck); },
                Component.literal(titel), Component.literal(text)));
    }

    private Button knopf(String t, int x, int y, int b, int h, String tooltip, Button.OnPress was) {
        Button k = Button.builder(Component.literal(t), was).bounds(x, y, b, h).tooltip(tip(tooltip)).build();
        addRenderableWidget(k);
        return k;
    }

    // ------------------------------------------------------------------ squad (member)
    private void squadReiter(int y) {
        text(px + 8, y, t("gui.squad_name", Zustand.squad), TEXT);
        String status = Zustand.offen ? t("gui.status.offen") : t("gui.status.geschlossen");
        text(px + pw - 8 - font.width(status), y, status, Zustand.offen ? (hell == 1 ? 0xFF2E7D2E : 0xFF6FD66F) : (hell == 1 ? 0xFFA05030 : 0xFFFFAA55));

        List<Zustand.Mitglied> m = Zustand.mitglieder;
        int by = py + ph - 48;
        int ly = y + 11, lh = by - 4 - ly;
        int kh = 26, abstand = 2;
        int platz = Math.max(1, (lh - 4 + abstand) / (kh + abstand));
        Einsatz liste = new Einsatz(px + 7, ly, pw - 14, lh);
        einsaetze.add(liste);
        int start = scrollen(m.size(), platz, liste);
        int kb = pw - 20 - (scrollMax > 0 ? 8 : 0);
        int zy = ly + 3;
        for (int i = start; i < Math.min(m.size(), start + platz); i++) {
            Zustand.Mitglied mit = m.get(i);
            boolean istLeiter = mit.name().equals(Zustand.leiter), istIch = Zustand.ich != null && Zustand.ich.equals(mit.uuid());
            boolean knoepfe = Zustand.ichLeiter && !istIch;
            kacheln.add(new Kachel(px + 10, zy, kb, kh, mit, istLeiter, istIch, knoepfe ? 34 : 0));
            if (knoepfe) {
                int kg = Math.min(14, kh - 1), kx = px + 10 + kb - 32, ky = zy + (kh - kg) / 2;
                knopf("★", kx, ky, 14, kg, t("gui.leiter.tip", mit.name()), b ->
                        fragen(t("gui.leiter.frage"), t("gui.leiter.text", mit.name()), () -> befehl("squad leiter " + mit.name())))
                        .setMessage(Component.literal("★").withStyle(ChatFormatting.GOLD));
                knopf("✖", kx + 16, ky, 14, kg, t("gui.kick.tip", mit.name()), b ->
                        fragen(t("gui.kick.frage", mit.name()), t("gui.kick.text", mit.name()), () -> befehl("squad kicken " + mit.name())))
                        .setMessage(Component.literal("✖").withStyle(ChatFormatting.RED));
            }
            zy += kh + abstand;
        }

        int b3 = (pw - 16 - 4) / 3;
        String pf = Zustand.pingfarbeId.isEmpty() ? Zustand.pingfarbe : t("pingfarbe." + Zustand.pingfarbeId);
        knopf("Ping", px + 8, by, b3, 20, t("gui.pingfarbe.tip", pf), b -> befehl("squad pingfarbe"))
                .setMessage(Component.literal("■").withColor(Zustand.pingrgb).append(Component.literal(" " + pf).withColor(0xFFFFFF)));
        knopf(t("gui.chat", Zustand.chat ? t("gui.an") : t("gui.aus")), px + 10 + b3, by, b3, 20,
                t("gui.chat.tip", Zustand.chat ? t("gui.an") : t("gui.aus")), b -> {
                    Zustand.chatLokal(!Zustand.chat);       // toggle right away, the server confirms
                    befehl("sc");
                    rebuildWidgets();
                });
        knopf(t("gui.einladen"), px + 12 + 2 * b3, by, b3, 20, t("gui.einladen.tip"), b -> { reiter = Reiter.EINLADEN; scroll = 0; rebuildWidgets(); });
        by += 22;
        if (Zustand.ichLeiter) {
            int idx = 0;
            for (int i = 0; i < TEAMFARBEN.length; i++) if (TEAMFARBEN[i][0].equals(Zustand.teamfarbeName)) idx = i;
            final int aktuell = idx;
            knopf(t("gui.farbe"), px + 8, by, b3, 20, t("gui.teamfarbe.tip"),
                    b -> befehl("squad farbe " + TEAMFARBEN[(aktuell + (minecraft.hasShiftDown() ? TEAMFARBEN.length - 1 : 1)) % TEAMFARBEN.length][0]))
                    .setMessage(Component.literal(t("gui.farbe") + ": ").append(Component.literal(farbName(Zustand.teamfarbeName)).withColor(Integer.parseInt(TEAMFARBEN[aktuell][1], 16))));
            knopf(Zustand.offen ? t("gui.offen") : t("gui.geschlossen"), px + 10 + b3, by, b3, 20,
                    t("gui.offen.tip"), b -> {
                        boolean neu = !Zustand.offen;
                        Zustand.offenLokal(neu);
                        befehl(neu ? "squad offen" : "squad geschlossen");
                        rebuildWidgets();
                    });
            knopf(t("gui.aufloesen"), px + 12 + 2 * b3, by, b3, 20, t("gui.aufloesen.tip"), b ->
                    fragen(t("gui.aufloesen.frage"), t("gui.aufloesen.text"), () -> befehl("squad aufloesen")))
                    .setMessage(Component.literal(t("gui.aufloesen")).withStyle(ChatFormatting.RED));
        }
        knopf(t("gui.verlassen"), px + 8, py + ph + 4, 80, 20, Zustand.ichLeiter ? t("gui.verlassen.tip_leiter") : t("gui.verlassen.tip"),
                b -> fragen(t("gui.verlassen.frage"), Zustand.ichLeiter ? t("gui.verlassen.text_leiter") : t("gui.verlassen.text"),
                        () -> befehl("squad verlassen"))).setMessage(Component.literal(t("gui.verlassen")).withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------ squads (no squad)
    private void squadsReiter(int y) {
        text(px + 8, y, t("gui.neu"), TEXT);
        EditBox name = new EditBox(font, px + 8, y + 11, 104, 18, Component.literal("Name"));
        name.setMaxLength(12);
        name.setHint(Component.literal(t("gui.name")).withStyle(ChatFormatting.DARK_GRAY));
        name.setValue(nameEingabe);
        name.setResponder(v -> nameEingabe = v);
        name.setTooltip(tip(t("gui.name.tip")));
        addRenderableWidget(name);
        knopf(t("gui.farbe"), px + 115, y + 10, 74, 20, t("gui.teamfarbe.tip"),
                b -> { farbeWahl = (farbeWahl + (minecraft.hasShiftDown() ? TEAMFARBEN.length - 1 : 1)) % TEAMFARBEN.length; rebuildWidgets(); })
                .setMessage(Component.literal(farbName(TEAMFARBEN[farbeWahl][0])).withColor(Integer.parseInt(TEAMFARBEN[farbeWahl][1], 16)));
        knopf(t("gui.erstellen"), px + 192, y + 10, pw - 200, 20, t("gui.erstellen.tip"), b -> {
            String n = nameEingabe.trim().toLowerCase(Locale.ROOT);
            if (!n.matches("[a-z0-9_]{1,12}")) return;
            befehl("squad erstellen " + n + " " + TEAMFARBEN[farbeWahl][0]);
            nameEingabe = "";
        });

        int zy = y + 36;
        for (String e : Zustand.einladungen) {
            text(px + 8, zy + 5, "✉ " + t("gui.einladung", e), hell == 1 ? 0xFF8A6A10 : 0xFFFFD25A);
            knopf(t("gui.annehmen"), px + pw - 78, zy, 70, 18, t("gui.annehmen.tip", e), b -> befehl("squad annehmen " + e));
            zy += 20;
            if (zy > py + 90) break;
        }
        text(px + 8, zy + 3, t("gui.alle"), TEXT);
        zy += 13;
        List<Zustand.SquadInfo> sq = Zustand.squads;
        int platz = Math.max(1, (py + ph - 8 - zy - 4) / 22);
        Einsatz liste = new Einsatz(px + 7, zy, pw - 14, platz * 22 + 4);
        einsaetze.add(liste);
        int start = scrollen(sq.size(), platz, liste);
        int rb = pw - 20 - (scrollMax > 0 ? 8 : 0);
        int ry = zy + 3;
        if (sq.isEmpty()) text(px + 12, ry + 5, t("gui.keine_squads"), TEXT2);
        for (int i = start; i < Math.min(sq.size(), start + platz); i++) {
            Zustand.SquadInfo s = sq.get(i);
            zeilen.add(new Zeile(px + 10, ry, rb, null, s.name(), 0xFF000000 | s.farbe(), t("gui.squad_info", s.anzahl(), s.leiter()), TEXT2, 0, false));
            boolean darf = s.offen() || s.eingeladen();
            Button b = knopf(darf ? t("gui.beitreten") : "🔒", px + 10 + rb - 68, ry + 2, 66, 16, darf ? t("gui.beitreten.tip", s.name()) : t("gui.nur_einladung"),
                    x -> befehl("squad beitreten " + s.name()));
            b.active = darf;
            // info button: hovering shows who is in the squad
            StringBuilder wer = new StringBuilder(t("gui.mitglieder", s.mitglieder().size()));
            for (Zustand.SquadMitglied sm : s.mitglieder())
                wer.append("\n").append(sm.leiter() ? "★ " : "• ").append(sm.name()).append(sm.online() ? "" : " (" + t("gui.offline") + ")");
            knopf("i", px + 10 + rb - 86, ry + 2, 16, 16, wer.toString(), x -> {}).setMessage(Component.literal("i").withStyle(ChatFormatting.AQUA));
            ry += 22;
        }
    }

    // ------------------------------------------------------------------ invite
    private void einladenReiter(int y) {
        if (Zustand.squad == null) { text(px + 8, y, t("gui.erst_squad"), TEXT2); return; }
        if (!Zustand.offen && !Zustand.ichLeiter) { text(px + 8, y, t("gui.nur_leiter"), TEXT2); return; }
        text(px + 8, y, t("gui.online_einladen"), TEXT);
        List<Zustand.Spieler> sp = Zustand.spieler;
        int ly = y + 11, platz = Math.max(1, (py + ph - 8 - ly - 4) / 22);
        Einsatz liste = new Einsatz(px + 7, ly, pw - 14, platz * 22 + 4);
        einsaetze.add(liste);
        int start = scrollen(sp.size(), platz, liste);
        int rb = pw - 20 - (scrollMax > 0 ? 8 : 0);
        int zy = ly + 3;
        if (sp.isEmpty()) text(px + 12, zy + 5, t("gui.niemand"), TEXT2);
        for (int i = start; i < Math.min(sp.size(), start + platz); i++) {
            Zustand.Spieler s = sp.get(i);
            zeilen.add(new Zeile(px + 10, zy, rb, s.uuid(), s.name(), 0, s.squad() == null ? t("gui.kein_squad") : t("gui.in_squad", s.squad()), TEXT2, 0, false));
            knopf(t("gui.einladen"), px + 10 + rb - 68, zy + 2, 66, 16, t("gui.einladen_spieler.tip", s.name()), b -> befehl("squad einladen " + s.name()));
            zy += 22;
        }
    }

    // ------------------------------------------------------------------ controls
    private void steuerungReiter(int y) {
        int x = px + 8, b3 = (pw - 16 - 8) / 3;
        text(x, y, t("gui.tasten"), TEXT);
        int ty = y + 11;
        tasteKnopf(x, ty, b3, t("gui.taste.ping"), TeamPingClient.tastePing, t("gui.taste.ping.tip"));
        tasteKnopf(x + b3 + 4, ty, b3, t("gui.taste.menu"), TeamPingClient.tasteMenue, t("gui.taste.menu.tip"));
        knopf(t("gui.tasten_reset"), x + 2 * (b3 + 4), ty, b3, 20, t("gui.tasten_reset.tip"), b -> {
            for (KeyMapping k : tastenListe) k.setKey(k.getDefaultKey());
            tastenGespeichert();
        });

        int gy = ty + 28;
        text(x, gy, t("gui.gesten"), TEXT);
        text(x, gy + 10, t("gui.gesten.hinweis"), TEXT2);
        gy += 23;
        if (!Zustand.serverDa()) text(x, gy + 4, t("gui.nur_server"), TEXT2);
        for (Zustand.Geste g : Zustand.gesten) {
            Checkbox c = Checkbox.builder(Component.empty(), font).pos(x + 2, gy).selected(g.an())
                    .tooltip(tip(gesteName(g) + "\n" + gesteInfo(g) + "\n" + t("gui.gesten.mehrere")))
                    .onValueChange((box, an) -> befehl("squad taste " + g.id())).build();
            addRenderableWidget(c);
            text(x + 26, gy + 1, gesteName(g), TEXT);
            text(x + 26, gy + 11, gesteInfo(g), TEXT2);
            gy += 24;
        }
    }

    private static String gesteName(Zustand.Geste g) { return net.minecraft.locale.Language.getInstance().has("teamping.geste." + g.id()) ? t("geste." + g.id()) : g.name(); }
    private static String gesteInfo(Zustand.Geste g) { return net.minecraft.locale.Language.getInstance().has("teamping.geste." + g.id() + ".info") ? t("geste." + g.id() + ".info") : g.beschreibung(); }

    private void tasteKnopf(int x, int y, int b, String name, KeyMapping k, String tooltip) {
        Button knopf = Button.builder(beschriftung(name, k, false), btn -> { wartetAuf = k; aktualisieren(); }).bounds(x, y, b, 20).tooltip(tip(tooltip)).build();
        tastenKnoepfe.add(knopf); tastenListe.add(k); tastenNamen.add(name);
        addRenderableWidget(knopf);
    }

    private static Component beschriftung(String name, KeyMapping k, boolean wartet) {
        if (wartet) return Component.literal(name + ": ").append(Component.literal("> ? <").withStyle(ChatFormatting.YELLOW));
        Component taste = k.isUnbound() ? Component.literal(t("gui.keine_taste")).withStyle(ChatFormatting.GRAY) : k.getTranslatedKeyMessage();
        return Component.literal(name + ": ").append(taste);
    }

    private void aktualisieren() {
        for (int i = 0; i < tastenKnoepfe.size(); i++)
            tastenKnoepfe.get(i).setMessage(beschriftung(tastenNamen.get(i), tastenListe.get(i), tastenListe.get(i) == wartetAuf));
    }

    private void tastenGespeichert() {
        KeyMapping.resetMapping();
        minecraft.options.save();
        wartetAuf = null;
        aktualisieren();
    }

    // ------------------------------------------------------------------ settings
    private void einstellungenReiter(int y) {
        Einstellungen e = Einstellungen.aktuell;
        int x = px + 10, cy = y - 2;
        cy = haken(x, cy, "uebersicht", e.uebersicht, v -> e.uebersicht = v);
        cy = haken(x, cy, "selbst", e.selbst, v -> e.selbst = v);
        cy = haken(x, cy, "marker", e.marker, v -> e.marker = v);
        cy = haken(x, cy, "namen", e.markerNamen, v -> e.markerNamen = v);
        int halb = (pw - 20) / 2;
        addRenderableWidget(CycleButton.builder((Einstellungen.Ecke c) -> Component.literal(t("ecke." + c.name())), e.ecke)
                .withValues(List.of(Einstellungen.Ecke.values()))
                .withTooltip(v -> tip(t("opt.ort.tip")))
                .create(px + 8, cy + 2, halb, 20, Component.literal(t("opt.ort")), (b, v) -> { e.ecke = v; Einstellungen.speichern(); }));
        addRenderableWidget(CycleButton.builder((Einstellungen.MarkerOrt c) -> Component.literal(t("opt.markerort." + c.name().toLowerCase(Locale.ROOT))), e.markerOrt)
                .withValues(List.of(Einstellungen.MarkerOrt.values()))
                .withTooltip(v -> tip(t("opt.markerort.tip")))
                .create(px + 12 + halb, cy + 2, halb, 20, Component.literal(t("opt.markerort")), (b, v) -> { e.markerOrt = v; Einstellungen.speichern(); }));
        Regler groesse = new Regler(px + 8, cy + 26, pw - 16, 20, t("opt.groesse"), (e.groesse - 0.5) / 1.5,
                v -> Math.round((0.5 + v * 1.5) * 100) + " %", v -> { e.groesse = 0.5 + v * 1.5; Einstellungen.speichern(); });
        groesse.setTooltip(tip(t("opt.groesse.tip")));
        addRenderableWidget(groesse);
        List<Integer> anzahlen = List.of(1, 2, 3, 4, 5, 6, 8, 10, 12);
        addRenderableWidget(CycleButton.builder((Integer n) -> Component.literal(String.valueOf(n)), anzahlen.contains(e.maxEintraege) ? e.maxEintraege : 5)
                .withValues(anzahlen)
                .withTooltip(v -> tip(t("opt.max.tip")))
                .create(px + 8, cy + 50, halb, 20, Component.literal(t("opt.max")), (b, v) -> { e.maxEintraege = v; Einstellungen.speichern(); }));
        addRenderableWidget(CycleButton.builder((Einstellungen.Sortierung c) -> Component.literal(t("opt.sort." + c.name().toLowerCase(Locale.ROOT))), e.sortierung)
                .withValues(List.of(Einstellungen.Sortierung.values()))
                .withTooltip(v -> tip(t("opt.sort.tip")))
                .create(px + 12 + halb, cy + 50, halb, 20, Component.literal(t("opt.sort")), (b, v) -> { e.sortierung = v; Einstellungen.speichern(); }));
    }

    /** "Look" tab: how YOU see pings – beam or dot only, width, opacity, height. Only for you. */
    private void aussehenReiter(int y) {
        Einstellungen e = Einstellungen.aktuell;
        int x = px + 10, cy = y;
        text(x, cy, t("aussehen.titel"), TEXT);
        text(x, cy + 11, t("aussehen.text"), TEXT2);
        cy += 26;
        int b = pw - 20;
        addRenderableWidget(CycleButton.builder((Einstellungen.StrahlArt a) -> Component.literal(t("aussehen.art." + a.name().toLowerCase(Locale.ROOT))), e.strahlArt)
                .withValues(List.of(Einstellungen.StrahlArt.values()))
                .withTooltip(v -> tip(t("aussehen.art.tip")))
                .create(px + 10, cy, b, 20, Component.literal(t("aussehen.art")), (k, v) -> { e.strahlArt = v; gespeichert(); }));
        cy += 24;
        addRenderableWidget(new Regler(px + 10, cy, b, 20, t("aussehen.breite"), (e.strahlBreite - 50) / 150.0,
                v -> Math.round(50 + v * 150) + " %", v -> { e.strahlBreite = (int) Math.round(50 + v * 150); gespeichert(); }));
        cy += 24;
        addRenderableWidget(new Regler(px + 10, cy, b, 20, t("aussehen.hell"), (e.strahlHell - 30) / 70.0,
                v -> Math.round(30 + v * 70) + " %", v -> { e.strahlHell = (int) Math.round(30 + v * 70); gespeichert(); }));
        cy += 24;
        addRenderableWidget(new Regler(px + 10, cy, b, 20, t("aussehen.hoehe"), (e.strahlHoehe - 25) / 125.0,
                v -> Math.round(25 + v * 125) + " %", v -> { e.strahlHoehe = (int) Math.round(25 + v * 125); gespeichert(); }));
        cy += 26;
        knopf(t("aussehen.standard"), px + 10, cy, b / 2, 20, t("aussehen.standard.tip"), k -> {
            e.strahlArt = Einstellungen.StrahlArt.STRAHL; e.strahlBreite = 100; e.strahlHell = 100; e.strahlHoehe = 100;
            gespeichert(); rebuildWidgets();
        });
    }

    private static void gespeichert() { Einstellungen.speichern(); TeamPingClient.aussehenGeaendert(); }

    /** "Maps & mods" tab: working together with other mods on your PC (Xaero maps, Distant Horizons). */
    private void kartenReiter(int y) {
        Einstellungen e = Einstellungen.aktuell;
        int x = px + 10, cy = y;
        text(x, cy, t("karten.titel"), TEXT);
        cy += 14;
        boolean xa = TeamPingClient.xaeroDa(), dh = FernPing.dhInstalliert();
        cy = haken(x, cy, "xaero", e.xaero, v -> {
            e.xaero = v;
            if (!v && TeamPingClient.xaeroDa()) XaeroBruecke.leeren();
        });
        text(x + 24, cy - 3, xa ? t("karten.erkannt") : t("karten.fehlt"), xa ? 0xFF55AA55 : 0xFFAA5555);
        cy += 10;
        cy = haken(x, cy, "dh", e.distantHorizons, v -> e.distantHorizons = v);
        text(x + 24, cy - 3, dh ? t("karten.erkannt") : t("karten.fehlt"), dh ? 0xFF55AA55 : 0xFFAA5555);
        cy += 16;
        text(x, cy, t("karten.weit1"), TEXT2);
        text(x, cy + 10, t("karten.weit2"), TEXT2);
        text(x, cy + 20, t("karten.weit3"), TEXT2);
    }

    /** Checkbox with name and a short visible explanation below (long explanation in the tooltip). */
    private int haken(int x, int y, String id, boolean an, java.util.function.Consumer<Boolean> setzen) {
        Checkbox c = Checkbox.builder(Component.empty(), font).pos(x, y).selected(an).tooltip(tip(t("opt." + id + ".tip")))
                .onValueChange((box, v) -> { setzen.accept(v); Einstellungen.speichern(); }).build();
        addRenderableWidget(c);
        text(x + 24, y + 1, t("opt." + id), TEXT);
        text(x + 24, y + 11, t("opt." + id + ".kurz"), TEXT2);
        return y + 24;
    }

    // ------------------------------------------------------------------ scrolling (mouse wheel + scroll bar)
    /** Remembers the list for the mouse wheel and returns the first visible entry. */
    private int scrollen(int anzahl, int sichtbar, Einsatz liste) {
        scrollMax = Math.max(0, anzahl - sichtbar);
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        scrollListe = liste;
        return scroll;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (scrollMax > 0 && v != 0) {
            int neu = Math.max(0, Math.min(scrollMax, scroll - (int) Math.signum(v)));
            if (neu != scroll) { scroll = neu; rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    private void bildlaufZeichnen(GuiGraphicsExtractor g) {
        if (scrollMax <= 0 || scrollListe == null) return;
        Einsatz l = scrollListe;
        int x = l.x() + l.b() - 9, y = l.y() + 2, h = l.h() - 4;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("widget/scroller_background"), x, y, 6, h);
        int griff = Math.max(10, h * h / (h + scrollMax * 22));
        int gy = y + (h - griff) * scroll / scrollMax;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("widget/scroller"), x, gy, 6, griff);
    }

    // ================================================================== flow
    @Override
    public void tick() {
        super.tick();
        if (Zustand.serverDa() != warSquadDa) { warSquadDa = Zustand.serverDa(); rebuildWidgets(); return; }
        if (Zustand.version != gesehen && reiter != Reiter.EINSTELLUNGEN && reiter != Reiter.KARTEN && reiter != Reiter.AUSSEHEN && wartetAuf == null) rebuildWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        if (wartetAuf != null) {
            if (ev.key() == 256) wartetAuf.setKey(InputConstants.UNKNOWN);
            else wartetAuf.setKey(InputConstants.getKey(ev));
            tastenGespeichert();
            return true;
        }
        return super.keyPressed(ev);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean doppelt) {
        if (wartetAuf != null) {
            wartetAuf.setKey(InputConstants.Type.MOUSE.getOrCreate(ev.button()));
            tastenGespeichert();
            return true;
        }
        if (ev.button() == InputConstants.MOUSE_BUTTON_LEFT && ev.y() >= py - 28 && ev.y() < py && ev.x() >= px) {
            int i = (int) ((ev.x() - px) / 27);
            if (i >= 0 && i < Reiter.values().length && Reiter.values()[i] != reiter) {
                reiter = Reiter.values()[i]; scroll = 0; wartetAuf = null;
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(ev, doppelt);
    }

    // ------------------------------------------------------------------ drawing
    private void reiterZeichnen(GuiGraphicsExtractor g, boolean ausgewaehlt) {
        Reiter[] alle = Reiter.values();
        for (int i = 0; i < alle.length; i++) {
            boolean sel = alle[i] == reiter;
            if (sel != ausgewaehlt) continue;
            Identifier spr = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_" + (sel ? "selected_" : "unselected_") + (i + 1));
            g.blitSprite(RenderPipelines.GUI_TEXTURED, spr, px + i * 27, py - 28, 26, 32);
            int ix = px + i * 27 + 5, iy = py - 28 + (sel ? 9 : 11);
            switch (alle[i]) {
                case SQUAD -> PlayerFaceExtractor.extractRenderState(g, eigenerSkin(), ix, iy, 16);
                case EINLADEN -> g.item(new ItemStack(Items.WRITABLE_BOOK), ix, iy);
                case STEUERUNG -> g.item(new ItemStack(Items.TRIPWIRE_HOOK), ix, iy);
                case EINSTELLUNGEN -> g.item(new ItemStack(Items.COMPARATOR), ix, iy);
                case KARTEN -> g.item(new ItemStack(Items.FILLED_MAP), ix, iy);
                case AUSSEHEN -> g.item(new ItemStack(Items.BEACON), ix, iy);
            }
        }
    }

    /** Window background as a 9-slice from the creative inventory (4 px border, centre from the empty title row). */
    private void fensterZeichnen(GuiGraphicsExtractor g) {
        int r = 4, t = 256;
        int x0 = px, y0 = py, x1 = px + pw, y1 = py + ph;
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x0, y0, 0, 0, r, r, t, t);
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x1 - r, y0, 195 - r, 0, r, r, t, t);
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x0, y1 - r, 0, 136 - r, r, r, t, t);
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x1 - r, y1 - r, 195 - r, 136 - r, r, r, t, t);
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x0 + r, y0, 8, 0, pw - 2 * r, r, 100, r, t, t);
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x0 + r, y1 - r, 8, 136 - r, pw - 2 * r, r, 100, r, t, t);
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x0, y0 + r, 0, 5, r, ph - 2 * r, r, 10, t, t);
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x1 - r, y0 + r, 195 - r, 5, r, ph - 2 * r, r, 10, t, t);
        g.blit(RenderPipelines.GUI_TEXTURED, FENSTER, x0 + r, y0 + r, 8, 5, pw - 2 * r, ph - 2 * r, 100, 10, t, t);
    }

    private void einsatzZeichnen(GuiGraphicsExtractor g, Einsatz e) {
        g.fill(e.x(), e.y(), e.x() + e.b(), e.y() + e.h(), EINSATZ);
        g.fill(e.x(), e.y(), e.x() + e.b() - 1, e.y() + 1, EINSATZ_DUNKEL);
        g.fill(e.x(), e.y(), e.x() + 1, e.y() + e.h() - 1, EINSATZ_DUNKEL);
        g.fill(e.x() + 1, e.y() + e.h() - 1, e.x() + e.b(), e.y() + e.h(), EINSATZ_HELL);
        g.fill(e.x() + e.b() - 1, e.y() + 1, e.x() + e.b(), e.y() + e.h(), EINSATZ_HELL);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        if (testMaus != null) { mx = testMaus[0]; my = testMaus[1]; }
        super.extractBackground(g, mx, my, pt);
        reiterZeichnen(g, false);
        fensterZeichnen(g);
        reiterZeichnen(g, true);
        for (Einsatz e : einsaetze) einsatzZeichnen(g, e);
        for (Zeile z : zeilen) {
            g.fill(z.x(), z.y(), z.x() + z.b(), z.y() + 20, ZEILE_BG);
            g.fill(z.x(), z.y() + 19, z.x() + z.b(), z.y() + 20, EINSATZ_DUNKEL);
            if (drin(mx, my, z.x(), z.y(), z.b(), 20)) hover(g, z.x(), z.y(), z.b(), 20);
        }
        for (int i = 0; i < kacheln.size(); i++) kachelHintergrund(g, kacheln.get(i), i, mx, my);
        bildlaufZeichnen(g);
    }

    private static boolean drin(int mx, int my, int x, int y, int b, int h) { return mx >= x && mx < x + b && my >= y && my < y + h; }

    /** Hover like vanilla slots: light overlay + light frame. */
    private void hover(GuiGraphicsExtractor g, int x, int y, int b, int h) {
        g.fill(x, y, x + b, y + h, hell == 1 ? 0x40FFFFFF : 0x24FFFFFF);
        int r = hell == 1 ? 0xFFFFFFFF : 0xFF8A8A8A;
        g.fill(x, y, x + b, y + 1, r); g.fill(x, y + h - 1, x + b, y + h, r);
        g.fill(x, y, x + 1, y + h, r); g.fill(x + b - 1, y, x + b, y + h, r);
    }

    private int pingFarbe(Kachel k) { return k.m().online() ? 0xFF000000 | k.m().farbe() : 0xFF777777; }

    private void kachelHintergrund(GuiGraphicsExtractor g, Kachel k, int i, int mx, int my) {
        int x = k.x(), y = k.y(), b = k.b(), h = k.h();
        // colour strip on the left in ping colour, head with dark frame
        g.fill(x, y, x + b, y + h, ZEILE_BG);
        g.fill(x, y + h - 1, x + b, y + h, EINSATZ_DUNKEL);
        g.fill(x, y, x + 3, y + h, pingFarbe(k));
        g.fill(x + 7, y + 4, x + 25, y + 22, EINSATZ_DUNKEL);
        if (drin(mx, my, x, y, b, h)) hover(g, x, y, b, h);
    }

    private void kachelInhalt(GuiGraphicsExtractor g, Kachel k) {
        Zustand.Mitglied m = k.m();
        int x = k.x(), y = k.y(), rechts = x + k.b() - 4 - k.rechtsFrei();
        String name = m.name();
        int nameFarbe = m.online() ? TEXT : TEXT2;
        String info = !m.online() ? t("gui.offline") : k.ich() ? t("gui.du") : SquadHud.entfernung(minecraft, m, true);
        if (info == null) info = "";
        PlayerFaceExtractor.extractRenderState(g, SquadHud.skin(minecraft, m), x + 8, y + 5, 16);
        if (!m.online()) g.fill(x + 8, y + 5, x + 24, y + 21, 0x99000000);
        int tx = x + 30;
        if (k.leiter()) { g.text(font, "★", tx, y + 4, 0xFFE0A000, false); tx += 9; }
        g.text(font, name, tx, y + 4, nameFarbe, false);
        g.text(font, info, rechts - font.width(info), y + 4, TEXT2, false);
        if (m.online()) herzen(g, x + 30, y + 14, m.hp(), m.max());
    }

    /** 10 vanilla hearts (health converted to 20 half hearts). */
    private void herzen(GuiGraphicsExtractor g, int x, int y, float hp, float max) {
        int halbe = max > 0 ? Math.round(Math.max(0, Math.min(1, hp / max)) * 20) : 0;
        for (int i = 0; i < 10; i++) {
            int hx = x + i * 8;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/heart/container"), hx, y, 9, 9);
            if (halbe >= i * 2 + 2) g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/heart/full"), hx, y, 9, 9);
            else if (halbe == i * 2 + 1) g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/heart/half"), hx, y, 9, 9);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        for (Text t : texte) g.text(font, t.t(), t.x(), t.y(), t.farbe(), false);
        for (Kachel k : kacheln) kachelInhalt(g, k);
        for (Zeile z : zeilen) {
            int tx = z.x() + 4;
            if (z.kopf() != null) { PlayerFaceExtractor.extractRenderState(g, skin(z.kopf()), z.x() + 2, z.y() + 2, 16); tx = z.x() + 22; }
            g.text(font, z.text(), tx, z.y() + 2, z.punkt() != 0 && z.kopf() == null ? z.punkt() : TEXT, false);
            int ux = tx;
            if (z.zeigHp()) {
                g.text(font, "❤", ux, z.y() + 11, 0xFFC03030, false);
                g.text(font, String.valueOf(Math.round(z.hp())), ux + 8, z.y() + 11, 0xFFB03030, false);
                ux += 26;
            }
            if (z.kopf() != null && z.punkt() != 0) { g.fill(ux, z.y() + 12, ux + 5, z.y() + 17, z.punkt()); ux += 8; }
            if (z.unten() != null) g.text(font, z.unten(), ux, z.y() + 11, z.untenFarbe(), false);
        }
        if (reiter == Reiter.STEUERUNG && wartetAuf != null)
            g.centeredText(font, t("gui.taste_druecken"), width / 2, py + ph + 8, 0xFFFFFF55);
    }

    private PlayerSkin skin(UUID id) {
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(id);
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(id);
    }

    private PlayerSkin eigenerSkin() {
        return minecraft.player != null ? skin(minecraft.player.getUUID()) : DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    public void onClose() {
        Einstellungen.speichern();
        minecraft.gui.setScreen(eltern);
    }

    /** Simple slider 0..1. */
    private static final class Regler extends AbstractSliderButton {
        private final String name;
        private final DoubleFunction<String> text;
        private final DoubleConsumer setzen;

        Regler(int x, int y, int b, int h, String name, double wert, DoubleFunction<String> text, DoubleConsumer setzen) {
            super(x, y, b, h, Component.empty(), Math.max(0, Math.min(1, wert)));
            this.name = name; this.text = text; this.setzen = setzen;
            updateMessage();
        }

        @Override protected void updateMessage() { setMessage(Component.literal(name + ": " + text.apply(value))); }
        @Override protected void applyValue() { setzen.accept(value); }
    }
}
