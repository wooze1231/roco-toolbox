import re, ssl, urllib.request
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(u):
    req=urllib.request.Request(u,headers={'User-Agent':UA})
    with urllib.request.urlopen(req,timeout=20,context=CTX) as r: return r.read().decode('utf-8','ignore')
h=get('https://rocom.qq.com/main/index.html')
for m in re.finditer(r'fillnews|fillNews|target', h):
    s=max(0,m.start()-160); print('...', h[s:m.start()+200].replace('\n',' ')[:340]); print('---')
    if m.start()>len(h)-100: break
