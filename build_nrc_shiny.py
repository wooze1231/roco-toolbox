"""从 nrc wiki（洛克王国本体）抓精灵「普通 / 异色」立绘直链。

页面结构：<div class="roco-art" data-view="pet|shiny"><img src="..."></div>
输出 nrc_shiny.json: { "<slug>": { "n":中文名, "p":普通直链, "y":异色直链 } }
"""
import ssl, urllib.request, json, urllib.parse, re, time, sys

CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/nrc/api.php"


def get(u, retry=4):
    for i in range(retry):
        try:
            return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}),
                                          timeout=45, context=CTX).read().decode("utf-8", "ignore")
        except Exception as e:
            time.sleep(3 * (i + 1))
    return ""


def art_urls(page):
    t = get(API + "?" + urllib.parse.urlencode(
        {"action": "parse", "page": page, "prop": "text", "format": "json", "formatversion": "2"}))
    if not t.strip():
        return None, None
    try:
        html = json.loads(t).get("parse", {}).get("text", "")
    except Exception:
        return None, None
    pet = re.search(r'data-view="pet"[^>]*>\s*<img[^>]+src="([^"]+)"', html)
    shiny = re.search(r'data-view="shiny"[^>]*>\s*<img[^>]+src="([^"]+)"', html)
    return (pet.group(1) if pet else None), (shiny.group(1) if shiny else None)


def targets():
    out = []
    d = json.load(open("shiny_default.json", encoding="utf-8"))
    for s in d["seasons"]:
        for g in s["groups"]:
            for it in g["items"]:
                out.append((it.get("s", ""), it.get("n", "")))
    return out


items = targets()
print("targets:", len(items))
res = {}
for slug, name in items:
    p, y = art_urls(name)
    print(name, slug, "pet:", bool(p), "shiny:", bool(y))
    if p or y:
        r = {"n": name}
        if p: r["p"] = p
        if y: r["y"] = y
        res[slug or name] = r
    time.sleep(0.6)

json.dump(res, open("nrc_shiny.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("ok:", len(res), "with shiny:", len([v for v in res.values() if v.get("y")]))
