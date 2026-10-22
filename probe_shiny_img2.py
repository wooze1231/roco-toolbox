import ssl, urllib.request, re, json, collections
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    return urllib.request.urlopen(req, timeout=40, context=CTX).read().decode("utf-8", "ignore")

lst = get("https://rocokingdomworld.org/zh/pokedex")
cells = re.findall(r'<div class="spirit-cell"(.*?)</div>\s*</a>', lst, re.S)
print("cells:", len(cells))
rows = []
for c in cells:
    def attr(k):
        m = re.search(k + r'="([^"]*)"', c)
        return m.group(1) if m else ""
    img = re.search(r'src="(/img/[^"]+)"', c)
    href = re.search(r'href="(/zh/pokedex/[^"]+)"', c)
    rows.append(dict(
        title=attr("data-title"), no=attr("data-no"), shiny=attr("data-shiny"),
        family=attr("data-family"), stage=attr("data-form-stage"), ftype=attr("data-form-type"),
        img=img.group(1) if img else "", slug=(href.group(1).rsplit("/", 1)[-1] if href else ""),
    ))
cnt = collections.Counter(r["shiny"] for r in rows)
print("shiny values:", cnt)
shiny_rows = [r for r in rows if r["shiny"] == "是"]
print("shiny rows:", len(shiny_rows))
for r in shiny_rows[:15]:
    print(r)
json.dump(rows, open("dex_cells.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)

# 同名配对情况
by_title = collections.defaultdict(list)
for r in rows:
    by_title[r["title"]].append(r)
both = {k: v for k, v in by_title.items() if len(v) > 1}
print("titles with multiple rows:", len(both))
for k in list(both)[:10]:
    print(k, [(x["shiny"], x["ftype"], x["img"]) for x in both[k]])
