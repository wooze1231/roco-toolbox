import ssl, urllib.request, re, sys
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":UA})
    return urllib.request.urlopen(req,timeout=30,context=CTX).read().decode("utf-8","ignore")

d=get("https://rocokingdomworld.org/zh/pokedex")
print("len",len(d))
links=sorted(set(re.findall(r'href="(/zh/pokedex/[^"?]+)"',d)))
print("links:",len(links), links[:10])
# 卡片结构示例
cards=re.findall(r'<a[^>]+href="(/zh/pokedex/[^"]+)"[^>]*>([\s\S]{0,800}?)</a>',d)
print("cards:",len(cards))
for u,body in cards[:4]:
    img=re.findall(r'<img[^>]+>',body)
    txt=re.findall(r'>([^<>{}]{2,14})<',body)
    print("==",u)
    for t in img[:3]: print("   img:",t[:200])
    print("   txt:",[t for t in txt if t.strip()][:8])
