import ssl, urllib.request, re, json, urllib.parse, time
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/rocom/api.php"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    return urllib.request.urlopen(req, timeout=40, context=CTX).read().decode("utf-8", "ignore")

cells = json.load(open("dex_cells.json", encoding="utf-8"))
shiny = [c for c in cells if c["shiny"] == "是"]
print("total", len(cells), "shiny-capable", len(shiny))

def imageinfo(titles):
    q = urllib.parse.urlencode({
        "action": "query", "titles": "|".join(titles), "prop": "imageinfo",
        "iiprop": "url", "format": "json", "formatversion": "2",
    })
    j = json.loads(get(API + "?" + q))
    out = {}
    for p in j.get("query", {}).get("pages", []):
        t = p.get("title", "")
        ii = p.get("imageinfo")
        if ii:
            out[t] = ii[0].get("url")
    return out

# 先测 10 个
sample = shiny[:10]
titles = ["文件:JL_%s_yise.png" % c["slug"] for c in sample]
res = imageinfo(titles)
for c in sample:
    t = "文件:JL_%s_yise.png" % c["slug"]
    print(c["title"], c["slug"], "->", res.get(t, "MISS"))
