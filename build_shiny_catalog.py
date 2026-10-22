import ssl, urllib.request, re, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":UA})
    return urllib.request.urlopen(req,timeout=40,context=CTX).read().decode("utf-8","ignore")

# 1) 列表页 -> 名字 / 拼音 / slug / 头像
d=get("https://rocokingdomworld.org/zh/pokedex")
cards=re.findall(r'<a[^>]+href="(/zh/pokedex/[^"]+)"[^>]*>([\s\S]{0,1500}?)</a>',d)
out={}
for u,body in cards:
    if "/type/" in u: continue
    slug=u.rsplit("/",1)[-1]
    name=None; py=None
    for a in re.findall(r'alt="([^"]{1,24})"',body):
        a=a.strip()
        if re.match(r'^[\u4e00-\u9fff]',a):
            m=re.match(r'^([\u4e00-\u9fff（）()·]+)\s*\(([A-Za-z]+)\)\s*$',a)
            if m: name,py=m.group(1),m.group(2)
            else: name=a
            break
    if name is None:
        for t in [t.strip() for t in re.findall(r'>([^<>{}]{2,18})<',body)]:
            if re.match(r'^[\u4e00-\u9fff（）()·]+$',t): name=t; break
    if not name: continue
    img=None
    m=re.search(r'src="(/img/[^"]+\.(?:webp|png|jpg|jpeg))"',body)
    if m: img=m.group(1)
    rec=out.setdefault(name,{})
    rec["slug"]=slug
    if py: rec["py"]=py.lower()
    if img: rec["img"]=img

# 2) 详情页里找编号
d2=get("https://rocokingdomworld.org/zh/pokedex/youling")
print("--- 编号线索 ---")
for pat in [r'No\.?\s*(\d{1,4})', r'#(\d{3,4})', r'编号[^0-9]{0,6}(\d{1,4})', r'"(?:no|number|id)"\s*:\s*"?(\d+)']:
    print(pat, re.findall(pat,d2)[:8])
print("--- 头像图 ---")
print(sorted(set(re.findall(r'src="(/img/[^"]+\.(?:webp|png|jpg|jpeg))"',d2)))[:12])

items=[]
for n,r in out.items():
    items.append({"n":n,"s":r.get("slug",""),"p":r.get("py",""),"i":r.get("img","")})
items.sort(key=lambda x:x["n"])
json.dump(items,open("shiny_catalog.json","w",encoding="utf-8"),ensure_ascii=False)
print("catalog entries:",len(items))
print(json.dumps(items[:3],ensure_ascii=False))
