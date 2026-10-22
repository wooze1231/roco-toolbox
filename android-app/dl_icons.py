import urllib.request, ssl, os
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
hdr={'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36',
     'Referer':'https://rocokingdomworld.org/'}

# (name, list of candidate urls)
targets = {
    'lanjingbixi': [
        'https://patchwiki.biligame.com/images/rocom/thumb/c/c5/k8wzdzechd88qs9bxuvcnyg0pv4tzl4.png/256px-100681.png',
        'https://patchwiki.biligame.com/images/rocom/c/c5/k8wzdzechd88qs9bxuvcnyg0pv4tzl4.png',
    ],
    'moliguo': [
        'https://patchwiki.biligame.com/images/rocom/thumb/c/c6/5qe6rcdtfxe1gsrvgj3lrn033athx9p.png/256px-100668.png',
        'https://patchwiki.biligame.com/images/rocom/c/c6/5qe6rcdtfxe1gsrvgj3lrn033athx9p.png',
    ],
    'xuancaidan': [
        'https://img.71acg.net/whd~hykb/comm/hd_admin_upload_2982_d3f07a7c316e2b71dc3e5fc1871bd6da.png',
    ],
}

out_dir = 'app/src/main/res/drawable-nodpi'
os.makedirs(out_dir, exist_ok=True)

for name, urls in targets.items():
    done = False
    for u in urls:
        try:
            req = urllib.request.Request(u, headers=hdr)
            with urllib.request.urlopen(req, timeout=20, context=ctx) as r:
                data = r.read()
            if data[:4] == b'\x89PNG' or data[:3] == b'\xff\xd8\xff':
                ext = 'png' if data[:4] == b'\x89PNG' else 'jpg'
                fn = f'item_{name}.{ext}'
                with open(os.path.join(out_dir, fn), 'wb') as f:
                    f.write(data)
                print('OK', name, fn, len(data), 'from', u[:90])
                done = True
                break
            else:
                print('NOTIMG', name, u[:90], data[:8])
        except Exception as e:
            print('FAIL', name, u[:90], type(e).__name__, str(e)[:80])
    if not done:
        print('NO_ICON', name)
