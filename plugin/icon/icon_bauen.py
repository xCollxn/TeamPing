# Builds drafts for the TeamPing icon (pixel style, 32 x 32, scaled up to 512).
# Usage: python icon_bauen.py  → icon-A.png, icon-B.png, icon-C.png + uebersicht.png (overview)
import os, random
from PIL import Image

os.chdir(os.path.dirname(os.path.abspath(__file__)))
N = 32

def hex2(c, a=255):
    c = c.lstrip('#'); return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)

def hintergrund(oben, unten, rand):
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    o, u = hex2(oben), hex2(unten)
    for y in range(N):
        t = y / (N - 1)
        c = tuple(int(o[i] + (u[i] - o[i]) * t) for i in range(3)) + (255,)
        for x in range(N):
            # rounded corners (2 px)
            if (x in (0, N - 1) and y in (0, 1, N - 2, N - 1)) or (y in (0, N - 1) and x in (0, 1, N - 2, N - 1)): continue
            im.putpixel((x, y), c)
    for i in range(2, N - 2):
        for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)): im.putpixel((x, y), hex2(rand))
    for (x, y) in ((1, 1), (N - 2, 1), (1, N - 2), (N - 2, N - 2)): im.putpixel((x, y), hex2(rand))
    return im

def gras(im, y0):
    random.seed(7)
    for y in range(y0, N - 1):
        for x in range(1, N - 1):
            if im.getpixel((x, y))[3] == 0: continue
            if y < y0 + 2:
                c = random.choice(['#6cbf3a', '#5aa02c', '#4f8f27', '#7fd04a'])
            else:
                c = random.choice(['#7a5232', '#6b4628', '#86603c', '#5d3c22'])
            im.putpixel((x, y), hex2(c))

def strahl(im, x0, y_oben, y_unten, kern, schein, rand):
    for y in range(y_oben, y_unten):
        t = (y - y_oben) / max(1, (y_unten - y_oben))
        a = int(90 + 165 * min(1, t * 1.6))   # fade out at the top
        for dx, c in ((-3, rand), (-2, schein), (-1, kern), (0, kern), (1, schein), (2, rand)):
            base = im.getpixel((x0 + dx, y))
            col = hex2(c)
            k = a / 255
            im.putpixel((x0 + dx, y), tuple(int(base[i] * (1 - k) + col[i] * k) for i in range(3)) + (255,))

def raute(im, cx, cy, r, fuell, hell, umriss):
    for y in range(cy - r - 1, cy + r + 2):
        for x in range(cx - r - 1, cx + r + 2):
            d = abs(x - cx) + abs(y - cy)
            if d <= r:
                c = hell if (x < cx and y < cy and d < r - 1) else fuell
                im.putpixel((x, y), hex2(c))
            elif d == r + 1:
                im.putpixel((x, y), hex2(umriss))

def ring(im, cx, cy, rx, ry, farbe):
    import math
    for i in range(0, 360, 4):
        x = round(cx + rx * math.cos(math.radians(i))); y = round(cy + ry * math.sin(math.radians(i)))
        if 0 < x < N - 1 and 0 < y < N - 1: im.putpixel((x, y), hex2(farbe))

# A: light beam from a grass block, diamond floating above (yellow ping colour) – the chosen icon
A = hintergrund('#16202f', '#27344a', '#0b0f17')
gras(A, 24)
strahl(A, 16, 2, 24, '#e6fdff', '#3fd6ff', '#0d4a5c')
ring(A, 16, 25, 7, 2, '#bff4ff')
raute(A, 16, 12, 4, '#ffcc33', '#fff2b0', '#5a3b00')
A.save('icon-A.png')

# B: like A, but purple beam, white diamond
B = hintergrund('#1a1430', '#2c2050', '#0c0918')
gras(B, 24)
strahl(B, 16, 2, 24, '#f3e6ff', '#b45cff', '#3c1466')
ring(B, 16, 25, 7, 2, '#e3c4ff')
raute(B, 16, 12, 4, '#ffffff', '#ffffff', '#2a0d4a')
B.save('icon-B.png')

# C: big diamond (map pin) with ping waves, no ground
C = hintergrund('#10263a', '#1d4566', '#08131d')
for r, c in ((13, '#2f6f99'), (10, '#4aa8d8'), (7, '#7fd3ff')):
    ring(C, 16, 22, r, r // 3 + 1, c)
raute(C, 16, 13, 7, '#ff5a3c', '#ffb3a3', '#4a0f05')
C.putpixel((16, 13), hex2('#ffffff'))
C.save('icon-C.png')

gross = [Image.open(f'icon-{k}.png').resize((256, 256), Image.NEAREST) for k in 'ABC']
u = Image.new('RGBA', (256 * 3 + 40, 256 + 20), (60, 60, 60, 255))
for i, g in enumerate(gross): u.alpha_composite(g, (10 + i * 266, 10))
u.save('uebersicht.png')
print('ok')
