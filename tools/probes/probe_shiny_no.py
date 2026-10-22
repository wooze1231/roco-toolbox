import ssl, urllib.request, re, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":UA})
    return urllib.request.urlopen(req,timeout=40,context=CTX).read().decode("utf-8","ignore")

d=get("https://rocokingdomworld.org/zh/pokedex")
cards=[(u,b) for u,b in re.findall(r'<a[^>]+href="(/zh/pokedex/[^"]+)"[^>]*>([\s\S]{0,1500}?)</a>',d) if "/type/" not in u]
order={}
for idx,(u,body) in enumerate(cards):
    a=[x.strip() for x in re.findall(r'alt="([^"]{1,24})"',body) if re.match(r'^[\u4e00-\u9fff]',x.strip())]
    if a: order[u.rsplit("/",1)[-1]]=idx
for s in ["youling","zhangnaowan","yueshiluna","dingdingmao","mimifu"]:
    print(s,"列表序号:",order.get(s))
d2=get("https://rocokingdomworld.org/zh/pokedex/youling")
for m in list(re.finditer(r'#(\d{1,4})\b',d2))[:3]:
    print("ctx:",d2[max(0,m.start()-150):m.start()+60].replace("\n"," "))
