import ssl, urllib.request, urllib.parse, json, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
API="https://wiki.biligame.com/rocom/api.php"
def api(params):
    u=API+"?"+urllib.parse.urlencode(params)
    req=urllib.request.Request(u, headers={"User-Agent":UA})
    return json.loads(urllib.request.urlopen(req,timeout=25,context=CTX).read().decode("utf-8","ignore"))

# 1) 搜索
d=api({"action":"query","list":"search","srsearch":"黄石榴石","srlimit":5,"format":"json","formatversion":"2"})
print("SEARCH:")
for s in d.get("query",{}).get("search",[])[:5]: print("   -", s["title"])
# 2) 解析页面
t=d.get("query",{}).get("search",[{}])[0].get("title")
if t:
    p=api({"action":"parse","page":t,"prop":"text|displaytitle","format":"json","formatversion":"2"})
    html=p.get("parse",{}).get("text","")
    print("PARSE", t, "len", len(html), "title", p.get("parse",{}).get("displaytitle"))
    imgs=re.findall(r'src="([^"]+)"', html)[:6]
    for i in imgs: print("    img:", i[:110])
    print("    纯文本片段:", re.sub(r"<[^>]+>","",html)[:160].replace("\n"," "))
