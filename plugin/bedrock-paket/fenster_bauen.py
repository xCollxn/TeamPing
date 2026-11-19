# Builds the Bedrock resource pack "TeamPing UI" (TeamPing-Bedrock-Fenster.mcpack).
#
# 1) ui/server_form.json – only OUR elements + one targeted change (no copy of Mojang's file): the form factory of
#    server_form.main_screen_content is replaced so that long forms go through a switch: forms with the marker "§t§p" in the
#    title get the TeamPing look in Bedrock's light default style (inventory window, tabs on top = buttons with "§p§1"/"§p§2",
#    rows with icon). Bedrock's OWN textures are used (dialog_background_opaque, TabTop…, light buttons) so it matches the
#    rest of the game and Bedrock texture packs apply. All other server forms stay unchanged.
# 2) ui/hud_screen.json – squad overview as a box: titles with the marker "§h§u§<n>" are not shown as a big title but as a
#    box at the screen edge (n: 1 = right, 2 = left, 3 = top right, 0 = off). Partial override only (rest stays vanilla).
# Usage: python fenster_bauen.py <version, e.g. 1.2.0>
import json, re, os, sys, zipfile
from PIL import Image

VERSION = [int(x) for x in (sys.argv[1] if len(sys.argv) > 1 else "1.2.0").split(".")]
HIER = os.path.dirname(os.path.abspath(__file__))
os.chdir(HIER)
MARKE, R1, R2 = "§t§p", "§p§1", "§p§2"
HUD = "§h§r§u"   # Geyser inserts §r between all colour codes when converting (§h§u§1 becomes §h§r§u§r§1)

# ---------------------------------------------------------------- own textures (check boxes only)
T = "ui-pack/textures/ui/teamping"
os.makedirs(T, exist_ok=True)
for f in os.listdir(T):           # remove old (dark) textures
    os.remove(os.path.join(T, f))

