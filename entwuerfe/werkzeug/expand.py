import re, sys, json
# Mini-Templating fuer sc-for / sc-if / {{a.b}} (nur fuer die Kompakt-Vorschau).
def look(path, ctx):
    path = path.strip()
    if path in ('true', 'false'): return path == 'true'
    cur = ctx
    for p in path.split('.'):
        cur = cur.get(p) if isinstance(cur, dict) else None
    return cur
TAG = re.compile(r'<(sc-for|sc-if)\b([^>]*)>|</(sc-for|sc-if)>')
def render(s, ctx):
    out, i = [], 0
    while True:
        m = TAG.search(s, i)
        if not m:
            out.append(sub(s[i:], ctx)); break
        out.append(sub(s[i:m.start()], ctx))
        if m.group(3): raise ValueError('unerwartetes Ende')
        name, attrs = m.group(1), m.group(2)
        depth, j = 1, m.end()
        while depth:
            n = TAG.search(s, j)
            if n.group(1) == name: depth += 1
            elif n.group(3) == name: depth -= 1
            j = n.end()
        inner = s[m.end():n.start()]
        if name == 'sc-for':
            lst = look(re.search(r'list="\{\{(.*?)\}\}"', attrs).group(1), ctx)
            var = re.search(r'as="(\w+)"', attrs).group(1)
            for k, it in enumerate(lst): out.append(render(inner, {**ctx, var: it, '$index': k}))
        else:
            if look(re.search(r'value="\{\{(.*?)\}\}"', attrs).group(1), ctx): out.append(render(inner, ctx))
        i = j
    return ''.join(out)
def sub(s, ctx):
    def r(m):
        v = look(m.group(1), ctx)
        return '' if v is None or v is False else str(v)
    return re.sub(r'\{\{(.*?)\}\}', r, s)
src, data = sys.argv[1], json.loads(open(sys.argv[2], encoding='utf-8').read())
t = open(src, encoding='utf-8').read()
body = t.split('</helmet>', 1)[1].split('</x-dc>', 1)[0]
print(render(body, data))
