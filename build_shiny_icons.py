"""批量生成「精灵异色贴图 URL」映射（来源：BWIKI 文件 JL_<拼音>_yise.png）。

输入 dex_cells.json（罗可图鉴列表卡片解析结果，含 slug/title/no/shiny）
输出 shiny_icons.json: { "<slug>": { "n":"中文名", "y":"异色图直链", "p":"普通图直链" } }
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


def batch(titles):
    """titles: 完整『文件:xxx』列表 -> {原始title: url}"""
    q = urllib.parse.urlencode({
        "action": "query", "titles": "|".join(titles), "prop": "imageinfo",
        "iiprop": "url", "format": "json", "formatversion": "2",
    })
    j = json.loads(get(API + "?" + q))
    norm = {}
    for n in j.get("query", {}).get("normalized", []):
        norm[n["to"]] = n["from"]
    out = {}
    for p in j.get("query", {}).get("pages", []):
        t = p.get("title", "")
        ii = p.get("imageinfo")
        key = norm.get(t, t)
        if ii:
            out[key] = ii[0].get("url")
    return out


cells = json.load(open("dex_cells.json", encoding="utf-8"))
# 只处理站上标注「异色: 是」的精灵（其它没有异色形态）
targets = [c for c in cells if c["shiny"] == "是" and c["slug"]]
print("targets:", len(targets))

result = {}
B = 20
for kind, tpl in (("y", "文件:JL_%s_yise.png"), ("p", "文件:JL_%s.png")):
    for i in range(0, len(targets), B):
        part = targets[i:i + B]
        titles = [tpl % c["slug"] for c in part]
        try:
            res = batch(titles)
        except Exception as e:
            print("batch err", i, e)
            continue
        for c in part:
            t = tpl % c["slug"]
            url = res.get(t)
            if url:
                r = result.setdefault(c["slug"], {"n": c["title"], "no": c["no"]})
                r[kind] = url
        print(kind, i, "/", len(targets), "ok", len([1 for c in part if res.get(tpl % c['slug'])]))
        time.sleep(0.25)

json.dump(result, open("shiny_icons.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("slugs with shiny:", len([k for k, v in result.items() if v.get("y")]))
print("slugs with normal:", len([k for k, v in result.items() if v.get("p")]))
for k in list(result)[:5]:
    print(k, result[k])
