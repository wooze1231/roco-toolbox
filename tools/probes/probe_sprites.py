import ssl, urllib.request, urllib.parse, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
API="https://wiki.biligame.com/rocom/api.php"
def api(params):
    u=API+"?"+urllib.parse.urlencode(params)
    req=urllib.request.Request(u, headers={"User-Agent":UA})
    return json.loads(urllib.request.urlopen(req,timeout=30,context=CTX).read().decode("utf-8","ignore"))
d=api({"action":"query","generator":"categorymembers","gcmtitle":"分类:精灵",
       "gcmlimit":"50","prop":"pageimages","piprop":"thumbnail","pithumbsize":"96",
       "format":"json","formatversion":"2"})
pages=d.get("query",{}).get("pages",[])
print("count:", len(pages))
for p in pages[:12]:
    th=(p.get("thumbnail") or {}).get("source")
    print("  -", p.get("title"), "|", (th or "NO-THUMB")[:100])
# 继续翻页
cont=d.get("continue")
print("continue:", cont)
