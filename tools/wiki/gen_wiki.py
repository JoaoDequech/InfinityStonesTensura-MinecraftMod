#!/usr/bin/env python3
"""Generates the GitHub wiki pages from the mod's data (stone trees + lang files).

Usage: python tools/wiki/gen_wiki.py <output-dir>
Run from the repository root. The CI workflow (.github/workflows/wiki.yml) runs it on every push to main
and pushes the result to the wiki repository, so the wiki always matches the code.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src" / "main" / "resources"
TREES = RES / "data" / "infinity" / "stone_trees"
LANG = RES / "assets" / "infinity" / "lang"

STONES = [
    ("mind", "Joia-da-Mente", "Joia da Mente", "Mind Stone", "🟡"),
    ("power", "Joia-do-Poder", "Joia do Poder", "Power Stone", "🟣"),
    ("space", "Joia-do-Espaco", "Joia do Espaço", "Space Stone", "🔵"),
    ("reality", "Joia-da-Realidade", "Joia da Realidade", "Reality Stone", "🔴"),
    ("soul", "Joia-da-Alma", "Joia da Alma", "Soul Stone", "🟠"),
    ("time", "Joia-do-Tempo", "Joia do Tempo", "Time Stone", "🟢"),
]
# Same rules as StoneTree.java
HIDDEN_IN_WHEEL = {"mind.flight_toggle"}
ALWAYS_ON = {"mind.recipe"}
# The menu list is read straight from extra/client/ExtraClientApi.java
SRC = ROOT / "src" / "main" / "java" / "com" / "infinitystones" / "tensura"


def load_json(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def java_set(path, name):
    txt = (SRC / path).read_text(encoding="utf-8")
    m = re.search(name + r"\s*=\s*Set\.of\((.*?)\);", txt, re.S)
    return set(re.findall(r'"([^"]+)"', m.group(1))) if m else set()


L = {l: load_json(LANG / f"{l}.json") for l in ("pt_br", "en_us")}
PUBLIC = {k: v for k, v in load_json(Path(__file__).with_name("public-text.json")).items() if not k.startswith("_")}
MENU = java_set("skill/ability/extra/client/ExtraClientApi.java", "MENU_POWERS")


def tr(key, lang="pt_br", fallback=None):
    return L[lang].get(key, L["en_us"].get(key, fallback if fallback is not None else key))


def text(value, stone, node_id, field, lang="pt_br"):
    if not value:
        return ""
    if value.startswith("@"):
        return tr(value[1:], lang, "")
    if field == "title":
        return tr(f"ability.infinity.{stone}_stone.{node_id}.title", lang, value)
    return value


def load_tree(stone):
    nodes = {}
    order = []
    skipped = set()
    for sub in ("", "extras/", "extras/port/", "extras/v2a/", "extras/v2b/", "extras/v2c/", "extras/v2d/"):
        p = TREES / sub / f"{stone}.json"
        if not p.exists():
            continue
        d = load_json(p)
        for n in d if isinstance(d, list) else d.get("nodes", []):
            if (n.get("tier") or 1) >= 2:
                skipped.add(n["id"])
            if n["id"] not in nodes:
                order.append(n["id"])
            nodes[n["id"]] = n
    return [nodes[i] for i in order if i not in skipped]


def wheel_nodes(stone, nodes):
    active, passives = [], []
    for n in nodes:
        mode = f"{stone}.{n['id']}"
        act = (n.get("activation") or "press").lower()
        if act != "passive" and n.get("in_bar") and mode not in HIDDEN_IN_WHEEL:
            active.append(n)
        elif act == "passive" and n.get("in_tree") and n.get("original_type") != "palladium:dummy" and mode not in ALWAYS_ON:
            passives.append(n)
    return active, passives


KIND = {
    "press": ("Ativa", "Active"), "hold": ("Canalizada", "Channelled"),
    "toggle": ("Liga/desliga", "Toggle"), "passive": ("Passiva liga/desliga", "Passive toggle"),
}


def cost(n):
    c = n.get("cost") or {}
    if not c:
        return "—"
    if c.get("type") == "item":
        item = c.get("item", "")
        name = tr("item." + item.replace(":", "."), "pt_br", item)
        return f"{c.get('amount', 1)}× {name}"
    if c.get("type") in ("xp", "experience", "level", "levels"):
        return f"{c.get('amount', 0)} níveis de XP"
    return "—"


def stone_names(ids):
    names = {s[0]: s[2] for s in STONES}
    return ", ".join(names.get(i.replace("_stone", "").split(":")[-1], i) for i in ids)


def stats_line(stone, n):
    """Compact stats of a public power (tier 2 never reaches here: load_tree skips it)."""
    s = n.get("stats")
    if not isinstance(s, dict) or (n.get("tier") or 1) >= 2:
        return ""
    parts = []
    for key, label in (("damage", "Dano"), ("range", "Alcance"), ("duration", "Duração"), ("area", "Área")):
        v = s.get(key)
        if v:
            parts.append(f"{label}: {text(v, stone, n['id'], key) if v.startswith('@') else v}")
    if isinstance(s.get("players"), bool):
        parts.append("Atinge jogadores: " + ("sim" if s["players"] else "não"))
    if not parts:
        return ""
    return "**Atributos:** " + " · ".join(parts) + " · sem recarga · magícula 0,001 por uso"


def ability_md(stone, n):
    mode = f"{stone}.{n['id']}"
    pub = PUBLIC.get(mode, {})
    title = text(n.get("title"), stone, n["id"], "title")
    title_en = text(n.get("title"), stone, n["id"], "title", "en_us")
    if not title.strip():
        return None
    desc = pub.get("pt") or text(n.get("description"), stone, n["id"], "description")
    act = (n.get("activation") or "press").lower()
    kind = KIND.get(act, KIND["press"])[0]
    lines = [f"### {title}" + (f" · *{title_en}*" if title_en and title_en != title else ""), ""]
    meta = [f"**Tipo:** {kind}"]
    if n.get("requires_stones"):
        meta.append(f"**Conjunção:** precisa também de {stone_names(n['requires_stones'])}")
    meta.append(f"**Custo na árvore:** {cost(n)}")
    lines.append(" · ".join(meta))
    lines.append("")
    if desc:
        lines.append(desc)
        lines.append("")
    buttons = []
    dual_key = f"infinity.wheel.dual.{n['id']}"
    if dual_key in L["pt_br"]:
        buttons.append(L["pt_br"][dual_key])
    elif act in ("toggle", "passive"):
        buttons.append("Esq.: liga/desliga")
    elif act == "hold":
        buttons.append("Esq.: começa / para (sem segurar) · Dir. ou toque em H: para")
    else:
        buttons.append("Esq.: usar")
    if n.get("aim"):
        buttons.append("Depois de escolher, entra no modo mira: Esq. confirma, Dir. cancela")
    if mode in MENU and "Meio" not in buttons[0]:
        buttons.append("Meio: abre o menu do poder")
    s = n.get("settings")
    if isinstance(s, dict):
        if pub.get("hide_levels"):
            buttons.append("Scroll (em cima do poder na roda ou durante o uso): muda o nível")
        else:
            label = tr(s.get("label", ""), "pt_br", "Nível")
            buttons.append(f"Scroll (em cima do poder na roda ou durante o uso): {label} de {s.get('min')} a {s.get('cap1', s.get('max'))} (inicial {s.get('default', s.get('min'))})")
    lines.append("**Botões:** " + " · ".join(buttons))
    lines.append("")
    stats = stats_line(stone, n)
    if stats:
        lines.append(stats)
        lines.append("")
    return "\n".join(lines)


def is_gauntlet(n):
    return n["id"].startswith("gauntlet_")


def stone_page(key, slug, name, name_en, emoji):
    nodes = load_tree(key)
    active, passives = wheel_nodes(key, nodes)
    out = [f"# {emoji} {name} · *{name_en}*", "",
           "Poderes que aparecem na roda (tecla **H**) quando você carrega esta joia e já comprou o poder na árvore.",
           "Os poderes da Manopla estão na página [[Manopla do Infinito|Manopla-do-Infinito]].", ""]
    seen = set()
    blocks_a, blocks_p = [], []
    for n in active:
        if is_gauntlet(n):
            continue
        b = ability_md(key, n)
        if b and (sig := b.split("\n", 1)[1]) not in seen:
            seen.add(sig)
            blocks_a.append(b)
    for n in passives:
        b = ability_md(key, n)
        if b and (sig := b.split("\n", 1)[1]) not in seen:
            seen.add(sig)
            blocks_p.append(b)
    out += ["## Poderes", ""] + blocks_a
    if blocks_p:
        out += ["## Passivas (liga/desliga na roda, começam desligadas)", ""] + blocks_p
    return "\n".join(out), len(blocks_a) + len(blocks_p)


def gauntlet_page():
    out = ["# 🧤 Manopla do Infinito · *Infinity Gauntlet*", "",
           "Coloque as joias na Manopla pela tela de encaixes (clique direito com a Manopla). "
           "Para tirar uma joia de um artefato (Orbe, Tesserato, Éter, Cetro, Olho de Agamotto): Shift + olhar para o céu + bater. "
           "A Manopla fica pequena na mão esquerda e reage a cada poder usado.", "",
           "## Poderes da Manopla", ""]
    count = 0
    for key, _, name, _, _ in STONES:
        for n in load_tree(key):
            if is_gauntlet(n) and n.get("in_bar"):
                b = ability_md(key, n)
                if b:
                    out.append(b.replace("### ", f"### ", 1) + f"*Aparece na roda junto da {name}.*\n")
                    count += 1
    return "\n".join(out), count


def git(*args):
    try:
        return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8").stdout.strip()
    except OSError:
        return ""


def props():
    p = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            p[k.strip()] = v.strip()
    return p


def main(out_dir):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    pr = props()
    commit = git("rev-parse", "--short", "HEAD") or "local"
    date = git("log", "-1", "--format=%cd", "--date=format:%d/%m/%Y %H:%M") or ""
    counts = {}
    for key, slug, name, name_en, emoji in STONES:
        md, c = stone_page(key, slug, name, name_en, emoji)
        counts[name] = c
        (out / f"{slug}.md").write_text(md + "\n", encoding="utf-8")
    gmd, gc = gauntlet_page()
    (out / "Manopla-do-Infinito.md").write_text(gmd + "\n", encoding="utf-8")
    total = sum(counts.values()) + gc

    stones_list = "\n".join(f"- {e} [[{n}|{s}]] ({counts[n]} poderes)" for _, s, n, _, e in STONES)
    (out / "Home.md").write_text(f"""# Infinity Stones · Tensura Edition

