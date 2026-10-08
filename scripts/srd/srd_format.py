"""The block and inline format our SRD data files use, and the conversion from open5e markdown.

Shared by emit-conditions.py and emit-rules.py. A body is a vector of blocks:

  [:h level "Text" :anchor]          a heading; :anchor (or nil) is what a rule link points at
  [:p inline...]                     a paragraph
  [:ul item...] / [:ol item...]      a list; each item is a vector of inlines
  [:table {:caption "" :columns [] :rows [[]]}]   header cells are strings; body cells are
                                     vectors of inlines
  [:aside block...]                  an SRD sidebar (open5e's "> " lines)

and an inline is a string, [:b inline...], [:i inline...], or
  [:a {:kind :spell|:condition|:rule ...} inline...]   a link to a page the app has.

Text stays verbatim. Markdown markers and open5e's own markup ("srd:slug" references, "(table)"
after captions) are structure, not text, and are converted or dropped.
"""
import re

CONDITIONS = ['blinded', 'charmed', 'deafened', 'exhaustion', 'frightened', 'grappled',
              'incapacitated', 'invisible', 'paralyzed', 'petrified', 'poisoned', 'prone',
              'restrained', 'stunned', 'unconscious']
_COND = '|'.join(CONDITIONS)
# A condition word is linked only where it names the condition, never as an ordinary adjective.
CONDITION_LINK = re.compile(
    r'\b(?:(?P<w1>' + _COND + r') (?:condition|creature|target|character|wielder)'
    r'|(?:is|are|be|been|becomes|become|being|remains|remain|knocked|falls|fall|fell|lands|land|drop|while'
    r"|isn't|aren't|is not|are not|wasn't)(?: also)? (?P<w2>" + _COND + r')'
    r'|of (?P<w3>exhaustion)|(?P<w4>exhaustion) level)\b', re.I)

INLINE = re.compile(r'(\*\*[^*\n]+\*\*'                     # bold
                    r'|(?<![\w*])\*[^*\n]+\*(?![\w*])'      # *italic*
                    r'|(?<![\w_])_[^_\n]+_(?![\w_])'        # _italic_
                    r'|\bsrd:[a-z0-9-]+)')                 # open5e cross-reference


class Links:
    """What can be linked: spell names, the conditions, and rule names, with their targets."""

    def __init__(self, spells=None, rules=None):
        self.spells = {s['name'].lower(): s['key'] for s in (spells or [])}
        self.rules = rules or {}          # lower-case rule or section name -> (section, anchor)
        self.rule_titles = {}             # (section, anchor) -> the name as printed
        self.unlinked_conditions = []     # bare condition words left as text, for review

    def spell(self, text):
        k = self.spells.get(text.lower().strip())
        return {'kind': 'spell', 'key': k} if k else None


def _conditions(s, links, here=None):
    """Splits plain text into strings and condition links; a condition never links to itself."""
    out, i = [], 0
    for m in CONDITION_LINK.finditer(s):
        g = next(n for n in ('w1', 'w2', 'w3', 'w4') if m.group(n))
        word, a = m.group(g), m.start(g)
        if here == ('condition', word.lower()):
            continue
        out.append(s[i:a])
        out.append(['a', {'kind': 'condition', 'key': word.lower()}, word])
        i = a + len(word)
    out.append(s[i:])
    rest = ''.join(x for x in out if isinstance(x, str))
    for w in re.findall(r'\b(' + _COND + r')\b', rest, re.I):
        links.unlinked_conditions.append(w)
    return [x for x in out if x != '']


def _rule_refs(s, links, here):
    """Splits plain text into strings and links for quoted rule names (“Cover”)."""
    out, i = [], 0
    for m in re.finditer(r'“([^”]{2,60})”', s):
        target = links.rules.get(m.group(1).lower())
        if target and target != here:
            out.append(s[i:m.start()])
            out.append(['a', {'kind': 'rule', 'section': target[0], 'anchor': target[1]}, m.group(0)])
            i = m.end()
    out.append(s[i:])
    return [x for x in out if x != '']


