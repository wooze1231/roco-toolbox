import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
req=urllib.request.Request("https://wiki.biligame.com/rocom/", headers={"User-Agent":UA})
d=urllib.request.urlopen(req,timeout=25,context=CTX).read().decode("utf-8","ignore")
for k in ["请求已被拦截","EdgeOne","访问验证","567","安全策略","拦截"]:
    idx=[m.start() for m in re.finditer(re.escape(k), d)][:3]
    print(k, "->", idx)
    for i in idx:
        print("    ...", d[max(0,i-90):i+110].replace("\n"," ")[:200])
t=re.search(r"<title>(.*?)</title>", d, re.S)
print("TITLE:", t.group(1).strip()[:80] if t else None)
print("body head:", re.sub(r"<[^>]+>"," ", d[d.find("<body"):d.find("<body")+800]).strip()[:300])
