import re, os, glob

BASE = "C:/Users/wooze/WorkBuddy/2026-09-22-16-31-24/roco-merchant-reminder/android-app/app/src/main/java/com/wooze/rocomerchant"

# Material 库语义 attr（app 自己的 R.attr 不含，必须全限定）；colorBg 是 app 自定义，保留 R.attr.colorBg
MATERIAL_ATTRS = [
    'colorSurface', 'colorOnSurface', 'colorOnSurfaceVariant',
    'colorPrimary', 'colorPrimaryContainer', 'colorOnPrimaryContainer',
    'colorError', 'colorErrorContainer', 'colorOutline', 'colorOutlineVariant',
]

for path in glob.glob(os.path.join(BASE, '*.kt')):
    s = open(path, encoding='utf-8').read()
    orig = s
    for name in MATERIAL_ATTRS:
        # (?!android\.) 避免误伤 android.R.attr.*；\b 避免 colorOnSurface 误伤 colorOnSurfaceVariant
        s = re.sub(r'(?<!android\.)R\.attr\.%s\b' % re.escape(name),
                   'com.google.android.material.R.attr.%s' % name, s)
    if s != orig:
        open(path, 'w', encoding='utf-8').write(s)
        print('fixed', os.path.basename(path))
print('DONE')
