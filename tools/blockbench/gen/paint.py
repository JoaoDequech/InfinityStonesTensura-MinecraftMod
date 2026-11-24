"""Procedural pixel-art painter. Every texel is coloured from its 3D position on the model, so
neighbouring cubes (bevel steps, overlapping plates) get matching pixels."""
import numpy as np

# ------------------------------------------------------------------ uv layout


def pack(cubes, width):
    """Shelf-pack box-UV footprints. Returns the used height."""
    items = []
    for c in cubes:
        w, h, d = c.size
        items.append((c, int(2 * (d + w)), int(d + h)))
    items.sort(key=lambda t: -t[2])
    x = y = rowh = 0
    for c, fw, fh in items:
        if x + fw > width:
            x, y, rowh = 0, y + rowh, 0
        c.uv = (x, y)
        x += fw
        rowh = max(rowh, fh)
    return y + rowh


def face_rects(c):
    """name -> (u0, v0, fw, fh, normal, axis-extractor) in texel space."""
    w, h, d = (int(v) for v in c.size)
    U, V = c.uv
    return {
        "north": (U + d, V + d, w, h),
        "east": (U, V + d, d, h),
        "south": (U + 2 * d + w, V + d, w, h),
        "west": (U + d + w, V + d, d, h),
        "up": (U + d, V, w, d),
        "down": (U + d + w, V, w, d),
    }


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def face_positions(c, face, fw, fh):
    x0, y0, z0 = c.f
    x1, y1, z1 = c.t
    i = np.arange(fw)[None, :] + 0.5
    j = np.arange(fh)[:, None] + 0.5
    one = np.ones((fh, fw))
    if face == "north":
        return x1 - i + 0 * j, y1 - j + 0 * i, z0 * one
    if face == "south":
        return x0 + i + 0 * j, y1 - j + 0 * i, z1 * one
    if face == "east":
        return x1 * one, y1 - j + 0 * i, z1 - i + 0 * j
    if face == "west":
        return x0 * one, y1 - j + 0 * i, z0 + i + 0 * j
    if face == "up":
        return x1 - i + 0 * j, y1 * one, z1 - j + 0 * i
    return x1 - i + 0 * j, y0 * one, z1 - j + 0 * i


# ------------------------------------------------------------------ noise


def hash3(ix, iy, iz, seed=0):
    x = (ix.astype(np.int64) * 73856093) ^ (iy.astype(np.int64) * 19349663) ^ (iz.astype(np.int64) * 83492791) ^ (seed * 2654435761)
    x &= 0xFFFFFFFF
    x ^= x >> 13
    x = (x * 0x5BD1E995) & 0xFFFFFFFF
    x ^= x >> 15
    x = (x * 0x27D4EB2D) & 0xFFFFFFFF
    x ^= x >> 16
    return (x & 0xFFFF) / 65536.0


def vnoise(X, Y, Z, cell, seed=0):
    return hash3(np.floor(X / cell), np.floor(Y / cell), np.floor(Z / cell), seed)


def worley(X, Y, Z, cs, seed=0):
    cx, cy, cz = np.floor(X / cs), np.floor(Y / cs), np.floor(Z / cs)
    f1 = np.full(X.shape, 1e9)
    f2 = np.full(X.shape, 1e9)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                ax, ay, az = cx + dx, cy + dy, cz + dz
                px = (ax + hash3(ax, ay, az, seed + 1)) * cs
                py = (ay + hash3(ax, ay, az, seed + 2)) * cs
                pz = (az + hash3(ax, ay, az, seed + 3)) * cs
                dist = np.sqrt((X - px) ** 2 + (Y - py) ** 2 + (Z - pz) ** 2)
                nf1 = np.minimum(f1, dist)
                f2 = np.minimum(f2, np.maximum(f1, dist))
                f1 = nf1
    return f1, f2


def pick(pal, t):
    pal = np.array(pal, dtype=float) / 255.0
    idx = np.clip((t * len(pal)).astype(int), 0, len(pal) - 1)
    return pal[idx]


GLYPHS = [
    "10101 01110 11111 01110 10101", "00100 01110 10101 00100 01010", "11011 10001 00100 10001 11011",
    "01110 10001 10101 10001 01110", "10001 01010 00100 01010 10001", "00100 00100 11111 00100 00100",
    "11110 00010 01110 01000 01111", "01110 00100 11111 00100 01110", "10010 10101 01110 10101 01001",
    "11111 10001 01110 00100 00100", "00100 01010 10001 01010 00100", "11100 00100 01110 00100 00111",
]
GLYPH_ARR = np.array([[[int(ch) for ch in row] for row in g.split()] for g in GLYPHS])


