# -*- coding: utf-8 -*-
"""从 rocokingdomworld.org/pokedex 抓全部精灵（含 data-shiny 是否可异色），
下载图标到 sprites/icons/，生成 sprites.json。"""
import ssl, urllib.request, re, json, os, time
from concurrent.futures import ThreadPoolExecutor

CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
BASE="https://www.rocokingdomworld.org"
os.makedirs("sprites/icons", exist_ok=True)

req=urllib.request.Request(BASE+"/pokedex",headers={"User-Agent":UA})
d=urllib.request.urlopen(req,timeout=40,context=CTX).read().decode("utf-8","ignore")

# 每张卡：先找 data-shiny 标记位置，取其后最近的 <a href="/pokedex/x">，再在卡内取 img alt 中文名
cards=[]
for m in re.finditer(r'data-shiny="(否|是)"[\s\S]{0,80}?<a href="(/pokedex/(?!type)[^"]+)" class="group block">([\s\S]{0,1800}?)</a>', d):
    shiny, slug, body = m.group(1)=="是", m.group(2), m.group(3)
    im=re.search(r'<img src="(/img/[^"]+)" alt="[^"]*?[\(（]([^)）]+)[)）]"', body)
    no=re.search(r'NO(\d+)', body)
    if im:
        cards.append({"slug":slug,"name":im.group(2),"img":im.group(1),
                      "no":("NO"+no.group(1)) if no else "", "shiny":shiny})
# 去重（同 slug 保留首个）
seen=set(); sprites=[]
for c in cards:
    if c["slug"] in seen: continue
    seen.add(c["slug"]); sprites.append(c)
print("cards:", len(cards), "unique:", len(sprites))
print("shiny=是:", sum(1 for s in sprites if s["shiny"]), " shiny=否:", sum(1 for s in sprites if not s["shiny"]))

def dl(s):
    fn="sprites/icons/"+s["slug"]+".webp"
    if os.path.exists(fn) and os.path.getsize(fn)>500: return True
    try:
        r=urllib.request.Request(BASE+s["img"],headers={"User-Agent":UA})
        data=urllib.request.urlopen(r,timeout=25,context=CTX).read()
        open(fn,"wb").write(data)
        return True
    except Exception as e:
        print("  dl fail", s["slug"], type(e).__name__)
        return False

targets=[s for s in sprites if s["shiny"]]
ok=0
with ThreadPoolExecutor(max_workers=6) as ex:
    for r in ex.map(dl, targets): ok+=r
print(f"icons downloaded: {ok}/{len(targets)}")

json.dump(sprites, open("sprites/sprites.json","w",encoding="utf-8"), ensure_ascii=False, indent=0)
print("saved sprites/sprites.json")
