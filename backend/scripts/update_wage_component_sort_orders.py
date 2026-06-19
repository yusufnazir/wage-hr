#!/usr/bin/env python3
"""Rewrite processingOrder values in SR wage component seed XML (clean-database DML only)."""

from __future__ import annotations

import re
import sys
from pathlib import Path

# Keep in sync with WageComponentSortOrder.BY_TEMPLATE_CODE
ORDERS: dict[str, int] = {
    "1001": 1010,
    "1002": 1020,
    "1003": 6010,
    "1004": 4010,
    "1005": 4020,
    "1006": 1050,
    "1007": 1060,
    "1008": 1070,
    "1009": 1080,
    "1010": 5010,
    "1011": 5020,
    "1012": 5030,
    "1013": 5040,
    "1014": 5050,
    "1015": 5060,
    "1016": 5070,
    "1017": 5080,
    "1018": 5090,
    "1019": 5210,
    "1020": 5220,
    "1021": 5230,
    "1022": 5240,
    "1023": 5250,
    "1024": 5260,
    "1025": 5270,
    "1026": 8010,
    "1027": 8020,
    "1028": 1090,
    "1029": 6030,
    "1030": 1100,
    "1031": 3010,
    "1032": 2010,
    "1033": 6040,
    "1034": 4030,
    "1035": 4050,
    "1036": 4040,
    "1037": 7010,
    "1038": 6110,
    "1039": 7020,
    "1040": 2110,
    "1041": 6120,
    "1042": 3020,
    "1043": 7030,
    "1044": 5310,
}

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    ROOT / "src/main/resources/db/changelog/dml/data-m13-platform-wage-components-seed-sr-1.xml",
    ROOT / "src/main/resources/db/changelog/dml/data-m23-platform-wage-component-templates-sr-law-1.xml",
    ROOT / "src/main/resources/db/changelog/dml/data-m24-platform-wage-component-templates-sr-catalog-1.xml",
    ROOT / "src/main/resources/db/changelog/dml/data-m37-demo-ledger-balance-phase8-1.xml",
]

JSON_ORDER_RE = re.compile(r'"processingOrder"\s*:\s*\d+')
HINT_RE = re.compile(r'(<column name="processing_order_hint" valueNumeric=")\d+(")')


def patch_file(path: Path) -> int:
    text = path.read_text(encoding="utf-8")
    original = text
    blocks = text.split('<column name="template_code" value="')
    if len(blocks) <= 1:
        # demo upsert by code param
        for code, order in ORDERS.items():
            text = re.sub(
                rf'(<param name="code" value="{code}"[^>]*>[\s\S]*?<param name="processingOrder" value=")\d+(")',
                rf"\g<1>{order}\2",
                text,
                count=1,
            )
    else:
        out = [blocks[0]]
        for block in blocks[1:]:
            code = block[:4]
            if code in ORDERS:
                order = ORDERS[code]
                block = JSON_ORDER_RE.sub(f'"processingOrder":{order}', block, count=1)
                block = HINT_RE.sub(rf"\g<1>{order}\2", block, count=1)
            out.append('<column name="template_code" value="' + block)
        text = "".join(out)

    if text != original:
        path.write_text(text, encoding="utf-8")
        return 1
    return 0


def main() -> int:
    changed = 0
    for f in FILES:
        if f.exists():
            changed += patch_file(f)
            print(f"patched {f.name}")
        else:
            print(f"skip missing {f}", file=sys.stderr)
    return 0 if changed else 1


if __name__ == "__main__":
    raise SystemExit(main())
