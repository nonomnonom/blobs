from contextlib import closing, redirect_stdout
import errno
import gzip
import hashlib
import html as html_module
import io
import json
from pathlib import Path
import sqlite3
import struct
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

from blobs_corpus.acquire import download, read_lock
from blobs_corpus.extract import extract, write_lines, write_json, verified_shards
from blobs_corpus.library import build_library, passage_spans
from blobs_corpus.names import name_key
from blobs_corpus.normalize import article, ArticleRedirect
from blobs_corpus.package import package, verify_package
from blobs_corpus.footprint import calculate
from blobs_corpus.records import documents
from blobs_corpus.report import report as quality_report
from blobs_corpus.curate import curate
from blobs_corpus.export import export_records

LOCK = read_lock(Path(__file__).resolve().parents[1] / 'sources/wikipedia-en-top-2026-09.json')


class NormalizationTests(unittest.TestCase):
    def test_redirect_titles_keep_internal_apostrophes_and_section_anchors(self):
        for target in ("./'Ndrangheta#Belgium", "./O'Brien", './Rock_%27n%27_roll#Origin', './A;B'):
            content = html_module.escape("0;URL='" + target + "'", quote=True)
            source = f'<meta http-equiv="refresh" content="{content}">'.encode()
            with self.assertRaises(ArticleRedirect) as caught:
                article(source, 'Redirect', 'Redirect', LOCK)
            from urllib.parse import unquote
            self.assertEqual(caught.exception.target, unquote(target.removeprefix('./')))
        with self.assertRaisesRegex(ValueError, 'Empty HTML redirect'):
            article(b'<meta http-equiv="refresh" content="0;URL=\'./\'">', 'Bad', 'Bad', LOCK)

    def test_navigation_roles_are_removed_without_dropping_scientific_tables(self):
        html = b'''<div class="mw-parser-output"><table class="sidebar" role="navigation">
        <tr><td>Unrelated navigation links</td></tr></table>
        <p>This definition explains an original physical observation and preserves its important limitations.</p>
        <table role="table"><tr><th>Temperature (C)</th><th>Sample</th></tr>
        <tr><td>20</td><td>Water</td></tr></table></div>'''
        doc = article(html, 'Original observation', 'Original_observation', LOCK)
        self.assertNotIn('Unrelated navigation', doc['body'])
        self.assertTrue(doc['body'].startswith('This definition'))
        self.assertIn('Temperature (C) | Sample\n20 | Water', doc['body'])
        self.assertEqual(doc['provenance']['normalization']['aria_navigation_nodes_removed'], 1)

    def test_leading_infobox_moves_after_intro_with_all_values_and_units(self):
        html = b'''<div class="mw-parser-output"><table class="infobox"><tr><th>Identity</th>
        <td><p>Identifier A</p><table class="infobox"><tr><td>Density</td><td>20 kg/m3</td></tr></table></td></tr></table>
        <p>This original introductory explanation comes before reference details in the readable article.</p>
        <h2>Observations</h2><p>Lower temperature does not prove lower total energy.</p>
        <table class="infobox"><tr><td>Later local observation</td><td>30 C</td></tr></table></div>'''
        doc = article(html, 'Original observation', 'Original_observation', LOCK)
        body = doc['body']
        self.assertTrue(body.startswith('This original introductory explanation'))
        self.assertLess(body.index('== Article details =='), body.index('== Observations =='))
        self.assertIn('Identifier A', body)
        self.assertIn('20 kg/m3', body)
        self.assertGreater(body.index('Later local observation'), body.index('== Observations =='))
        self.assertEqual(doc['provenance']['normalization']['leading_infoboxes_moved'], 1)
        lead = next(passage_spans(body, doc['title']))
        self.assertNotIn('Identifier', lead['body'])
        self.assertIn('introductory explanation', lead['body'])

    def test_infobox_details_survive_an_article_without_section_headings(self):
        html = b'''<table class="infobox"><tr><th>Density</th><td>20 kg/m3</td></tr></table>
        <p>This original introductory explanation has no subsequent section headings but remains complete.</p>'''
        doc = article(html, 'Density', 'Density', LOCK)
        self.assertTrue(doc['body'].startswith('This original'))
        self.assertIn('== Article details ==\n\nDensity | 20 kg/m3', doc['body'])

    def test_structure_units_math_footnote_and_revision_survive(self):
        html = b'''<script>RLCONF = {"wgCurRevisionId":123,"wgCategories":["Physics"]};</script>
        <link rel="canonical" href="https://en.wikipedia.org/wiki/Heat">
        <div class="mw-parser-output"><nav>Discard navigation</nav><h1>Heat</h1>
        <p>A cooler surface does <b>not</b> always mean less total energy.<sup>[1]</sup></p>
        <h2>Measurements</h2><table><tr><th>Material</th><th>Temperature (C)</th></tr>
        <tr><td rowspan="2">Water</td><td>20</td></tr><tr><td>30</td></tr></table>
        <h3>Relation</h3><p>Example: <math><semantics><mi>x</mi>
        <annotation encoding="application/x-tex">E=mc^2</annotation></semantics></math>.</p>
        <h2>Notes</h2><p>[1] These are illustrative measurements, not universal constants.</p></div>'''
        item = article(html, 'Heat', 'Heat', LOCK)
        self.assertNotIn('Discard', item['body'])
        self.assertIn('does not always', item['body'])
        self.assertIn('Temperature (C)\nWater | 20\nWater | 30', item['body'])
        self.assertIn('E=mc^2', item['body'])
        self.assertIn('[1] These are illustrative', item['body'])
        self.assertEqual(item['provenance']['revision_id'], 123)
        spans = list(passage_spans(item['body'], item['title']))
        self.assertEqual(next(s['path'] for s in spans if s['section'] == 'Relation'), ['Measurements', 'Relation'])
        self.assertTrue(all(item['body'][s['start']:s['end']] == s['body'] for s in spans))

    def test_redirect_is_not_misrepresented_as_an_article(self):
        with self.assertRaises(ArticleRedirect) as caught:
            article(b'<meta http-equiv="refresh" content="0;URL=\'./Energy#History\'">', 'Alias', 'Alias', LOCK)
        self.assertEqual(caught.exception.target, 'Energy#History')

    def test_long_unbroken_text_is_not_silently_truncated(self):
        body = 'x' * 4201
        rows = list(passage_spans(body, 'Long'))
        self.assertEqual(''.join(row['body'] for row in rows), body)
        self.assertTrue(all(len(row['body']) <= 1400 for row in rows))

    def test_nested_table_spans_cannot_expand_exponentially(self):
        html = 'An original long enough paragraph describing a table and preserving all of its relevant text.'
        for _ in range(14):
            html = '<table><tr><td colspan="50">' + html + '</td></tr></table>'
        item = article(html.encode(), 'Nested', 'Nested', LOCK)
        self.assertLess(len(item['body']), 10000)

    def test_oversized_table_is_rejected_before_unbounded_expansion(self):
        html = '<table><tr><td colspan="50">' + 'x' * 30000 + '</td></tr></table>'
        with self.assertRaisesRegex(ValueError, 'Expanded text'):
            article(html.encode(), 'Large', 'Large', LOCK)


