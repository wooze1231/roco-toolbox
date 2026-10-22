"""对指定精灵（默认 S4 的 19 只）用中文页面查 BWIKI 文件列表，找异色图直链。"""
import ssl, urllib.request, json, urllib.parse, time, sys
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/rocom/api.php"

def get(u, retry=3):
    for i in range(retry):
        try:
            return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}),
                                          timeout=40, context=CTX).read().decode("utf-8", "ignore")
        except Exception as e:
            if i == retry - 1: print("ERR", u[:80], e)
            time.sleep(1.5 * (i + 1))
    return ""

def query(p): return json.loads(get(API + "?" + urllib.parse.urlencode(p)))

def_ = json.load(open("shiny_default.json", encoding="utf-8"))
items = []
for s in def_["seasons"]:
    for g in s["groups"]:
        for it in g["items"]:
            items.append((it.get("s", ""), it.get("n", "")))

for slug, name in items:
    j = query({"action": "query", "titles": name, "prop": "images", "imlimit": "200",
               "format": "json", "formatversion": "2"})
    pages = j.get("query", {}).get("pages", [])
    print("=====", name, slug)
    if not pages or "missing" in pages[0]:
        print("  page missing"); continue
    imgs = [im["title"] for im in pages[0].get("images", [])]
    print("  imgs:", len(imgs), [t for t in imgs if "yise" in t.lower()][:6])
    print("  all:", imgs[:12])
    time.sleep(0.2)
