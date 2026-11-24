"""python build.py  ->  writes ../build/<model>.json (loader input) and textures into ../textures/ and the mod assets."""
import json, os, sys
import numpy as np
from PIL import Image
import model, paint

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, ".."))
ASSETS = os.path.normpath(os.path.join(HERE, "../../../src/main/resources/assets/infinity/textures/entity/v2"))
WIDTH = 512

MODELS = {
    "stone_hand": ("stone", ["sky", "sculptor"], "hand"),
    "gauntlet": ("gold", ["gold"], "gauntlet"),
}


def group_json(g):
    return {"name": g.name, "origin": list(g.origin), "rot": list(g.rot),
            "cubes": [{"name": c.name, "from": c.f, "to": c.t, "uv": list(c.uv), "mat": c.mat} for c in g.cubes],
            "kids": [group_json(k) for k in g.kids]}


def main(only=None):
    os.makedirs(os.path.join(OUT, "build"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "textures"), exist_ok=True)
    os.makedirs(ASSETS, exist_ok=True)
    for name, (kind, styles, prefix) in MODELS.items():
        if only and name != only:
            continue
        roots = model.build(kind)
        cubes = list(model.cubes_of(roots))
        h = paint.pack(cubes, WIDTH)
        H = 1
        while H < h:
            H *= 2
        print(name, "cubes", len(cubes), "atlas", WIDTH, "x", h, "->", H)
        texes = []
        for style in styles:
            img, emi = paint.paint(cubes, style, (WIDTH, H))
            for tag, arr in (("", img), ("_e", emi)):
                im = Image.fromarray((arr * 255 + 0.5).clip(0, 255).astype(np.uint8), "RGBA")
                fn = f"{prefix}_{style}{tag}.png"
                im.save(os.path.join(OUT, "textures", fn))
                im.save(os.path.join(ASSETS, fn))
            texes.append(f"{prefix}_{style}")
            if kind == "gold":
                paint.PREVIEW = True
                pv, _ = paint.paint(cubes, style, (WIDTH, H))
                paint.PREVIEW = False
                Image.fromarray((pv * 255 + 0.5).clip(0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "textures", f"{prefix}_{style}_preview.png"))
        data = {"name": name, "tex_w": WIDTH, "tex_h": H, "groups": [group_json(g) for g in roots], "textures": texes,
                "cubes": len(cubes)}
        with open(os.path.join(OUT, "build", name + ".json"), "w") as f:
            json.dump(data, f)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
