import urllib.request, ssl
CTX = ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA='Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36'
urls=[
 'https://wiki.biligame.com/rocom/',
 'https://wiki.biligame.com/rocom/index.php?search=%E9%BB%84%E7%9F%B3%E6%A6%B4%E7%9F%B3&title=%E7%89%B9%E6%AE%8A:%E6%90%9C%E7%B4%A2&fulltext=1',
 'https://rocokingdom.huijiwiki.com/wiki/',
 'https://www.gamekee.com/rocom/',
]
for u in urls:
    try:
        req=urllib.request.Request(u, headers={'User-Agent':UA,'Accept-Language':'zh-CN,zh;q=0.9'})
        with urllib.request.urlopen(req, timeout=20, context=CTX) as r:
            d=r.read()
        print('OK  ', r.status, len(d), u)
        t=d.decode('utf-8','ignore')
        print('     title:', t[t.find('<title'):t.find('</title>')+8][:120].replace('\n',' '))
    except Exception as e:
        print('FAIL', type(e).__name__, str(e)[:80], u)