def haken(datei, an):
    """Box like on Java (slot look), white tick with shadow."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(1, 15):
        for y in range(1, 15):
            if x == 1 or y == 1: c = (55, 55, 55, 255)
            elif x == 14 or y == 14: c = (255, 255, 255, 255)
            else: c = (139, 139, 139, 255)
            im.putpixel((x, y), c)
    if an:
        punkte = [(4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)]
        for (x, y) in punkte: im.putpixel((x, y + 1), (55, 55, 55, 255))
        for (x, y) in punkte: im.putpixel((x, y), (255, 255, 255, 255)); im.putpixel((x, y - 1), (255, 255, 255, 255))
    im.save(f"{T}/{datei}.png")

haken("haken_an", True)
haken("haken_aus", False)

# ---------------------------------------------------------------- own glyphs (font/glyph_E1.png)
# U+E100 heart, U+E101-E108 arrows (up, up-right, right, down-right, down, down-left, left, up-left), U+E109 tick.
# Bedrock's pixel font lacks these symbols and would otherwise switch the whole text to a smaller fallback font.
os.makedirs("ui-pack/font", exist_ok=True)
GL = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
K = (40, 40, 40, 255)
def zelle(i, muster, farben, rand=False):
    """Pattern at original size (1 pixel = 1 pixel), vertically centred; rand=True: dark outline around the pixels."""
    zx, zy = (i % 16) * 16, (i // 16) * 16
    h, w = len(muster), max(len(z) for z in muster)
    x0, y0 = 1 + (1 if rand else 0), (16 - h) // 2 + 1
    voll = {(x, y) for y, z in enumerate(muster) for x, ch in enumerate(z) if ch in farben}
    if rand:
        for (x, y) in voll:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if (x + dx, y + dy) not in voll: GL.putpixel((zx + x0 + x + dx, zy + y0 + y + dy), K)
    for y, z in enumerate(muster):
        for x, ch in enumerate(z):
            if ch in farben: GL.putpixel((zx + x0 + x, zy + y0 + y), farben[ch])
ROT, HELL, DUNKEL, W = (255, 19, 19, 255), (255, 200, 200, 255), (160, 0, 0, 255), (255, 255, 255, 255)
# heart like on Java (9 x 8 with dark outline)
zelle(0, [".KK...KK.", "KRRK.KRRK", "KRHRKRRRK", "KRRRRRRRK", ".KRRRRRK.", "..KRRRK..", "...KRK...", "....K...."],
      {"R": ROT, "H": HELL, "K": K})
hoch = ["..W..", ".WWW.", "W.W.W", "..W..", "..W..", "..W..", "..W.."]
diag = ["..WWWW", "....WW", "...W.W", "..W..W", ".W....", "W....."]   # up-right
def vflip(m): return m[::-1]
def hflip(m): return [z[::-1] for z in m]
def quer(m):  # transpose (up -> left)
    w = max(len(z) for z in m); m = [z.ljust(w, ".") for z in m]
    return ["".join(m[y][x] for y in range(len(m))) for x in range(w)]
links = quer(hoch)
PFEILE = [hoch, diag, hflip(links), vflip(diag), vflip(hoch), hflip(vflip(diag)), links, hflip(diag)]
for i, m in enumerate(PFEILE): zelle(1 + i, m, {"W": W}, rand=True)
zelle(9, ["......G", ".....GG", "G...GG.", "GG.GG..", ".GGG...", "..G...."], {"G": (60, 200, 60, 255)})
GL.save("ui-pack/font/glyph_E1.png")

# ---------------------------------------------------------------- server_form.json
ui = {"namespace": "server_form"}

def text_bindung():
    return {"binding_name": "#form_button_text", "binding_type": "collection", "binding_collection_name": "form_buttons"}

def sichtbar_wenn(ausdruck):
    return {"binding_type": "view", "source_property_name": ausdruck, "target_property_name": "#visible"}

IST_REITER = f"(not (((#form_button_text - '{R1}') - '{R2}') = #form_button_text))"
KEIN_REITER = f"(((#form_button_text - '{R1}') - '{R2}') = #form_button_text)"
IST_AKTIV = f"(not ((#form_button_text - '{R2}') = #form_button_text))"
NICHT_AKTIV = f"((#form_button_text - '{R2}') = #form_button_text)"
MIT_MARKE = f"(not ((#title_text - '{MARKE}') = #title_text))"
OHNE_MARKE = f"((#title_text - '{MARKE}') = #title_text)"

SYMBOL_BINDUNGEN = [
    {"binding_name": "#form_button_texture", "binding_name_override": "#texture", "binding_type": "collection", "binding_collection_name": "form_buttons"},
    {"binding_name": "#form_button_texture_file_system", "binding_name_override": "#texture_file_system", "binding_type": "collection", "binding_collection_name": "form_buttons"},
    sichtbar_wenn("(not ((#texture = '') or (#texture = 'loading')))"),
]

# switch: vanilla form without marker, TeamPing form with marker. Only the factory control of main_screen_content is
# replaced (targeted modification); long_form/custom_form themselves remain Bedrock's own.
ui["main_screen_content"] = {"modifications": [{"array_name": "controls", "operation": "replace", "control_name": "server_form_factory",
    "value": {"server_form_factory": {"type": "factory", "control_ids": {
        "long_form": "@server_form.tp_weiche", "custom_form": "@server_form.custom_form"}}}}]}
ui["tp_weiche"] = {"type": "panel", "size": [0, 0], "controls": [
    {"standard@server_form.long_form": {"bindings": [{"binding_name": "#title_text"}, sichtbar_wenn(OHNE_MARKE)]}},
    {"teamping@server_form.tp_fenster": {"bindings": [{"binding_name": "#title_text"}, sichtbar_wenn(MIT_MARKE)]}},
]}
ui["tp_leer"] = {"type": "panel", "size": [0, 0]}

ui["tp_fenster"] = {"type": "panel", "size": [320, 220], "anchor_from": "center", "anchor_to": "center", "layer": 2, "controls": [
    {"reiter_leiste": {"type": "stack_panel", "orientation": "horizontal", "size": ["100%c", 24], "offset": [6, 0],
                       "anchor_from": "top_left", "anchor_to": "top_left", "layer": 1,
                       "factory": {"name": "buttons", "control_ids": {"button": "@server_form.tp_reiter", "label": "@server_form.tp_leer",
                                                                      "header": "@server_form.tp_leer", "divider": "@server_form.tp_leer"}},
                       "collection_name": "form_buttons",
                       "bindings": [{"binding_name": "#form_button_contents", "binding_name_override": "#collection_length"}]}},
    {"rahmen": {"type": "image", "texture": "textures/ui/dialog_background_opaque", "size": ["100%", "100% - 21px"], "offset": [0, 21],
                "anchor_from": "top_left", "anchor_to": "top_left", "layer": 3, "controls": [
        {"titel": {"type": "label", "text": "#tp_titel", "color": "$title_text_color", "shadow": False, "layer": 2,
                   "offset": [8, 7], "size": ["100% - 32px", 10], "anchor_from": "top_left", "anchor_to": "top_left",
                   "bindings": [{"binding_name": "#title_text"},
                                {"binding_type": "view", "source_property_name": f"(#title_text - '{MARKE}')", "target_property_name": "#tp_titel"}]}},
        {"zu@common.close_button": {"$close_button_offset": [-4, 4], "layer": 4}},
        {"bereich": {"type": "panel", "size": ["100% - 10px", "100% - 26px"], "offset": [5, 21],
                     "anchor_from": "top_left", "anchor_to": "top_left", "layer": 1, "controls": [
            {"scrolling_panel@common.scrolling_panel": {
                "anchor_to": "top_left", "anchor_from": "top_left", "$show_background": False, "size": ["100%", "100%"],
                "$scrolling_content": "server_form.tp_inhalt", "$scroll_size": [5, "100% - 4px"],
                "$scrolling_pane_size": ["100% - 4px", "100% - 2px"], "$scrolling_pane_offset": [2, 1],
                "$scroll_bar_right_padding_size": [0, 0]}}]}},
    ]}},
]}

ui["tp_inhalt"] = {"type": "stack_panel", "size": ["100% - 4px", "100%c"], "orientation": "vertical",
                   "anchor_from": "top_left", "anchor_to": "top_left", "controls": [
    {"text_panel": {"type": "panel", "size": ["100%", "100%c + 8px"], "controls": [
        {"text": {"type": "label", "text": "#form_text", "color": [0.3, 0.3, 0.3], "offset": [3, 3], "size": ["100% - 6px", "default"],
                  "anchor_from": "top_left", "anchor_to": "top_left"}}]}},
    {"knoepfe": {"type": "stack_panel", "size": ["100% - 4px", "100%c"], "offset": [2, 0], "orientation": "vertical",
                 "anchor_from": "top_middle", "anchor_to": "top_middle",
                 "factory": {"name": "buttons", "control_ids": {"button": "@server_form.tp_zeile", "label": "@server_form.dynamic_label",
                                                                "header": "@server_form.dynamic_header",
                                                                "divider": "@settings_common.option_group_section_divider"}},
                 "collection_name": "form_buttons",
                 "bindings": [{"binding_name": "#form_button_contents", "binding_name_override": "#collection_length"}]}},
    {"abstand": {"type": "panel", "size": ["100%", 3]}},
]}

# row = Bedrock's light default button (grey, green on hover) with icon on the left and text next to it
ui["tp_zeile"] = {"type": "panel", "size": ["100%", 31], "bindings": [text_bindung(), sichtbar_wenn(KEIN_REITER)],
                  "controls": [{"knopf@server_form.tp_knopf": {}}]}

ui["tp_knopf@common_buttons.light_content_button"] = {
    "size": ["100%", 29], "anchor_from": "top_left", "anchor_to": "top_left",
    "$pressed_button_name": "button.form_button_click",
    "$button_content": "server_form.tp_knopf_inhalt",
    "bindings": [{"binding_type": "collection_details", "binding_collection_name": "form_buttons"}]}

ui["tp_knopf_inhalt"] = {"type": "panel", "size": ["100%", "100%"], "$text_color|default": [0.3, 0.3, 0.3], "controls": [
    {"symbol": {"type": "image", "size": [18, 18], "offset": [5, 0], "anchor_from": "left_middle", "anchor_to": "left_middle",
                "layer": 2, "bindings": SYMBOL_BINDUNGEN}},
    {"text": {"type": "label", "text": "#form_button_text", "color": "$text_color", "shadow": False, "layer": 2,
              "size": ["100% - 34px", "default"], "offset": [29, 0], "anchor_from": "left_middle", "anchor_to": "left_middle",
              "bindings": [text_bindung()]}},
]}

# tabs (buttons with §p§1 / §p§2): Bedrock's tab textures, the active one merges with the window
ui["tp_reiter"] = {"type": "panel", "size": [27, 24], "bindings": [text_bindung(), sichtbar_wenn(IST_REITER)], "controls": [
    {"aktiv": {"type": "panel", "size": [26, 24], "anchor_from": "top_left", "anchor_to": "top_left", "layer": 6,
               "bindings": [text_bindung(), sichtbar_wenn(IST_AKTIV)],
               "controls": [{"k@server_form.tp_reiter_knopf": {"$tex": "textures/ui/TabTopFront", "$tex_hover": "textures/ui/TabTopFront"}}]}},
    {"normal": {"type": "panel", "size": [26, 22], "anchor_from": "top_left", "anchor_to": "top_left", "offset": [0, 2], "layer": 0,
                "bindings": [text_bindung(), sichtbar_wenn(NICHT_AKTIV)],
                "controls": [{"k@server_form.tp_reiter_knopf": {"$tex": "textures/ui/TabTopBack", "$tex_hover": "textures/ui/TabTopBackHover"}}]}},
]}

def reiter_zustand(tex):
    return {"type": "image", "texture": tex, "size": ["100%", "100%"], "layer": 1, "controls": [
        {"symbol": {"type": "image", "size": [16, 16], "offset": [0, 0], "anchor_from": "center", "anchor_to": "center", "layer": 2,
                    "bindings": SYMBOL_BINDUNGEN}}]}

ui["tp_reiter_knopf@common.button"] = {"size": ["100%", "100%"], "$pressed_button_name": "button.form_button_click",
    "$tex|default": "textures/ui/TabTopBack", "$tex_hover|default": "textures/ui/TabTopBackHover",
    "bindings": [{"binding_type": "collection_details", "binding_collection_name": "form_buttons"}],
    "controls": [{"default": reiter_zustand("$tex")}, {"hover": reiter_zustand("$tex_hover")}, {"pressed": reiter_zustand("$tex_hover")}]}

os.makedirs("ui-pack/ui", exist_ok=True)
json.dump(ui, open("ui-pack/ui/server_form.json", "w", encoding="utf8"), indent=2, ensure_ascii=False)

# ---------------------------------------------------------------- hud_screen.json (squad overview as a box)
TITEL = "#hud_title_text_string"
def kasten(name, anker, versatz, kennung):
    return {name: {
        "type": "panel", "size": ["100%c", "100%c"], "anchor_from": anker, "anchor_to": anker, "offset": versatz,
        "bindings": [{"binding_name": TITEL, "binding_type": "global"},
                     sichtbar_wenn(f"(not (({TITEL} - '{kennung}') = {TITEL}))")],
        "controls": [{"hg": {"type": "image", "texture": "textures/ui/hud_tip_text_background", "size": ["100%c + 10px", "100%c + 8px"],
                             "alpha": 0.75, "controls": [
            {"text": {"type": "label", "text": "#tp_text", "size": ["default", "default"], "anchor_from": "center", "anchor_to": "center",
                      "shadow": True, "color": [1, 1, 1], "layer": 1,
                      "bindings": [{"binding_name": TITEL, "binding_type": "global"},
                                   {"binding_type": "view", "source_property_name": f"({TITEL} - '{kennung}')", "target_property_name": "#tp_text"}]}}]}}]}}

hud = {
    "namespace": "hud",
    # normal big title: hide it when it is our box text (only bindings added, the rest stays vanilla)
    "hud_title_text": {"bindings": [{"binding_name": TITEL, "binding_type": "global"},
                                    sichtbar_wenn(f"(({TITEL} - '{HUD}') = {TITEL})")]},
    "root_panel": {"modifications": [{"array_name": "controls", "operation": "insert_back", "value": [{"tp_hud@hud.tp_hud": {}}]}]},
    "tp_hud": {"type": "panel", "size": ["100%", "100%"], "layer": 20, "controls": [
        kasten("rechts", "right_middle", [-4, -10], HUD + "§r§1"),
        kasten("links", "left_middle", [4, 10], HUD + "§r§2"),
        kasten("oben_rechts", "top_right", [-4, 4], HUD + "§r§3"),
    ]},
}
json.dump(hud, open("ui-pack/ui/hud_screen.json", "w", encoding="utf8"), indent=2, ensure_ascii=False)

mf = {"format_version": 2,
      "header": {"name": "TeamPing UI", "description": "Squad windows and squad overview for TeamPing",
                 "uuid": "8e3f1a52-6b7c-4d9e-a0f1-2b3c4d5e6f03", "version": VERSION, "min_engine_version": [1, 21, 0]},
      "modules": [{"type": "resources", "uuid": "1d2c3b4a-5e6f-4a7b-8c9d-0e1f2a3b4c04", "version": VERSION}]}
json.dump(mf, open("ui-pack/manifest.json", "w"), indent=2)
if os.path.exists("pack/pack_icon.png"):
    Image.open("pack/pack_icon.png").save("ui-pack/pack_icon.png")
with zipfile.ZipFile("TeamPing-Bedrock-Fenster.mcpack", "w", zipfile.ZIP_DEFLATED) as z:
    for r, _, fs in os.walk("ui-pack"):
        for f in fs:
            p = os.path.join(r, f)
            z.write(p, os.path.relpath(p, "ui-pack"))
print("UI pack", ".".join(map(str, VERSION)), "built")
