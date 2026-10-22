import json, re, shutil, os

CAT=json.load(open("shiny_catalog.json",encoding="utf-8"))
for _i,_e in enumerate(CAT): _e["no"]=_i+1
by={}
for e in CAT:
    by.setdefault(e["n"], e)
    by.setdefault(e["s"], e)

# S4 赛季异色图鉴（玩家习惯叫法 -> 罗可图鉴正式名）
ALIAS={
 "拿脑丸":"章脑丸","芽眼魇":"芽眼魔","叮叮卵":"叮叮卯",
 "刺轮砣（上弦）":"刺轮砣（上弦的样子）","刺轮砣（下弦）":"刺轮砣（下弦的样子）",
}
SEASON="S4"
GROUPS=[
 ("赤月奇遇精灵",["拿脑丸","量风碗","幽铃","未完虫","觅觅蝠","逗逗","刺轮砣（上弦）","刺轮砣（下弦）"]),
 ("金月奇遇精灵",["小浣蛋","星星眼","玳龟","布灵","小黑猫","瑰眼仔","芽眼魇","小灵菇","叮叮卵"]),
 ("S4赛季「月涌」通行证",["月使鹭纳","圣凯布米龙"]),
]
OWNED={"拿脑丸","量风碗","小浣蛋","未完虫","小黑猫","芽眼魇","小灵菇"}  # 图中划线的

out={"version":1,"seasons":[{"name":SEASON,"groups":[]}]}
miss=[]
for gname,names in GROUPS:
    items=[]
    for nm in names:
        real=ALIAS.get(nm,nm)
        e=by.get(real)
        if not e:
            miss.append((nm,real)); e={"n":nm,"s":"","i":"","no":0}
        items.append({"n":nm,"no":e.get("no") or 0,"s":e.get("s",""),"i":e.get("i",""),"own":nm in OWNED})
    out["seasons"][0]["groups"].append({"name":gname,"items":items})

print("未命中:",miss)
print(json.dumps(out,ensure_ascii=False)[:400])

os.makedirs("android-app/app/src/main/assets",exist_ok=True)
json.dump(out,open("shiny_default.json","w",encoding="utf-8"),ensure_ascii=False)
# 内置库：补齐序号（列表顺序）
CAT2=json.load(open("shiny_catalog.json",encoding="utf-8"))
for i,e in enumerate(CAT2): e["no"]=i+1
json.dump(CAT2,open("android-app/app/src/main/assets/shiny_catalog.json","w",encoding="utf-8"),ensure_ascii=False)
print("catalog ->",len(CAT2),"bytes",os.path.getsize("android-app/app/src/main/assets/shiny_catalog.json"))
