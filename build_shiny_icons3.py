"""生成最终异色贴图映射 assets/shiny_icons.json

来源：BWIKI 文件 JL_<拼音>_yise.png（异色）/ JL_<拼音>.png（原皮）
输出：{ "<slug>": { "n": 中文名, "y": 异色直链, "p": 原皮直链 } }
"""
import ssl, urllib.request, json, urllib.parse, time

CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/rocom/api.php"


def get(u, retry=3):
    for i in range(retry):
        try:
            return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}),
                                          timeout=40, context=CTX).read().decode("utf-8", "ignore")
        except Exception:
            time.sleep(1.5 * (i + 1))
    return ""


def query(p):
    return json.loads(get(API + "?" + urllib.parse.urlencode(p)))


def imageinfo(titles):
    txt = get(API + "?" + urllib.parse.urlencode(
        {"action": "query", "titles": "|".join(titles), "prop": "imageinfo",
         "iiprop": "url", "format": "json", "formatversion": "2"}))
    if not txt.strip():
        return {}
    try:
        j = json.loads(txt)
    except Exception:
        return {}
    norm = {n["to"]: n["from"] for n in j.get("query", {}).get("normalized", [])}
    out = {}
    for p in j.get("query", {}).get("pages", []):
        t = p.get("title", "")
        if p.get("imageinfo"):
            out[norm.get(t, t)] = p["imageinfo"][0].get("url")
    return out


files = json.load(open("bwiki_jl_files.json", encoding="utf-8"))
yise_names = [f for f in files if f.lower().endswith("_yise.png")]
print("yise files:", len(yise_names))
slugs = sorted({f[len("JL_"):-len("_yise.png")] for f in yise_names})
print("slugs:", len(slugs))

# 中文名：dex_cells（罗可图鉴）+ shiny_catalog 兜底
name_by_slug = {}
for c in json.load(open("dex_cells.json", encoding="utf-8")):
    if c["slug"]:
        name_by_slug[c["slug"]] = c["title"]
for it in json.load(open("shiny_catalog.json", encoding="utf-8")):
    name_by_slug.setdefault(it.get("s", ""), it.get("n", ""))

result = {}
B = 25
for i in range(0, len(slugs), B):
    part = slugs[i:i + B]
    titles = ["文件:JL_%s_yise.png" % s for s in part] + ["文件:JL_%s.png" % s for s in part]
    res = imageinfo(titles)
    for s in part:
        y = res.get("文件:JL_%s_yise.png" % s)
        p = res.get("文件:JL_%s.png" % s)
        if y or p:
            r = {"n": name_by_slug.get(s, s)}
            if y: r["y"] = y
            if p: r["p"] = p
            result[s] = r
    print(i, len(result))
    time.sleep(0.2)

json.dump(result, open("shiny_icons.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("final:", len(result), "with y:", len([v for v in result.values() if v.get("y")]),
      "with p:", len([v for v in result.values() if v.get("p")]))
