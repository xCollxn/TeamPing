package io.github.xcollxn.teamping;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.function.Consumer;

/** Chest menus for squads (also work on Bedrock via Geyser). */
final class SquadMenu implements InventoryHolder {

    private final Inventory inv;
    final Map<Integer, Consumer<Player>> aktionen = new HashMap<>();

    private SquadMenu(int reihen, Component titel) {
        this.inv = Bukkit.createInventory(this, reihen * 9, titel);
    }

    @Override public Inventory getInventory() { return inv; }

    private void setze(int slot, ItemStack item, Consumer<Player> aktion) {
        inv.setItem(slot, item);
        if (aktion != null) aktionen.put(slot, aktion);
    }

    private void rahmen() {
        ItemStack glas = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null && (i < 9 || i >= inv.getSize() - 9)) inv.setItem(i, glas);
    }

    // ------------------------------------------------------------------ item helpers
    static Component t(String s, NamedTextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    /** Translated text in the player's language, in colour c. */
    static Component tx(Player p, NamedTextColor c, String schluessel, Object... werte) {
        return Texte.fuer(p, schluessel, werte).colorIfAbsent(c).decoration(TextDecoration.ITALIC, false);
    }

    static ItemStack item(Material m, Component name, Component... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        i.setItemMeta(meta);
        return i;
    }

    static ItemStack kopf(String spieler, Component name, Component... lore) {
        ItemStack i = item(Material.PLAYER_HEAD, name, lore);
        SkullMeta sm = (SkullMeta) i.getItemMeta();
        Player online = Bukkit.getPlayerExact(spieler);
        OfflinePlayer op = online != null ? online : Bukkit.getOfflinePlayerIfCached(spieler);
        if (op != null) sm.setOwningPlayer(op);
        i.setItemMeta(sm);
        return i;
    }

    // ------------------------------------------------------------------ menus
    /** Entry: depending on whether the player is in a squad. */
    static void oeffnen(TeamPing pl, Player p) {
        if (pl.hatMod(p)) { p.closeInventory(); pl.uebersicht().menuOeffnen(p); return; }   // the companion mod's own window
        kisteOeffnen(pl, p);
    }

    /** Chest menu or Bedrock form – also for mod users via /squad chest. */
    static void kisteOeffnen(TeamPing pl, Player p) {
        if (BedrockCheck.aktiv(p)) { BedrockMenus.oeffnen(pl, p); return; }
        Team t = pl.squads().squadVon(p);
        if (t == null) hauptmenue(pl, p); else squadMenue(pl, p, t);
    }

    static void hauptmenue(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        SquadMenu m = new SquadMenu(6, Texte.fuer(p, "menu.titel"));
        m.setze(4, item(Material.BOOK, tx(p, NamedTextColor.AQUA, "menu.info.titel"),
                tx(p, NamedTextColor.GRAY, "menu.info.1"),
                tx(p, NamedTextColor.GRAY, "menu.info.2"),
                Component.empty(),
                tx(p, NamedTextColor.YELLOW, pl.istBedrock(p) ? "menu.info.ping_bedrock" : "menu.info.ping_java"),
                tx(p, NamedTextColor.YELLOW, "menu.info.oder_ping")), null);
        m.setze(20, item(Material.NETHER_STAR, tx(p, NamedTextColor.GREEN, "menu.erstellen"),
                tx(p, NamedTextColor.GRAY, "menu.erstellen.1"),
                tx(p, NamedTextColor.GRAY, "menu.erstellen.2")), pl::namensEingabe);
        m.setze(22, steuerungItem(p), pp -> steuerungMenue(pl, pp));
        List<Team> einl = sq.einladungenVon(p);
        m.setze(24, item(einl.isEmpty() ? Material.BOOK : Material.WRITABLE_BOOK,
                tx(p, einl.isEmpty() ? NamedTextColor.GRAY : NamedTextColor.GOLD, "menu.einladungen", einl.size()),
                tx(p, NamedTextColor.GRAY, einl.isEmpty() ? "menu.keine_einladungen" : "menu.klick_ansehen")),
                einl.isEmpty() ? null : pp -> einladungen(pl, pp));
        int slot = 27;
        for (Team t : sq.alle()) {
            if (slot > 44) break;
            boolean offen = sq.offen(t), eingeladen = sq.hatEinladung(p, Squads.name(t));
            NamedTextColor c = Squads.farbe(t);
            m.setze(slot++, item(Squads.WOLLE.getOrDefault(c, Material.WHITE_WOOL), t(Squads.name(t), c).decorate(TextDecoration.BOLD),
                    tx(p, NamedTextColor.GRAY, "menu.leiter", sq.leiterName(t)),
                    tx(p, NamedTextColor.GRAY, "menu.mitglieder", String.join(", ", sq.mitglieder(t))),
                    Component.empty(),
                    offen || eingeladen ? tx(p, NamedTextColor.GREEN, "menu.klick_beitreten") : tx(p, NamedTextColor.RED, "menu.nur_einladung")),
                    pp -> { if (sq.beitreten(pp, t, eingeladen)) squadMenue(pl, pp, t); });
        }
        if (sq.alle().isEmpty()) m.setze(31, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, tx(p, NamedTextColor.GRAY, "menu.keine_squads"),
                tx(p, NamedTextColor.DARK_GRAY, "menu.erstelle_erstes")), null);
        m.rahmen();
        p.openInventory(m.inv);
    }

    static void squadMenue(TeamPing pl, Player p, Team t) {
        Squads sq = pl.squads();
        NamedTextColor c = Squads.farbe(t);
        boolean leiter = sq.istLeiter(p, t), offen = sq.offen(t);
        SquadMenu m = new SquadMenu(6, Texte.fuer(p, "menu.squad_titel", Component.text(Squads.name(t), c)));
        m.setze(4, item(Squads.WOLLE.getOrDefault(c, Material.WHITE_WOOL), t(Squads.name(t), c).decorate(TextDecoration.BOLD),
                tx(p, NamedTextColor.GRAY, "menu.leiter_krone", sq.leiterName(t)),
                tx(p, NamedTextColor.GRAY, "menu.status", Texte.tr(offen ? "menu.offen" : "menu.geschlossen")),
                tx(p, NamedTextColor.GRAY, "menu.mitglieder", t.getEntries().size())), null);

        int slot = 9;
        String leiterName = sq.leiterName(t);
        for (String mit : sq.mitglieder(t)) {
            if (slot > 35) break;
            boolean istL = mit.equals(leiterName), online = Bukkit.getPlayerExact(mit) != null;
            List<Component> lore = new ArrayList<>();
            lore.add(tx(p, istL ? NamedTextColor.GOLD : NamedTextColor.GRAY, istL ? "menu.ist_leiter" : "menu.ist_mitglied"));
            lore.add(tx(p, online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, online ? "menu.online" : "menu.offline"));
            Squads.PingFarbe pf = sq.pingFarbeVonName(mit);
            if (pf != null) lore.add(tx(p, NamedTextColor.GRAY, "menu.pingfarbe", Component.text("■ ", pf.text()).append(Texte.tr("pingfarbe." + pf.name()).color(pf.text()))));
            if (leiter && !mit.equals(p.getName())) { lore.add(Component.empty()); lore.add(tx(p, NamedTextColor.YELLOW, "menu.klick_verwalten")); }
            m.setze(slot++, kopf(mit, t(mit, c), lore.toArray(Component[]::new)),
                    leiter && !mit.equals(p.getName()) ? pp -> mitgliedMenue(pl, pp, mit) : null);
        }

        // bottom bar
        boolean darfEinladen = offen || leiter;
        m.setze(45, item(darfEinladen ? Material.EMERALD : Material.GRAY_DYE, tx(p, darfEinladen ? NamedTextColor.GREEN : NamedTextColor.GRAY, "menu.einladen"),
                tx(p, NamedTextColor.GRAY, darfEinladen ? "menu.einladen.info" : "squad.nur_leiter_einladen")),
                darfEinladen ? pp -> einladenMenue(pl, pp) : null);
        boolean chat = pl.squadChatAn(p);
        m.setze(46, item(chat ? Material.LIME_DYE : Material.PAPER, tx(p, chat ? NamedTextColor.GREEN : NamedTextColor.GRAY, "squad.chat", Texte.anAus(chat)),
                tx(p, NamedTextColor.GRAY, chat ? "menu.chat_an" : "menu.chat_aus"),
                tx(p, NamedTextColor.DARK_GRAY, "menu.chat_einzeln"),
                tx(p, NamedTextColor.YELLOW, "menu.klick_umschalten")),
                pp -> { pl.squadChatUmschalten(pp); squadMenue(pl, pp, t); });
        if (leiter) {
            m.setze(48, item(Squads.FARBSTOFF.getOrDefault(c, Material.WHITE_DYE), tx(p, c, "menu.farbe"),
                    tx(p, NamedTextColor.GRAY, "menu.aktuell", Texte.tr("farbe." + Squads.farbName(c)))), pp -> farbMenue(pl, pp));
            m.setze(49, item(offen ? Material.OAK_DOOR : Material.IRON_DOOR, tx(p, offen ? NamedTextColor.GREEN : NamedTextColor.RED, offen ? "menu.offen" : "menu.geschlossen"),
                    tx(p, NamedTextColor.GRAY, offen ? "menu.offen.info" : "menu.geschlossen.info"),
                    tx(p, NamedTextColor.YELLOW, "menu.klick_umschalten")),
                    pp -> { sq.offenSetzen(pp, !offen); squadMenue(pl, pp, t); });
            m.setze(52, item(Material.TNT, tx(p, NamedTextColor.RED, "menu.aufloesen"), tx(p, NamedTextColor.GRAY, "menu.aufloesen.info")),
                    pp -> bestaetigen(pl, pp, Texte.fuer(pp, "menu.aufloesen.frage"), x -> { sq.aufloesen(x); x.closeInventory(); }));
        }
        Squads.PingFarbe eigene = sq.pingFarbe(p);
        m.setze(47, item(eigene.glas, tx(p, NamedTextColor.WHITE, "menu.meine_pingfarbe", Component.text("■ ", eigene.text()).append(Texte.tr("pingfarbe." + eigene.name()).color(eigene.text()))),
                tx(p, NamedTextColor.GRAY, "menu.pingfarbe.info"),
                tx(p, NamedTextColor.YELLOW, "menu.pingfarbe.klick")),
                pp -> { Squads.PingFarbe neu = sq.pingFarbeWuerfeln(pp);
                        pp.sendActionBar(Texte.tr("squad.neue_pingfarbe", Component.text("■ ", neu.text()).append(Texte.tr("pingfarbe." + neu.name()).color(neu.text()))));
                        squadMenue(pl, pp, t); });
        m.setze(51, steuerungItem(p), pp -> steuerungMenue(pl, pp));
        m.setze(50, item(Material.COMPASS, tx(p, NamedTextColor.AQUA, "menu.pings"),
                tx(p, NamedTextColor.GRAY, "menu.pings.1"),
                tx(p, NamedTextColor.GRAY, "menu.pings.2"),
                Component.empty(),
                tx(p, NamedTextColor.RED, "menu.pings.3"),
                tx(p, NamedTextColor.GOLD, "menu.pings.4"),
                tx(p, NamedTextColor.RED, "menu.pings.5"),
                tx(p, NamedTextColor.GRAY, "menu.pings.6"),
                tx(p, NamedTextColor.GRAY, "menu.pings.7"),
                tx(p, NamedTextColor.GRAY, "menu.pings.8")), null);
        boolean hud = sq.uebersichtAn(p);
        m.setze(8, item(hud ? Material.SPYGLASS : Material.GRAY_DYE, tx(p, hud ? NamedTextColor.GREEN : NamedTextColor.GRAY, "squad.uebersicht", Texte.anAus(hud)),
                tx(p, NamedTextColor.GRAY, "menu.uebersicht.1"),
                tx(p, NamedTextColor.DARK_GRAY, "menu.uebersicht.2"),
                tx(p, NamedTextColor.YELLOW, "menu.klick_umschalten")),
                pp -> { sq.uebersichtUmschalten(pp); squadMenue(pl, pp, t); });
        m.setze(53, item(Material.BARRIER, tx(p, NamedTextColor.RED, "menu.verlassen"),
                tx(p, NamedTextColor.GRAY, leiter ? "menu.verlassen.leiter" : "menu.verlassen.normal")),
                pp -> bestaetigen(pl, pp, Texte.fuer(pp, "menu.verlassen.frage"), x -> { sq.verlassen(x, true); hauptmenue(pl, x); }));
        m.rahmen();
        p.openInventory(m.inv);
    }

    static void mitgliedMenue(TeamPing pl, Player p, String mitglied) {
        Squads sq = pl.squads();
        Team t = sq.squadVon(p);
        if (t == null || !sq.istLeiter(p, t)) { oeffnen(pl, p); return; }
        SquadMenu m = new SquadMenu(3, Component.text(mitglied));
        m.setze(4, kopf(mitglied, t(mitglied, Squads.farbe(t))), null);
        m.setze(11, item(Material.GOLDEN_HELMET, tx(p, NamedTextColor.GOLD, "menu.zum_leiter"), tx(p, NamedTextColor.GRAY, "menu.zum_leiter.info")),
                pp -> bestaetigen(pl, pp, Texte.fuer(pp, "menu.zum_leiter.frage", mitglied), x -> { sq.leiterUebertragen(x, mitglied); oeffnen(pl, x); }));
        m.setze(15, item(Material.IRON_SWORD, tx(p, NamedTextColor.RED, "menu.kicken")),
                pp -> bestaetigen(pl, pp, Texte.fuer(pp, "menu.kicken.frage", mitglied), x -> { sq.kicken(x, mitglied); oeffnen(pl, x); }));
        m.setze(22, item(Material.ARROW, tx(p, NamedTextColor.GRAY, "menu.zurueck")), pp -> oeffnen(pl, pp));
        m.rahmen();
        p.openInventory(m.inv);
    }

    static void einladenMenue(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        Team t = sq.squadVon(p);
        if (t == null) { hauptmenue(pl, p); return; }
        SquadMenu m = new SquadMenu(6, Texte.fuer(p, "menu.einladen"));
        int slot = 9;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (slot > 44) break;
            if (o.equals(p) || t.equals(sq.squadVon(o))) continue;
            Team anderes = sq.squadVon(o);
            m.setze(slot++, kopf(o.getName(), t(o.getName(), NamedTextColor.WHITE),
                    anderes == null ? tx(p, NamedTextColor.GRAY, "menu.kein_squad") : tx(p, NamedTextColor.GRAY, "menu.gerade_in", Squads.name(anderes)),
                    tx(p, NamedTextColor.GREEN, "menu.klick_einladen")),
                    pp -> { sq.einladen(pp, o); squadMenue(pl, pp, t); });
        }
        if (slot == 9) m.setze(22, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, tx(p, NamedTextColor.GRAY, "menu.niemand")), null);
        m.setze(49, item(Material.ARROW, tx(p, NamedTextColor.GRAY, "menu.zurueck")), pp -> oeffnen(pl, pp));
        m.rahmen();
        p.openInventory(m.inv);
    }

    static void farbMenue(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        SquadMenu m = new SquadMenu(3, Texte.fuer(p, "menu.farbe_waehlen"));
        int slot = 9;
        for (Map.Entry<String, NamedTextColor> e : Squads.FARBEN.entrySet()) {
            NamedTextColor c = e.getValue();
            m.setze(slot++, item(Squads.FARBSTOFF.get(c), tx(p, c, "farbe." + e.getKey())), pp -> { sq.farbeSetzen(pp, c); oeffnen(pl, pp); });
        }
        m.setze(22, item(Material.ARROW, tx(p, NamedTextColor.GRAY, "menu.zurueck")), pp -> oeffnen(pl, pp));
        m.rahmen();
        p.openInventory(m.inv);
    }

    static void einladungen(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        SquadMenu m = new SquadMenu(3, Texte.fuer(p, "menu.einladungen_titel"));
        int slot = 9;
        for (Team t : sq.einladungenVon(p)) {
            if (slot > 17) break;
            NamedTextColor c = Squads.farbe(t);
            m.setze(slot++, item(Squads.WOLLE.getOrDefault(c, Material.WHITE_WOOL), t(Squads.name(t), c),
                    tx(p, NamedTextColor.GRAY, "menu.leiter", sq.leiterName(t)), tx(p, NamedTextColor.GREEN, "menu.klick_annehmen")),
                    pp -> { if (sq.annehmen(pp, Squads.name(t))) oeffnen(pl, pp); });
        }
        m.setze(22, item(Material.ARROW, tx(p, NamedTextColor.GRAY, "menu.zurueck")), pp -> hauptmenue(pl, pp));
        m.rahmen();
        p.openInventory(m.inv);
    }

    static ItemStack steuerungItem(Player p) {
        return item(Material.TRIPWIRE_HOOK, tx(p, NamedTextColor.AQUA, "menu.steuerung"),
                tx(p, NamedTextColor.GRAY, "menu.steuerung.info"), tx(p, NamedTextColor.YELLOW, "menu.klick"));
    }

    static void steuerungMenue(TeamPing pl, Player p) {
        Squads sq = pl.squads();
        SquadMenu m = new SquadMenu(4, Texte.fuer(p, "menu.steuerung_titel"));
        int slot = 11;
        for (Squads.Ausloeser a : Squads.Ausloeser.values()) {
            if (a == Squads.Ausloeser.EMOTE && !pl.istBedrock(p)) continue;
            boolean an = sq.aktiv(p, a);
            m.setze(slot++, item(an ? Material.LIME_DYE : Material.GRAY_DYE,
                    tx(p, an ? NamedTextColor.GREEN : NamedTextColor.GRAY, "geste." + a.name()).append(Component.text(an ? "  ✔ " : "  ✖ ")).append(Texte.fuer(p, an ? "an" : "aus")),
                    tx(p, NamedTextColor.GRAY, "geste." + a.name() + ".info"),
                    tx(p, NamedTextColor.YELLOW, "menu.klick_umschalten")),
                    pp -> { sq.umschalten(pp, a); steuerungMenue(pl, pp); });
        }
        if (pl.istBedrock(p)) {
            m.setze(22, item(Material.BOOK, tx(p, NamedTextColor.GOLD, "menu.tipp"),
                    tx(p, NamedTextColor.GRAY, "menu.tipp_bedrock.1"),
                    tx(p, NamedTextColor.GRAY, "menu.tipp_bedrock.2"),
                    tx(p, NamedTextColor.GRAY, "menu.tipp.2")), null);
        } else {
            m.setze(22, item(Material.BOOK, tx(p, NamedTextColor.GOLD, "menu.tipp"),
                    tx(p, NamedTextColor.GRAY, "menu.tipp.1"),
                    tx(p, NamedTextColor.GRAY, "menu.tipp.2")), null);
        }
        m.setze(20, item(Material.AMETHYST_SHARD, tx(p, NamedTextColor.AQUA, "menu.pinggegenstand"), tx(p, NamedTextColor.GRAY, "menu.pinggegenstand.info")),
                pp -> { pl.pingGegenstandGeben(pp); pp.closeInventory(); });
        m.setze(31, item(Material.ARROW, tx(p, NamedTextColor.GRAY, "menu.zurueck")), pp -> oeffnen(pl, pp));
        m.rahmen();
        p.openInventory(m.inv);
    }

    static void bestaetigen(TeamPing pl, Player p, Component frage, Consumer<Player> ja) {
        SquadMenu m = new SquadMenu(3, frage);
        m.setze(11, item(Material.LIME_WOOL, tx(p, NamedTextColor.GREEN, "menu.ja")), ja);
        m.setze(15, item(Material.RED_WOOL, tx(p, NamedTextColor.RED, "menu.nein")), pp -> oeffnen(pl, pp));
        m.rahmen();
        p.openInventory(m.inv);
    }
}
