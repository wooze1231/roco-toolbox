import ssl, urllib.request, json, urllib.parse, time
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/rocom/api.php"
def get(u):
    return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}), timeout=40, context=CTX).read().decode("utf-8", "ignore")
def query(p): return json.loads(get(API + "?" + urllib.parse.urlencode(p)))

for name in ["小黑猫", "幽铃", "拿脑丸"]:
    j = query({"action": "query", "list": "search", "srsearch": "异色" + name, "srlimit": "10", "format": "json", "formatversion": "2"})
    print("search 异色" + name, [r["title"] for r in j.get("query", {}).get("search", [])])

# 拉全站 JL 文件（翻页），统计 yise
allnames = []
cont = None
for i in range(8):
    p = {"action": "query", "list": "allimages", "aiprefix": "JL", "ailimit": "500", "format": "json", "formatversion": "2"}
    if cont: p["aicontinue"] = cont
    j = query(p)
    allnames += [im["name"] for im in j.get("query", {}).get("allimages", [])]
    cont = j.get("continue", {}).get("aicontinue")
    if not cont: break
print("JL total:", len(allnames))
yise = [n for n in allnames if "yise" in n.lower()]
print("yise files:", len(yise))
print(yise[:10])
json.dump(allnames, open("bwiki_jl_files.json", "w", encoding="utf-8"), ensure_ascii=False)

# 异色獠牙猪 页面图片
j = query({"action": "query", "titles": "异色獠牙猪", "prop": "images", "imlimit": "100", "format": "json", "formatversion": "2"})
pg = j.get("query", {}).get("pages", [])
print("异色獠牙猪 imgs:", [im["title"] for im in pg[0].get("images", [])][:15] if pg else "missing")
