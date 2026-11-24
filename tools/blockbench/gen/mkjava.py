"""Turns the Blockbench 'Java Class' exports (java/_raw_<model>.java) into the mod's HandLayers.java."""
import os, re

HERE = os.path.dirname(os.path.abspath(__file__))
BB = os.path.normpath(os.path.join(HERE, ".."))
DEST = os.path.normpath(os.path.join(HERE, "../../../src/main/java/com/infinitystones/tensura/skill/ability/extra/v2a/client/HandLayers.java"))

MODELS = [("stone_hand", "stone"), ("gauntlet", "gauntlet")]


def body(raw):
    m = re.search(r"public static LayerDefinition createBodyLayer\(\) \{\n(.*?)\n\t\}\n", raw, re.S)
    text = m.group(1)
    text = text.replace("\t\t", "        ").replace("\t", "    ")
    return text


def main():
    out = ["""package com.infinitystones.tensura.skill.ability.extra.v2a.client;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;

/** Model layers of the giant hands, exported from the Blockbench sources in tools/blockbench (Modded Entity format). */
final class HandLayers {
"""]
    for name, method in MODELS:
        path = os.path.join(BB, "java", f"_raw_{name}.java")
        if not os.path.exists(path):
            print("missing", path)
            continue
        raw = open(path, encoding="utf8").read()
        out.append(f"    static LayerDefinition {method}() {{\n{body(raw)}\n    }}\n")
    out.append("    private HandLayers() {}\n}\n")
    with open(DEST, "w", encoding="utf8", newline="\n") as f:
        f.write("\n".join(out))
    print("wrote", DEST)


main()
