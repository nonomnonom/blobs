import json
import hashlib
from pathlib import Path
import tempfile
import unittest

from evaluation.answers.export_host_run import export_run
from evaluation.answers.benchmark_summary import summarize


class ExportHostRunTests(unittest.TestCase):
    def test_benchmark_summary_keeps_run_provenance_without_answer_or_source_text(self):
        cases = {'cases': [dict(id='one', category='recall', question='Question?',
                                expected_titles=['Known'], review='Review the answer.') ]}
        run = {
            'format': 1, 'complete': True, 'model': {'file': 'model.gguf', 'sha256': 'a' * 64},
            'runtime_commit': 'b' * 40, 'runtime_version': 'fixture', 'server_sha256': 'c' * 64,
            'platform': 'test-host', 'processor': 'test-cpu', 'host_physical_memory_bytes': 1234,
            'context': 4096, 'input_limit': 3000, 'threads': 2, 'batch': 32, 'ubatch': 16,
            'sampling_profile': 'product', 'sampling': {'seed': 7}, 'system': 'Use supplied sources.',
            'template_kwargs': {}, 'script_sha256': 'd' * 64, 'work_controller_sha256': 'e' * 64,
            'native_bridge_sha256': 'f' * 64, 'retrieval_report_sha256': '1' * 64,
            'model_package_sha256': '2' * 64,
            'results': [dict(case_id='one', question='Question?', status='RECORDED',
                             response={'content': 'Sensitive answer text [S1].', 'timings': {
                                 'prompt_ms': 20, 'prompt_per_second': 10,
                                 'predicted_ms': 30, 'predicted_per_second': 5,
                                 'predicted_n': 3,
                             }},
                             evidence=[dict(id='s1', title='Known', text='Sensitive source text.')],
                             host_elapsed_ms=100, first_content_arrival_ms=40, prompt_tokens=10)],
        }
        with tempfile.TemporaryDirectory() as folder:
            run_path = Path(folder) / 'run.json'
            cases_path = Path(folder) / 'cases.json'
            run_path.write_text(json.dumps(run), encoding='utf-8')
            cases_path.write_text(json.dumps(cases), encoding='utf-8')
            run_sha256 = hashlib.sha256(run_path.read_bytes()).hexdigest()
            record = summarize(run, cases, run_path, cases_path)

        serialized = json.dumps(record)
        self.assertNotIn('Sensitive answer text', serialized)
        self.assertNotIn('Sensitive source text', serialized)
        self.assertEqual(record['inputs']['host_run']['sha256'], run_sha256)
        self.assertEqual(record['summary']['host_elapsed_ms_p95'], 100)
        self.assertEqual(record['summary']['model_prefill_ms_median'], 20)
        self.assertEqual(record['summary']['model_generation_tokens_per_second_median'], 5)
        self.assertEqual(record['cases'][0]['generated_tokens'], 3)
        self.assertEqual(record['cases'][0]['source_count'], 1)
        self.assertNotIn('citation_count', record['cases'][0])
        self.assertNotIn('valid_citations', json.dumps(record))

    def test_export_preserves_failures_sources_and_mechanical_metrics(self):
        cases = {
            'scope': 'Titles are retrieval targets, not answer-quality labels.',
            'cases': [
                dict(id='one', category='recall', question='What is a checksum?',
                     expected_titles=['Checksum'], review='Define it.'),
                dict(id='two', category='explanation', question='Why can salt melt ice?',
                     expected_titles=['Freezing-point depression'], review='Explain the cause.'),
                dict(id='three', category='explanation', question='Why did it happen?',
                     expected_titles=[], review='State when the source is insufficient.'),
            ],
        }
        run = {
            'format': 1, 'complete': True, 'model': {'file': 'fixture.gguf', 'sha256': 'a' * 64},
            'runtime_commit': 'b' * 40, 'runtime_version': 'fixture', 'server_sha256': 'c' * 64,
            'platform': 'test', 'processor': 'test', 'host_physical_memory_bytes': 1000,
            'context': 4096, 'input_limit': 3000, 'threads': 2, 'batch': 32, 'ubatch': 16,
            'sampling_profile': 'controlled', 'sampling': {'seed': 1}, 'system': 'Use sources.',
            'template_kwargs': {}, 'script_sha256': 'd' * 64,
            'work_controller_sha256': 'e' * 64, 'native_bridge_sha256': 'f' * 64,
            'retrieval_report_sha256': '1' * 64,
            'results': [
                dict(case_id='one', question='What is a checksum?', status='RECORDED',
                     response={'content': 'It detects transfer errors [S1, S2].'},
                     evidence=[dict(id='one', title='Checksum', text='A checksum detects errors.'),
                               dict(id='two', title='Integrity', text='Integrity is assessed.')],
                     host_elapsed_ms=1000, first_content_arrival_ms=400, prompt_tokens=200),
                dict(case_id='two', question='Why can salt melt ice?', status='ERROR',
                     response=None, error='timeout', evidence=[], host_elapsed_ms=None,
                     first_content_arrival_ms=None, prompt_tokens=300),
                dict(case_id='three', question='Why did it happen?', status='RECORDED',
                     response={'content': 'The saved sources do not establish why.'},
                     evidence=[dict(id='three', title='Event', text='An event occurred.')],
                     host_elapsed_ms=900, first_content_arrival_ms=300, prompt_tokens=100),
            ],
        }
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            run_path = root / 'run.json'
            cases_path = root / 'cases.json'
            run_path.write_text(json.dumps(run), encoding='utf-8')
            cases_path.write_text(json.dumps(cases), encoding='utf-8')
            result = export_run(run, cases, run_path, cases_path)

        self.assertEqual(result['summary']['case_count'], 3)
        self.assertEqual(result['summary']['generation_status_counts'], {'ERROR': 1, 'RECORDED': 2})
        self.assertEqual(result['summary']['recorded_answers_with_valid_citations'], 1)
        self.assertEqual(result['summary']['recorded_answers_without_citations'], 1)
        self.assertEqual(result['summary']['recorded_answers_with_out_of_range_citations'], 0)
        self.assertEqual(result['summary']['cases_with_any_exact_expected_title'], 1)
        self.assertEqual(result['results'][1]['error'], 'timeout')
        self.assertEqual(result['results'][0]['evidence'][0]['title'], 'Checksum')
        self.assertEqual(result['results'][0]['citation_ids'], [1, 2])
        self.assertTrue(result['results'][0]['citation_ids_in_range'])
        self.assertIn('semantic score', result['scope'])

    def test_export_rejects_duplicate_or_changed_questions(self):
        cases = {'cases': [dict(id='one', question='Question?')]}
        row = dict(case_id='one', question='Changed?', status='ERROR', evidence=[])
        run = {'format': 1, 'complete': True, 'results': [row]}
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'input.json'
            path.write_text('{}', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'differs from the pinned case'):
                export_run(run, cases, path, path)
            run['results'] = [dict(case_id='one', question='Question?', status='ERROR', evidence=[])] * 2
            with self.assertRaisesRegex(ValueError, 'duplicate result case'):
                export_run(run, cases, path, path)

    def test_export_accepts_no_sources_as_a_distinct_non_error_outcome(self):
        cases = {'cases': [dict(id='empty', category='recall', question='No answerable source?',
                                expected_titles=[], review='State that sources are missing.') ]}
        run = {'format': 1, 'complete': True, 'results': [
            dict(case_id='empty', question='No answerable source?', status='NO_SOURCES',
                 response=None, evidence=[], error=None, host_elapsed_ms=None,
                 first_content_arrival_ms=None, prompt_tokens=None),
        ]}
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'input.json'
            path.write_text('{}', encoding='utf-8')
            result = export_run(run, cases, path, path)
        self.assertEqual(result['summary']['generation_status_counts'], {'NO_SOURCES': 1})
        self.assertEqual(result['results'][0]['answer'], '')
        self.assertIsNone(result['results'][0]['error'])


if __name__ == '__main__':
    unittest.main()
