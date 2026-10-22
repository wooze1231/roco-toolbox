import ssl, urllib.request, re, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":UA})
    return urllib.request.urlopen(req,timeout=30,context=CTX).read().decode("utf-8","ignore")

mapping=json.load(open("shiny_name_map.json",encoding="utf-8"))
for kw in ["脑丸","芽眼","叮叮","魇","卵"]:
    print(kw,"->",[(n,u) for n,u in mapping.items() if kw in n][:6])

print("===== 详情页结构 youling =====")
d=get("https://rocokingdomworld.org/zh/pokedex/youling")
# 找异色相关
for m in re.finditer(r'data-shiny="([^"]*)"',d):
    print("data-shiny:",m.group(1))
# 所有图片及上下文
for m in re.finditer(r'<img[^>]+>',d):
    tag=m.group(0)
    ctx=d[max(0,m.start()-120):m.start()]
    if 'shiny' in ctx.lower() or '异色' in ctx or 'yise' in ctx.lower():
        print("CTX-shiny:",ctx[-100:].replace("\n"," "),"||",tag[:200])
# 找 "异色" 关键字附近
for m in re.finditer(r'异色',d):
    seg=d[m.start()-200:m.start()+400].replace("\n"," ")
    print("----",seg[:500])
    break
# 收集 /img/ 图
imgs=re.findall(r'src="(/img/[^"]+)"',d)
print("imgs:",len(set(imgs)))
