"""Edit records in open5e's fixture JSON without re-serialising the file.

open5e's fixtures come in more than one layout: srd-2014 files put each record at column 0 with
`fields` first; srd-2024 files indent records two spaces and mix key orders. Rewriting a whole file
with json.dump changes thousands of lines, so every edit here touches only the lines it means to.
Used by agents and scripts that change open5e data (docs/kb/open5e-upstream-notes.md).
"""
import json, re


def _span(text, pk):
    """(start, end) of the record with this pk, its braces included.

    Args: text, the file's text; pk, the record's pk (int or str).
    Returns: character offsets of the record's opening and closing brace lines.
    """
    m = re.search(r'"pk": %s\s*[,\n]' % re.escape(json.dumps(pk)), text)
    if not m:
        raise KeyError(f'no record with pk {pk!r}')
    i = m.start()
    s = max(text.rfind('\n{\n', 0, i), text.rfind('\n  {\n', 0, i)) + 1
    indent = text[s:text.index('{', s)]
    e = text.index('\n' + indent + '}', i) + len(indent) + 2
    return s, e


def set_field(path, pk, field, value):
    """Sets one field of one record in place.

    Args: path, the fixture file; pk, the record's pk; field, a key under "fields"; value, any JSON
    value. Returns nothing; raises KeyError if the record or field is missing.
    """
    text = open(path, encoding='utf-8').read()
    s, e = _span(text, pk)
    block, n = re.subn(r'("%s": )(null|true|false|"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?)' % re.escape(field),
                       lambda m: m.group(1) + json.dumps(value, ensure_ascii=False), text[s:e], count=1)
    if not n:
        raise KeyError(f'record {pk!r} has no field {field!r}')
    open(path, 'w', encoding='utf-8').write(text[:s] + block + text[e:])
    json.loads(open(path, encoding='utf-8').read())


def drop(path, pk):
    """Removes one record and its separating comma.

    Args: path, the fixture file; pk, the record's pk. Returns nothing.
    """
    text = open(path, encoding='utf-8').read()
    s, e = _span(text, pk)
    if text[e:e + 1] == ',':
        text = text[:s] + text[e + 2:]
    else:
        cut = text.rindex(',', 0, s)
        text = text[:cut] + text[cut + 1:s - 1] + text[e:]
    open(path, 'w', encoding='utf-8').write(text)
    json.loads(text)


def append(path, record):
    """Appends a record at the end of the file, in the file's own layout and key order.

    Args: path, the fixture file; record, a dict {fields, model, pk}. The fields are reordered to
    match the file's first record. Returns nothing.
    """
    text = open(path, encoding='utf-8').read()
    first = json.loads(text)[0]
    record = {k: record[k] for k in first if k in record}
    record['fields'] = {k: record['fields'].get(k) for k in first['fields']}
    indent = '  ' if re.search(r'^  \{$', text, re.M) else ''
    block = '\n'.join(indent + line for line in json.dumps(record, indent=2, ensure_ascii=False).split('\n'))
    body = text.rstrip()
    assert body.endswith(']'), path
    trailing = text[len(body):]
    text = body[:-1].rstrip() + ',\n' + block + '\n]' + trailing
    open(path, 'w', encoding='utf-8').write(text)
    json.loads(text)


def next_pk(clone, model_file):
    """The next free integer pk for a model, across every document in the clone.

    Args: clone, the open5e checkout; model_file, e.g. "SpellCastingOption.json".
    Returns: max existing pk + 1.
    """
    import glob
    return 1 + max(r['pk'] for p in glob.glob(f'{clone}/data/v2/**/{model_file}', recursive=True)
                   for r in json.load(open(p, encoding='utf-8')))
