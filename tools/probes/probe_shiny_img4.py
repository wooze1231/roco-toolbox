import ssl, urllib.request, re
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

def head(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA}, method="GET")
    try:
        r = urllib.request.urlopen(req, timeout=20, context=CTX)
        data = r.read(200)
        return r.status, r.headers.get("Content-Type", ""), len(data)
    except Exception as e:
        return "ERR", str(e)[:60], 0

base = "https://rocokingdomworld.org"
slug = "qilicao"
cands = [
    f"/images/spirits/{slug}.webp",
    f"/images/spirits/{slug}_shiny.webp",
    f"/images/spirits/{slug}-shiny.webp",
    f"/images/spirits/shiny/{slug}.webp",
    f"/images/spirits/{slug}_s.webp",
    f"/images/spirits/{slug}_yise.webp",
    f"/images/spirits/{slug}.png",
    f"/images/spirits/{slug}-2.webp",
    f"/images/spirits_shiny/{slug}.webp",
    f"/images/spirits/{slug}_1.webp",
    f"/img/spirits/{slug}.webp",
]
for c in cands:
    print(c, head(base + c))
