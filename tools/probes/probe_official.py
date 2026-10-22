import urllib.request, ssl, re
CTX = ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36'
def get(u, hdr=None):
    h={'User-Agent':UA}; h.update(hdr or {})
    req=urllib.request.Request(u,headers=h)
    with urllib.request.urlopen(req,timeout=20,context=CTX) as r: return r.read()
for u in ['https://rocom.qq.com/','https://rocom.qq.com/main/index.html']:
    try:
        d=get(u).decode('utf-8','ignore')
        print('OK',u,len(d))
        # 找接口/活动相关
        urls=set(re.findall(r'https?://[a-zA-Z0-9./_?=&%-]{10,120}', d))
        key=[x for x in urls if any(k in x.lower() for k in ['api','act','activity','event','news','notice','announce'])]
        for x in sorted(key)[:25]: print('   API?', x)
        hits=re.findall(r'.{40}活动.{40}', d)
        print('   活动命中', len(hits))
        for h in hits[:6]: print('     ...', h.replace('\n',' ')[:110])
    except Exception as e:
        print('FAIL',u,type(e).__name__,str(e)[:70])
