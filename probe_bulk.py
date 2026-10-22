import ssl, urllib.request, urllib.parse, json, re, time
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
API="https://wiki.biligame.com/rocom/api.php"
def api(params):
    u=API+"?"+urllib.parse.urlencode(params)
    req=urllib.request.Request(u, headers={"User-Agent":UA})
    try:
        return json.loads(urllib.request.urlopen(req,timeout=40,context=CTX).read().decode("utf-8","ignore"))
    except Exception as e:
        print("API FAIL:", type(e).__name__, str(e)[:200]); raise SystemExit
all_pages=[]
cont=None
while True:
    p={"action":"query","generator":"categorymembers","gcmtitle":"分类:精灵",
       "gcmlimit":"50","prop":"images","imlimit":"50","format":"json","formatversion":"2"}
    if cont: p.update(cont)
    d=api(p)
    all_pages.extend(d.get("query",{}).get("pages",[]))
    cont=d.get("continue")
    if not cont: break
    time.sleep(0.3)
print("total sprite pages:", len(all_pages))
num=0; empty=[]
for pg in all_pages:
    imgs=[i["title"] for i in pg.get("images",[])]
    cand=[i for i in imgs if re.fullmatch(r"文件:\d{5,7}\.png", i)]
    if cand: num+=1
    else: empty.append(pg.get("title"))
print(f"有数字图标: {num}/{len(all_pages)}")
print("无图标样例:", empty[:12])
