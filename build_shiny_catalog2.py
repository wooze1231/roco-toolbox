import ssl, urllib.request, re, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":UA})
    return urllib.request.urlopen(req,timeout=40,context=CTX).read().decode("utf-8","ignore")

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
    no=None
    m2=re.search(r'#(\d{1,4})\b',body)
    if m2: no=int(m2.group(1))
    rec=out.setdefault(name,{})
    rec["slug"]=slug
    if py: rec["py"]=py.lower()
    if img: rec["img"]=img
    if no: rec["no"]=no

items=[{"n":n,"s":r.get("slug",""),"p":r.get("py",""),"i":r.get("img",""),"no":r.get("no")}
       for n,r in out.items()]
items.sort(key=lambda x:x["n"])
json.dump(items,open("shiny_catalog.json","w",encoding="utf-8"),ensure_ascii=False)
print("entries:",len(items),"with no:",sum(1 for x in items if x["no"]))
print(json.dumps(items[:2],ensure_ascii=False))
for t in ["幽铃","拿脑丸","章脑丸","叮叮卯","月使鹭纳"]:
    print(t,[x for x in items if t in x["n"]][:3])
