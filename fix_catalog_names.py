# -*- coding: utf-8 -*-
"""
修补 shiny_catalog.json 的 4 处数据缺陷（源站 rocokingdomworld 未提供中文名所致）：

  1. idx285  n='机械'  s=chilunxiaozi   -> 真名「齿轮小子」
     （源站页面 h1 就是拼音 Chilunxiaozi，抓取脚本回退时误抓了属性标签「机械」）
  2. 缺 gedouxiaowu   -> 「格斗小五」NO227
  3. 缺 gedouxiaoba   -> 「格斗小八」NO229
  4. 缺 quanzhang-226 -> 「权杖」    NO226（与「电动长颈鹿」同号，是另一只）

改完同步写回 android-app/app/src/main/assets/shiny_catalog.json，
并校验全部名字的汉字是否都在 OCR 字体子集里（新增字不能缺）。
"""
import json, shutil, os, re, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "shiny_catalog.json")
DST = os.path.join(ROOT, "android-app", "app", "src", "main", "assets", "shiny_catalog.json")
TIERS = os.path.join(ROOT, "android-app", "app", "src", "main", "assets", "fonts", "ocr_char_tiers.json")

FIX_NAME = {"chilunxiaozi": "齿轮小子"}
ADD = [
    {"n": "格斗小五", "s": "gedouxiaowu",   "p": "", "i": "/img/dd340272e43e434c.webp", "no": None},
    {"n": "格斗小八", "s": "gedouxiaoba",   "p": "", "i": "/img/056a85498b794561.webp", "no": None},
    {"n": "权杖",     "s": "quanzhang-226", "p": "", "i": "/img/a403d5eed1007d07.webp", "no": None},
]

def main():
    d = json.load(open(SRC, encoding="utf-8"))
    before = len(d)
    byslug = {e.get("s"): e for e in d}

    # 1) 改错名
    fixed = []
    for e in d:
        want = FIX_NAME.get(e.get("s"))
        if want and e.get("n") != want:
            fixed.append((e.get("n"), want))
            e["n"] = want

    # 2) 补缺失
    added = []
    for a in ADD:
        if a["s"] in byslug:
            print("  已存在，跳过:", a["s"], "->", byslug[a["s"]].get("n"))
            continue
        d.append(dict(a))
        added.append(a["n"])

    # 3) 按名字排序（与生成脚本一致：items.sort(key=lambda x: x["n"])）
    d.sort(key=lambda x: x["n"])

    # 4) 重名 / 空名校验
    names = [e.get("n") for e in d]
    dup = [n for n in set(names) if names.count(n) > 1]
    if dup:
        print("!! 出现重名:", dup)
    else:
        print("重名校验: 0")

    json.dump(d, open(SRC, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    shutil.copyfile(SRC, DST)
    print(f"条数: {before} -> {len(d)}")
    print("改名:", fixed)
    print("新增:", added)

    # 5) 字覆盖校验：新增字的每一笔都得在字体子集里
    #    结构: {"source": str, "tiers": {"dex": "字串", "common": "字串"}}
    tiers = json.load(open(TIERS, encoding="utf-8"))["tiers"]
    pool = set()
    for v in tiers.values():
        if isinstance(v, str):
            pool |= set(v)
    newchars = set()
    for n in [w for _, w in fixed] + added:
        newchars |= set(re.findall(r"[\u4e00-\u9fff]", n))
    miss = sorted(c for c in newchars if c not in pool)
    print("新增汉字:", "".join(sorted(newchars)), "| 子集缺失:", miss if miss else "0")
    if miss:
        print("!! 缺字，需要重建字体子集")
        sys.exit(1)

if __name__ == "__main__":
    main()
