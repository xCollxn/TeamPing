#!/bin/bash
# World move: new world with a new seed; players keep inventory, ender chest, XP, statistics, advancements and squads.
# Usage (server must be STOPPED): bash welt-umzug.sh <server folder> <seed> <id, e.g. 2025-01-01>
# - the old world is only renamed (<world>-alt-<id>), nothing is deleted
# - taken over: players/ (inventory, XP, stats, advancements), data/minecraft/scoreboard.dat (squad teams), data/minecraft/maps/
# - server.properties: level-seed; TeamPing config: world-move → everyone starts at the new world spawn on their first join
set -e
DIR="$1"; SEED="$2"; KENNUNG="$3"
[ -n "$DIR" ] && [ -n "$SEED" ] && [ -n "$KENNUNG" ] || { echo "Usage: welt-umzug.sh <server folder> <seed> <id>"; exit 1; }
cd "$DIR"
W=$(grep '^level-name=' server.properties | cut -d= -f2 | tr -d '\r')
ALT="$W-alt-$KENNUNG"
[ -d "$W" ] || { echo "World $W not found"; exit 1; }
[ -e "$ALT" ] && { echo "$ALT already exists – aborted"; exit 1; }
[ -e "$W/session.lock" ] && command -v fuser >/dev/null && fuser "$W/session.lock" >/dev/null 2>&1 && { echo "Server is still running – aborted"; exit 1; }

mv "$W" "$ALT"
mkdir -p "$W/data/minecraft"
cp -a "$ALT/players" "$W/players"
[ -f "$ALT/data/minecraft/scoreboard.dat" ] && cp -a "$ALT/data/minecraft/scoreboard.dat" "$W/data/minecraft/"
[ -d "$ALT/data/minecraft/maps" ] && cp -a "$ALT/data/minecraft/maps" "$W/data/minecraft/"

cp server.properties "server.properties.vor-umzug-$KENNUNG"
sed -i "s/^level-seed=.*/level-seed=$SEED/" server.properties

C=plugins/TeamPing/config.yml
if [ -f "$C" ]; then
  cp "$C" "$C.vor-umzug-$KENNUNG"
  sed -i '/^welt-umzug:/d' "$C"
  if grep -q '^world-move:' "$C"; then sed -i "s/^world-move:.*/world-move: \"$KENNUNG\"/" "$C"; else printf '\n# One-time world move (start at the new world spawn)\nworld-move: "%s"\n' "$KENNUNG" >> "$C"; fi
fi
echo "Done: old world → $ALT, seed $SEED, move \"$KENNUNG\""
ls "$W" "$W/players" "$W/data/minecraft"
grep '^level-seed=' server.properties; grep '^world-move' "$C" 2>/dev/null || true
