import urllib.request, ssl, json
CTX = ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
HDR={'Referer':'https://servicewechat.com/wx38b0bed275763e1b/91/page-frame.html',
     'User-Agent':'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 MicroMessenger/8.0.20'}
base='https://roco-toolkit-img.oss-cn-shenzhen.aliyuncs.com/'
cands=['activity.json','activity_data.json','activities.json','calendar.json','notice.json',
       'events.json','event.json','task.json','daily.json','weekly.json','news.json','version.json',
       'home.json','index.json','data.json','all.json','merchant.json','signin.json','map.json',
       'activity_list.json','activity_list_data.json','banner.json','announce.json']
for c in cands:
    try:
        req=urllib.request.Request(base+c,headers=HDR)
        with urllib.request.urlopen(req,timeout=12,context=CTX) as r: d=r.read()
        print('OK  ',c,len(d))
        print('     ',d[:300].decode('utf-8','ignore').replace('\n',' ')[:300])
    except Exception as e:
        pass
print('=== 现有 merchant_data.json 结构 ===')
try:
    req=urllib.request.Request(base+'merchant_data.json',headers=HDR)
    with urllib.request.urlopen(req,timeout=15,context=CTX) as r: d=r.read()
    print(d[:1500].decode('utf-8','ignore'))
except Exception as e:
    print('FAIL', e)
