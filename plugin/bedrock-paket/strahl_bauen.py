# Builds the Bedrock resource pack "TeamPing" (light beam block) + the Geyser mapping teamping_mappings.json.
# Java light block level 1-10 becomes a glowing beam in ping colour 1-10 on Bedrock (waterlogged = wide variant for far viewers).
# Usage: python strahl_bauen.py <version, e.g. 1.1.1>
import json, os, sys, zipfile
from PIL import Image

VERSION = [int(x) for x in (sys.argv[1] if len(sys.argv) > 1 else "1.0.1").split(".")]
os.chdir(os.path.dirname(os.path.abspath(__file__)))
os.makedirs("pack/textures/blocks/teamping", exist_ok=True)
os.makedirs("pack/models/blocks", exist_ok=True)
# order = Squads.PingFarbe (ordinal + 1 = light level)
FARBEN = [("rot", 0xFF3030), ("orange", 0xFF8C00), ("gelb", 0xFFE600), ("gruen", 0x3CFF3C), ("tuerkis", 0x00E5E5),
          ("hellblau", 0x40B4FF), ("blau", 0x3355FF), ("lila", 0xA845FF), ("pink", 0xFF4FD8), ("weiss", 0xFFFFFF)]

def hell(c, f):
    return tuple(int(v + (255 - v) * f) for v in c)

td = {}
for name, rgb in FARBEN:
    c = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255)
    im = Image.new("RGBA", (16, 16))
    for x in range(16):
        d = abs(x - 7.5) / 8.0          # 0 = centre, 1 = edge
        if d < 0.25:   col, a = hell(c, 0.7), 255                         # bright core (like the Java core)
        elif d < 0.75: col, a = hell(c, 0.1), 240                         # strong colour (glow)
        else:          col, a = tuple(int(v * 0.3) for v in c), 200       # dark rim (like the Java rim) – contrast by day
        for y in range(16):
            im.putpixel((x, y), col + (a,))
    im.save(f"pack/textures/blocks/teamping/strahl_{name}.png")
    td[f"teamping_strahl_{name}"] = {"textures": f"textures/blocks/teamping/strahl_{name}"}
json.dump({"resource_pack_name": "teamping", "texture_name": "atlas.terrain", "padding": 8, "num_mip_levels": 4, "texture_data": td},
          open("pack/textures/terrain_texture.json", "w"), indent=2)

# geometry: slim pillar, side faces only -> no horizontal stripes when stacked
seite = {"uv": [0, 0], "uv_size": [16, 16]}
def geometrie(name, breite):
    return {"description": {"identifier": name, "texture_width": 16, "texture_height": 16,
                            "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": [{"name": "strahl", "pivot": [0, 0, 0], "cubes": [{"origin": [-breite / 2, 0, -breite / 2], "size": [breite, 16, breite],
                       "uv": {"north": seite, "south": seite, "east": seite, "west": seite}}]}]}
geo = {"format_version": "1.12.0", "minecraft:geometry": [geometrie("geometry.teamping_strahl", 10), geometrie("geometry.teamping_strahl_breit", 24)]}
json.dump(geo, open("pack/models/blocks/teamping_strahl.geo.json", "w"), indent=2)

mf = {"format_version": 2,
      "header": {"name": "TeamPing", "description": "Light beam for TeamPing pings",
                 "uuid": "5b8d7f0e-1c2a-4e7a-9f3b-7a1e2c4d6f01", "version": VERSION, "min_engine_version": [1, 21, 0]},
      "modules": [{"type": "resources", "uuid": "0c6e2b9a-3d4f-4b8e-a1c2-9e8f7d6c5b02", "version": VERSION}]}
json.dump(mf, open("pack/manifest.json", "w"), indent=2)

ic = Image.new("RGBA", (64, 64), (20, 22, 28, 255))
ic.alpha_composite(Image.open("pack/textures/blocks/teamping/strahl_hellblau.png").resize((24, 64), Image.NEAREST), (20, 0))
ic.save("pack/pack_icon.png")

mat = lambda n: {"*": {"texture": f"teamping_strahl_{n}", "render_method": "blend", "face_dimming": False, "ambient_occlusion": False}}
over = {f"level={i + 1},waterlogged=false": {"material_instances": mat(n)} for i, (n, _) in enumerate(FARBEN)}
# wide (only for viewers from 40 m): emits light – every Bedrock client computes light itself, so nearby players see no lit ground
over.update({f"level={i + 1},waterlogged=true": {"material_instances": mat(n), "geometry": "geometry.teamping_strahl_breit", "light_emission": 15}
             for i, (n, _) in enumerate(FARBEN)})
mp = {"format_version": 1, "blocks": {"minecraft:light": {
    "name": "teamping_strahl", "display_name": "Ping", "geometry": "geometry.teamping_strahl", "material_instances": mat("weiss"),
    "light_emission": 0, "light_dampening": 0, "only_override_states": True, "state_overrides": over}}}
json.dump(mp, open("teamping_mappings.json", "w"), indent=2)

with zipfile.ZipFile("TeamPing-Bedrock.mcpack", "w", zipfile.ZIP_DEFLATED) as z:
    for r, _, fs in os.walk("pack"):
        for f in fs:
            p = os.path.join(r, f)
            z.write(p, os.path.relpath(p, "pack"))
print("Beam pack", ".".join(map(str, VERSION)), "built")
