import ssl, urllib.request, urllib.parse, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
API="https://wiki.biligame.com/rocom/api.php"
def api(params):
    u=API+"?"+urllib.parse.urlencode(params)
    req=urllib.request.Request(u, headers={"User-Agent":UA})
    return json.loads(urllib.request.urlopen(req,timeout=30,context=CTX).read().decode("utf-8","ignore"))
d=api({"action":"query","titles":"波波拉|阿米亚特|黄石榴石","prop":"images","imlimit":"20","format":"json","formatversion":"2"})
for p in d.get("query",{}).get("pages",[]):
    imgs=[i["title"] for i in p.get("images",[])]
    print(p.get("title"),"->", imgs[:8])
# 取第一个文件的缩略 URL
files=[i["title"] for i in d.get("query",{}).get("pages",[])[0].get("images",[])][:3]
if files:
    d2=api({"action":"query","titles":"|".join(files),"prop":"imageinfo",
            "iiprop":"url","iiurlwidth":"96","format":"json","formatversion":"2"})
    for p in d2.get("query",{}).get("pages",[]):
        ii=p.get("imageinfo")
        print(p.get("title"),"->", (ii[0].get("thumburl") if ii else "NO")[:110])