def _plain(s, links, here):
    parts = []
    for x in _rule_refs(s, links, here):
        parts += _conditions(x, links, here) if isinstance(x, str) else [x]
    return parts


def srd_ref(slug, links):
    """open5e's srd:slug -> (inline, plain words) in the SRD's own wording."""
    k = links.spells.get(slug.replace('-', ' '))
    if k:
        name = slug.replace('-', ' ')
        return ['a', {'kind': 'spell', 'key': k}, ['i', name]], name
    if slug in CONDITIONS:
        return ['a', {'kind': 'condition', 'key': slug}, slug], slug
    for name, target in links.rules.items():
        if target[1] == slug or (target[1] is None and target[0] == slug):
            title = links.rule_titles[target]
            text = f'the “{title}” section'
            return ['a', {'kind': 'rule', 'section': target[0], 'anchor': target[1]}, text], text
    raise ValueError(f'srd:{slug} names nothing known')


EM_DASH = re.compile(r'(?<=\S)---(?=\S)')    # open5e's em dash; table dividers have spaces


def inlines(s, links, here=None):
    """One line of open5e markdown -> a list of inlines. open5e's "---" becomes the SRD's "—"."""
    s = EM_DASH.sub('—', s)
    out = []
    for tok in INLINE.split(s):
        if not tok:
            continue
        if tok.startswith('**') and tok.endswith('**'):
            out.append(['b'] + inlines(tok[2:-2], links, here))
        elif (tok[0] in '*_') and tok[-1] == tok[0] and len(tok) > 2:
            inner = tok[1:-1]
            target = links.spell(inner)
            out.append(['a', target, ['i', inner]] if target else ['i'] + inlines(inner, links, here))
        elif tok.startswith('srd:'):
            out.append(srd_ref(tok[4:], links)[0])
        else:
            out += _plain(tok, links, here)
    return out


def plain_source(s, links):
    """The words a line of source carries, with open5e markup resolved, for the word check."""
    if '-' in s and re.fullmatch(r'[\s|:>-]*', s):      # a table's divider row
        return []
    s = EM_DASH.sub('—', s)
    s = re.sub(r'\bsrd:([a-z0-9-]+)', lambda m: srd_ref(m.group(1), links)[1], s)
    s = re.sub(r'\s*\(table\)', '', s)
    s = re.sub(r'^\s*(?:(?:#{1,6}|>|[*-]|\d+\.)(?:\s+|$))+', '', s)
    return words(s.replace('**', ' ').replace('|', ' ').replace('*', ' ').replace('_', ' '))


def words(s):
    return re.findall(r"[^\s*_|]+", s.replace('---', ' '))


