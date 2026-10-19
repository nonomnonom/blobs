"""Build immutable native libraries with exact text spans and weighted search."""
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile

from .records import documents, LIMITS
from .names import build_names


def passage_spans(body: str, title: str, limit: int = 1400):
    headings = list(re.finditer(r'^\s*(={2,6})\s*(.+?)\s*\1\s*$', body, re.MULTILINE))
    section, ancestry, start = title, [], 0

    def chunks(start, end):
        while start < end:
            while start < end and body[start].isspace():
                start += 1
            if start == end:
                return
            stop = min(start + limit, end)
            if stop < end:
                window = body[start:stop]
                paragraphs = [m.end() for m in re.finditer(r'\n\s*\n', window)]
                suitable = [p for p in paragraphs if p >= limit // 2]
                if not suitable:
                    sentences = [m.end() for m in re.finditer(r'[.!?][\]"”\')]*\s+', window)]
                    suitable = [p for p in sentences if p >= limit // 2]
                if suitable:
                    stop = start + suitable[-1]
                else:
                    space = max(window.rfind(' '), window.rfind('\n'))
                    if space > 0:
                        stop = start + space
            while stop > start and body[stop - 1].isspace():
                stop -= 1
            yield dict(section=section[:300], path=[text for _, text in ancestry] or [title],
                       start=start, end=stop, body=body[start:stop])
            start = stop

    for heading in headings:
        yield from chunks(start, heading.start())
        depth, section = len(heading.group(1)), heading.group(2)
        ancestry = [(level, name) for level, name in ancestry if level < depth] + [(depth, section)]
        start = heading.end()
    yield from chunks(start, len(body))


def passages(body: str, title: str):
    for row in passage_spans(body, title):
        yield row['section'], row['body']


def build_library(source: Path, target: Path, schema: int = 3):
    if schema not in {1, 2, 3}:
        raise ValueError('Unsupported library schema')
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        raise FileExistsError(target)
    with tempfile.TemporaryDirectory(prefix='.library-', dir=target.parent) as folder:
        staged = Path(folder) / 'library.sqlite'
        count, passage_count = 0, 0
        logical = hashlib.sha256()
        with closing(sqlite3.connect(staged)) as db:
            db.executescript("""
            PRAGMA cache_size=-65536;
            PRAGMA temp_store=FILE;
            CREATE TABLE documents(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,publisher TEXT NOT NULL,
              url TEXT NOT NULL,license TEXT NOT NULL,updated TEXT NOT NULL,saved TEXT NOT NULL,
              body TEXT NOT NULL,collection TEXT NOT NULL);
            CREATE TABLE document_provenance(doc_id TEXT PRIMARY KEY NOT NULL,body_sha256 TEXT NOT NULL,details TEXT NOT NULL);
            CREATE TABLE passage_spans(passage_id INTEGER PRIMARY KEY,start INTEGER NOT NULL,end INTEGER NOT NULL,section_path TEXT NOT NULL);
            CREATE TABLE build_metadata(key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL);
            """)
            db.execute(f'PRAGMA user_version={schema}')
            if schema == 1:
                db.execute("CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT NOT NULL,body TEXT NOT NULL,section TEXT NOT NULL)")
                db.execute("CREATE VIRTUAL TABLE passages_fts USING fts5(body,content='passages',content_rowid='id',tokenize='unicode61')")
            elif schema == 2:
                db.execute("CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT NOT NULL,body TEXT NOT NULL,section TEXT NOT NULL,title TEXT NOT NULL)")
                db.execute("CREATE VIRTUAL TABLE passages_fts USING fts5(body,title,section,content='passages',content_rowid='id',tokenize='unicode61')")
                db.execute("INSERT INTO passages_fts(passages_fts,rank) VALUES ('rank','bm25(1.0,6.0,2.0)')")
            else:
                db.execute("CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT NOT NULL,section TEXT NOT NULL,title TEXT NOT NULL)")
                db.execute("CREATE VIRTUAL TABLE passages_fts USING fts5(body,title,section,content='',tokenize='unicode61')")
                db.execute("INSERT INTO passages_fts(passages_fts,rank) VALUES ('rank','bm25(1.0,6.0,2.0)')")
            db.execute("PRAGMA temp_store_directory='" + folder.replace("'", "''") + "'")
            for item in documents(source):
                fields = ['id','title','publisher','url','license','updated','saved','body','collection']
                db.execute('INSERT INTO documents VALUES (?,?,?,?,?,?,?,?,?)', [item[f] for f in fields])
                body_hash = hashlib.sha256(item['body'].encode()).hexdigest()
                provenance = item.get('provenance', {})
                if provenance.get('body_sha256', body_hash) != body_hash:
                    raise ValueError(f'Body hash mismatch: {item["id"]}')
                db.execute('INSERT INTO document_provenance VALUES (?,?,?)',
                           (item['id'], body_hash, json.dumps(provenance, sort_keys=True, ensure_ascii=False)))
                logical.update((json.dumps(item, sort_keys=True, ensure_ascii=False,
                                           separators=(',', ':')) + '\n').encode())
                for position, span in enumerate(passage_spans(item['body'], item['title'])):
                    if schema >= 2:
                        # Title-only matches route to the article's opening, not
                        # short references/footers that gain a length advantage.
                        title = item['title'] if position == 0 else ''
                        if schema == 2:
                            cursor = db.execute('INSERT INTO passages(doc_id,body,section,title) VALUES (?,?,?,?)',
                                                (item['id'], span['body'], span['section'], title))
                        else:
                            cursor = db.execute('INSERT INTO passages(doc_id,section,title) VALUES (?,?,?)',
                                                (item['id'], span['section'], title))
                        db.execute('INSERT INTO passages_fts(rowid,body,title,section) VALUES (?,?,?,?)',
                                   (cursor.lastrowid, span['body'], title, span['section']))
                    else:
                        cursor = db.execute('INSERT INTO passages(doc_id,body,section) VALUES (?,?,?)',
                                            (item['id'], span['body'], span['section']))
                        db.execute('INSERT INTO passages_fts(rowid,body) VALUES (?,?)', (cursor.lastrowid, span['body']))
                    db.execute('INSERT INTO passage_spans VALUES (?,?,?,?)',
                               (cursor.lastrowid, span['start'], span['end'], json.dumps(span['path'], ensure_ascii=False)))
                    passage_count += 1
                count += 1
                if count % 1000 == 0:
                    db.commit()
                    print(f'Indexed {count:,} documents / {passage_count:,} passages', flush=True)
                    if shutil.disk_usage(target.parent).free < 1024**3:
                        raise OSError('Stopped build before consuming the final 1 GiB of free space')
            if count == 0 or passage_count == 0:
                raise ValueError('Empty library')
            db.execute("CREATE INDEX documents_title ON documents(title,id)")
            db.execute("CREATE INDEX provenance_body_hash ON document_provenance(body_sha256)")
            db.execute("CREATE INDEX passages_document ON passages(doc_id)")
            names = build_names(db, source) if schema >= 2 else None
            duplicate_count = db.execute('SELECT count(*)-count(DISTINCT body_sha256) FROM document_provenance').fetchone()[0]
            report = dict(format=1, schema=schema, audit_schema=1, documents=count, passages=passage_count,
                          title_index='opening passage only' if schema >= 2 else 'none',
                          passage_storage='document spans + contentless FTS' if schema == 3 else 'passage text + external-content FTS',
                          exact_duplicate_bodies=duplicate_count, records_sha256=logical.hexdigest(),
                          offsets='Unicode code points, half-open into normalized documents.body',
                          sqlite_version=sqlite3.sqlite_version)
            report['builder_sources'] = {name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
                                         for name in ('library.py', 'names.py', 'records.py')}
            if names is not None:
                report['document_names'] = names
            db.execute('INSERT INTO build_metadata VALUES (?,?)', ('build', json.dumps(report, sort_keys=True)))
            db.execute("INSERT INTO passages_fts(passages_fts,rank) VALUES ('integrity-check',1)")
            db.commit()
            if db.execute('PRAGMA quick_check').fetchone()[0] != 'ok':
                raise ValueError('SQLite integrity failure')
            db.execute('PRAGMA journal_mode=DELETE')
        with staged.open('r+b') as completed:
            os.fsync(completed.fileno())
        os.link(staged, target)
    print(json.dumps(report, indent=2), flush=True)
    return report
