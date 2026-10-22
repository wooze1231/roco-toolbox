import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
req=urllib.request.Request("https://www.rocokingdomworld.org/",headers={"User-Agent":UA})
d=urllib.request.urlopen(req,timeout=25,context=CTX).read().decode("utf-8","ignore")
links=set(re.findall(r'href="([^"]+)"', d))
sprite=[l for l in links if any(k in l.lower() for k in ["sprite","pet","精灵","tujian","illustration","pokedex","pet"])]
for l in sorted(sprite)[:30]: print(l)
print("---含'精灵'的导航文本---")
for m in re.finditer(r'>([^<]{0,12}精灵[^<]{0,12})<', d):
    print(m.group(1))
