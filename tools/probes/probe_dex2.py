import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
req=urllib.request.Request("https://www.rocokingdomworld.org/pokedex",headers={"User-Agent":UA})
d=urllib.request.urlopen(req,timeout=30,context=CTX).read().decode("utf-8","ignore")
slugs=[s for s in dict.fromkeys(re.findall(r'href="(/pokedex/[^"]+)"', d)) if "/type/" not in s]
print("sprite slugs:", len(slugs), slugs[:5], "...")
# 名字：抓 slug 链接标签内的可见文本
pat=re.compile(r'<a[^>]+href="(/pokedex/(?!type)[^"]+)"[^>]*>([\s\S]{0,800}?)</a>')
names={}
for u,body in pat.findall(d):
    txts=re.findall(r'>([^<>{}]{2,20})<', body)
    txts=[t.strip() for t in txts if t.strip() and not re.fullmatch(r'[\d#+%.\-]+', t)]
    if txts: names.setdefault(u, txts)
print("named:", len(names))
import itertools
for u,ts in itertools.islice(names.items(), 8):
    print("  ", u, ts[:4])
