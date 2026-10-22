import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
req=urllib.request.Request("https://www.rocokingdomworld.org/pokedex",headers={"User-Agent":UA})
d=urllib.request.urlopen(req,timeout=25,context=CTX).read().decode("utf-8","ignore")
print("len",len(d))
# 条目链接
items=re.findall(r'href="(/pokedex/[^"]+)"[^>]*>.{0,400}?<img[^>]+src="([^"]+)"[^>]*>.{0,200}?alt="([^"]*)"|href="(/pokedex/[^"]+)"', d, re.S)
print("item links:", len(set(re.findall(r'href="(/pokedex/[^"]+)"', d))))
# 找名字+图的模式
cards=re.findall(r'<a[^>]+href="(/pokedex/[^"]+)"[^>]*>([\s\S]{0,600}?)</a>', d)
print("cards:", len(cards))
for u,body in cards[:8]:
    img=re.search(r'src="([^"]+)"', body)
    nm=re.search(r'alt="([^"]+)"|>([^<>]{2,12})</', body)
    print("  ", u, "|", (img.group(1)[:80] if img else None), "|", (nm.group(1) or nm.group(2) if nm else None))
print("---- 是否有 __NEXT_DATA__ / JSON ----")
m=re.search(r'__NEXT_DATA__[^>]*>([\s\S]{0,300})', d)
print((m.group(1)[:300] if m else "none"))
print("---- 分页 ----")
print(sorted(set(re.findall(r'href="(/pokedex\?[^"]*|/pokedex/page[^"]*)"', d)))[:10])
