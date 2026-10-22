import ssl, urllib.request, urllib.parse, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
API="https://wiki.biligame.com/rocom/api.php"
def api(params):
    u=API+"?"+urllib.parse.urlencode(params)
    req=urllib.request.Request(u, headers={"User-Agent":UA})
    return json.loads(urllib.request.urlopen(req,timeout=25,context=CTX).read().decode("utf-8","ignore"))
# 1) 全部分类
d=api({"action":"query","list":"allcategories","aclimit":"50","format":"json","formatversion":"2"})
print("CATEGORIES:", [c["category"] for c in d.get("query",{}).get("allcategories",[])][:50])
d2=api({"action":"query","list":"allcategories","acfrom":"精","aclimit":"30","format":"json","formatversion":"2"})
print("CATEGORIES(acfrom=精):", [c["category"] for c in d2.get("query",{}).get("allcategories",[])][:30])
