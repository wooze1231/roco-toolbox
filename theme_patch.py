import re, os, glob

BASE = "C:/Users/wooze/WorkBuddy/2026-09-22-16-31-24/roco-merchant-reminder/android-app/app/src/main"
PKG = os.path.join(BASE, "java/com/wooze/rocomerchant")

# 仅框架色 -> 语义 attr（gold 系图鉴标记 / red / item 渐变 / Color.WHITE / ripple 保留具体色）
MAPPING = {
    'page_bg': 'colorBg', 'card_bg': 'colorSurface', 'panel_bg': 'colorSurface', 'row_bg': 'colorSurface',
    'text_primary': 'colorOnSurface', 'text_secondary': 'colorOnSurfaceVariant', 'text_sub': 'colorOnSurfaceVariant',
    'text_soft': 'colorOnSurfaceVariant', 'text_danger': 'colorError', 'divider': 'colorOutline',
    'stroke_soft': 'colorOutline', 'line_soft': 'colorOutlineVariant', 'arrow_soft': 'colorOnSurfaceVariant',
    'row_soft_bg': 'colorSurfaceVariant', 'row_sel_bg': 'colorPrimaryContainer', 'row_danger_bg': 'colorErrorContainer',
    'chip_bg': 'colorSurfaceVariant', 'input_bg': 'colorSurfaceVariant', 'text_accent': 'colorPrimary',
    'pill_yellow': 'colorPrimaryContainer', 'pill_yellow_text': 'colorOnPrimaryContainer'
}

ACTIVITIES = ['MainActivity.kt', 'SettingsActivity.kt', 'ShinyActivity.kt', 'ShinySettingsActivity.kt',
              'WikiActivity.kt', 'HistoryActivity.kt', 'ItemEditActivity.kt', 'SubscribeActivity.kt']
UI_KT = [os.path.join(PKG, f) for f in ACTIVITIES] + [os.path.join(PKG, 'UiDialogs.kt')]

for path in UI_KT:
    s = open(path, encoding='utf-8').read()
    for name, attr in MAPPING.items():
        # ContextCompat.getColor(ctx, R.color.NAME) -> ctx.themed(R.attr.ATTR)
        pat = re.compile(r'ContextCompat\.getColor\(([^,]*?),\s*R\.color\.%s\)' % name, re.S)
        s = pat.sub(lambda m, a=attr: '%s.themed(R.attr.%s)' % (m.group(1).strip(), a), s)
        # color(R.color.NAME) -> themed(R.attr.ATTR)  (SettingsActivity helper)
        s = s.replace('color(R.color.%s)' % name, 'themed(R.attr.%s)' % attr)
        # col(c, R.color.NAME) -> c.themed(R.attr.ATTR)  (UiDialogs helper)
        s = s.replace('col(c, R.color.%s)' % name, 'c.themed(R.attr.%s)' % attr)
    open(path, 'w', encoding='utf-8').write(s)
    print('patched', os.path.basename(path))

# XML layouts
for xml in glob.glob(os.path.join(BASE, 'res/layout', '*.xml')):
    s = open(xml, encoding='utf-8').read()
    orig = s
    for name, attr in MAPPING.items():
        s = s.replace('@color/%s' % name, '?attr/%s' % attr)
    if s != orig:
        open(xml, 'w', encoding='utf-8').write(s)
        print('xml   ', os.path.basename(xml))

# 接入 Activity：super.onCreate 前插入 ThemeBridge.apply(this)
for path in [os.path.join(PKG, f) for f in ACTIVITIES]:
    s = open(path, encoding='utf-8').read()
    if 'ThemeBridge.apply(this)' not in s and 'super.onCreate(savedInstanceState)' in s:
        s = s.replace('super.onCreate(savedInstanceState)',
                      'ThemeBridge.apply(this)\n        super.onCreate(savedInstanceState)', 1)
        open(path, 'w', encoding='utf-8').write(s)
        print('applied', os.path.basename(path))
print('DONE')