def glyph_mask(A, B, seed, density=0.7, cell=8):
    ca, cb = np.floor(A / cell), np.floor(B / cell)
    la, lb = A - ca * cell, B - cb * cell
    gx, gy = np.floor(la - 1.5).astype(int), np.floor(lb - 1.5).astype(int)
    ok = (gx >= 0) & (gx < 5) & (gy >= 0) & (gy < 5)
    gid = (hash3(ca, cb, np.zeros_like(ca), seed) * len(GLYPHS)).astype(int)
    on = hash3(ca, cb, np.ones_like(ca), seed) < density
    bits = GLYPH_ARR[gid, np.clip(gy, 0, 4), np.clip(gx, 0, 4)]
    return ok & on & (bits == 1)


# ------------------------------------------------------------------ styles

SKY = dict(
    body=[(38, 28, 62), (50, 38, 80), (64, 50, 100), (80, 64, 122), (98, 80, 144)],
    trim=[(96, 80, 138), (120, 104, 164), (148, 132, 192), (180, 164, 218)],
    glow=(196, 120, 255), glyph=True, cracks=False, nail=((150, 90, 210), (208, 150, 255)),
)
SCULPTOR = dict(
    body=[(84, 80, 90), (102, 98, 110), (120, 116, 128), (138, 134, 146), (156, 152, 164)],
    trim=[(64, 60, 72), (78, 74, 88), (94, 90, 104), (110, 106, 120)],
    glow=(190, 110, 255), glyph=False, cracks=True, nail=((160, 156, 170), (206, 150, 255)),
)
GOLD = dict(
    body=[(138, 94, 20), (184, 133, 28), (224, 176, 48), (246, 216, 96), (255, 241, 168)],
    trim=[(92, 61, 14), (138, 94, 20), (184, 133, 28), (214, 166, 44)],
    glow=(255, 214, 120), glyph=True, cracks=False, nail=((246, 216, 96), (255, 241, 168)),
)
PREVIEW = False
GEM_COLORS = {"mind": (242, 208, 43), "power": (164, 60, 230), "space": (47, 140, 255), "reality": (226, 43, 59), "soul": (242, 138, 26), "time": (43, 213, 106)}
STYLES = {"sky": SKY, "sculptor": SCULPTOR, "gold": GOLD}


