"""Promote a local host-answer run into a compact, reviewable benchmark record."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import statistics


def sha256(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_json(path: Path):
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict):
        raise ValueError(f'Expected a JSON object: {path}')
    return value


def _evidence(row):
    raw = row.get('evidence')
    if not isinstance(raw, list) or len(raw) > 6:
        raise ValueError(f'Invalid evidence list in {row.get("case_id")}')
    fields = ('id', 'title', 'publisher', 'url', 'license', 'updated', 'saved', 'section', 'text', 'edition')
    output = []
    for item in raw:
        if not isinstance(item, dict) or not isinstance(item.get('text'), str):
            raise ValueError(f'Invalid evidence passage in {row.get("case_id")}')
        output.append({key: item[key] for key in fields if key in item})
    return output


def export_run(run: dict, cases: dict, run_path: Path, cases_path: Path):
    if run.get('complete') is not True or run.get('format') != 1:
        raise ValueError('Only complete format-1 host runs can be promoted')
    definitions = cases.get('cases')
    rows = run.get('results')
    if not isinstance(definitions, list) or not isinstance(rows, list):
        raise ValueError('Case set or host results have the wrong shape')
    definitions_by_id = {item.get('id'): item for item in definitions if isinstance(item, dict)}
    if len(definitions_by_id) != len(definitions) or not definitions_by_id:
        raise ValueError('Case IDs must be present and unique')
    results_by_id = {}
    for row in rows:
        case_id = row.get('case_id') if isinstance(row, dict) else None
        if case_id not in definitions_by_id or case_id in results_by_id:
            raise ValueError(f'Unknown or duplicate result case: {case_id!r}')
        if row.get('question') != definitions_by_id[case_id].get('question'):
            raise ValueError(f'Run question differs from the pinned case: {case_id}')
        if row.get('status') not in {'RECORDED', 'NO_SOURCES', 'ERROR'}:
            raise ValueError(f'Unsupported generation status for {case_id}')
        results_by_id[case_id] = row
    if set(results_by_id) != set(definitions_by_id):
        raise ValueError('Run must preserve exactly one result, including failures, for every case')

    results = []
    for case in definitions:
        case_id = case['id']
        row = results_by_id[case_id]
        raw_response = row.get('response')
        answer = raw_response.get('content', '') if isinstance(raw_response, dict) else ''
        if not isinstance(answer, str):
            raise ValueError(f'Answer content is not text for {case_id}')
        evidence = _evidence(row)
        if row['status'] == 'NO_SOURCES' and (answer or evidence):
            raise ValueError(f'NO_SOURCES result must not contain an answer or evidence for {case_id}')
        source_ids = [int(value) for group in re.findall(r'\[(S\d+(?:\s*,\s*S\d+)*)\]', answer)
                      for value in re.findall(r'S(\d+)', group)]
        evidence_titles = {item.get('title', '').casefold() for item in evidence}
        expected_titles = case.get('expected_titles', [])
        results.append(dict(
            case_id=case_id,
            category=case.get('category'),
            question=case['question'],
            review_criteria=case.get('review', ''),
            generation_status=row['status'],
            answer=answer,
            error=row.get('error') if row['status'] == 'ERROR' else None,
            elapsed_ms=row.get('host_elapsed_ms'),
            first_content_ms=row.get('first_content_arrival_ms'),
            prompt_tokens=row.get('prompt_tokens'),
            source_count=len(evidence),
            citation_ids=source_ids,
            citations_present=bool(source_ids),
            citation_ids_in_range=bool(source_ids) and all(1 <= source_id <= len(evidence)
                                                           for source_id in source_ids),
            expected_titles=expected_titles,
            expected_titles_present=[title for title in expected_titles
                                     if title.casefold() in evidence_titles],
            evidence=evidence,
        ))

    statuses = Counter(item['generation_status'] for item in results)
    completed = [item for item in results if item['generation_status'] == 'RECORDED']
    latencies = [item['elapsed_ms'] for item in completed if isinstance(item['elapsed_ms'], (int, float))]
    first_content = [item['first_content_ms'] for item in completed
                     if isinstance(item['first_content_ms'], (int, float))]
    tokens = [item['prompt_tokens'] for item in completed if isinstance(item['prompt_tokens'], int)]
    summary = dict(
        case_count=len(results),
        categories=dict(sorted(Counter(item['category'] for item in results).items())),
        generation_status_counts=dict(sorted(statuses.items())),
        recorded_answers_with_valid_citations=sum(item['citation_ids_in_range'] for item in completed),
        recorded_answers_without_valid_citations=sum(not item['citation_ids_in_range'] for item in completed),
        recorded_answers_without_citations=sum(not item['citations_present'] for item in completed),
        recorded_answers_with_out_of_range_citations=sum(
            item['citations_present'] and not item['citation_ids_in_range'] for item in completed
        ),
        cases_with_any_exact_expected_title=sum(bool(item['expected_titles_present']) for item in results),
        cases_with_all_exact_expected_titles=sum(
            bool(item['expected_titles']) and item['expected_titles_present'] == item['expected_titles']
            for item in results
        ),
        host_elapsed_ms_median=statistics.median(latencies) if latencies else None,
        first_content_ms_median=statistics.median(first_content) if first_content else None,
        prompt_tokens_median=statistics.median(tokens) if tokens else None,
    )
    return dict(
        format=1,
        complete=True,
        scope=('Raw model answers and mechanical citation/title/timing observations for source-level review. '
               'This host run is not the Android product path, a <=12 GB device measurement, a semantic score, '
               'or a comparison against internet search plus a frontier model.'),
        inputs=dict(
            host_run=dict(file=run_path.name, bytes=run_path.stat().st_size, sha256=sha256(run_path)),
            case_set=dict(file=cases_path.name, sha256=sha256(cases_path)),
            retrieval_report_sha256=run.get('retrieval_report_sha256'),
            answer_script_sha256=run.get('script_sha256'),
            work_controller_sha256=run.get('work_controller_sha256'),
            native_bridge_sha256=run.get('native_bridge_sha256'),
        ),
        model=run.get('model'),
        runtime=dict(repository='ggml-org/llama.cpp', revision=run.get('runtime_commit'),
                     version=run.get('runtime_version'), server_sha256=run.get('server_sha256')),
        host=dict(platform=run.get('platform'), processor=run.get('processor'),
                  physical_memory_bytes=run.get('host_physical_memory_bytes')),
        inference=dict(context=run.get('context'), input_limit=run.get('input_limit'),
                       threads=run.get('threads'), batch=run.get('batch'), ubatch=run.get('ubatch'),
                       sampling_profile=run.get('sampling_profile'), sampling=run.get('sampling'),
                       system=run.get('system'), template_kwargs=run.get('template_kwargs')),
        summary=summary,
        results=results,
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--cases', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    result = export_run(read_json(args.run), read_json(args.cases), args.run, args.cases)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + '.partial')
    if temporary.exists():
        raise FileExistsError(temporary)
    try:
        temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        temporary.replace(args.output)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise
    print(json.dumps(result['summary'], ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