class BuildTests(unittest.TestCase):
    def test_mechanism_paragraph_stays_together_with_bounded_exact_passages(self):
        introduction = ('Earlier observations establish the setting and its limits. ' * 16).strip()
        mechanism = (
            "Compression raises the fluid's pressure and temperature. Warm fluid enters an indoor "
            "exchanger, transfers thermal energy to the room, and condenses into a liquid. The liquid "
            "travels to an outdoor exchanger, where reduced pressure allows it to evaporate and cool. "
            "Because the fluid is now colder than the outdoor environment, it absorbs energy from that "
            "environment before returning to the compressor. The final step closes the cycle and "
            "explains where the energy delivered indoors comes from."
        )
        body = introduction + '\n\n' + mechanism + '\n\n== Consequences ==\nDifferent section.'
        spans = list(passage_spans(body, 'Cycle'))
        self.assertTrue(any(mechanism in span['body'] for span in spans))
        self.assertEqual(spans[-1]['section'], 'Consequences')
        for span in spans:
            self.assertEqual(span['body'], body[span['start']:span['end']])
            self.assertTrue(0 < len(span['body']) <= 1400)
            self.assertNotIn('== Consequences ==', span['body'])
        long = 'An uninterrupted paragraph still has to fit the bounded evidence window. ' * 100
        chunks = list(passage_spans(long, 'Long'))
        self.assertTrue(all(0 < len(span['body']) <= 1400 for span in chunks))
        self.assertEqual(' '.join(span['body'] for span in chunks).split(), long.split())

    def test_package_verification_counts_bytes_instead_of_trusting_zip_headers(self):
        payload = b'fixture'
        manifest = dict(format=1, kind='library', id='fixture', version='1', title='Fixture',
                        license='CC0', origin='local:test', bytes=100,
                        sha256=hashlib.sha256(payload).hexdigest())
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'forged.blobs'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('manifest.json', json.dumps(manifest))
                archive.writestr('library.sqlite', payload)
            data = bytearray(path.read_bytes())
            with zipfile.ZipFile(path) as archive:
                offset = archive.getinfo('library.sqlite').header_offset
            struct.pack_into('<I', data, offset + 22, 100)
            cursor = 0
            while (cursor := data.find(b'PK\x01\x02', cursor)) != -1:
                length = struct.unpack_from('<H', data, cursor + 28)[0]
                if data[cursor + 46:cursor + 46 + length] == b'library.sqlite':
                    struct.pack_into('<I', data, cursor + 24, 100)
                cursor += 4
            path.write_bytes(data)
            with self.assertRaisesRegex(ValueError, 'byte count'):
                verify_package(path)

    def test_source_aliases_resolve_chains_without_inventing_missing_targets(self):
        text = b'<p>This is original explanatory text for a complete source article with a stable title and provenance.</p>'
        items = [article(text, title, path, LOCK) for title, path in
                 [('Carbon dioxide', 'Carbon_dioxide'), ('Other subject', 'Other_subject')]]
        aliases = [dict(path=path, title=title, target=target) for path, title, target in [
            ('CO2', 'CO₂', 'Carbon_dioxide#Properties'),
            ('Chain', 'Gas name', './CO2'),
            ('Ambiguous1', 'Shared', 'Carbon_dioxide'),
            ('Ambiguous2', 'Shared', 'Other_subject'),
            ('Missing', 'Missing', 'Excluded_article'),
            ('Cycle1', 'Cycle one', 'Cycle2'), ('Cycle2', 'Cycle two', 'Cycle1')]]
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            files = {}
            for kind, rows in [('documents', items), ('aliases', aliases)]:
                path = root / f'{kind}.jsonl.gz'
                write_lines(path, rows)
                files[kind] = dict(name=path.name, bytes=path.stat().st_size,
                                   sha256=hashlib.sha256(path.read_bytes()).hexdigest())
            write_json(root / 'manifest.json', dict(format=1, complete=True, next_entry=9,
                       shards=[dict(start=0, end=9, files=files)]))
            report = build_library(root / 'manifest.json', root / 'library.sqlite')
            self.assertEqual(report['document_names']['resolved_redirects'], 4)
            self.assertEqual(report['document_names']['unresolved_redirects'], 3)
            self.assertEqual(report['document_names']['ambiguous_names'], 1)
            with closing(sqlite3.connect(root / 'library.sqlite')) as db:
                rows = dict(db.execute("SELECT name,doc_id FROM document_names WHERE name!='shared'"))
                self.assertEqual(rows['co2'], items[0]['id'])
                self.assertEqual(rows['gas name'], items[0]['id'])
                self.assertEqual(rows['other subject'], items[1]['id'])
                self.assertNotIn('missing', rows)
                self.assertNotIn('cycle one', rows)
        self.assertEqual(name_key('  CO₂ / ＡＩ — Earth’s  '), 'co2 ai earth s')

    def test_review_excludes_support_pages_without_changing_source_shards(self):
        text = b'<p>This original text is long enough to be extracted, but its archive path determines whether it belongs to an article.</p>'
        items = [article(text, name, path, LOCK) for name, path in [('Article', 'Article'), ('Main Page', 'index'), ('Support file', '_assets_/MathML')]]
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            shard = root / 'documents.jsonl.gz'
            write_lines(shard, items)
            before = shard.read_bytes()
            info = dict(name=shard.name, bytes=len(before), sha256=hashlib.sha256(before).hexdigest())
            manifest = dict(format=1, source=LOCK, complete=True, next_entry=3,
                            totals={'documents':3}, shards=[dict(start=0, end=3, counts={'documents':3}, files={'documents':info})])
            write_json(root / 'manifest.json', manifest)
            reviewed = curate(root / 'manifest.json', root / 'reviewed')
            self.assertEqual([d['title'] for d in documents(root / 'reviewed/manifest.json')], ['Article'])
            self.assertEqual(reviewed['totals']['excluded_non_articles'], 2)
            self.assertEqual(len(reviewed['curation']['excluded_documents']), 2)
            self.assertEqual(shard.read_bytes(), before)
            exported = root / 'exported'
            export_records(root / 'reviewed/manifest.json', exported)
            self.assertEqual((exported / 'manifest.json').read_bytes(), (root / 'reviewed/manifest.json').read_bytes())
            self.assertEqual([d['title'] for d in documents(exported / 'manifest.json')], ['Article'])
            with self.assertRaises(FileExistsError):
                export_records(root / 'reviewed/manifest.json', exported)
            with patch('blobs_corpus.export.MAX_FILE_BYTES', 1):
                with self.assertRaisesRegex(ValueError, 'artifact budget'):
                    export_records(root / 'reviewed/manifest.json', root / 'oversized')
            self.assertFalse((root / 'oversized').exists())
            with self.assertRaises(FileExistsError):
                curate(root / 'manifest.json', root / 'reviewed')

    def test_review_accepts_pinned_top_mini_source(self):
        lock = dict(LOCK, id='wikipedia-en-top1m-mini')
        item = article(
            b'<p>This article has enough explanatory text to satisfy the extraction quality threshold.</p>',
            'Article', 'Article', lock)
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            shard = root / 'documents.jsonl.gz'
            write_lines(shard, [item])
            info = dict(name=shard.name, bytes=shard.stat().st_size,
                        sha256=hashlib.sha256(shard.read_bytes()).hexdigest())
            write_json(root / 'manifest.json', dict(
                format=1, source=lock, complete=True, next_entry=1, totals={'documents': 1},
                shards=[dict(start=0, end=1, counts={'documents': 1}, files={'documents': info})]))
            reviewed = curate(root / 'manifest.json', root / 'reviewed')
            self.assertEqual(reviewed['curation']['source_id'], 'wikipedia-en-top1m-mini')
            self.assertEqual([doc['title'] for doc in documents(root / 'reviewed/manifest.json')], ['Article'])

    def test_title_and_section_search_is_explicitly_versioned(self):
        item = article(b'<p>This original explanatory text contains no article name. It is deliberately long enough to be a valid fixture.</p><h2>Observations</h2><p>A second paragraph contains the evidence.</p>', 'Thermodynamics', 'Thermodynamics', LOCK)
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            source = root / 'input.jsonl.gz'
            write_lines(source, [item])
            for schema, expected in [(1, 0), (2, 1), (3, 1)]:
                target = root / f'{schema}.sqlite'
                build_library(source, target, schema=schema)
                with closing(sqlite3.connect(target)) as db:
                    self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], schema)
                    self.assertEqual(db.execute("SELECT count(*) FROM passages_fts WHERE passages_fts MATCH 'thermodynamics'").fetchone()[0], expected)
                    if schema >= 2:
                        self.assertEqual(db.execute("SELECT count(*) FROM passages_fts WHERE passages_fts MATCH 'section:observations'").fetchone()[0], 1)
                        if schema == 2:
                            hit = db.execute("SELECT body FROM passages_fts WHERE passages_fts MATCH 'thermodynamics' ORDER BY rank").fetchone()[0]
                        else:
                            hit = db.execute("""SELECT substr(d.body,s.start+1,s.end-s.start)
                                FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
                                JOIN passage_spans s ON s.passage_id=p.id JOIN documents d ON d.id=p.doc_id
                                WHERE passages_fts MATCH 'thermodynamics' ORDER BY rank""").fetchone()[0]
                        self.assertIn('original explanatory text', hit)

    def test_schema3_reconstructs_unicode_spans_without_a_passage_body_copy(self):
        paragraphs = ''.join(
            f'<p>Observation {index} 😀 records evaporation, temperature, and a distinct explanation.</p>'
            for index in range(160)
        )
        item = article(paragraphs.encode(), 'Unicode observations', 'Unicode_observations', LOCK)
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            source = root / 'input.jsonl.gz'
            write_lines(source, [item])
            schema2, schema3 = root / 'schema2.sqlite', root / 'schema3.sqlite'
            build_library(source, schema2, schema=2)
            build_library(source, schema3, schema=3)
            with closing(sqlite3.connect(schema3)) as db:
                columns = {row[1] for row in db.execute('PRAGMA table_info(passages)')}
                self.assertNotIn('body', columns)
                rows = db.execute("""SELECT passages_fts.body,d.body,s.start,s.end,
                    substr(d.body,s.start+1,s.end-s.start)
                    FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
                    JOIN passage_spans s ON s.passage_id=p.id JOIN documents d ON d.id=p.doc_id
                    WHERE passages_fts MATCH 'evaporation' ORDER BY p.id""").fetchall()
                self.assertTrue(rows)
                for indexed, body, start, end, reconstructed in rows:
                    self.assertIsNone(indexed)
                    self.assertEqual(reconstructed, body[start:end])
                    self.assertIn('😀', reconstructed)
            self.assertLess(schema3.stat().st_size, schema2.stat().st_size)

    def test_reproducible_searchable_package_and_exact_spans(self):
        item = article(b'<p>Evaporation transfers energy from water to the surrounding air. '
                       b'The temperature and humidity affect the rate of this process.</p>', 'Evaporation', 'Evaporation', LOCK)
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            source = root / 'documents.jsonl.gz'
            write_lines(source, [item])
            outputs = []
            for index in range(2):
                db = root / f'{index}.sqlite'
                build_library(source, db)
                with closing(sqlite3.connect(db)) as connection:
                    self.assertEqual(connection.execute("SELECT count(*) FROM passages_fts WHERE passages_fts MATCH 'humidity'").fetchone()[0], 1)
                    text, start, end = connection.execute('''SELECT substr(d.body,s.start+1,s.end-s.start),s.start,s.end
                        FROM passages p JOIN passage_spans s ON s.passage_id=p.id
                        JOIN documents d ON d.id=p.doc_id''').fetchone()
                    self.assertEqual(text, item['body'][start:end])
                out = root / f'{index}.blobs'
                package(db, out, kind='library', id='test', version='1', title='Test', license='CC BY-SA 4.0', origin=LOCK['url'])
                outputs.append(out.read_bytes())
                with zipfile.ZipFile(out) as archive:
                    self.assertEqual(archive.namelist(), ['manifest.json', 'library.sqlite'])
                    self.assertEqual(archive.getinfo('library.sqlite').compress_type, zipfile.ZIP_DEFLATED)
                    manifest = json.loads(archive.read('manifest.json'))
                    self.assertEqual(manifest['sha256'], hashlib.sha256(archive.read('library.sqlite')).hexdigest())
                self.assertEqual(verify_package(out), manifest)
            self.assertEqual(outputs[0], outputs[1])
            with self.assertRaises(FileExistsError):
                build_library(source, db)

    def test_invalid_input_never_publishes_a_partial_database(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'bad.jsonl'
            source.write_text('{"id":"incomplete"}\n', encoding='utf-8')
            with self.assertRaises(ValueError):
                build_library(source, root / 'library.sqlite')
            self.assertFalse((root / 'library.sqlite').exists())
            self.assertEqual(list(root.glob('.library-*')), [])

    def test_failure_after_committed_rows_cannot_publish_and_can_be_rebuilt(self):
        item = article(b'<p>This original fixture preserves a complete explanatory source for testing interrupted builds.</p>',
                       'Observation', 'Observation', LOCK)
        def interrupted():
            for index in range(1000):
                yield dict(item, id=f'fixture-{index}')
            raise OSError('Input stream interrupted after a committed batch')
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()), patch(
            'blobs_corpus.library.shutil.disk_usage', return_value=SimpleNamespace(free=10 * 1024**3)
        ):
            root = Path(folder)
            source, target = root / 'input.jsonl.gz', root / 'library.sqlite'
            write_lines(source, [item])
            with patch('blobs_corpus.library.documents', return_value=interrupted()):
                with self.assertRaisesRegex(OSError, 'Input stream interrupted'):
                    build_library(source, target)
            self.assertFalse(target.exists())
            self.assertEqual(list(root.glob('.library-*')), [])
            build_library(source, target)
            with closing(sqlite3.connect(target)) as db:
                self.assertEqual(db.execute('PRAGMA integrity_check').fetchall(), [('ok',)])
                self.assertEqual(db.execute('SELECT id FROM documents').fetchall(), [(item['id'],)])
                self.assertEqual(db.execute("""SELECT substr(d.body,s.start+1,s.end-s.start)
                    FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
                    JOIN passage_spans s ON s.passage_id=p.id JOIN documents d ON d.id=p.doc_id
                    WHERE passages_fts MATCH 'explanatory'""").fetchone()[0], item['body'])

    def test_changed_checkpoint_and_incomplete_corpus_cannot_be_packaged(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            shard = root / 'test.jsonl.gz'
            write_lines(shard, [])
            info = dict(name=shard.name, bytes=shard.stat().st_size, sha256=hashlib.sha256(shard.read_bytes()).hexdigest())
            manifest = dict(format=1, complete=False, next_entry=1, shards=[dict(start=0, end=1, files={'documents':info})])
            write_json(root / 'manifest.json', manifest)
            with self.assertRaisesRegex(ValueError, 'incomplete'):
                list(documents(root / 'manifest.json'))
            shard.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'Corrupt'):
                verified_shards(root / 'manifest.json')


