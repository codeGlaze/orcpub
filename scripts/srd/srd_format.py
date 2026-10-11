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

Markdown is parsed by markdown-it-py (CommonMark with tables), set for open5e's habits: a single
line break starts a new paragraph, and setext headings, indented code, rules and HTML are off,
because open5e text never means them. Entry scripts declare the dependency for `uv run`.
"""
import re

from markdown_it import MarkdownIt

_MD = (MarkdownIt('commonmark', {'html': False}).enable('table')
       .disable(['lheading', 'code', 'hr', 'html_block', 'html_inline']))

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

FOLLOW_ON = re.compile(r'(,? (?:and|or) |, )(' + _COND + r')\b', re.I)

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
        if a < i:          # already linked as a follow-on of the previous match
            continue
        out.append(s[i:a])
        out.append(['a', {'kind': 'condition', 'key': word.lower()}, word])
        i = a + len(word)
        # a condition continuing this one: "is blinded and deafened", "charmed, frightened, or poisoned"
        while True:
            f = FOLLOW_ON.match(s, i)
            if not f or here == ('condition', f.group(2).lower()):
                break
            out.append(f.group(1))
            out.append(['a', {'kind': 'condition', 'key': f.group(2).lower()}, f.group(2)])
            i = f.end()
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


SRD_REF = re.compile(r'\bsrd:[a-z0-9-]+')


def _text(t, links, here):
    """A run of plain text -> inlines: open5e's srd: references, em dashes, rule and condition links."""
    t = EM_DASH.sub('—', t)
    out, i = [], 0
    for m in SRD_REF.finditer(t):
        out += _plain(t[i:m.start()], links, here)
        out.append(srd_ref(m.group(0)[4:], links)[0])
        i = m.end()
    return out + _plain(t[i:], links, here)


BREAK = object()      # a line break inside an inline run; splits open5e paragraphs


def _inline(children, links, here):
    """markdown-it inline tokens -> inlines, with BREAK where a line break fell."""
    root = []
    stack = [root]
    for tok in children:
        if tok.type == 'text' or tok.type == 'code_inline':
            stack[-1].extend(_text(tok.content, links, here))
        elif tok.type in ('softbreak', 'hardbreak'):
            stack[-1].append(BREAK)
        elif tok.type in ('strong_open', 'em_open'):
            node = ['b' if tok.type == 'strong_open' else 'i']
            stack[-1].append(node)
            stack.append(node)
        elif tok.type in ('strong_close', 'em_close'):
            node = stack.pop()
            if node[0] == 'i':                     # an italic spell name is a spell link
                inner = ''.join(x for x in node[1:] if isinstance(x, str))
                target = links.spell(inner) if inner and all(isinstance(x, str) for x in node[1:]) else None
                if target:
                    stack[-1][-1] = ['a', target, ['i', inner]]
    return root


def _split_breaks(xs):
    """Inlines with BREAK markers -> a list of inline runs, split at each break."""
    runs, cur = [], []
    for x in xs:
        if x is BREAK:
            runs.append(cur)
            cur = []
        else:
            cur.append(x)
    runs.append(cur)
    return [r for r in runs if any((y.strip() if isinstance(y, str) else True) for y in r)]


def _trim(run):
    """An inline run without leading or trailing whitespace."""
    run = list(run)
    if run and isinstance(run[0], str):
        run[0] = run[0].lstrip()
    if run and isinstance(run[-1], str):
        run[-1] = run[-1].rstrip()
    return [x for x in run if x != '']


def _plain_text(xs):
    """The visible text of inlines, markup dropped (for headings and table header cells)."""
    out = []
    for x in xs:
        if isinstance(x, str):
            out.append(x)
        elif x is not BREAK:
            out.append(_plain_text([y for y in x[1:] if not isinstance(y, dict)]))
    return ''.join(out)


def plain_source(s, links):
    """The words a line of source carries, with open5e markup resolved, for the word check."""
    if '-' in s and re.fullmatch(r'[\s|:>-]*', s):      # a table's divider row
        return []
    s = EM_DASH.sub('—', s)
    s = re.sub(r'\\([!-/:-@[-`{-~])', r'\1', s)       # markdown backslash escapes
    s = re.sub(r'\bsrd:([a-z0-9-]+)', lambda m: srd_ref(m.group(1), links)[1], s)
    s = re.sub(r'\s*\(table\)', '', s)
    s = re.sub(r'^\s*(?:(?:#{1,6}|>|[*-]|\d+\.)(?:\s+|$))+', '', s)
    return words(s.replace('**', ' ').replace('|', ' ').replace('*', ' ').replace('_', ' '))


def words(s):
    return re.findall(r"[^\s*_|]+", s.replace('---', ' '))


