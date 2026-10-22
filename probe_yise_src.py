import ssl, urllib.request, json, urllib.parse, time
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def head(u):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}), timeout=20, context=CTX)
        return r.status, r.headers.get("Content-Type", "")
    except Exception as e:
        return "ERR", str(e)[:50]
def get(u):
    return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}), timeout=40, context=CTX).read().decode("utf-8", "ignore")

print("== 罗可图鉴 命名试探（小黑猫/奇丽草）==")
for s in ["xiaoheimao", "qilicao"]:
    for suf in ["", "_yise", "-yise", "_shiny", "_2", "_alt"]:
        print(s + suf, head("https://rocokingdomworld.org/images/spirits/%s%s.webp" % (s, suf)))

print("== BWIKI 搜索「异色」==")
j = json.loads(get("https://wiki.biligame.com/rocom/api.php?" + urllib.parse.urlencode(
    {"action": "query", "list": "search", "srsearch": "异色", "srlimit": "20", "format": "json", "formatversion": "2"})))
for r in j.get("query", {}).get("search", []):
    print(" -", r["title"])

print("== BWIKI 文件搜索 JL xiaoheimao ==")
j = json.loads(get("https://wiki.biligame.com/rocom/api.php?" + urllib.parse.urlencode(
    {"action": "query", "list": "allimages", "aiprefix": "JL xiaoheimao", "ailimit": "30", "format": "json", "formatversion": "2"})))
print([im["name"] for im in j.get("query", {}).get("allimages", [])])

print("== BWIKI 全站含 yise 的文件（前缀 JL）计数 ==")
j = json.loads(get("https://wiki.biligame.com/rocom/api.php?" + urllib.parse.urlencode(
    {"action": "query", "list": "allimages", "aiprefix": "JL", "ailimit": "500", "format": "json", "formatversion": "2"})))
names = [im["name"] for im in j.get("query", {}).get("allimages", [])]
print("JL files:", len(names), "sample:", names[:5])
