import ssl, urllib.request, json, urllib.parse, re, time
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/nrc/api.php"

def get(u, retry=4):
    for i in range(retry):
        try:
            return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}),
                                          timeout=45, context=CTX).read().decode("utf-8", "ignore")
        except Exception:
            time.sleep(3 * (i + 1))
    return ""

def query(p):
    t = get(API + "?" + urllib.parse.urlencode(p))
    return json.loads(t) if t.strip() else {}

def art_urls(page):
    j = query({"action": "parse", "page": page, "prop": "text", "format": "json", "formatversion": "2"})
    html = j.get("parse", {}).get("text", "")
    pet = re.search(r'data-view="pet"[^>]*>\s*<img[^>]+src="([^"]+)"', html)
    shiny = re.search(r'data-view="shiny"[^>]*>\s*<img[^>]+src="([^"]+)"', html)
    return (pet.group(1) if pet else None), (shiny.group(1) if shiny else None)

for kw in ["章脑丸", "刺轮砣", "芽眼魔", "叮叮卯", "拿脑丸", "芽眼魇", "叮叮卵"]:
    j = query({"action": "query", "list": "search", "srsearch": kw, "srlimit": "6",
               "format": "json", "formatversion": "2"})
    titles = [r["title"] for r in j.get("query", {}).get("search", [])]
    print(kw, "->", titles)
    for t in titles[:2]:
        p, y = art_urls(t)
        print("   ", t, "pet:", bool(p), "shiny:", bool(y))
        if y: print("     Y:", y)
    time.sleep(0.5)
