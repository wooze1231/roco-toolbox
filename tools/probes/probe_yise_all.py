import ssl, urllib.request, json, urllib.parse, time
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/rocom/api.php"

def get(u, retry=4):
    for i in range(retry):
        try:
            return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}),
                                          timeout=40, context=CTX).read().decode("utf-8", "ignore")
        except Exception as e:
            time.sleep(3 * (i + 1))
    return ""

def query(p):
    t = get(API + "?" + urllib.parse.urlencode(p))
    return json.loads(t) if t.strip() else {}

# 全站文件，前缀为空，按 aiadir? 用 list=allimages 无 prefix，取含 yise 的
names = []
cont = None
for i in range(6):
    p = {"action": "query", "list": "allimages", "ailimit": "500", "format": "json", "formatversion": "2"}
    if cont: p["aicontinue"] = cont
    j = query(p)
    if not j: print("stop at", i); break
    names += [im["name"] for im in j.get("query", {}).get("allimages", [])]
    cont = j.get("continue", {}).get("aicontinue")
    if not cont: break
print("files scanned:", len(names))
yise = [n for n in names if "yise" in n.lower()]
print("yise total:", len(yise), "sample:", yise[:8])
pref = {}
for n in yise:
    p = n.split("_")[0].split(" ")[0]
    pref[p] = pref.get(p, 0) + 1
print("prefixes:", sorted(pref.items(), key=lambda x: -x[1])[:10])
json.dump(yise, open("bwiki_yise_files.json", "w", encoding="utf-8"), ensure_ascii=False)
