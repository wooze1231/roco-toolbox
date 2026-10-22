import ssl, urllib.request, json, urllib.parse, re, time
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

j = query({"action": "parse", "page": "小灵菇", "prop": "text", "format": "json", "formatversion": "2"})
txt = j.get("parse", {}).get("text", "")
print("html len", len(txt))
for kw in ["异色", "shiny", "Shiny", "yise"]:
    idxs = [m.start() for m in re.finditer(kw, txt)]
    print(kw, len(idxs), idxs[:6])

# 所有图片 src
srcs = re.findall(r'src="([^"]+\.(?:png|jpg|jpeg|gif|webp))"', txt)
print("img src count", len(srcs))
seen = []
for s in srcs:
    if any(k in s.lower() for k in ["yise", "shiny", "%E5%BC%82%E8%89%B2"]):
        seen.append(s)
print("candidate:", len(seen))
for s in seen[:15]:
    print("  ", s)

i = txt.find("异色")
if i > 0:
    print("---- context ----")
    print(txt[max(0, i-1200):i+1200].replace("\n", " ")[:2400])
