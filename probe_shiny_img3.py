import ssl, urllib.request, re
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    return urllib.request.urlopen(req, timeout=40, context=CTX).read().decode("utf-8", "ignore")

d = get("https://rocokingdomworld.org/zh/pokedex/qilicao")
print("len", len(d))
for kw in ["shiny", "异色", "Shiny", "data-shiny"]:
    idxs = [m.start() for m in re.finditer(re.escape(kw), d)]
    print(kw, "count:", len(idxs), idxs[:10])
i = d.find("shiny")
if i < 0:
    i = d.find("异色")
if i > 0:
    print("----- context -----")
    print(d[max(0, i-2000):i+2000])
