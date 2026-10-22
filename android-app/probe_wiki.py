import urllib.request, ssl, re
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
hdr={'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36'}
for url in ['https://rocodex.org/items', 'https://rocokingdomworld.org/zh-hant/']:
    try:
        req=urllib.request.Request(url, headers=hdr)
        with urllib.request.urlopen(req, timeout=20, context=ctx) as r:
            html=r.read().decode('utf-8','ignore')
        print('===', url, len(html), '===')
        imgs = re.findall(r'https?://[^\s"\'<>)]+?\.(?:png|webp|jpg|jpeg)', html, re.I)
        print('imgs found:', len(imgs))
        for u in imgs[:20]:
            print('  ', u[:140])
        for kw in ['蓝晶碧玺','魔力果','炫彩蛋','item/']:
            if kw in html:
                i = html.find(kw)
                print(' KW', kw, '@', i, html[i-50:i+70].replace('\n',' '))
    except Exception as e:
        print('FAIL', url, type(e).__name__, str(e)[:120])