class FootprintTests(unittest.TestCase):
    def test_verified_release_artifacts_produce_import_and_steady_storage_bounds(self):
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            model_payload = root / 'model.gguf'
            model_payload.write_bytes(b'GGUF' + b'm' * 96)
            library_payload = root / 'library.sqlite'
            library_payload.write_bytes(b'l' * 400)
            model = root / 'model.blobs'
            library = root / 'library.blobs'
            package(model_payload, model, kind='model', id='model', version='1', title='Model',
                    license='Apache-2.0', origin='https://example.invalid/model')
            package(library_payload, library, kind='library', id='library', version='1', title='Library',
                    license='CC0', origin='https://example.invalid/library')
            apk = root / 'apk.json'
            write_json(apk, dict(apk='app.apk', application_id='dev.blobs.app', version_code=1,
                version_name='1.0', bytes=50, sha256='a' * 64, internet_permission=True,
                zip_alignment_16kb=True))
            installer = root / 'PackageInstaller.kt'
            installer.write_text(
                'check(dao.all().sumOf { it.bytes } + expected.bytes < 48_000_000_000L)\n'
                'val required = expected.bytes - resumeBytes + 64L * 1024 * 1024\n',
                encoding='utf-8')
            result = calculate(apk, [model, library], installer)
            storage = result['storage']
            self.assertEqual(storage['installed_payload_bytes'], 500)
            self.assertEqual(storage['known_steady_bytes'], 550)
            self.assertEqual(storage['distribution_bytes'], 50 + model.stat().st_size + library.stat().st_size)
            self.assertEqual(storage['retained_sources']['peak_bytes'],
                             50 + model.stat().st_size + library.stat().st_size + 500)
            self.assertEqual(storage['move_each_source_after_import']['peak_bytes'],
                             max(50 + model.stat().st_size + 100,
                                 50 + 100 + library.stat().st_size + 400))
            self.assertEqual(storage['importer_allocatable_thresholds'][0]['fresh_import_threshold_bytes'],
                             100 + 64 * 1024 * 1024)
            self.assertEqual(storage['importer_allocatable_thresholds'][0]['comparison'],
                             'device allocatable bytes > threshold')
            self.assertTrue(result['checks']['android_installed_payload_cap']['passes'])
            self.assertTrue(result['checks']['bounty_storage_limit']['passes'])

    def test_footprint_rejects_bad_apk_policy_and_package_selection(self):
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            payload = root / 'model.gguf'
            payload.write_bytes(b'GGUFmodel')
            model = root / 'model.blobs'
            package(payload, model, kind='model', id='model', version='1', title='Model',
                    license='Apache-2.0', origin='https://example.invalid/model')
            apk = root / 'apk.json'
            write_json(apk, dict(apk='app.apk', application_id='dev.blobs.app', version_code=1,
                version_name='1.0', bytes=50, sha256='a' * 64, internet_permission=False,
                zip_alignment_16kb=True))
            installer = root / 'PackageInstaller.kt'
            installer.write_text('changed policy', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'package-download-enabled aligned'):
                calculate(apk, [model, model], installer)
            value = json.loads(apk.read_text())
            value['internet_permission'] = True
            write_json(root / 'good-apk.json', value)
            with self.assertRaisesRegex(ValueError, 'distinct'):
                calculate(root / 'good-apk.json', [model, model], installer)


