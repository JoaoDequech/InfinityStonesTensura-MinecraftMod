# Giant hand models (Blockbench)

Sources of the Sky Fist / Sculptor's Hands (`stone_hand`) and the Hand of the Creator (`gauntlet`). Not shipped.

- `gen/model.py`: cube/bone layout (Modded Entity format, 16 px per pose unit). `gen/paint.py`: procedural pixel-art textures painted from the 3D position of each texel (`*.png` plus `*_e.png` emissive layer).
- `python gen/build.py` writes `build/*.json` and the textures (here and in `assets/infinity/textures/entity/v2`).
- `gen/loader.js` is run inside Blockbench (risky_eval): loads `build/<model>.json`, poses the fingers, exports the Java class (`java/_raw_*.java`), the `*.bbmodel` and `renders/*.png`.
- `python gen/mkjava.py` turns the exports into `HandLayers.java`. Posing at run time is in `HandRig.java` (finger chains driven by curl/spread).
- `textures/*_preview.png` has the gem colours baked in for the previews; the game textures are grey and tinted per stone at run time.
