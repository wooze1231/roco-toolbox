import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
URL="https://wiki.biligame.com/rocom/"
CHROME_HDRS={
 "Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
 "Accept-Language":"zh-CN,zh;q=0.9",
 "Upgrade-Insecure-Requests":"1",
 "Sec-Fetch-Dest":"document","Sec-Fetch-Mode":"navigate","Sec-Fetch-Site":"none","Sec-Fetch-User":"?1",
}
cases={
 "ua_only": {"User-Agent":UA},
 "ua_chrome": {"User-Agent":UA, **CHROME_HDRS},
 "ua_chrome_xrw": {"User-Agent":UA, **CHROME_HDRS, "X-Requested-With":"com.wooze.rocomerchant"},
 "ua_wv": {"User-Agent":UA.replace("Mobile Safari","wv) AppleWebKit/537.36 Version/4.0 Chrome/120.0 Mobile Safari")},
 "no_ua": {"Accept":"*/*"},
}
for name,h in cases.items():
    try:
        req=urllib.request.Request(URL, headers=h)
        with urllib.request.urlopen(req, timeout=25, context=CTX) as r:
            d=r.read().decode("utf-8","ignore")
        blocked = ("请求已被拦截" in d) or ("EdgeOne" in d) or ("访问验证" in d)
        print(f"{name:15s} status={r.status} len={len(d)} {'BLOCKED' if blocked else 'ok'}")
        if blocked:
            i=d.find("请求已被拦截"); print("     ", d[max(0,i-120):i+160].replace("\n"," ")[:250])
    except Exception as e:
        print(f"{name:15s} FAIL {type(e).__name__} {str(e)[:70]}")
