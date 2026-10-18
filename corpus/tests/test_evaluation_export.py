import json
from pathlib import Path
import tempfile
import unittest

from evaluation.answers.export_host_run import export_run


class ExportHostRunTests(unittest.TestCase):
    def test_export_preserves_failures_sources_and_mechanical_metrics(self):
        cases = {
            'scope': 'Titles are retrieval targets, not answer-quality labels.',
            'cases': [
                dict(id='one', category='recall', question='What is a checksum?',
                     expected_titles=['Checksum'], review='Define it.'),
                dict(id='two', category='explanation', question='Why can salt melt ice?',
                     expected_titles=['Freezing-point depression'], review='Explain the cause.'),
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
                     response={'content': 'It detects transfer errors. [S1]'},
                     evidence=[dict(id='one', title='Checksum', text='A checksum detects errors.')],
                     host_elapsed_ms=1000, first_content_arrival_ms=400, prompt_tokens=200),
                dict(case_id='two', question='Why can salt melt ice?', status='ERROR',
                     response=None, error='timeout', evidence=[], host_elapsed_ms=None,
                     first_content_arrival_ms=None, prompt_tokens=300),
            ],
        }
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            run_path = root / 'run.json'
            cases_path = root / 'cases.json'
            run_path.write_text(json.dumps(run), encoding='utf-8')
            cases_path.write_text(json.dumps(cases), encoding='utf-8')
            result = export_run(run, cases, run_path, cases_path)

        self.assertEqual(result['summary']['case_count'], 2)
        self.assertEqual(result['summary']['generation_status_counts'], {'ERROR': 1, 'RECORDED': 1})
        self.assertEqual(result['summary']['recorded_answers_with_valid_citations'], 1)
        self.assertEqual(result['summary']['cases_with_any_exact_expected_title'], 1)
        self.assertEqual(result['results'][1]['error'], 'timeout')
        self.assertEqual(result['results'][0]['evidence'][0]['title'], 'Checksum')
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


if __name__ == '__main__':
    unittest.main()