class AcquisitionTests(unittest.TestCase):
    def test_lost_disk_headroom_preserves_prefix_and_resumes_without_network_retries(self):
        expected = b'a' * (1024 * 1024) + b'b' * (1024 * 1024)
        lock = dict(LOCK, filename='test.zim', bytes=len(expected), sha256=hashlib.sha256(expected).hexdigest())
        class Response(io.BytesIO):
            pass
        initial = Response(expected)
        initial.status, initial.headers, initial.url = 200, {'Content-Length': str(len(expected))}, lock['url']
        enough = SimpleNamespace(free=10 * 1024**3)
        low = SimpleNamespace(free=1024**3)
        with tempfile.TemporaryDirectory() as folder, patch('urllib.request.build_opener') as factory, redirect_stdout(io.StringIO()), patch('blobs_corpus.acquire.shutil.disk_usage') as space:
            root = Path(folder)
            factory.return_value.open.return_value = initial
            space.side_effect = [enough, enough, low]
            with self.assertRaises(OSError) as caught:
                download(lock, root)
            self.assertEqual(caught.exception.errno, errno.ENOSPC)
            factory.return_value.open.assert_called_once()
            partial = (root / 'test.zim.part').read_bytes()
            self.assertEqual(partial, expected[:1024 * 1024])
            self.assertFalse((root / 'test.zim').exists())
            resumed = Response(expected[len(partial):])
            resumed.status, resumed.url = 206, lock['url']
            resumed.headers = {'Content-Range': f'bytes {len(partial)}-{len(expected)-1}/{len(expected)}',
                               'Content-Length': str(len(expected)-len(partial))}
            factory.return_value.open.return_value = resumed
            space.side_effect = None
            space.return_value = enough
            result = download(lock, root)
            self.assertEqual(factory.return_value.open.call_args.args[0].get_header('Range'), f'bytes={len(partial)}-')
            self.assertEqual(result.read_bytes(), expected)
            self.assertFalse((root / 'test.zim.part').exists())

    def test_invalid_download_name_or_origin_never_sends_a_request(self):
        with tempfile.TemporaryDirectory() as folder, patch('urllib.request.build_opener') as opener:
            for lock in (dict(LOCK, filename='../escape.gguf'), dict(LOCK, url='https://unapproved.invalid/model')):
                with self.assertRaises(ValueError):
                    download(lock, Path(folder))
            opener.assert_not_called()

    def run_download(self, partial, response_body, status, headers, expected=b'abcdef'):
        lock = dict(LOCK, filename='test.zim', bytes=len(expected), sha256=hashlib.sha256(expected).hexdigest())
        class Response(io.BytesIO):
            pass
        response = Response(response_body)
        response.status, response.headers, response.url = status, headers, lock['url']
        with tempfile.TemporaryDirectory() as folder, patch('urllib.request.build_opener') as factory, redirect_stdout(io.StringIO()), patch('blobs_corpus.acquire.shutil.disk_usage') as space:
            space.return_value.free = 10 * 1024**3
            root = Path(folder)
            (root / 'test.zim.part').write_bytes(partial)
            factory.return_value.open.return_value = response
            result = download(lock, root)
            self.assertEqual(result.read_bytes(), expected)

    def test_range_resume(self):
        self.run_download(b'abc', b'def', 206, {'Content-Range':'bytes 3-5/6', 'Content-Length':'3'})

    def test_server_ignoring_range_restarts_instead_of_appending(self):
        self.run_download(b'abc', b'abcdef', 200, {'Content-Length':'6'})

    def test_wrong_range_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Content-Range'):
            self.run_download(b'abc', b'def', 206, {'Content-Range':'bytes 0-2/6'})

    def test_corrupt_download_cannot_activate(self):
        with self.assertRaisesRegex(ValueError, 'do not match'):
            self.run_download(b'', b'badbad', 200, {'Content-Length':'6'})


