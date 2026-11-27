"""Generates the mini gem art: 6 stones x 3 decay stages of animated 16x16 pixel art plus the item models.

Design: tiny bionic frame (steel brackets, rivets, a status LED) holding a translucent holographic crystal tinted per
stone; a scanline sweeps the core and it flickers/glitches; cracks appear as the life runs out; stages 3-5 are the last 10 seconds (property infinity:final).
Run from the repo root: python tools/gen-shard-art.py
"""
import json
import os
import random
from PIL import Image

ROOT = os.path.join("src", "main", "resources", "assets", "infinity")
TEX = os.path.join(ROOT, "textures", "item", "mini_joia")
MOD = os.path.join(ROOT, "models", "item", "mini_joia")
STONES = [("mind", 0xF2D02B), ("power", 0xA43CE6), ("space", 0x2F8CFF), ("reality", 0xE22B3B), ("soul", 0xF28A1A), ("time", 0x2BD56A)]
FRAMES = 8

STEEL_D, STEEL_M, STEEL_L, STEEL_H = (30, 34, 46), (62, 69, 86), (118, 128, 150), (205, 214, 232)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


# crystal silhouette: half width per row (rows 2..13), a tall hexagon
HALF = {2: 1, 3: 2, 4: 3, 5: 3, 6: 3, 7: 3, 8: 3, 9: 3, 10: 3, 11: 2, 12: 1}
# frame pixels (drawn under the core): prongs, side clamps and corner plates
FRAME = {
    (7, 0): "L", (8, 0): "M", (7, 1): "M", (8, 1): "D",
    (7, 14): "M", (8, 14): "D", (7, 15): "L", (8, 15): "M",
    (3, 4): "M", (4, 4): "L", (3, 5): "D", (4, 5): "M", (4, 3): "M",
    (12, 4): "M", (11, 4): "L", (12, 5): "D", (11, 5): "M", (11, 3): "M",
    (3, 10): "M", (4, 10): "L", (3, 11): "D", (4, 11): "M", (4, 12): "M",
    (12, 10): "M", (11, 10): "L", (12, 11): "D", (11, 11): "M", (11, 12): "M",
    (2, 7): "M", (2, 8): "D", (3, 7): "L", (3, 8): "M", (3, 6): "D", (3, 9): "D",
    (13, 7): "M", (13, 8): "D", (12, 7): "L", (12, 8): "M", (12, 6): "D", (12, 9): "D",
    (5, 1): "D", (10, 1): "D", (5, 14): "D", (10, 14): "D",
}
RIVETS = [(3, 4), (12, 4), (3, 11), (12, 11), (2, 7), (13, 8)]
LED = [(7, 15), (8, 0)]
CRACK1 = [(9, 4), (9, 5), (8, 6), (8, 7), (7, 8)]
CRACK2 = [(6, 3), (6, 4), (5, 5), (5, 6), (6, 7), (8, 9), (9, 10), (10, 9)]
HOLES2 = [(10, 6), (5, 9), (9, 11)]
CRACK3 = [(7, 3), (7, 4), (6, 5), (7, 6), (6, 8), (5, 9), (9, 7), (10, 8), (9, 9), (8, 10), (7, 11), (6, 10)]
HOLES3 = [(9, 5), (6, 6), (8, 8), (7, 9), (10, 7), (5, 7), (8, 4), (9, 8)]


