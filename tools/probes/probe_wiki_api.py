import urllib.request, ssl, json, re, urllib.parse
CTX = ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA='Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36'
def get(u):
    req=urllib.request.Request(u,headers={'User-Agent':UA})
    with urllib.request.urlopen(req,timeout=20,context=CTX) as r: return r.read()
# 1) MediaWiki API 搜索
for q in ['活动','限时活动','活动一览','版本活动']:
    u='https://wiki.biligame.com/rocom/api.php?action=query&list=search&srsearch='+urllib.parse.quote(q)+'&srlimit=10&format=json'
    try:
        d=json.loads(get(u).decode('utf-8','ignore'))
        print('SEARCH',q)
        for s in d.get('query',{}).get('search',[])[:8]:
            print('   -', s.get('title'))
    except Exception as e:
        print('FAIL api',q,type(e).__name__,str(e)[:80])
