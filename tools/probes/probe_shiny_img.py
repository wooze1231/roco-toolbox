import ssl, urllib.request, re, json
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    return urllib.request.urlopen(req, timeout=30, context=CTX).read().decode("utf-8", "ignore")

# 1) 列表页卡片结构，找 data-shiny
print("===== LIST PAGE =====")
try:
    lst = get("https://rocokingdomworld.org/zh/pokedex")
    print("len", len(lst))
    idx = lst.find("data-shiny")
    print("data-shiny idx:", idx)
    if idx > 0:
        print(lst[idx-1500:idx+800])
    else:
        m = re.search(r'<a[^>]+href="/zh/pokedex/[^"]+"[^>]*>', lst)
        print("card sample:", m.group(0)[:600] if m else "none")
except Exception as e:
    print("list err", e)
