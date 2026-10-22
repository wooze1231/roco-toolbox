"""全量生成精灵「普通图 / 异色图」直链映射（来源 BWIKI 文件 JL_<拼音>[_yise].png）。

第一轮：按罗可图鉴 slug 直接猜文件名；
第二轮：对没命中的，用中文页面 prop=images 找文件列表里的 *yise* 图。
输出 shiny_icons.json: { "<slug>": { "n":中文名, "no":编号, "y":异色直链, "p":普通直链 } }
"""
import ssl, urllib.request, json, urllib.parse, time

CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/rocom/api.php"


def get(url, retry=3):
    for i in range(retry):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            return urllib.request.urlopen(req, timeout=40, context=CTX).read().decode("utf-8", "ignore")
        except Exception as e:
            if i == retry - 1:
                raise
            time.sleep(1.5 * (i + 1))
    return ""


def query(params):
    return json.loads(get(API + "?" + urllib.parse.urlencode(params)))


def batch_imageinfo(titles):
    j = query({"action": "query", "titles": "|".join(titles), "prop": "imageinfo",
               "iiprop": "url", "format": "json", "formatversion": "2"})
    norm = {n["to"]: n["from"] for n in j.get("query", {}).get("normalized", [])}
    out = {}
    for p in j.get("query", {}).get("pages", []):
        t = p.get("title", "")
        key = norm.get(t, t)
        if p.get("imageinfo"):
            out[key] = p["imageinfo"][0].get("url")
    return out


cells = json.load(open("dex_cells.json", encoding="utf-8"))
targets = [c for c in cells if c["slug"]]
print("targets:", len(targets))
result = {}

# —— 第一轮：猜文件名 ——
B = 20
for kind, tpl in (("y", "文件:JL_%s_yise.png"), ("p", "文件:JL_%s.png")):
    for i in range(0, len(targets), B):
        part = targets[i:i + B]
        titles = [tpl % c["slug"] for c in part]
        try:
            res = batch_imageinfo(titles)
        except Exception as e:
            print("err", kind, i, e); continue
        for c in part:
            u = res.get(tpl % c["slug"])
            if u:
                result.setdefault(c["slug"], {"n": c["title"], "no": c["no"], "sh": c["shiny"]})[kind] = u
        time.sleep(0.2)
print("round1 shiny:", len([v for v in result.values() if v.get("y")]))

# —— 第二轮：没命中的用页面文件列表补 ——
miss = [c for c in targets if not result.get(c["slug"], {}).get("y")]
print("miss:", len(miss))
fixed = 0
for c in miss:
    try:
        j = query({"action": "query", "titles": c["title"], "prop": "images",
                   "imlimit": "200", "format": "json", "formatversion": "2"})
        pages = j.get("query", {}).get("pages", [])
        if not pages:
            continue
        imgs = [im["title"] for im in pages[0].get("images", [])]
        cand = [t for t in imgs if "yise" in t.lower() and t.lower().startswith("文件:jl")]
        if not cand:
            continue
        res = batch_imageinfo(cand[:1])
        url = list(res.values())[0] if res else None
        if url:
            result.setdefault(c["slug"], {"n": c["title"], "no": c["no"], "sh": c["shiny"]})["y"] = url
            fixed += 1
    except Exception as e:
        print("page err", c["title"], e)
    time.sleep(0.2)
print("round2 fixed:", fixed)

json.dump(result, open("shiny_icons.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("with shiny:", len([v for v in result.values() if v.get("y")]),
      "with normal:", len([v for v in result.values() if v.get("p")]))