def frame_image(color, stage, f, rnd):
    base = rgb(color)
    light, dark = mix(base, (255, 255, 255), 0.55), mix(base, (0, 0, 0), 0.45)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    pal = {"D": STEEL_D, "M": STEEL_M, "L": STEEL_L, "H": STEEL_H}
    for (x, y), k in FRAME.items():
        if stage >= 2 and (x, y) in ((12, 4), (11, 4), (12, 5), (11, 5), (11, 3), (13, 7), (13, 8)):
            continue  # a bracket has broken off
        px[x, y] = pal[k] + (255,)
    for x, y in RIVETS:
        if px[x, y][3]:
            px[x, y] = STEEL_H + (255,)
    scan = (f * 2) % 18 - 2          # scanline row sweeping top to bottom
    flick = stage == 0 and f in (3,) or stage == 1 and f in (2, 5) or stage == 2 and f in (1, 2, 5, 6) or stage == 3 and f % 2 == 1 or stage >= 4 and f % 3 != 0
    glitch_row = 4 + (f * 3) % 7
    for y, hw in HALF.items():
        for x in range(8 - hw - 1, 8 + hw):
            dx = x - 7.5
            if abs(dx) > hw - 0.4 + (0.5 if hw >= 3 else 0):
                continue
            t = 0.5 + 0.5 * (-dx / max(hw, 1)) * 0.6 - (y - 7.5) * 0.04
            c = mix(dark, light, max(0, min(1, t)))
            if abs(dx) < 0.9 and 4 <= y <= 9:
                c = mix(c, (255, 255, 255), 0.35)          # central facet highlight
            if dx > 0 and y < 6:
                c = mix(c, (255, 255, 255), 0.25)
            a = 205
            if abs(dx) >= hw - 0.6:
                a = 235                                    # brighter rim
            if y % 2 == 1:
                a -= 28                                    # hologram scanlines
            if abs(y - scan) < 0.6:
                c = mix(c, (255, 255, 255), 0.85); a = 250
            elif abs(y - scan) < 1.6:
                c = mix(c, (255, 255, 255), 0.35)
            if flick:
                a = int(a * 0.62)
            xx = x
            if y == glitch_row and (f in (3, 6) or (stage > 0 and f in (1, 4))):
                xx = x + (1 if f % 2 else -1)              # glitch: one row slides sideways
            if 0 <= xx < 16:
                px[xx, y] = c + (max(60, min(255, a)),)
    if stage >= 1:
        for x, y in CRACK1:
            px[x, y] = (8, 8, 14, 240)
            if px[x + 1, y][3] and (x + y) % 2 == 0:
                px[x + 1, y] = mix(light, (255, 255, 255), 0.6) + (255,)
    if stage >= 2:
        for x, y in CRACK2[:4 if stage == 3 else 8]:
            px[x, y] = (8, 8, 14, 245)
        for x, y in (HOLES2 if stage != 3 else HOLES2[:1]):
            px[x, y] = (0, 0, 0, 0)
        for (x, y) in [(8, 6), (6, 5), (9, 10)]:
            if px[x, y][3]:
                px[x, y] = (255, 255, 255, 255)
    if stage >= 4:
        for x, y in CRACK3[:8 if stage == 4 else 12]:
            px[x, y] = (8, 8, 14, 250)
        for x, y in HOLES3[:4 if stage == 4 else 8]:
            px[x, y] = (0, 0, 0, 0)
        for (x, y) in [(7, 5), (8, 7), (6, 9), (9, 6), (7, 10)][: 3 if stage == 4 else 5]:
            if px[x, y][3]:
                px[x, y] = (255, 255, 255, 255)
    if stage == 5 and f % 2 == 0:
        for y in range(2, 13):                              # digital tear: the whole crystal jitters apart
            if (y + f) % 3 == 0:
                for x in range(16):
                    if px[x, y][3] and 5 <= x <= 9:
                        px[min(15, x + 1), y] = px[x, y]
    # status LED: blinks, turns red when dying
    on = f % 4 < 2 if stage < 2 else f % 2 == 0
    led = base if stage == 0 else (255, 160, 60) if stage == 1 else (255, 50, 40)
    if stage >= 3:
        on = f % 2 == 0
    for x, y in LED:
        px[x, y] = (led if on else mix(led, (0, 0, 0), 0.6)) + (255,)
    return img


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MOD, exist_ok=True)
    rnd = random.Random(7)
    overrides = []
    for stage, thr in ((0, None), (1, 0.55), (2, 0.85), (3, 0.0001), (4, 0.4), (5, 0.75)):
        for idx, (name, color) in enumerate(STONES):
            strip = Image.new("RGBA", (16, 16 * FRAMES), (0, 0, 0, 0))
            for f in range(FRAMES):
                strip.paste(frame_image(color, stage, f, rnd), (0, 16 * f))
            tex = f"{name}_{stage}"
            strip.save(os.path.join(TEX, tex + ".png"))
            with open(os.path.join(TEX, tex + ".png.mcmeta"), "w") as fh:
                json.dump({"animation": {"frametime": 1 if stage >= 3 else 2 if stage == 2 else 3, "interpolate": False}}, fh, indent=2)
            with open(os.path.join(MOD, tex + ".json"), "w") as fh:
                json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"infinity:item/mini_joia/{tex}"}}, fh, indent=2)
            pred = {"custom_model_data": idx + 1}
            if thr is not None:
                pred["infinity:final" if stage >= 3 else "infinity:decay"] = thr
            overrides.append({"predicate": pred, "model": f"infinity:item/mini_joia/{tex}"})
    with open(os.path.join(ROOT, "models", "item", "mini_joia.json"), "w") as fh:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "infinity:item/mini_joia/mind_0"}, "overrides": overrides}, fh, indent=2)
    print("ok", len(overrides), "models")


if __name__ == "__main__":
    main()