As seis Joias do Infinito para **Minecraft {pr.get('minecraft_version')}** com **NeoForge {pr.get('neo_version')}**, feitas para rodar junto do **Tensura Reincarnated**.
Versão atual: **{pr.get('mod_version')}** · {total} poderes · gerado do código no commit `{commit}` ({date}).

## Começando
- [[Instalação|Instalacao]]: o que precisa no cliente e no servidor.
- [[Controles e a roda de poderes|Controles]]: tecla H, cliques, scroll, favoritos.
- [[Manopla do Infinito|Manopla-do-Infinito]] ({gc} poderes).
- [[Novidades|Novidades]]: o que mudou em cada atualização.

## Joias
{stones_list}

## Como funciona
- Cada joia tem uma árvore de poderes. Abra pela roda (clique no centro) e compre os poderes com **Aspectos da Existência**.
- Os poderes só funcionam enquanto você carrega a joia (inventário, mãos, armadura, Curios ou encaixada na Manopla).
- Tudo funciona em multiplayer e no survival, sem precisar de OP.
- As passivas começam **desligadas**: cada jogador liga o que quiser na roda.
- Só existe uma estrutura de cada joia gerada no mundo.
""", encoding="utf-8")

    (out / "Instalacao.md").write_text(f"""# Instalação