def parse(desc, links, here=None, labelled=False):
    """open5e markdown -> blocks. `labelled`: bullets open with a label ("Can’t See. ...")."""
    blocks, stack = [], []
    def target(aside):
        if aside:
            if not blocks or blocks[-1][0] != 'aside':
                blocks.append(['aside'])
            return blocks[-1]
        return None
    lines = desc.replace('\r\n', '\n').split('\n')
    i = 0
    pending_caption = None
    while i < len(lines):
        raw = lines[i].strip()            # sidebar lines sometimes start " > "
        aside = raw.startswith('>')
        line = raw[1:].strip() if aside else raw.strip()
        box = target(aside)
        dest = box if box is not None else blocks
        i += 1
        if not line:
            continue
        if line.startswith('|'):
            rows = []
            j = i - 1
            while j < len(lines) and lines[j].strip().lstrip('>').strip().startswith('|'):
                cells = [c.strip() for c in lines[j].strip().lstrip('>').strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', c) for c in cells if c):
                    rows.append([inlines(c, links, here) for c in cells])
                j += 1
            i = j
            t = {'columns': [''.join(x if isinstance(x, str) else ''.join(y for y in x[1:] if isinstance(y, str))
                                     for x in c) for c in rows[0]], 'rows': rows[1:]}
            if pending_caption:
                t['caption'] = pending_caption
                dest.pop()
            dest.append(['table', t])
            pending_caption = None
            continue
        m = re.match(r'(#{1,6})\s+(.*)', line)
        if m:
            dest.append(['h', len(m.group(1)) + 1, re.sub(r'\*\*', '', m.group(2)).strip(), None])
            pending_caption = None
            continue
        m = re.match(r'([*-]|\d+\.)\s+(.*)', line)
        if m:
            kind = 'ol' if m.group(1)[0].isdigit() else 'ul'
            body = m.group(2)
            if labelled:
                label, sep, rest = body.partition('. ')
                item = [['b', label + '.'], ' '] + inlines(rest, links, here) if sep else inlines(body, links, here)
            else:
                item = inlines(body, links, here)
            if dest and dest[-1][0] == kind:
                dest[-1].append(item)
            else:
                dest.append([kind, item])
            pending_caption = None
            continue
        cap = re.fullmatch(r'\*\*(.+?)\s*\(table\)\*\*', line)
        if cap:
            pending_caption = cap.group(1).strip()
            dest.append(['p', ['b', pending_caption]])
            continue
        dest.append(['p'] + inlines(line, links, here))
        pending_caption = None
    return blocks


def block_words(blocks):
    """The words a body carries, in order, for the word check."""
    out = []
    def inl(x):
        if isinstance(x, str):
            out.extend(words(x))
        else:
            for y in x[1:]:
                if not isinstance(y, dict):
                    inl(y)
    for b in blocks:
        k = b[0]
        if k == 'h':
            out.extend(words(b[2]))
        elif k == 'p':
            for x in b[1:]:
                inl(x)
        elif k in ('ul', 'ol'):
            for item in b[1:]:
                for x in item:
                    inl(x)
        elif k == 'table':
            t = b[1]
            if 'caption' in t:
                out.extend(words(t['caption']))
            for c in t['columns']:
                out.extend(words(c))
            for r in t['rows']:
                for c in r:
                    for x in c:
                        inl(x)
        elif k == 'aside':
            out.extend(block_words(b[1:]))
    return out


def edn(x, ind=0):
    """Python data -> EDN text. Lists whose first item is a str tag become [:tag ...] vectors."""
    pad = ' ' * ind
    if isinstance(x, str):
        return '"' + x.replace('\\', '\\\\').replace('"', '\\"') + '"'
    if isinstance(x, bool):
        return 'true' if x else 'false'
    if x is None:
        return 'nil'
    if isinstance(x, int):
        return str(x)
    if isinstance(x, float):
        return repr(x)
    if isinstance(x, Kw):
        return ':' + x.name
    if isinstance(x, dict):
        return '{' + ' '.join(f':{k} {edn(v, ind + 1)}' for k, v in x.items()) + '}'
    if isinstance(x, list):
        if x and isinstance(x[0], str) and x[0] in TAGS:
            return '[:' + x[0] + (' ' if len(x) > 1 else '') + ' '.join(edn(v, ind + 1) for v in x[1:]) + ']'
        return '[' + ' '.join(edn(v, ind + 1) for v in x) + ']'
    raise TypeError(type(x))


class Kw:
    """A keyword in the EDN output."""
    def __init__(self, name):
        self.name = name


TAGS = {'h', 'p', 'ul', 'ol', 'table', 'aside', 'b', 'i', 'a'}


def keywordize(x):
    """Turns the values that are keywords in EDN (link kinds and keys, anchors) into Kw."""
    if isinstance(x, list):
        return [keywordize(v) for v in x]
    if isinstance(x, dict):
        return {k: (Kw(v) if k in ('kind', 'key', 'section', 'anchor') and isinstance(v, str) else keywordize(v))
                for k, v in x.items()}
    return x
