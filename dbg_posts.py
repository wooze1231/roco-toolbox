import re, ssl, urllib.request
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(u):
    req=urllib.request.Request(u,headers={'User-Agent':UA})
    with urllib.request.urlopen(req,timeout=25,context=CTX) as r: return r.read().decode('utf-8','ignore')
h=get('https://pd.qq.com/g/roco135790')
ids=[]
for i in re.findall(r'/g/roco135790/post/([A-Za-z0-9_\-]+)', h):
    if i not in ids: ids.append(i)
print('ids:',len(ids))
for i in ids[:12]:
    u='https://pd.qq.com/g/roco135790/post/'+i
    try:
        p=get(u)
        t=re.search(r'<title>(.*?)</title>',p,re.S)
        title=re.sub(r'<[^>]+>','',t.group(1)).strip() if t else '(no title)'
        print(' ',i[:28],'| len',len(p),'| 活动时间x',p.count('活动时间'),'|',title[:60])
    except Exception as e:
        print(' ',i[:28],'FAIL',type(e).__name__)
