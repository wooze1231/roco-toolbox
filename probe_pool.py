import urllib.request, ssl, json
CTX = ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
HDR={'Referer':'https://servicewechat.com/wx38b0bed275763e1b/91/page-frame.html',
     'User-Agent':'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 MicroMessenger/8.0.20'}
base='https://roco-toolkit-img.oss-cn-shenzhen.aliyuncs.com/'
cands=['merchant_data.json','items.json','item_data.json','merchant_items.json','goods.json',
       'item_list.json','all_items.json','merchant_pool.json','pool.json','props.json','config.json']
for c in cands:
    url=base+c
    try:
        req=urllib.request.Request(url,headers=HDR)
        with urllib.request.urlopen(req,timeout=15,context=CTX) as r:
            d=r.read()
        print('OK  ',c,len(d))
        txt=d[:400].decode('utf-8','ignore').replace('\n',' ')
        print('     ',txt[:400])
    except Exception as e:
        print('FAIL',c,type(e).__name__,str(e)[:80])