## Requisitos
- Minecraft {pr.get('minecraft_version')} + NeoForge {pr.get('neo_version')}
- Tensura Reincarnated 2.0.1.5, ManasCore 4.0.0.2 e Architectury 13.0.12
- Opcional: Create 6.0.10, Create Aeronautics 1.3.2 e Sable 2.0.6 (a Manipulação da Matéria usa as construções físicas deles)

## Cliente e servidor
1. Baixe o `.jar` mais recente em **Releases** (https://github.com/JoaoDequech/InfinityStonesTensura-MinecraftMod/releases/latest) ou com quem administra o modpack.
2. Coloque o `.jar` na pasta `mods` do cliente **e** do servidor. O mod precisa estar nos dois.
3. Apague a versão antiga do mod da pasta `mods` antes, para não ficar duas.

O mod não precisa de OP nem de comandos para os jogadores usarem os poderes.
""", encoding="utf-8")

    (out / "Controles.md").write_text("""# Controles e a roda de poderes

A roda abre na tecla **H** (dá para trocar em *Opções → Controles*).

| Ação | Como |
|---|---|
| Abrir a roda | Aperte **H**. Ela fica aberta e você pode andar com ela aberta. |
| Repetir o último poder | Toque rápido em **H** |
| Escolher a joia | Passe o mouse no anel de joias |
| Usar um poder | **Clique esquerdo** no poder |
| Segunda opção do poder | **Clique direito** no poder (quando ele tem duas opções; a dica aparece no painel) |
| Menu do poder | **Clique do meio** (opções extras de certos poderes: ordens, leis…) |
| Mudar nível ou modo | **Scroll** em cima do poder na roda, ou **scroll durante o uso** (modo mira ou poder canalizado). Uma barra mostra o nível. |
| Favoritos | Com o mouse em cima de um poder, aperte **1 a 4**. Depois, **H + 1..4** usa o favorito. |
| Árvore da joia | Clique no centro da roda |

## Modo mira
Poderes com alvo fecham a roda e entram no modo mira: você anda e mira livremente, **esquerdo confirma** e **direito (ou Esc) cancela**.

## Poderes canalizados
Não precisa segurar nada: escolha uma vez para começar e escolha de novo (ou clique direito, ou toque em H) para parar.

## Passivas
Toda passiva é um liga/desliga na roda (marcador redondo) e começa desligada.
""", encoding="utf-8")

    cl = ROOT / "CHANGELOG.md"
    log = cl.read_text(encoding="utf-8").split("\n", 1)[1].strip() if cl.exists() else ""
    (out / "Novidades.md").write_text("# Novidades\n\nO que mudou em cada versão.\n\n" + (log or "- (sem histórico)") + "\n", encoding="utf-8")

    side = ["**[[Início|Home]]**", "", "- [[Instalação|Instalacao]]", "- [[Controles|Controles]]", "- [[Manopla do Infinito|Manopla-do-Infinito]]", "- [[Novidades|Novidades]]", "", "**Joias**", ""]
    side += [f"- {e} [[{n}|{s}]]" for _, s, n, _, e in STONES]
    (out / "_Sidebar.md").write_text("\n".join(side) + "\n", encoding="utf-8")
    (out / "_Footer.md").write_text(f"Página gerada automaticamente a partir do código (commit `{commit}`). Não edite aqui: as mudanças são sobrescritas no próximo push.\n", encoding="utf-8")
    print(f"wiki: {total} powers -> {out}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "build/wiki")
