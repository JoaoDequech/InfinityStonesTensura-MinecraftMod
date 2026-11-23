"""Merges notes/<prefix>-lang-<locale>.json into assets/infinity/lang/<locale>.json (new keys appended, existing kept).
Usage: python tools/merge-lang.py <prefix> [<prefix> ...]"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/infinity/lang"

for prefix in sys.argv[1:]:
    for loc in ("pt_br", "en_us"):
        src = ROOT / "notes" / f"{prefix}-lang-{loc}.json"
        if not src.exists():
            continue
        new = json.loads(src.read_text(encoding="utf-8"))
        dst = LANG / f"{loc}.json"
        raw = dst.read_text(encoding="utf-8")
        cur = json.loads(raw)
        add = [(k, v) for k, v in new.items() if k not in cur]
        if add:
            body = raw[: raw.rstrip().rfind("}")].rstrip()
            raw = body + ",\n" + ",\n".join(f"  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}" for k, v in add) + "\n}\n"
            json.loads(raw)
            dst.write_text(raw, encoding="utf-8", newline="")
        print(f"{prefix} {loc}: +{len(add)}")
