from contextlib import redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from blobs_corpus.package import package, verify_package
from evaluation.answers.citation_output import parse_answer
from evaluation.answers.host_answers import (
    completion_packets,
    main,
    prepared_model,
    record_chat_chunk,
    sampling_for,
)
from evaluation.native.audit_native_prefill import audit as audit_native_prefill
from evaluation.native.audit_native_smoke import audit as audit_native_smoke
from evaluation.native.audit_product_answer import audit as audit_product_answer
from evaluation.retrieval.host_replay import select_evidence
from evaluation.retrieval.host_rerank import named_openings_first, reciprocal_rank_order
from evaluation.retrieval.title_coverage import inspect as inspect_titles


class EvaluationInputTests(unittest.TestCase):
    def test_qwen35_2b_text_profile_preserves_publisher_sampling_contract(self):
        sampling = sampling_for('qwen35-2b-text')
        self.assertEqual(
            {key: sampling[key] for key in
             ('temperature', 'top_p', 'top_k', 'min_p', 'presence_penalty', 'repeat_penalty')},
            dict(temperature=1.0, top_p=1.0, top_k=20, min_p=0,
                 presence_penalty=2.0, repeat_penalty=1.0),
        )
        self.assertEqual(sampling_for('controlled')['temperature'], 0.3)
        self.assertEqual(
            {key: sampling_for('product')[key] for key in
             ('temperature', 'top_p', 'top_k', 'min_p', 'presence_penalty',
              'repeat_penalty', 'repeat_last_n')},
            dict(temperature=0.3, top_p=0.8, top_k=20, min_p=0,
                 presence_penalty=1.5, repeat_penalty=1.0, repeat_last_n=256),
        )
        with self.assertRaisesRegex(ValueError, 'Unknown sampling profile'):
            sampling_for('unreviewed')

    def test_native_prefill_audit_rejects_unpaired_runtime_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            model_hash, app_hash = 'a' * 64, 'b' * 64
            protocol = root / 'protocol.json'
            protocol.write_text(json.dumps(dict(model_sha256=model_hash, output_limit=2)), encoding='utf-8')
            protocol_hash = hashlib.sha256(protocol.read_bytes()).hexdigest()
            manifest = dict(complete=True, protocol_sha256=protocol_hash,
                system={'sha256': 'c' * 64}, configurations=[
                    dict(id='full-five', evidence_indices=[1, 2], user={'sha256': 'd' * 64}),
                    dict(id='compact-three', evidence_indices=[1], user={'sha256': 'e' * 64})])
            metric = dict(total_us=100, prefill_us=60, first_token_callback_us=70,
                          generation_loop_us=30, prompt_tokens=10, output_tokens=2)
            full = dict(complete=True, fingerprint='device', model_sha256=model_hash,
                        system_sha256='c' * 64, output_limit=2, user_sha256='d' * 64,
                        result=2, visible_answer='Truncated [1', metrics=metric)
            compact_metric = dict(metric, total_us=70, prefill_us=30,
                                  first_token_callback_us=40, prompt_tokens=5)
            compact = dict(complete=True, fingerprint='device', model_sha256=model_hash,
                           system_sha256='c' * 64, output_limit=2, user_sha256='e' * 64,
                           result=0, visible_answer='Answer [1][2]', metrics=compact_metric)
            values = {
                'manifest.json': manifest, 'full.json': full, 'compact.json': compact,
                'staging.json': dict(complete=True, staged={'sha256': model_hash}),
                'cleanup.json': dict(complete=True, targets_absent={'fixture': True}),
                'installed.json': dict(complete=True, apk_sha256=app_hash),
            }
            paths = {'protocol.json': protocol}
            for name, value in values.items():
                paths[name] = root / name
                paths[name].write_text(json.dumps(value), encoding='utf-8')
            result = audit_native_prefill(
                protocol, paths['manifest.json'], paths['full.json'], paths['compact.json'],
                paths['staging.json'], paths['cleanup.json'], paths['installed.json'],
                app_hash, 'f' * 64)
            self.assertEqual(result['decision'], 'reject_both')
            compact['fingerprint'] = 'different-device'
            paths['compact.json'].write_text(json.dumps(compact), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'exact_paired_runtime'):
                audit_native_prefill(
                    protocol, paths['manifest.json'], paths['full.json'], paths['compact.json'],
                    paths['staging.json'], paths['cleanup.json'], paths['installed.json'],
                    app_hash, 'f' * 64)

    def test_product_answer_audit_rejects_out_of_range_saved_citations(self):
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            payload, library_package = root / 'library.sqlite', root / 'library.blobs'
            payload.write_bytes(b'library fixture')
            package(payload, library_package, kind='library', id='library', version='1',
                    title='Library', license='CC0', origin='local:test')
            library = verify_package(library_package)
            model_hash = 'a' * 64
            run = dict(complete=True, generation_started=True, finished_within_timeout=True,
                       status='COMPLETE', error='', model_file_bytes=100, model_sha256=model_hash,
                       library_manifest=library, library_sha256=library['sha256'],
                       evidence=json.dumps([dict(title='Fixture', text='Saved evidence')]),
                       answer='Supported answer [1].', question='Question?',
                       application_id='dev.blobs.fixture', first_visible_state_ms=1,
                       saved_elapsed_ms=2, wall_elapsed_ms=3)
            values = {
                'run.json': run,
                'failure.json': dict(complete=False, generation_started=False,
                                     status='PREPARATION_FAILED',
                                     error='java.lang.IllegalStateException: STORAGE'),
                'staging.json': dict(complete=True, staged=dict(bytes=100, sha256=model_hash)),
                'cleanup.json': dict(complete=True, targets_absent={'fixture': True}),
                'lock.json': dict(model=dict(bytes=100, sha256=model_hash)),
                'installed.json': dict(complete=True, apk_sha256='b' * 64),
            }
            paths = {}
            for name, value in values.items():
                paths[name] = root / name
                paths[name].write_text(json.dumps(value), encoding='utf-8')
            result = audit_product_answer(
                paths['run.json'], paths['failure.json'], paths['staging.json'],
                paths['cleanup.json'], paths['lock.json'], library_package,
                paths['installed.json'], 'b' * 64, 'c' * 64)
            self.assertTrue(all(result['checks'].values()))
            run['answer'] = 'Unsupported source number [2].'
            paths['run.json'].write_text(json.dumps(run), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'citations_present_and_in_range'):
                audit_product_answer(
                    paths['run.json'], paths['failure.json'], paths['staging.json'],
                    paths['cleanup.json'], paths['lock.json'], library_package,
                    paths['installed.json'], 'b' * 64, 'c' * 64)

    def test_native_smoke_audit_requires_exact_identity_and_lifecycle_results(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            model_hash = 'a' * 64
            values = {
                'run.json': dict(complete=True, model_bytes=100, model_sha256=model_hash,
                    fingerprint='fixture', answer_result=0, raw_answer='Supported answer.',
                    answer_metrics=dict(prompt_tokens=10, output_tokens=3, non_thinking_prefix=True,
                                        total_us=100, first_token_callback_us=80, prefill_us=70),
                    cancellation_result=1,
                    cancellation_metrics=dict(output_tokens=3, non_thinking_prefix=True),
                    failure_metrics=dict(total_us=1, first_token_callback_us=-1, output_tokens=0)),
                'staging.json': dict(complete=True, staged=dict(bytes=100, sha256=model_hash)),
                'lock.json': dict(model=dict(bytes=100, sha256=model_hash)),
                'installed.json': dict(complete=True, package='dev.blobs.fixture', apk_sha256='b' * 64),
            }
            paths = {}
            for name, value in values.items():
                paths[name] = root / name
                paths[name].write_text(json.dumps(value), encoding='utf-8')
            result = audit_native_smoke(
                paths['run.json'], paths['staging.json'], paths['lock.json'],
                paths['installed.json'], 'b' * 64, 'c' * 64)
            self.assertTrue(result['complete'])
            self.assertTrue(all(result['checks'].values()))
            values['run.json']['cancellation_metrics']['output_tokens'] = 4
            paths['run.json'].write_text(json.dumps(values['run.json']), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'exact_three_token_cancellation'):
                audit_native_smoke(
                    paths['run.json'], paths['staging.json'], paths['lock.json'],
                    paths['installed.json'], 'b' * 64, 'c' * 64)

    def test_chat_nonstream_keeps_full_output_without_fabricating_partial_arrival(self):
        packet = {'choices': [{'index': 0, 'message': {'content': '{}', 'reasoning_content': 'analysis'},
                               'finish_reason': 'stop'}],
                  '__verbose': {'stop': True, 'content': 'analysis</think>{}', 'tokens': [1, 2, 3]},
                  'usage': {'completion_tokens': 3}}
        opener = SimpleNamespace(open=lambda request, timeout: io.BytesIO(json.dumps(packet).encode()))
        packets = list(completion_packets(opener, None, True))
        self.assertIsNone(packets[-1])
        response = {'content': ''}
        record_chat_chunk(response, packets[0])
        self.assertEqual(response['content'], '{}')
        self.assertEqual(response['raw_content'], 'analysis</think>{}')
        self.assertEqual(response['tokens'], [1, 2, 3])
        self.assertTrue(response['raw_output_available'])

    def test_streaming_missing_raw_output_and_terminal_marker_remain_missing(self):
        packet = {'choices': [{'index': 0, 'delta': {'content': '{}'}, 'finish_reason': 'stop'}],
                  '__verbose': {'stop': True, 'content': '', 'tokens': []}}
        wire = b': comment\n\ndata: ' + json.dumps(packet).encode() + b'\n\n'
        opener = SimpleNamespace(open=lambda request, timeout: io.BytesIO(wire))
        packets = list(completion_packets(opener, None, False))
        self.assertNotIn(None, packets)
        response = {'content': ''}
        record_chat_chunk(response, packets[0])
        self.assertFalse(response['raw_output_available'])
        self.assertEqual(response['content'], '{}')

    def test_chat_stream_keeps_reasoning_out_of_final_and_retains_terminal_metadata(self):
        response = {'content': ''}
        for delta in [{'role': 'assistant', 'content': None}, {'reasoning_content': 'Private analysis'},
                      {'content': '{"status":'}, {'content': '"insufficient","reason":"No cause supplied."}'}]:
            record_chat_chunk(response, {'choices': [{'index': 0, 'delta': delta}]})
        final = response['content']
        raw = 'Private analysis</think>\n' + final
        record_chat_chunk(response, {'choices': [{'index': 0, 'delta': {}, 'finish_reason': 'stop'}],
            '__verbose': {'stop': True, 'content': raw, 'generation_settings': {'n_predict': 1280},
                          'tokens_predicted': 30, 'stop_type': 'eos'}})
        record_chat_chunk(response, {'choices': [], 'usage': {'completion_tokens': 30},
                                     'timings': {'predicted_n': 30}})
        self.assertEqual(response['content'], final)
        self.assertEqual(response['reasoning_content'], 'Private analysis')
        self.assertEqual(response['raw_content'], raw)
        self.assertEqual(response['usage']['completion_tokens'], response['tokens_predicted'])
        self.assertEqual(parse_answer(final, 1)['status'], 'insufficient')

    def test_chat_stream_rejects_tools_multiple_choices_and_nontext(self):
        for choices in [
            [{'index': 0, 'delta': {'tool_calls': [{'id': 'unexpected'}]}}],
            [{'index': 0}, {'index': 1}],
            [{'index': 0, 'delta': {'reasoning_content': 0}}],
        ]:
            with self.assertRaises(ValueError):
                record_chat_chunk({'content': ''}, {'choices': choices})

    @staticmethod
    def passage(identifier, title, text, *, opening=False):
        return dict(passage_id=identifier, id=title, title=title, body=text,
                    section=title, opening_title=title if opening else '',
                    publisher='Fixture', url='https://example.invalid/' + title,
                    license='CC0', saved='2026-10-17', updated='Unknown')

    def test_named_concepts_keep_definitions_without_replacing_single_topic_ranking(self):
        rows = [self.passage(1, 'A', 'Specialized A detail'),
                self.passage(2, 'A', 'Another A detail'),
                self.passage(3, 'B', 'Specialized B detail'),
                self.passage(4, 'A', 'Definition A', opening=True),
                self.passage(5, 'B', 'Definition B', opening=True)]
        manifest = dict(id='fixture', version='1', sha256='0' * 64, title='Fixture')
        original = json.dumps(rows)
        baseline = select_evidence(rows, manifest)
        self.assertNotIn('Definition A', [p['text'] for p in baseline])
        ordered, protected = named_openings_first(rows, ['A', 'B'])
        chosen = select_evidence(ordered, manifest)
        self.assertIn('Definition A', [p['text'] for p in chosen])
        self.assertIn('Definition B', [p['text'] for p in chosen])
        self.assertEqual({p['passage_id'] for p in ordered}, {p['passage_id'] for p in rows})
        self.assertEqual(len(ordered), len(rows))
        self.assertEqual(protected, [4, 5])
        self.assertEqual(json.dumps(rows), original)
        self.assertEqual(named_openings_first(rows, ['A']), (rows, []))
        # Section labels alone cannot identify a real opening paragraph.
        self.assertNotIn(1, protected)
        rows[0]['opening_title'] = 'Different document'
        with self.assertRaisesRegex(ValueError, 'metadata'):
            named_openings_first(rows, ['A', 'B'])

    def test_named_coverage_retains_utf16_and_per_document_budgets_and_source_scope(self):
        rows = [self.passage(1, 'A', '\U0001f600' * 1400, opening=True),
                self.passage(2, 'B', '\U0001f601' * 1400, opening=True),
                self.passage(3, 'A', 'x' * 400),
                self.passage(4, 'A', 'Extra A'),
                self.passage(5, 'B', 'Extra B')]
        ordered, protected = named_openings_first(rows, ['A', 'B', 'Excluded C'])
        selected = select_evidence(ordered, dict(id='fixture', version='1', sha256='0' * 64, title='Fixture'))
        self.assertEqual(sum(len(p['text'].encode('utf-16-le')) // 2 for p in selected), 6000)
        self.assertLessEqual(len(selected), 6)
        self.assertEqual([p['title'] for p in selected], ['A', 'B', 'A'])
        self.assertEqual(selected[0]['text'], rows[0]['body'])
        self.assertEqual(selected[1]['text'], rows[1]['body'])
        self.assertEqual(protected, [1, 2])
        self.assertNotIn('Excluded C', [p['title'] for p in selected])

    def test_rank_fusion_recovers_a_displaced_definition_before_its_document_cap_fills(self):
        rows = [self.passage(i, f'Other{i}', f'Other passage {i}') for i in range(15)]
        rows[0] = self.passage(0, 'Broader concept', 'Definition of the broader concept', opening=True)
        rows[1] = self.passage(1, 'Mechanism', 'Definition of the mechanism', opening=True)
        rows[3] = self.passage(3, 'Broader concept', 'Specialized mechanism detail')
        rows[14] = self.passage(14, 'Broader concept', 'Another specialized mechanism detail')
        neural = [1, 3, 14, 8, 7, 6, 5, 4, 0, 2, 9, 10, 11, 12, 13]
        manifest = dict(id='fixture', version='1', sha256='0' * 64, title='Fixture')
        baseline = select_evidence([rows[i] for i in neural], manifest)
        self.assertNotIn(rows[0]['body'], [p['text'] for p in baseline])
        fused = reciprocal_rank_order(neural)
        selected = select_evidence([rows[i] for i in fused], manifest)
        self.assertIn(rows[0]['body'], [p['text'] for p in selected])
        self.assertIn(rows[1]['body'], [p['text'] for p in selected])
        self.assertEqual(set(fused), set(neural))
        self.assertEqual(len(fused), len(neural))
        with self.assertRaisesRegex(ValueError, 'permutation'):
            reciprocal_rank_order([0, 0])

    def test_title_inventory_preserves_missing_targets_and_input_identity(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            records, questions = root / 'records.jsonl', root / 'questions.json'
            document = dict(id='fixture', title='Existing title', publisher='Fixture',
                            url='https://example.invalid/fixture', license='CC0',
                            updated='Unknown', saved='2026-10-17', collection='Fixture',
                            body='This unrelated body does not establish answerability.')
            records.write_text(json.dumps(document) + '\n', encoding='utf-8')
            cases = [dict(id='comparison', category='comparison',
                          expected_titles=['Existing title', 'Absent title']),
                     dict(id='missing', category='recall', expected_titles=['Absent title'])]
            questions.write_text(json.dumps(dict(cases=cases)), encoding='utf-8')
            original = questions.read_bytes()
            report = inspect_titles(records, questions)
            self.assertEqual(report['results'][0]['present'], ['Existing title'])
            self.assertEqual(report['results'][0]['absent'], ['Absent title'])
            self.assertEqual(report['cases_with_no_target_titles'], 1)
            self.assertEqual(report['cases_with_all_target_titles'], 0)
            self.assertEqual(report['questions_sha256'], hashlib.sha256(original).hexdigest())
            self.assertEqual(questions.read_bytes(), original)
            cases[1]['id'] = 'comparison'
            questions.write_text(json.dumps(dict(cases=cases)), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'unique'):
                inspect_titles(records, questions)

    def test_citation_metadata_cannot_be_replaced_by_article_footnotes(self):
        value = dict(status='answered', claims=[dict(text='Array x[2] has a value; an article printed [105].', sources=['S1'])])
        self.assertEqual(parse_answer(json.dumps(value), 2), value)
        value['claims'][0]['sources'] = ['S105']
        with self.assertRaisesRegex(ValueError, 'unavailable'):
            parse_answer(json.dumps(value), 2)

    def test_truncated_or_ambiguous_citation_envelopes_are_rejected(self):
        invalid = [
            '{"status":"answered","claims":[',
            '{"status":"insufficient","reason":"First","reason":"Replacement"}',
            json.dumps(dict(status='answered', claims=[dict(text='Claim', sources=[])])),
            json.dumps(dict(status='answered', claims=[dict(text='Claim', sources=['S1', 'S1'])])),
            json.dumps(dict(status='answered', claims=[dict(text='Claim', sources=[True])])),
            json.dumps(dict(status='answered', claims=[dict(text=' ', sources=['S1'])])),
            json.dumps(dict(status='insufficient', reason='Unavailable', claims=[])),
            '{"status":"insufficient","reason":NaN}',
            '{"status":"insufficient","reason":"\\ud800"}',
        ]
        for content in invalid:
            with self.subTest(content=content), self.assertRaises(ValueError):
                parse_answer(content, 2)

    def test_insufficient_evidence_does_not_require_a_fabricated_citation(self):
        value = dict(status='insufficient', reason='The saved sources do not contain the current rate.')
        self.assertEqual(parse_answer(json.dumps(value), 3), value)

    def test_temporary_model_is_verified_and_removed_after_consumer_failure(self):
        payload = b'GGUF' + b'original evaluation fixture' * 8
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()), patch(
            'evaluation.answers.host_answers.shutil.disk_usage', return_value=SimpleNamespace(free=10 * 1024**3)
        ):
            root = Path(folder)
            raw, packaged = root / 'input.gguf', root / 'input.blobs'
            raw.write_bytes(payload)
            package(raw, packaged, kind='model', id='fixture', version='1', title='Fixture',
                    license='CC0', origin='local:test')
            original = packaged.read_bytes()
            args = SimpleNamespace(model=None, model_package=packaged, work_directory=root / 'work',
                                   model_sha256=hashlib.sha256(payload).hexdigest())
            with self.assertRaisesRegex(RuntimeError, 'Consumer failed'):
                with prepared_model(args) as model:
                    self.assertEqual(model.read_bytes(), payload)
                    self.assertNotEqual(model, raw)
                    raise RuntimeError('Consumer failed')
            self.assertEqual(list(args.work_directory.iterdir()), [])
            self.assertEqual(packaged.read_bytes(), original)
            self.assertEqual(raw.read_bytes(), payload)

    def test_wrong_model_pin_never_produces_an_extracted_candidate(self):
        with tempfile.TemporaryDirectory() as folder, redirect_stdout(io.StringIO()):
            root = Path(folder)
            raw, packaged = root / 'input.gguf', root / 'input.blobs'
            raw.write_bytes(b'GGUFfixture')
            package(raw, packaged, kind='model', id='fixture', version='1', title='Fixture',
                    license='CC0', origin='local:test')
            args = SimpleNamespace(model=None, model_package=packaged, work_directory=root / 'work',
                                   model_sha256='0' * 64)
            with self.assertRaisesRegex(ValueError, 'selected model artifact'):
                with prepared_model(args):
                    self.fail('Wrong model became available to the runner')
            self.assertFalse(args.work_directory.exists())

    def test_ambiguous_retrieval_variants_cannot_silently_choose_context(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            model, retrieval, output = root / 'model.gguf', root / 'retrieval.json', root / 'answers.json'
            model.write_bytes(b'GGUFfixture')
            retrieval.write_text(json.dumps(dict(complete=True, cases={'cases':[{'id':'case'}]},
                results=[dict(case_id='case', variant=name, evidence=[]) for name in
                         ['baselineLibrary', 'candidateLibrary']])), encoding='utf-8')
            command = ['host_answers', '--server', str(root / 'unused.exe'), '--model', str(model),
                       '--model-sha256', hashlib.sha256(model.read_bytes()).hexdigest(),
                       '--retrieval-report', str(retrieval), '--cases', 'case', '--output', str(output)]
            with patch('sys.argv', command), patch('evaluation.answers.host_answers.subprocess.Popen') as process:
                with self.assertRaisesRegex(ValueError, 'retrieval-variant'):
                    main()
                process.assert_not_called()
            self.assertFalse(output.exists())
