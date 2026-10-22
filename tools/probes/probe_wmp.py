import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(u,ref='https://rocom.qq.com/'):
    req=urllib.request.Request(u,headers={'User-Agent':UA,'Referer':ref})
    with urllib.request.urlopen(req,timeout=20,context=CTX) as r: return r.read().decode('utf-8','ignore')
cands=['https://gicp.qq.com/wmp/data/js/v3/WMP_135126.js',
       'https://gicp.qq.com/wmp/data/js/v3/WMP_467.js',
       'https://gicp.qq.com/wmp/data/js/v3/467/135126.js',
       'https://gicp.qq.com/wmp/data/js/v3/WMP_135110.js',
       'https://gicp.qq.com/wmp/data/js/v3/WMP_135111.js']
for u in cands:
    try:
        d=get(u)
        print('OK ',u,len(d))
        print('   ', d[:400].replace('\n',' ')[:400])
    except Exception as e:
        print('FAIL',u,type(e).__name__,str(e)[:50])
