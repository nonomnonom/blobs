"""Score a frozen, native-parity candidate set with a pinned local ONNX reranker."""
import argparse
import importlib.metadata
import json
import math
from pathlib import Path
import platform
import time

from blobs_corpus.acquire import verify_source
from blobs_corpus.extract import write_json
from .host_replay import select_evidence, sha


def named_openings_first(ranked, titles):
    """Keep available definitions of multiple explicitly named concepts in context."""
    if len(titles) > 4 or len(set(titles)) != len(titles):
        raise ValueError('Require at most four distinct resolved source titles')
    if any(p.get('opening_title') not in {'', p['title']} for p in ranked):
        raise ValueError('Require verified opening-title metadata for every candidate')
    if len(titles) < 2:
        return ranked, []
    protected = []
    for title in titles:
        matches = [p for p in ranked if p['opening_title'] == title]
        if len(matches) == 1:
            protected.append(matches[0])
    identifiers = {p['passage_id'] for p in protected}
    return protected + [p for p in ranked if p['passage_id'] not in identifiers], list(
        p['passage_id'] for p in protected)


def reciprocal_rank_order(neural_order):
    """Fuse lexical and neural ranks using the published, fixed RRF k=60."""
    if set(neural_order) != set(range(len(neural_order))):
        raise ValueError('Neural order must be a permutation of the lexical candidates')
    neural_rank = {index: rank for rank, index in enumerate(neural_order, 1)}
    return sorted(range(len(neural_order)),
                  key=lambda index: (-(1 / (60 + index + 1) + 1 / (60 + neural_rank[index])), index))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--lock', type=Path, required=True)
    parser.add_argument('--artifacts', type=Path, required=True)
    parser.add_argument('--graph', choices=['model_quint8_avx2.onnx', 'model_qint8_arm64.onnx'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--selection', choices=['score', 'named-openings', 'rrf'], default='score',
                        help='Controlled selection comparison; both retain existing evidence budgets')
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    source = json.loads(args.input.read_text(encoding='utf-8'))
    if not source['complete'] or not source['baseline_parity'] or not all(
        row['identical'] for row in source['baseline_parity']
    ):
        raise ValueError('Require a completed native/host parity report')
    case_ids = [row['case_id'] for row in source['results']]
    if not 1 <= len(case_ids) <= 100 or len(set(case_ids)) != len(case_ids):
        raise ValueError('Require 1..100 unique cases')
    lock = json.loads(args.lock.read_text(encoding='utf-8'))
    if lock['format'] != 1:
        raise ValueError('Unsupported artifact lock')
    for name, item in lock['files'].items():
        if Path(name).name != name or item['filename'] != name:
            raise ValueError('Artifact must be a direct child of its local directory')
        verify_source(args.artifacts / name, item)
    for row in source['results']:
        candidates = row['candidates']
        if len(candidates) > 48 or len({p['passage_id'] for p in candidates}) != len(candidates):
            raise ValueError('Invalid bounded candidate set')
        if select_evidence(candidates, source['candidate']) != row['evidence']:
            raise ValueError('Saved candidate order does not reproduce baseline evidence')
        if args.selection == 'named-openings':
            named_openings_first(candidates, row['matched_titles'])

    import numpy as np
    import onnxruntime as ort
    from tokenizers import Tokenizer

    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    started = time.monotonic()
    session = ort.InferenceSession(str(args.artifacts / args.graph), options,
                                   providers=['CPUExecutionProvider'])
    load_ms = (time.monotonic() - started) * 1000
    expected_inputs = {'input_ids', 'attention_mask', 'token_type_ids'}
    if {item.name for item in session.get_inputs()} != expected_inputs:
        raise ValueError('Unexpected graph inputs')
    tokenizer = Tokenizer.from_file(str(args.artifacts / 'tokenizer.json'))
    unbounded = Tokenizer.from_file(str(args.artifacts / 'tokenizer.json'))
    unbounded.no_truncation()
    unbounded.no_padding()
    tokenizer.enable_truncation(max_length=512, strategy='only_second')
    tokenizer.enable_padding(pad_id=0, pad_token='[PAD]')
    root = Path(__file__).resolve().parents[2]
    report = dict(format=1, complete=False, scope='Development-only host CPU reranking. Same frozen candidates and evidence budgets; no new retrieval, cutoff or calibrated confidence. Not Android/ARM execution or phone performance. Inspected cases are not held out.',
        input_sha256=sha(args.input), lock_sha256=sha(args.lock), uv_lock_sha256=sha(root / 'uv.lock'),
        script_sha256=sha(Path(__file__)), selection_sha256=sha(Path(__file__).with_name('host_replay.py')),
        platform=platform.platform(), graph=args.graph, model=lock, providers=session.get_providers(),
        versions={name: importlib.metadata.version(name) for name in ['onnxruntime', 'tokenizers', 'numpy']},
        inference=dict(threads=4, inter_op_threads=1, batch_size=8, max_tokens=512,
                       truncation='only_second', input='resolved query paired with title, section, complete passage',
                       output='raw logit; descending; stable lexical-order tie break'),
        selection_policy=args.selection,
        fusion=dict(method='reciprocal rank fusion', constant=60, weights=[1, 1],
                    scope='Within the same bounded candidate set; no new retrieval or confidence calibration') if args.selection == 'rrf' else None,
        model_load_host_ms=load_ms, candidate=source['candidate'], cases=source['cases'], results=[])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    try:
        for row in source['results']:
            candidates = row['candidates']
            result = dict(case_id=row['case_id'], resolved_query=row['resolved_query'],
                          lexical_evidence=row['evidence'], scores=[], evidence=[])
            report['results'].append(result)
            started = time.monotonic()
            for start in range(0, len(candidates), 8):
                batch = candidates[start:start + 8]
                pairs = [(row['resolved_query'], f'{p["title"]}\n{p["section"]}\n{p["body"]}') for p in batch]
                lengths = [len(item.ids) for item in unbounded.encode_batch(pairs)]
                encoded = tokenizer.encode_batch(pairs)
                feed = {name: np.asarray([getattr(item, attr) for item in encoded], dtype=np.int64)
                        for name, attr in [('input_ids', 'ids'), ('attention_mask', 'attention_mask'),
                                           ('token_type_ids', 'type_ids')]}
                logits = session.run(None, feed)[0]
                if logits.shape != (len(batch), 1):
                    raise ValueError('Expected one relevance logit per passage')
                for index, (passage, score, length, tokens) in enumerate(zip(batch, logits[:, 0], lengths, encoded)):
                    score = float(score)
                    if not math.isfinite(score):
                        raise ValueError('Non-finite relevance score')
                    result['scores'].append(dict(lexical_rank=start + index + 1,
                        passage_id=passage['passage_id'], title=passage['title'], section=passage['section'],
                        score=score, untruncated_tokens=length, scored_tokens=sum(tokens.attention_mask),
                        truncated=length > 512))
            order = sorted(range(len(candidates)), key=lambda i: (-result['scores'][i]['score'], i))
            result['ranked_passage_ids'] = [candidates[i]['passage_id'] for i in order]
            if args.selection == 'rrf':
                order = reciprocal_rank_order(order)
            ranked = [candidates[i] for i in order]
            result['protected_opening_ids'] = []
            if args.selection == 'named-openings':
                ranked, result['protected_opening_ids'] = named_openings_first(ranked, row['matched_titles'])
            result['selection_passage_ids'] = [p['passage_id'] for p in ranked]
            result['matched_titles'] = row.get('matched_titles', [])
            result['evidence'] = select_evidence(ranked, source['candidate'])
            result['elapsed_host_ms'] = (time.monotonic() - started) * 1000
            write_json(args.output, report)
            print(f'{row["case_id"]}: {len(candidates)} scored', flush=True)
        report['complete'] = True
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        write_json(args.output, report)


if __name__ == '__main__':
    main()
