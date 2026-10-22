# -*- coding: utf-8 -*-
"""扒 BWIKI 分类:精灵 的全部精灵 + 96px 图标，输出 sprites.json + icons/*.png。
带 567 退避重试与断点续跑。"""
import ssl, urllib.request, urllib.parse, json, re, os, time, sys

CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
API="https://wiki.biligame.com/rocom/api.php"
OUT="sprites"
os.makedirs(OUT+"/icons", exist_ok=True)

def get(url, tries=6):
    for k in range(tries):
        try:
            req=urllib.request.Request(url, headers={"User-Agent":UA,"Accept-Encoding":"gzip"})
            with urllib.request.urlopen(req,timeout=40,context=CTX) as r:
                d=r.read()
            if r.headers.get("Content-Encoding","").lower().find("gzip")>=0:
                import gzip; d=gzip.decompress(d)
            return d
        except urllib.error.HTTPError as e:
            if e.code==567:
                wait=8*(k+1)
                print(f"  567 blocked, wait {wait}s (try {k+1}/{tries})", flush=True)
                time.sleep(wait)
            else:
                raise
    raise RuntimeError("blocked too long")

def api(params):
    u=API+"?"+urllib.parse.urlencode(params)
    return json.loads(get(u).decode("utf-8","ignore"))

# 1) 全部精灵页 + 图片文件（断点：members_done.json）
mf=OUT+"/members.json"
if os.path.exists(mf):
    pages=json.load(open(mf,encoding="utf-8"))
    print("reuse members:", len(pages))
else:
    pages=[]; cont=None; n=0
    while True:
        p={"action":"query","generator":"categorymembers","gcmtitle":"分类:精灵",
           "gcmlimit":"50","prop":"images","imlimit":"50","format":"json","formatversion":"2"}
        if cont: p.update(cont)
        d=api(p); pages.extend(d.get("query",{}).get("pages",[]))
        n+=1; print(f"  members page {n}, total {len(pages)}", flush=True)
        cont=d.get("continue")
        if not cont: break
        time.sleep(2.0)
    json.dump(pages, open(mf,"w",encoding="utf-8"), ensure_ascii=False)

# 2) 选图标文件：纯数字文件名优先，否则第一张 png/jpg
sprites=[]
for pg in pages:
    t=pg.get("title","")
    if "(" in t:  # 形态页如「蹦蹦种子（海神球形态）」保留，异色是按精灵算的，先都收
        pass
    imgs=[i["title"] for i in pg.get("images",[])]
    cand=[i for i in imgs if re.fullmatch(r"文件:\d{5,7}\.(png|jpg)", i)]
    if not cand:
        cand=[i for i in imgs if re.search(r"\.(png|jpg)$", i, re.I) and not re.search(r"Head|Egg|Feature|Fruit|图标|来源", i)]
    if cand:
        sprites.append({"name":t,"file":cand[0]})
print("sprites with icon file:", len(sprites), "/", len(pages))
json.dump(sprites, open(OUT+"/sprites_files.json","w",encoding="utf-8"), ensure_ascii=False)
