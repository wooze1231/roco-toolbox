import ssl, urllib.request, re, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(u, ref=None):
    h={"User-Agent":UA}
    if ref: h["Referer"]=ref
    req=urllib.request.Request(u,headers=h)
    with urllib.request.urlopen(req,timeout=25,context=CTX) as r: return r.read()
tests = {
 "api.php(冷却?)": "https://wiki.biligame.com/rocom/api.php?action=query&meta=siteinfo&format=json",
 "patchwiki图片": "https://patchwiki.biligame.com/images/rocom/thumb/f/f3/c5x5f0lgk33ly801m8w1n9aod8ny4sh.png/96px-100683.png",
 "rocokingdomworld精灵图鉴": "https://www.rocokingdomworld.org/sprite",
 "rocokingdomworld首页": "https://www.rocokingdomworld.org/",
}
for k,u in tests.items():
    try:
        d=get(u)
        print(f"OK   {k:28s} {len(d)} bytes")
    except Exception as e:
        print(f"FAIL {k:28s} {type(e).__name__} {str(e)[:60]}")