def _label(item):
    """A labelled bullet ("Can’t See. You can’t see...") -> the label in bold, then the text."""
    if item and isinstance(item[0], str):
        label, sep, rest = item[0].partition('. ')
        if sep:
            return [['b', label + '.'], ' ', *([rest] if rest else []), *item[1:]]
    return item


def _blocks(tokens, i, stop, links, here, labelled):
    """Block tokens from `i` up to the token of type `stop` -> (blocks, index after it)."""
    out = []
    while i < len(tokens) and tokens[i].type != stop:
        t = tokens[i]
        if t.type == 'paragraph_open':
            for run in _split_breaks(_inline(tokens[i + 1].children, links, here)):
                out.append(['p'] + _trim(run))
            i += 3
        elif t.type == 'heading_open':
            text = _plain_text(_inline(tokens[i + 1].children, links, here)).strip()
            out.append(['h', int(t.tag[1]) + 1, text, None])
            i += 3
        elif t.type in ('bullet_list_open', 'ordered_list_open'):
            kind = 'ul' if t.type == 'bullet_list_open' else 'ol'
            close = t.type.replace('_open', '_close')
            items, i = [], i + 1
            while tokens[i].type != close:          # list_item_open ... list_item_close
                inner, i = _blocks(tokens, i + 1, 'list_item_close', links, here, labelled)
                item = []
                for b in inner:                      # an item's paragraphs read as one line
                    if b[0] == 'p':
                        item += ([' '] if item else []) + b[1:]
                items.append(_label(item) if labelled else item)
            out.append([kind] + items)
            i += 1
        elif t.type == 'blockquote_open':
            inner, i = _blocks(tokens, i + 1, 'blockquote_close', links, here, labelled)
            if out and out[-1][0] == 'aside':        # quoted paragraphs split by a blank line: one sidebar
                out[-1] += inner
            else:
                out.append(['aside'] + inner)
        elif t.type == 'table_open':
            columns, rows, in_head, i = [], [], True, i + 1
            while tokens[i].type != 'table_close':
                if tokens[i].type == 'inline':
                    cell = [x for x in _inline(tokens[i].children, links, here) if x is not BREAK]
                    if in_head:
                        columns.append(_plain_text(cell).strip())
                    else:
                        rows[-1].append(_trim(cell))
                elif tokens[i].type == 'thead_open':
                    in_head = True
                elif tokens[i].type == 'tbody_open':
                    in_head = False
                elif tokens[i].type == 'tr_open' and not in_head:
                    rows.append([])
                i += 1
            table = {'columns': columns, 'rows': rows}
            caption = out[-1] if out else None      # a "**Name (table)**" line just before it
            if caption and caption[0] == 'p' and len(caption) == 2 and isinstance(caption[1], list) \
                    and caption[1][0] == 'b':
                table['caption'] = _plain_text(caption[1][1:]).strip()
                out.pop()
            out.append(['table', table])
            i += 1
        else:
            i += 1
    return out, i + 1


def _drop_table_markers(blocks):
    """open5e writes "(table)" after a caption; it is a marker, not text."""
    for b in blocks:
        if b[0] == 'table' and 'caption' in b[1]:
            b[1]['caption'] = re.sub(r'\s*\(table\)$', '', b[1]['caption'])
        elif b[0] == 'p' and len(b) == 2 and isinstance(b[1], list) and b[1][0] == 'b' and \
                isinstance(b[1][-1], str) and b[1][-1].endswith('(table)'):
            b[1][-1] = re.sub(r'\s*\(table\)$', '', b[1][-1])
        elif b[0] == 'aside':
            _drop_table_markers(b[1:])
    return blocks


CONTAINER_LINE = re.compile(r'\s*(?:[*-]\s|\d+\.\s|>|\|)')


def _paragraph_breaks(text):
    """open5e source with a blank line wherever a list, quote or table line runs straight into
    plain text. open5e means a new paragraph there; CommonMark would continue the item."""
    out, prev = [], ''
    for line in text.split('\n'):
        if line.strip() and prev.strip() and CONTAINER_LINE.match(prev) and not CONTAINER_LINE.match(line) \
                and not line.startswith((' ', '\t')):
            out.append('')
        out.append(line)
        prev = line
    return '\n'.join(out)


def parse(desc, links, here=None, labelled=False):
    """open5e markdown -> blocks. `labelled`: bullets open with a label ("Can’t See. ...")."""
    tokens = _MD.parse(_paragraph_breaks((desc or '').replace('\r\n', '\n').replace('\r', '\n')))
    blocks, _ = _blocks(tokens, 0, None, links, here, labelled)
    return _drop_table_markers(blocks)


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
