import ssl, urllib.request, re, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":UA})
    return urllib.request.urlopen(req,timeout=30,context=CTX).read().decode("utf-8","ignore")

d=get("https://rocokingdomworld.org/zh/pokedex")
cards=re.findall(r'<a[^>]+href="(/zh/pokedex/[^"]+)"[^>]*>([\s\S]{0,1500}?)</a>',d)
mapping={}
for u,body in cards:
    if "/type/" in u: continue
    # 中文名通常在 alt 或末尾文本
    alts=re.findall(r'alt="([^"]{1,20})"',body)
    txts=[t.strip() for t in re.findall(r'>([^<>{}]{2,16})<',body) if t.strip()]
    name=None
    for a in alts:
        if re.match(r'^[\u4e00-\u9fff]',a): name=a; break
    if not name:
        for t in txts:
            if re.match(r'^[\u4e00-\u9fff（）()·]+$',t): name=t; break
    if name:
        mapping.setdefault(name,u)
print("mapped:",len(mapping))
targets=["拿脑丸","量风碗","幽铃","未完虫","觅觅蝠","逗逗","刺轮砣","小浣蛋","星星眼","玳龟","布灵","小黑猫","瑰眼仔","芽眼魇","小灵菇","叮叮卵","月使鹭纳","圣凯布米龙"]
for t in targets:
    hits=[(n,u) for n,u in mapping.items() if t in n]
    print(t,"->",hits[:4])
json.dump(mapping,open("shiny_name_map.json","w",encoding="utf-8"),ensure_ascii=False)