def shade_mat(st, key, mat, X, Y, Z, n, c, edge):
    """Returns (rgb, emissive) arrays (h, w, 3) in 0..1."""
    shp = X.shape
    emis = np.zeros(shp + (3,))
    glow = np.array(st["glow"]) / 255.0
    gold = st is GOLD
    if mat.startswith("gem"):
        cx, cy = (c.f[0] + c.t[0]) / 2, (c.f[1] + c.t[1]) / 2
        hs = max(c.size[0], c.size[1]) / 2
        if n == (0, 0, -1):
            u, v = (X - cx) / hs, (Y - cy) / hs
            k = np.where(np.abs(u) + np.abs(v) < 0.55, 0.95, 0.72)
            k = np.where((u < -0.1) & (v > 0.1) & (np.abs(u) + np.abs(v) < 0.55), 1.0, k)
            k = np.where((np.abs(np.abs(u) - np.abs(v)) < 0.18) & (np.abs(u) + np.abs(v) >= 0.55), 0.55, k)
        else:
            k = np.full(shp, 0.5) + 0.2 * (n[1] > 0)
        rgb = np.repeat(k[..., None], 3, axis=2)
        if PREVIEW and ":" in mat:
            col = np.array(GEM_COLORS[mat.split(":")[1]]) / 255.0
            rgb = rgb * col * 0.95
        return rgb, rgb * 0.95
    if mat == "nail":
        base, hi = (np.array(v) / 255.0 for v in st["nail"])
        t = vnoise(X, Y, Z, 2, 11)
        rgb = np.where((t > 0.5)[..., None], hi, base) * (0.8 + 0.2 * t[..., None])
        emis = rgb * (0.35 if st is not GOLD else 0.0)
        return rgb, emis
    pal = st["trim"] if mat in ("trim",) else st["body"]
    if gold and mat == "plate":
        pal = st["body"][1:]
    t = 0.45 * vnoise(X, Y, Z, 6, 3) + 0.3 * vnoise(X, Y, Z, 3, 5) + 0.25 * vnoise(X, Y, Z, 1, 9)
    if gold:
        band = 0.5 + 0.5 * np.sin(Y * 0.42 + 1.1 * vnoise(X, 0 * Y, Z, 7, 21) + 0.6 * (n[0] != 0))
        t = 0.8 * band + 0.12 * vnoise(X, Y, Z, 4, 5) + 0.08 * vnoise(X, Y, Z, 1, 9)
        if mat == "plate":
            t = np.clip(t + 0.15, 0, 0.999)
    rgb = pick(pal, np.clip(t, 0, 0.999))
    if mat == "body" and not gold:
        if n[1] != 0:
            A, B = X, Z
        elif n[0] != 0:
            A, B = Z, Y
        else:
            A, B = X, Y
        r = np.floor((B + 100) / 10)
        line_h = ((B + 100) - r * 10) < 1.0
        line_v = (((A + 100) + (r % 2) * 7) % 14) < 1.0
        if st["cracks"]:
            line_v = line_v & (hash3(np.floor((A + 100) / 14), r, np.zeros_like(r), 3) < 0.35)
            line_h = line_h & (hash3(np.floor((A + 100) / 14), r, np.ones_like(r), 4) < 0.5)
        rgb = np.where((line_h | line_v)[..., None], rgb * 0.62, rgb)
    if st["cracks"]:
        f1, f2 = worley(X, Y, Z, 15.0, 7)
        g = f2 - f1
        core = g < 1.0
        halo = g < 1.6
        rgb = np.where(halo[..., None] & ~core[..., None], rgb * 0.5, rgb)
        rgb = np.where(core[..., None], np.array([120, 60, 180]) / 255.0, rgb)
        k = 0.65 + 0.35 * vnoise(X, Y, Z, 3, 13)
        emis = np.where(core[..., None], glow * k[..., None], emis)
    if mat == "panel" and st["glyph"]:
        if n[1] != 0:
            A, B = X, Z
        elif n[0] != 0:
            A, B = Z, Y
        else:
            A, B = X, Y
        gm = glyph_mask(A + 100, B + 100, 5, 0.75 if not gold else 0.55)
        if gold:
            rgb = np.where(gm[..., None], np.array([92, 61, 14]) / 255.0, rgb)
            emis = np.where(gm[..., None], glow * 0.55, emis)
        else:
            rgb = np.where(gm[..., None], np.array([30, 14, 48]) / 255.0, rgb)
            emis = np.where(gm[..., None], glow, emis)
    if mat == "trim" and st is SKY:
        line = ((Y + 0.0) % 6 < 1) & (n[1] == 0)
        emis = np.where(line[..., None], glow * 0.6, emis)
    # edge shading
    rgb = np.clip(rgb * (1 + edge[..., None] * (1.5 if gold else 1.0)), 0, 1)
    return rgb, emis


def edge_term(c, face, X, Y, Z):
    x0, y0, z0 = c.f
    x1, y1, z1 = c.t
    e = np.zeros(X.shape)
    if face in ("north", "south", "east", "west"):
        A = X if face in ("north", "south") else Z
        lo, hi = (x0, x1) if face in ("north", "south") else (z0, z1)
        e += np.where(y1 - Y < 1.0, 0.16, 0)
        e += np.where(Y - y0 < 1.0, -0.2, 0)
        e += np.where((A - lo < 1.0) | (hi - A < 1.0), -0.1, 0)
    else:
        near = (X - x0 < 1.0) | (x1 - X < 1.0) | (Z - z0 < 1.0) | (z1 - Z < 1.0)
        e += np.where(near, 0.1 if face == "up" else -0.15, 0)
    return e


def paint(cubes, style, size):
    W, H = size
    img = np.zeros((H, W, 4))
    emi = np.zeros((H, W, 4))
    st = STYLES[style]
    for c in cubes:
        for face, (u0, v0, fw, fh) in face_rects(c).items():
            if fw <= 0 or fh <= 0:
                continue
            X, Y, Z = face_positions(c, face, fw, fh)
            n = NORMALS[face]
            edge = edge_term(c, face, X, Y, Z)
            rgb, em = shade_mat(st, style, c.mat, X, Y, Z, n, c, edge)
            img[v0:v0 + fh, u0:u0 + fw, :3] = rgb
            img[v0:v0 + fh, u0:u0 + fw, 3] = 1
            emi[v0:v0 + fh, u0:u0 + fw, :3] = em
            emi[v0:v0 + fh, u0:u0 + fw, 3] = 1
    return img, emi