class ExtractionTests(unittest.TestCase):
    def test_real_zim_resume_keeps_each_article_once_and_preserves_aliases(self):
        from libzim.writer import Creator, Item, StringProvider, Hint

        class Page(Item):
            def __init__(self, path, html):
                self.path, self.html = path, html
            def get_path(self): return self.path
            def get_title(self): return self.path
            def get_mimetype(self): return 'text/html'
            def get_contentprovider(self): return StringProvider(self.html)
            def get_hints(self): return {Hint.FRONT_ARTICLE: 1}

        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            source = root / 'fixture.zim'
            with Creator(source).config_indexing(False, 'eng') as writer:
                writer.add_metadata('BinaryFixture', b'\x89PNG\xff', 'application/octet-stream')
                for name in ('Alpha', 'Beta', 'Gamma'):
                    writer.add_item(Page(name, f'<p>{name} is an original fixture article. Its complete text remains available offline, including this longer explanatory paragraph.</p>'))
                writer.add_redirection('Alias', 'Alias', 'Alpha', {})
                writer.add_item(Page('HTML-alias', '<meta http-equiv="refresh" content="0;URL=\'./Beta#Section\'">'))
            lock = dict(LOCK, filename=source.name, bytes=source.stat().st_size,
                        sha256=hashlib.sha256(source.read_bytes()).hexdigest())
            write_json(root / 'lock.json', lock)
            extract(root / 'lock.json', source, root / 'resumed', max_entries=2)
            self.assertFalse(verified_shards(root / 'resumed/manifest.json')['complete'])
            extract(root / 'lock.json', source, root / 'resumed')
            extract(root / 'lock.json', source, root / 'single')
            resumed = list(documents(root / 'resumed/manifest.json'))
            single = list(documents(root / 'single/manifest.json'))
            self.assertEqual(resumed, single)
            self.assertEqual(sorted(d['title'] for d in resumed), ['Alpha', 'Beta', 'Gamma'])
            report = verified_shards(root / 'resumed/manifest.json')
            self.assertNotIn('BinaryFixture', report['archive_metadata'])
            self.assertEqual(report['archive_binary_metadata']['BinaryFixture'],
                             dict(bytes=5, sha256=hashlib.sha256(b'\x89PNG\xff').hexdigest()))
            self.assertEqual(report['totals']['redirects'], 1)
            self.assertEqual(report['totals']['html_redirects'], 1)
            quality = quality_report(root / 'resumed/manifest.json', root / 'quality.json')
            self.assertEqual(quality['quality']['documents'], 3)
            self.assertEqual(quality['quality']['attributed'], 3)
            self.assertEqual(quality['quality']['duplicate_urls'], 0)
            self.assertEqual(len(quality['review_sample']), 3)
            self.assertEqual(json.loads((root / 'quality.json').read_text(encoding='utf-8')), quality)


if __name__ == '__main__':
    unittest.main()
