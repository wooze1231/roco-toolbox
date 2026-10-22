import ssl, urllib.request, json, urllib.parse, time
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/nrc/api.php"

def get(u, retry=3):
    for i in range(retry):
        try:
            return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}),
                                          timeout=40, context=CTX).read().decode("utf-8", "ignore")
        except Exception as e:
            print("  retry", i, str(e)[:60]); time.sleep(2 * (i + 1))
    return ""

def query(p):
    t = get(API + "?" + urllib.parse.urlencode(p))
    if not t.strip(): return {}
    try: return json.loads(t)
    except Exception: return {}

print("== 小灵菇 页面文件 ==")
j = query({"action": "query", "titles": "小灵菇", "prop": "images", "imlimit": "200",
           "format": "json", "formatversion": "2"})
pg = j.get("query", {}).get("pages", [])
if pg and "missing" not in pg[0]:
    imgs = [im["title"] for im in pg[0].get("images", [])]
    print("imgs:", len(imgs))
    print("yise:", [t for t in imgs if "yise" in t.lower() or "异色" in t])
    print("all:", imgs[:20])
else:
    print("missing page")

print("== 全站含 yise 文件（前缀扫描）==")
names = []
cont = None
for i in range(10):
    p = {"action": "query", "list": "allimages", "ailimit": "500", "format": "json", "formatversion": "2"}
    if cont: p["aicontinue"] = cont
    j = query(p)
    if not j: print("stop", i); break
    names += [im["name"] for im in j.get("query", {}).get("allimages", [])]
    cont = j.get("continue", {}).get("aicontinue")
    if not cont: break
print("files:", len(names))
yise = [n for n in names if "yise" in n.lower()]
print("yise:", len(yise), yise[:10])
json.dump(names, open("nrc_files.json", "w", encoding="utf-8"), ensure_ascii=False)
