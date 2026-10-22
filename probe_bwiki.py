import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UAS = {
  "mobile_chrome": "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
  "android_webview": "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36",
  "desktop_chrome": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
  "iphone_safari": "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1",
}
urls = ["https://wiki.biligame.com/rocom/",
        "https://wiki.biligame.com/rocom/index.php?search=%E9%BB%84%E7%9F%B3%E6%A6%B4%E7%9F%B3&title=%E7%89%B9%E6%AE%8A:%E6%90%9C%E7%B4%A2&fulltext=1",
        "https://wiki.biligame.com/rocom/api.php?action=query&meta=siteinfo&format=json"]
for name, ua in UAS.items():
    print("=== UA:", name)
    for u in urls:
        try:
            req = urllib.request.Request(u, headers={"User-Agent": ua, "Accept-Language":"zh-CN,zh;q=0.9"})
            with urllib.request.urlopen(req, timeout=20, context=CTX) as r:
                d = r.read().decode("utf-8","ignore")
            blocked = any(k in d for k in ["请求已被拦截","EdgeOne","访问验证","567","安全策略"])
            print("   ", r.status, len(d), "BLOCKED" if blocked else "ok", u[:70])
            if blocked:
                m = re.search(r'请求[^<]{0,60}|EdgeOne[^<]{0,40}', d)
                print("        ->", m.group(0)[:80] if m else "")
        except Exception as e:
            print("    FAIL", type(e).__name__, str(e)[:60], u[:60])
