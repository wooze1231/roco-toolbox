import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
req=urllib.request.Request("https://www.rocokingdomworld.org/pokedex",headers={"User-Agent":UA})
d=urllib.request.urlopen(req,timeout=30,context=CTX).read().decode("utf-8","ignore")
occ=[m.start() for m in re.finditer(r'/pokedex/dimo"', d)]
print("occurrences:", len(occ))
for o in occ:
    seg=d[o-200:o+400].replace("\n"," ")
    if "<img" in seg or "img" in seg:
        print("----@", o, "----")
        print(seg[:550])
