"""
从 BWIKI 的 模块:Pets/data/Catalog 里提取「有异色」的精灵清单。
产物：android-app/app/src/main/assets/shiny_pets.json
字段：name / number / id / shiny(立绘文件名) / head
"""
import json
import os
import re
import urllib.parse
import urllib.request

API = "https://wiki.biligame.com/nrc/api.php?"
OUT = os.path.join("android-app", "app", "src", "main", "assets", "shiny_pets.json")


def fetch_raw(title: str) -> str:
    url = API + urllib.parse.urlencode({
        "action": "query", "prop": "revisions", "rvprop": "content",
        "rvslots": "main", "titles": title, "format": "json", "formatversion": "2",
    })
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    page = json.load(urllib.request.urlopen(req, timeout=90))["query"]["pages"][0]
    if "missing" in page:
        raise SystemExit(f"页面不存在: {title}")
    return page["revisions"][0]["slots"]["main"]["content"]


def main():
    text = fetch_raw("模块:Pets/data/Catalog")
    print("catalog 长度", len(text))

    # 按 id="pet_xxx" 切块
    marks = [(m.start(), m.group(1)) for m in re.finditer(r'id="(pet_\d+)"', text)]
    print("精灵条目", len(marks))

    pets = []
    for i, (pos, pid) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else len(text)
        chunk = text[pos:end]
        m = re.search(r'name="([^"]+)",number="([^"]+)"', chunk)
        if m:
            name, number = m.group(1), m.group(2)
        else:
            m2 = re.search(r'number="([^"]+)",name="([^"]+)"', chunk)
            if not m2:
                continue
            number, name = m2.group(1), m2.group(2)
        has_shiny = bool(re.search(r'has_shiny=true', chunk))
        if not has_shiny:
            continue
        shiny = re.search(r'shiny="([^"]+)"', chunk)
        head = re.search(r'shiny_head="([^"]+)"', chunk)
        pets.append({
            "id": pid,
            "name": name,
            "number": number,
            "shiny": shiny.group(1) if shiny else "",
            "head": head.group(1) if head else "",
        })

    pets.sort(key=lambda p: int(re.sub(r"\D", "", p["number"]) or 0))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(pets, f, ensure_ascii=False, indent=1)

    print("有异色的精灵:", len(pets))
    print("产物:", OUT, os.path.getsize(OUT), "B")
    for p in pets[:8]:
        print("  ", p["number"], p["name"], p["shiny"][:36])


if __name__ == "__main__":
    main()
